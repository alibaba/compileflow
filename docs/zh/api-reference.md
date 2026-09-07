# Java API 参考

受支持的 Java API 位于 `compileflow-api`。除非其他文档明确声明，引擎实现类、解析器模型、Spring 内部类和 Server DTO
均不属于公共 Java API。

## 1. 引擎生命周期

每组独立的资源与配置使用一个长生命周期引擎，与流程定义格式无关。引擎会发现已安装的 TBBPM/BPMN 前端解析模块，
并管理执行器、运行时缓存和生成类所用的 `ClassLoader`。

```java
try (ProcessEngine engine = ProcessEngineFactory.create()) {
    engine.execute(ProcessDefinition.inline(ProcessModelType.TBBPM, "order", orderXml), input);
    engine.execute(ProcessDefinition.inline(ProcessModelType.BPMN, "payment", paymentXml), input);
}
```

Spring Boot 与 Workbench 使用相同的单引擎装配方式。添加 BPMN 前端解析模块后，同一引擎即可同时执行 TBBPM 和 BPMN 流程。

## 2. 流程身份与内容

`ProcessDefinition` 提供明确的流程定义来源，`ProcessRef` 标识已发布流程：

```java
ProcessRef.Version version =
        ProcessRef.version("order.validate", "2026-07-25.1");
ProcessRef.Alias alias =
        ProcessRef.alias("tenant-a", "order.validate", "production");
```

- `Version` 选择一个精确且不可变的已发布版本。
- `Alias` 通过稳定版本或候选版本路由选择目标。

`ProcessRef.DEFAULT_NAMESPACE` 是固定的 `"default"` 作用域，只有不接收命名空间的重载会使用它。显式接收命名空间的工厂方法和
记录类构造器都会拒绝 `null`、空字符串、首尾空白和非法标识符。命名空间用于划分逻辑资源，本身不是授权边界。引用不携带源码内容。

`ProcessDefinition` 描述流程内容的提供或定位方式，并明确指定模型类型，但不携带命名空间、版本或别名：

```java
ProcessDefinition inline = ProcessDefinition.inline(ProcessModelType.TBBPM, "order.validate", xml);
ProcessDefinition classpath =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.validate", "flows/order.bpm");
```

模型类型由流程定义明确指定，引擎不会根据内容或文件名猜测格式。`toString()` 会隐藏内联内容。类路径访问、UTF-8 解码和源码大小限制由
引擎的流程定义加载器执行。

根 API 还公开受支持的协议基础类型：`ProcessDefinitionDigest` 计算稳定的精确定义摘要；`ProcessIdentifiers`
不经归一化地校验身份；`ProcessText` 提供显式 Unicode/文本操作。根错误使用 `CompileFlowException` 和 `ErrorCode`；
有界嵌套调用被拒绝时使用 `CF_EXEC_013`，对应的实现异常类型不属于受支持 API。这些公开类型及其文档语义属于 2.x Java API 契约。

## 3. 执行

唯一规范执行模型是 `Map<String, Object>`：

```java
ProcessResult<Map<String, Object>> execute(
        ProcessRef ref,
        Map<String, Object> variables,
        ProcessExecutionOptions options);

ProcessResult<Map<String, Object>> execute(
        ProcessDefinition definition,
        Map<String, Object> variables,
        ProcessExecutionOptions options);
```

`ProcessEngine` 的输入是一个封闭的部分映射，只能包含目标流程定义声明的 `param` 变量。传入 `return`、`inner` 或未声明字段会返回
`CF_VALIDATION_001`；缺失的参数保留模型默认值，键存在但值为 `null` 表示显式传入空值。`trigger(...)` 不是 Durable Run 的启动操作：
它从指定触发入口开始一次新的下游调用，其映射可包含任意已声明的根变量，但同样拒绝未声明字段。Durable 的 Wait 或事件完成操作不复用该接口。

两个 Map 入口都提供使用默认选项的重载。类型化入口复用同一执行管线，并使用配置的
`ProcessDataMapper`：

