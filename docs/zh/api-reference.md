# Java API 参考

受支持的 Java API 位于 `compileflow-api`。除非其他文档明确声明，Engine 实现类、解析器模型、Spring 内部类和 Server DTO
均不属于公共 Java API。

## 1. Engine 生命周期

每种模型类型和配置创建一个长生命周期 Engine。Engine 线程安全，并拥有执行器、runtime cache 与生成类的 class loader。

```java
ProcessEngine tbbpm = ProcessEngineFactory.createTbbpm();
ProcessEngine bpmn = ProcessEngineFactory.createBpmn();

try {
    // 执行流程。
} finally {
    tbbpm.close();
    bpmn.close();
}
```

Spring Boot 应用通常注入自动配置的单个 `ProcessEngine`。明确需要同时承载两种格式的平台，应在应用组合根中创建
`ProcessEngineRegistry`、将其注册为受容器管理的 Bean，并按 `ProcessModelType` 选择 Engine。自动配置模块提供这个 registry
类型，但 Starter 不会隐式创建多格式 registry。

## 2. 流程身份与内容

`ProcessDefinition` 表达显式 definition source，`ProcessRef` 标识已发布流程：

```java
ProcessRef.Version version =
        ProcessRef.version("order.validate", "2026-07-25.1");
ProcessRef.Alias alias =
        ProcessRef.alias("tenant-a", "order.validate", "production");
```

- `Version` 选择一个精确且不可变的已发布版本。
- `Alias` 选择一个权威的 stable/candidate 路由。

`ProcessRef.DEFAULT_NAMESPACE` 是固定的 `"default"` 作用域，只有不接收 namespace 的 overload 会选择它。显式接收 namespace
的 factory 与 record constructor 都拒绝 null、blank、首尾空白和非法标识符。Namespace 是逻辑资源作用域，本身不是授权边界。引用从不携带源码内容。

`ProcessDefinition` 描述内容如何提供或定位，但不携带 namespace、version、alias 或 model type：

```java
ProcessDefinition inline = ProcessDefinition.inline("order.validate", xml);
ProcessDefinition classpath =
        ProcessDefinition.classpath("order.validate", "flows/order.bpm");
```

模型类型由接收它的格式绑定 Engine 决定。`toString()` 会隐藏内联内容。Classpath 访问、UTF-8 解码与源码大小限制由
Engine definition loader 执行。

根 API 还公开四个受支持的协议基础类型：`ProcessDefinitionDigest` 计算稳定的精确定义摘要；`ProcessIdentifiers`
不经归一化地校验身份；`ProcessText` 提供显式 Unicode/文本操作。有界嵌套调用拒绝通过带 `CF_EXEC_013` 的
`CompileFlowException` 表达，其实现异常类型不属于 Supported API。
它们的公开形态与文档语义属于 2.x Java API 契约。

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

ProcessEngine 执行输入是精确 Process Definition 所声明 `param` 变量的封闭、部分 Map。传入 `return`、`inner` 或未声明字段会返回
`CF_VALIDATION_001`；缺失参数保留模型默认值，存在但值为 null 的键表示显式 null。`trigger(...)` 不是 Run Start：它从
指定 trigger entry 启动新的下游 invocation，因此其 Map 是任意已声明根变量组成的部分状态 seed，但同样拒绝未声明字段。
Durable Wait/Event completion 不复用这条状态 seed API。

两个 Map 入口都提供使用默认 options 的重载。类型化入口只是同一管线上的适配器，并使用配置的
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
        ProcessDefinition.classpath("order.validate", "flows/order.bpm");
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, Map.of("orderId", "A-42"));
```

已发布执行使用精确 Version 或 Alias 引用。inline 或 classpath definition 都是显式 source，不是隐藏的“最新版本”查询。

## 4. 请求元数据

`ProcessExecutionOptions` 将路由控制与业务变量隔离：

```java
ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .invocationId("request-42")
        .aliasRouting(new AliasRoutingOptions(
                "opaque-cohort-key", Map.of("region", "cn-hangzhou")))
        .build();
