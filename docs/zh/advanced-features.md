# 引擎高级用法

本页介绍引擎预热、工具接口、类加载器作用域、类型化结果和精确版本执行。流程发布与路由见
[热部署](hot-deploy.md)。

## 预热已知定义

首次执行可能需要解析、生成代码并编译。可以在应用就绪阶段校验并加载关键流程：

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
                ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
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

`warmUp` 只将指定内容编译到当前引擎的本地运行时缓存，不创建流程代码或版本绑定。它不会发布流程、修改别名，
也不能代表其他节点已经就绪。

部署运行时可以加载已发布的精确版本：

```java
engine.runtime().load(
        ProcessRef.version("default", "order.process", "2026-07-25.1"),
        ProcessDefinition.inline(ProcessModelType.TBBPM, "order.process", xml));
```

精确版本的生命周期应由部署运行时管理。

## 生成 Java 源码

`ProcessToolingService` 可以为指定流程定义生成 Java 源码，但不会执行流程：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
String javaSource = engine.tooling().generateJavaCode(definition);
```

生成源码可能暴露业务规则和动作调用，应按敏感诊断信息处理。解析器的 AST 类型属于实现细节。

## 使用受控类加载器

生成的流程类通过引擎配置的父类加载器解析应用动作：

```java
try (URLClassLoader applicationLoader = new URLClassLoader(
        new URL[] {Path.of("/opt/application/actions.jar").toUri().toURL()},
        FlowWarmup.class.getClassLoader())) {

    ProcessEngineConfig config = ProcessEngineConfig.builder()
            .classLoader(applicationLoader)
            .build();
    try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
        engine.execute(definition, variables).orElseThrow();
    }
}
```

类加载器是本地 `ProcessRuntimeIdentity` 的组成部分。引擎创建后，不要改变其有效类路径。外部提供的类加载器
由应用负责关闭；引擎只关闭自身资源。

## 映射类型化输入输出

类型化执行通过 `ProcessDataMapper` 与统一的变量映射管线衔接：

```java
ProcessResult<OrderResponse> result = engine.execute(
        definition,
        request,
        OrderResponse.class,
        ProcessExecutionOptions.defaults());
```

输入转换失败会在流程开始前返回 `CF_EXEC_010`。输出转换失败会在流程完成后返回
`CF_EXEC_009`，且不会回滚已经发生的动作副作用。

只有转换与失败语义明确，并且实现线程安全时，才注入自定义映射器：

```java
ProcessEngineConfig config = ProcessEngineConfig.builder()
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

分支判断应使用稳定错误码，不要解析提示文本，也不要记录原始变量、流程源码、路由键或任意错误上下文。

`map`、`orElse`、`orElseGet` 与 `orElseThrow` 只操作不可变结果，不会重试或改变原执行。

## 执行已发布版本或别名

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

精确版本必须已经安装到当前节点。别名只会从本地就绪的稳定版本和候选版本中选择；所选版本不可用时执行失败，
不会回退到此前版本。

## 扩展引擎

使用 `compileflow-api` 中的扩展接口：

- `ProcessEnginePlugin`：组合配置贡献；
- `ProcessEventListener`：事件；
- `ScriptExecutor`：脚本语言；
- `RetryPolicy` 与 `FailureHandler`：具名调用策略扩展；
- 具名 `ProcessAliasTargetingPolicy`：由路由显式绑定的目标选择策略。

扩展能力在构建配置时注册，并在引擎生命周期内保持不变，不支持在运行时替换插件。

另见[扩展指南](extension-guide.md)、[API 参考](api-reference.md)与[配置](configuration.md)。