```java
<I, O> ProcessResult<O> execute(
        ProcessRef ref,
        I input,
        Class<O> outputType,
        ProcessExecutionOptions options);

<I, O> ProcessResult<O> execute(
        ProcessDefinition definition,
        I input,
        Class<O> outputType,
        ProcessExecutionOptions options);
```

示例：

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.validate", "flows/order.bpm");
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, Map.of("orderId", "A-42"));
```

执行已发布流程时，应使用精确的 Version 或 Alias 引用。内联定义和类路径定义都有明确来源，不会隐式查询“最新版本”。

## 4. 请求元数据

`ProcessExecutionOptions` 将路由控制与业务变量隔离：

```java
ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .invocationId("request-42")
        .aliasRouting(new AliasRoutingOptions(
                "opaque-cohort-key", Map.of("region", "cn-hangzhou")))
        .build();
```

路由键只参与目标选择，不会进入变量、结果、事件、日志、指标或 HTTP 响应。路由属性不可变，也不能覆盖固定路由字段或
引擎保留的 `__cf_` 前缀。路由键最长 512 个字符；路由属性最多 32 个，名称最长 128 个字符，值最长 2,048 个字符，
全部路由输入的 UTF-8 编码总量不超过 32 KiB。调用 ID 可以省略；提供后按原值校验，不会归一化，并拒绝首尾空白。其长度不超过 128 个字符，
必须以 ASCII 字母或数字开头，且只能包含 ASCII 字母、数字、`.`、`_`、
`:`、`@` 或 `-`。

## 5. 结果与错误

`ProcessResult<T>` 是不可变的成功或失败值，并携带受控执行归因：

```java
boolean isSuccess();
boolean isFailure();
T getOutput();
ProcessError getError();
ProcessExecution getExecution();
<U> ProcessResult<U> map(Function<? super T, ? extends U> mapper);
T orElse(T fallback);
T orElseGet(Supplier<? extends T> fallback);
T orElseThrow();
<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;
```

成功结果包含输出且不含错误；失败结果包含 `ProcessError` 且不含输出。成功输出本身可以为 `null`，因此应通过
`isSuccess()` 判断结果，不能根据 `getOutput()` 推断。`ProcessError` 的错误码最长 128 个字符，脱敏消息最长 4,096 个字符。

`map()` 只转换成功输出；失败时保留原有错误和执行归因。`orElse()` 与 `orElseGet()`
会丢弃失败信息，只应在回退值本身就是业务契约时使用。`orElseThrow()` 抛出
`ProcessExecutionException`；接收异常供应器的重载用于在应用边界转换异常：

```java
Map<String, Object> output = result.orElseThrow(() ->
        new IllegalStateException(
                result.getError().getCode() + ": " + result.getError().getMessage()));
```

`ProcessExecution` 只公开跟踪 ID、调用 ID、逻辑命名空间、流程编码、可选的精确发布版本和起止时间。
根选择器、Alias 路由细节、路由输入、模型或来源诊断、变量和异常对象都保留在各自的职责边界内。

## 6. Trigger 入口

`ProcessTrigger` 通过全局唯一 ID 选择触发入口节点，并可指定事件：

```java
ProcessTrigger trigger = ProcessTrigger.on("paymentReceived", "approved");
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessRef.alias("default", "order.process", "production"),
        trigger,
        Map.of("orderId", "A-42"),
        ProcessExecutionOptions.defaults());
```

Trigger 通过与 `execute` 相同的来源、路由、运行时和结果管线启动一次新的内存执行。它不会恢复持久化流程实例，也不提供消息持久化、关联或状态恢复。
节点 ID 和可选的事件选择器均最长 512 个字符。

## 7. 本地管理与工具

`engine.runtime()` 返回稳定的引擎本地运行时管理视图：

```java
void warmUp(ProcessDefinition... definitions);
void load(ProcessRef.Version ref, ProcessDefinition definition);
void unload(ProcessRef.Version... refs);
```

`warmUp` 把精确定义编译到节点本地缓存，但不会创建或重绑定公开流程身份。带版本的 `load` 安装不可变版本
绑定；两者都不会发布持久状态或修改 Alias。`unload` 只释放显式版本的本地资源，Alias 生命周期由控制面管理。

`engine.tooling()` 提供不会执行流程的预检与源码生成功能：

```java
ProcessPreflightReport preflight(
        ProcessDefinition definition, ProcessPreflightOptions options);