```

routing key 只参与目标选择，不会进入变量、结果、事件、日志、指标或 HTTP 响应。routing attributes 不可变，并且不能覆盖固定路由字段或
Engine-owned `__cf_` 前缀。路由输入限制为：key 最长 512 字符、最多 32 个 attribute、name 最长 128 字符、value 最长
2,048 字符，并且 UTF-8 总量不超过 32 KiB。invocation
ID 可省略；提供时会先去除首尾空白，长度不超过 128 个字符，必须以 ASCII 字母或数字开头，且只能包含 ASCII 字母、数字、`.`、`_`、
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

成功结果有 output 且无 error；失败结果有 `ProcessError` 且无 output。成功 output 本身可以为 `null`，因此应通过
`isSuccess()` 判断结果，不能根据 `getOutput()` 推断。`ProcessError` code 最长 128 字符，脱敏 message 最长 4,096 字符。

`map()` 只转换成功输出；失败时保留同一份 error 与 execution attribution。`orElse()` 和 `orElseGet()`
会有意丢弃失败信息，只应在 fallback 本身就是业务契约时使用。`orElseThrow()` 抛出
`ProcessExecutionException`；supplier 重载用于在应用边界转换异常：

```java
Map<String, Object> output = result.orElseThrow(() ->
        new IllegalStateException(
                result.getError().getCode() + ": " + result.getError().getMessage()));
```

`ProcessExecution` 只暴露 trace ID、invocation ID、逻辑 namespace、process code、可选的精确发布 version 与起止时间。
根 selector、Alias route 细节、routing input、model/source 诊断、变量与异常对象都留在各自 owner 边界。

## 6. Trigger 入口

`ProcessTrigger` 通过全局唯一 ID 选择触发入口节点，并携带可选 event：

```java
ProcessTrigger trigger = ProcessTrigger.on("paymentReceived", "approved");
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessRef.alias("default", "order.process", "production"),
        trigger,
        Map.of("orderId", "A-42"),
        ProcessExecutionOptions.defaults());
```

Trigger 通过与 execute 相同的来源、路由、runtime 与结果管线启动一次新的内存执行。它不会恢复持久化流程实例，也不承诺消息持久化、关联或状态恢复。
node ID 与可选 event selector 均最长 512 字符。

## 7. 本地管理与工具

`engine.runtime()` 返回稳定的 Engine 本地 runtime 管理视图：

```java
void warmUp(ProcessDefinition... definitions);
void load(ProcessRef.Version ref, ProcessDefinition definition);
void unload(ProcessRef.Version... refs);
```

`warmUp` 把精确定义编译到节点本地缓存，但不会创建或重绑定公开流程身份。带版本的 `load` 安装不可变版本
binding；两者都不会发布持久状态或修改 Alias。`unload` 只释放显式版本 ownership，Alias 生命周期属于控制面。

`engine.tooling()` 暴露不执行流程的 preflight 与源码生成：

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

`fast()` 执行结构校验，`strict()` 还会 dry-run 编译生成的 Java。两者默认 deadline 都是一分钟；builder 可替换为正数且能用毫秒表示的
`Duration`。Preflight 对一个 definition 返回一个 report，不会安装 runtime 或执行流程逻辑。

生成源码可能包含业务逻辑，应按敏感诊断输出处理。

## 8. 配置与数据映射

配置是不可变快照：

```java
ProcessEngineConfig.Builder tbbpmBuilder();

ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
        .dataMapper(customMapper)
        .build();
