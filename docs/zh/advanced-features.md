# Engine 高级用法

以下内容覆盖 Engine 本地预热、tooling、class-loader scope、typed result 与精确版本执行。流程发布与路由见
[热部署](hot-deploy.md)。

## 预热已知定义

首次执行可能需要解析、代码生成与编译。应在应用 ready 阶段校验并加载关键定义：

```java
@Component
public final class FlowWarmup {

    private final ProcessEngine engine;

    public FlowWarmup(ProcessEngine engine) {
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warm() {
        ProcessDefinition definition =
                ProcessDefinition.classpath("order.process", "flows/order.bpm");
        ProcessPreflightReport report = engine.tooling()
                .preflight(definition, ProcessPreflightOptions.strict());
        if (report.getOverallStatus() != ProcessPreflightReport.OverallStatus.PASS) {
            throw new IllegalStateException(
                    "Flow preflight failed: "
                            + report.getItems().stream()
                                    .filter(item -> item.getStatus()
                                            != ProcessPreflightReport.ItemStatus.PASS)
                                    .map(item -> item.getType() + "/" + item.getStatus()
                                            + ": " + item.getMessage())
                                    .toList());
        }
        engine.runtime().warmUp(definition);
    }
}
```

`warmUp` 只把精确内容编译到当前 Engine 的本地 runtime cache，不创建 code 或 version binding。它不是流程发布，不修改
Alias，也不能证明其他节点 ready。

已发布 exact version 的部署 runtime 使用：

```java
engine.runtime().load(
        ProcessRef.version("default", "order.process", "2026-07-25.1"),
        ProcessDefinition.inline("order.process", xml));
```

普通应用代码应让部署数据面拥有这个 exact-version lifecycle。

## 生成 Java 源码

`ProcessToolingService` 为显式 definition 生成 Java source，但不执行：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath("order.process", "flows/order.bpm");
String javaSource = engine.tooling().generateJavaCode(definition);
```

生成源码可能暴露业务规则与 action call，应按敏感诊断输出处理。Parser AST class 仍是实现细节。

## 使用受控 Class Loader

生成流程类通过 Engine 配置的 parent class loader 解析应用 action：

```java
try (URLClassLoader applicationLoader = new URLClassLoader(
        new URL[] {Path.of("/opt/application/actions.jar").toUri().toURL()},
        FlowWarmup.class.getClassLoader())) {

    ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
            .classLoader(applicationLoader)
            .build();
    try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
        engine.execute(definition, variables).orElseThrow();
    }
}
```

Class loader 是本地 `ProcessRuntimeIdentity` identity 的一部分。Engine 创建后不要改变它的有效 classpath。外部提供的 class loader
由应用拥有并关闭；Engine 只关闭自身资源。

## 映射 Typed Input/Output

Typed execution 通过配置的 `ProcessDataMapper` 适配唯一规范 variable-map 管线：

```java
ProcessResult<OrderResponse> result = engine.execute(
        definition,
        request,
        OrderResponse.class,
        ProcessExecutionOptions.defaults());
```

Input conversion failure 在执行开始前返回 `CF_EXEC_010`。Output conversion failure 在流程完成后返回
`CF_EXEC_009`，不会回滚 action 副作用。

只有 conversion 与 failure semantics 都确定且线程安全时，才注入自定义 mapper：

```java
ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
        .dataMapper(customMapper)
        .build();
```

## 处理结果

```java
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, variables);

if (result.isFailure()) {
    ProcessError error = result.getError();
    logger.warn(
            "Process failed: invocationId={}, errorCode={}",
            result.getExecution().getInvocationId(),
            error.getCode());
    return;
}

Map<String, Object> output = result.getOutput();
```

分支判断使用稳定 error code，不解析 human message，也不记录 raw variable、source content、routing key 或任意 error context。

`map`、`orElse`、`orElseGet` 与 `orElseThrow` 只操作不可变结果，不会重试或改变原执行。

## 执行已发布 Version 或 Alias

```java
ProcessRef.Version exact =
        ProcessRef.version("default", "order.process", "2026-07-25.1");

ProcessRef.Alias production =
        ProcessRef.alias("default", "order.process", "production");

ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .aliasRouting(new AliasRoutingOptions(customerId))
        .build();

ProcessResult<OrderResponse> result = engine.execute(
        production,
        request,
        OrderResponse.class,
        options);
```

Exact version 必须已本地安装。Alias 读取 local-ready stable/candidate route；所选 version 不可用时 fail-closed，不存在
previous-version fallback。

## 扩展 Engine

使用 `compileflow-api` 中的扩展接口：

- `ProcessEnginePlugin`：组合配置贡献；
- `ProcessEventListener`：事件；
- `ScriptExecutor`：脚本语言；
- `RetryPolicy` 与 `FailureHandler`：具名 invocation policy 扩展；
- 具名 `ProcessAliasTargetingPolicy`：由 route 显式绑定的企业 targeting override。

扩展能力在构建配置时注册，并在 Engine 生命周期内保持不变。不支持运行时替换 plugin。

另见[扩展指南](extension-guide.md)、[API 参考](api-reference.md)与[配置](configuration.md)。