String generateJavaCode(ProcessDefinition definition);

static ProcessPreflightOptions strict();
static ProcessPreflightOptions fast();
Duration getTimeout();

ProcessPreflightReport.OverallStatus getOverallStatus();
List<ProcessPreflightReport.Item> getItems();
```

`fast()` 执行结构校验，`strict()` 还会试编译生成的 Java。两者的默认截止时间都是一分钟；构建器可将其替换为正数且能以整毫秒表示的
`Duration`。预检为每个流程定义返回一份报告，不会安装运行时或执行流程逻辑。

生成源码可能包含业务逻辑，应按敏感诊断输出处理。

## 8. 配置与数据映射

配置是不可变快照：

```java
ProcessEngineConfig.Builder builder();

ProcessEngineConfig config = ProcessEngineConfig.builder()
        .dataMapper(customMapper)
        .build();
ProcessEngine engine = ProcessEngineFactory.create(config);
```

使用 `ProcessEngineConfig.builder()`。构建器统一管理执行器、缓存、脚本、编译、流程定义加载、可观测性、类加载器、映射器、组件解析器、路由和扩展配置。外部配置由
Spring 边界解析一次，并构造成同一个不可变模型。

`ProcessDataMapper` 定义类型适配行为：

```java
Map<String, Object> toVariables(Object input);
<T> T fromVariables(Map<String, Object> variables, Class<T> outputType);
```

映射失败属于具有明确类型的引擎失败。输出转换发生在流程执行后，不会回滚流程动作已经产生的副作用。

## 9. 扩展 SPI

受支持的扩展契约位于 `com.alibaba.compileflow.engine.spi`。可以直接通过
`ProcessEngineConfig.Builder`、Spring bean 或 `ProcessEnginePlugin` 注册。构建配置时，引擎会校验并冻结最终能力集合。
`ProcessAliasRouteSource` 是例外：它必须通过 `aliasRouteSource(...)` 或单个 Spring Bean 显式配置，并作为唯一的在线路由依据，
不允许由插件提供。

扩展点、优先级、ServiceLoader 配置、生命周期和线程安全要求见[扩展指南](extension-guide.md)。

## 10. 部署 API

流程发布与路由属于 `compileflow-deploy-api` 的独立产品边界。`ProcessDeploymentService` 发布不可变版本，并执行带修订版本
前置条件的流量调整命令。发布不会安装运行时，也不会改变流量；回滚会创建新的流量调整记录，不改写历史。

应用应依赖 `compileflow-deploy-api` 中的命令与视图，不应依赖控制面存储库或运行时实现包。

线协议载荷、解析器、规范 JSON 编解码器和投影键由 `compileflow-deploy-protocol` 单独发布；领域 API 不依赖该表示模块。

## 11. Durable Process API

Durable 执行是 `compileflow-durable-api` 中的独立产品边界。它不会扩展同步
`ProcessEngine`，加入 Durable Starter 也不会让普通 `execute(...)` 自动持久化。与存储无关的应用门面为：

```java
public interface DurableProcessEngine {
    ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias,
                         Map<String, ?> input, AliasRoutingOptions options);
    ProcessRun completeWait(WaitToken waitToken, Map<String, ?> result);
    ProcessRun cancel(ProcessRunId runId);
    Optional<ProcessRun> getRun(ProcessRunId runId);
    ProcessRunPage listRuns(ProcessRunQuery query);
    ProcessRunResult getRunResult(ProcessRunId runId);
}
```

每次 Start 都要求调用方分配 `ProcessRunId`。它是永久绑定该 Run 的地址和恢复句柄，不是通用幂等键。
重复 Start 返回 `RUN_ALREADY_EXISTS`；响应不确定时使用 `getRun(runId)` 核实。HTTP、MQ 或应用请求是否等价，仍由
对应协议的适配器判断。

准入遵循以下规则：

- 显式流程定义经安全加载后冻结为不可变快照。
- Exact Version 不会回退到其他来源。
- Alias 只解析一次，Run 永久绑定所选的精确已存储 Process。
- `AliasRoutingOptions` 只适用于通过 Alias 启动 Run 时的准入过程。
- 恢复时按 `processId` 读取已存储 Process，不会重新解析 Alias，也不会读取当前类路径中的内容。

`completeWait` 接收不透明、一次性的 `WaitToken` 和类型化的部分结果。使用相同令牌和规范化结果重复提交已经完成的请求不会改变状态，
提交不同结果则会产生冲突。原始令牌应按凭据保护，不能写入日志、指标标签、浏览器可见 URL、第三方元数据或 Workbench 视图。

`getRun` 与 `listRuns` 不读取载荷；`getRunResult` 返回 `ProcessRunResult.NotFound`、`NotCompleted`、
`Succeeded`、`Failed` 或 `Cancelled`。

公开 Run 生命周期为：

| 状态                               | 含义                            |
| ---------------------------------- | ------------------------------- |
| `RUNNABLE`                         | 已提交，等待兼容的工作节点。    |
| `RUNNING`                          | 当前执行轮次持有租约。          |
| `WAITING`                          | 等待外部完成、Timer 或 Effect。 |
| `SUCCEEDED`、`FAILED`、`CANCELLED` | 终态。                          |

取消意图和 `ProcessRunControl` 与生命周期正交。`ACTIVE` 允许执行，`PAUSE_REQUESTED` 记录协作式收敛，
`PAUSED` 阻止新的业务执行轮次和 Effect 准入，但 Timer、Wait 完成、Outbox、对账、取消和维护工作仍会继续。

`DurableOperatorService` 是用于查看 Run 时间线、执行 Pause/Resume、裁决 Outbox 和处理 `UNKNOWN` Effect 的独立最小权限门面。
不要直接通过 HTTP 或 RPC 暴露；传输适配器必须补充认证、授权、必要审批、限流和审计。

`bpmCall` 或 `callActivity` 使用同一 Run 中的 `ProcessInvocation` 调用帧，不创建可独立查询的第二个 Run。Outbox 至少投递一次且不保证顺序，
接收端按稳定的 `eventId` 去重。Java API 使用类型化的键集游标，不透明分页令牌协议由传输适配器负责。

捕获 `DurableProcessException` 后按 `DurableErrorCode` 分支，不要解析消息文本。Action 的
`execution="replayable|effect"` 选择 Durable 执行语义。调用后结果为 `UNKNOWN` 的 Effect 先遵循配置的自动恢复策略；
只有 `reviewRequired()` 为 `true` 时，才需要经过认证的运维人员裁决。准入、Wait 令牌、流程调用、运维与 Effect 恢复见
[Durable Process 使用指南](durable-process.md)。

## 12. 失败边界

预期执行失败由 `ProcessResult` 表示。非法配置、错误的 API 输入、存储实现不可用、生命周期误用，以及其他无法形成执行结果的失败，使用
类型明确的 `CompileFlowException` 异常层次。

它的可选诊断上下文不属于权威事实，并限制为 32 个条目、键最长 128 个字符、文本值最长 2,048 个字符，集合最多 32 个元素。
可变集合会生成快照；不支持或超限的值只保存固定的省略标记。诊断信息绝不能携带流程变量、载荷、源码或凭据，也不能改变执行结果。

转换任何失败形式时，都不要记录原始变量、源码、路由键、凭据、完整本地路径或任意应用对象。

## 相关文档

- [快速开始](quick-start.md)
- [配置](configuration.md)
- [扩展指南](extension-guide.md)
- [热部署](hot-deploy.md)
- [Durable Process](durable-process.md)
- [支持面清单](architecture/supported-surfaces.md)