ProcessEngine engine = ProcessEngineFactory.create(config);
```

使用 `ProcessEngineConfig.tbbpmBuilder()` 或 `bpmnBuilder()`。Builder 统一管理 executor、cache、script、
compilation、definition loading、observability、class loader、mapper、component resolver、routing 和扩展贡献。外部配置由
Spring 边界解析一次，并构造成同一个不可变模型。

`ProcessDataMapper` 定义类型适配行为：

```java
Map<String, Object> toVariables(Object input);
<T> T fromVariables(Map<String, Object> variables, Class<T> outputType);
```

Mapper 失败是 typed engine failure。输出转换发生在流程执行后，不会回滚 action 已经产生的副作用。

## 9. 扩展 SPI

受支持的扩展契约位于 `com.alibaba.compileflow.engine.spi`。可以直接通过
`ProcessEngineConfig.Builder`、Spring bean 或 `ProcessEnginePlugin` 注册。构建配置时，引擎会校验并冻结最终能力集合。
`ProcessAliasRouteSource` 是例外：它是通过 `aliasRouteSource(...)` 或单个 Spring bean 显式配置的唯一 serving 权威，
不允许由 plugin 贡献。

扩展点、优先级、ServiceLoader 配置、生命周期和线程安全要求见[扩展指南](extension-guide.md)。

## 10. 部署 API

流程发布与路由属于 `compileflow-deploy-api` 的独立产品边界。`ProcessDeploymentService` 发布不可变版本并执行带 revision
前置条件的 rollout 命令。发布不会安装 runtime，也不会切流；回滚创建新 rollout，不改写历史。

应用应依赖 `compileflow-deploy-api` 中的命令与视图，不应依赖控制面 repository 或 runtime 实现包。

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

每次 Start 都要求调用方分配 `ProcessRunId`。它是永久绑定该 Run occurrence 的地址与恢复 handle，不是通用幂等键。
重复 Start 返回 `RUN_ALREADY_EXISTS`；响应不确定时使用 `getRun(runId)` 核实。HTTP、MQ 或应用请求的等价性仍由
拥有该协议的 adapter 负责。

准入遵循以下规则：

- 显式 definition 经安全加载后冻结为不可变快照。
- Exact Version 不会回退到其他来源。
- Alias 只解析一次，Run 永久绑定所选的精确已存储 Process。
- `AliasRoutingOptions` 只适用于 Alias admission。
- 恢复按 `processId` 读取已存储 Process，不会重新解析 Alias 或读取当前 Classpath 内容。

`completeWait` 接收 opaque one-shot `WaitToken` 和 typed partial result。重放相同的已提交 token 与 canonical
result 属于 current-equivalent，不同结果会冲突。Raw token 应按凭据保护，不能写入日志、metric label、浏览器可见 URL、
第三方 metadata 或 Workbench view。

`getRun` 与 `listRuns` 不读取 payload；`getRunResult` 返回 `ProcessRunResult.NotFound`、`NotCompleted`、
`Succeeded`、`Failed` 或 `Cancelled`。

公开 Run 生命周期为：

| 状态 | 含义 |
|---|---|
| `RUNNABLE` | 已提交，等待兼容 Worker。 |
| `RUNNING` | 当前 Turn 持有租约。 |
| `WAITING` | 等待外部完成、Timer 或 Effect。 |
| `SUCCEEDED`、`FAILED`、`CANCELLED` | 终态。 |

取消意图和 `ProcessRunControl` 与生命周期正交。`ACTIVE` 允许执行，`PAUSE_REQUESTED` 记录协作式收敛，
`PAUSED` 阻止新的业务 Turn/Effect admission，但 Timer、Wait completion、Outbox、对账、取消和维护工作继续。

`DurableOperatorService` 是用于 Run Timeline、Pause/Resume、Outbox 裁决和 UNKNOWN Effect review 的独立
最小权限门面。不要直接暴露为 HTTP 或 RPC；transport adapter 必须补充认证、授权、必要审批、限流和审计。

`bpmCall` 或 `callActivity` 使用同 Run `ProcessInvocation` frame，不创建可独立查询的 Child Run。Outbox 为
at-least-once 且不保证顺序，Sink 按稳定 `eventId` 去重。Java API 使用 typed keyset cursor，opaque page-token
协议由 transport adapter 负责。

捕获 `DurableProcessException` 后按 `DurableErrorCode` 分支，不要解析消息文本。Action 的
`execution="replayable|effect"` 选择 Durable 执行语义；只有调用后结果为 `UNKNOWN` 的 Effect 需要经过认证的
Operator 裁决。准入、Wait token、Process Call、运维与 Effect 恢复见
[Durable Process 使用指南](durable-process.md)。

## 12. 失败边界

预期执行失败由 `ProcessResult` 表示。非法配置、错误 API 输入、provider 不可用、生命周期误用，以及其他无法形成执行结果的失败，使用
typed `CompileFlowException` 层次。

它的可选诊断 context 明确不属于权威事实，并限制为 32 个 entry、128 字符 key、2,048 字符文本值和 32 个集合元素。
可变集合会被快照；不支持或超限的值只保存固定 omission marker。诊断增强绝不能携带流程变量、payload、源码或凭据，也不能改变执行结果。

翻译任何失败形式时，都不要记录原始变量、源码、routing key、凭据、完整本地路径或任意应用对象。

## 相关文档

- [快速开始](quick-start.md)
- [配置](configuration.md)
- [扩展指南](extension-guide.md)
- [热部署](hot-deploy.md)
- [Durable Process](durable-process.md)
- [支持面清单](../architecture/06-SUPPORTED_SURFACES.zh.md)
