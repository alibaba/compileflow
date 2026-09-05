# CompileFlow 执行流程

一次调用从公共 API 到类型化结果，依次经过请求身份、精确 source 解析、本地 runtime identity、路由、编译与执行归因。

第 1–14 节描述普通 `ProcessEngine`。Durable `Start/Trigger` 不复用这套 invocation 生命周期，其持久化执行流程在第
15 节单独说明。

## 1. 公共入口

规范 Map 入口接收已有 `ProcessRef` 或显式 `ProcessDefinition`、variables 与
`ProcessExecutionOptions`。

```java
ProcessResult<Map<String, Object>> result =
    engine.execute(ref, variables, options);
```

Typed input 在进入该路径前由 `ProcessDataMapper` 转换；typed output 在 Map 执行成功后转换。输入映射失败在流程执行前返回
`CF_EXEC_010`；输出映射失败在流程完成后返回 `CF_EXEC_009`，不代表 action 已产生的副作用被回滚。

`ProcessExecutionOptions` 包含仅供请求使用的 invocation 与 routing metadata。Routing key 和 routing attributes 绝不会进入
variables。

## 2. 请求归一化

`DefaultProcessEngine` 将公共输入转换为一个内部 `ProcessRuntimeRequest`：

| 公共输入                  | 内部 request                              |
|---------------------------|-------------------------------------------|
| `ProcessDefinition`       | default namespace/code 与显式 source      |
| `ProcessRef.Version`      | namespace/code/exact version              |
| `ProcessRef.Alias`        | namespace/code/Alias，等待 route selection |
| admin exact load                        | exact version reference 与显式 definition   |

Definition code 必须与 version-reference code 一致。直接 definition 除文档规定的 default namespace 和接收它的格式绑定
Engine 外，不会获得隐式 namespace、version、Alias 或 model type。

## 3. 执行作用域

Runtime 解析前，Engine 打开 operation scope 与 `EngineExecutionContext`。Context 拥有：

- invocation ID；
- routing key 与不可变 routing attributes；
- 请求流程引用；
- 解析过程中确定的有效 route attribution；
- 开始与完成时间。

`EngineExecutionContextHolder` 是内部组件使用的 scoped `ThreadLocal` bridge。Engine 始终在 `finally` 清理，它不是全局业务状态。

生命周期事件是封闭的类型化 `ProcessEvent` record 集合。完成和失败事件携带返回给调用方的受控 `ProcessExecution`，并使用独立的
`ProcessEvent.ExecutionAttribution` 携带运维归因；失败事件额外携带类型化 `ProcessError`。开始事件只包含流程 code 和
invocation ID。事件无法携带 routing key、流程变量、source 内容、任意 metadata 或原始异常。

公共 `ProcessExecution` 是封闭类型，只包含 trace 与 invocation ID、namespace、process code、可选的精确已发布 Version 和
开始/完成时间。运维事件归因包含父 invocation 与调用深度、model type、source digest 和准入 Alias 事实。两者都不暴露通用
metadata map。

## 4. 版本解析

`ProcessRuntimeResolver` 解析有效版本：

1. `ProcessRef.Version` 已是精确版本，绕过 Alias 选择。
2. `ProcessRef.Alias` 从配置的 `ProcessAliasRouteSource` 读取一条 route，先应用其显式绑定的具名
   `ProcessAliasTargetingPolicy`，未覆盖时再使用协议固定的百分比分桶。
3. 所有 `ProcessDefinition` 变体都不带版本。

已发布 Alias 选择使用一套有界接管协议：

- 所选 target 必须映射到 observed Alias revision 的 stable 或 candidate，具名 targeting policy 也必须在本节点可用；
- 若 route handoff 期间精确 runtime 获取失败，Core 会重读并完整 admission 一次；
- 多格式分发进入具体格式引擎前，会先 retain 所选根 runtime；
- 接管成功后，更新的 Alias revision 只影响后续调用。

选择 observed Alias authority 外的 target 或缺失 route 指定的 targeting policy 都会 fail-closed。重选不是
previous-version 或 latest-version 降级，而是完整评估一条更新后的 serving route。不存在 active-version lookup 或请求内随机降级。

在部署节点上，精确 version 必须存在于 `InstalledVersionState`。只有 version identity 而本地未 ready 的
请求，不能在执行热路径触发不可信远程读取。

## 5. Binding Key 与精确 Identity

Cache 有两个相关 key：

- binding key：`namespace#code` 或 `namespace#code#version`，诊断中常简称 `code#version`；
- exact `ProcessRuntimeIdentity`。

Binding key 回答“当前哪个 exact runtime 拥有这个流程坐标”，不足以标识编译代码。

`ProcessRuntimeIdentity` 组合 model type、process code、exact source digest、opaque engine-local compilation fingerprint 与
class-loader identity。Compilation fingerprint 与 class loader 使用对象 identity 比较。诊断字符串只包含 digest prefix 与
identity 描述。

## 6. 精确 Source 解析

Reference-only request 已命中 binding cache 时，可以直接返回 runtime。否则 request 必须携带带有显式 source 的
`ProcessDefinition`。

`DefaultProcessDefinitionLoader` 执行一次有界读取：

- inline content 以 UTF-8 编码并按 byte size 校验；
- classpath lookup 拒绝 network-backed URL；
- 每个 stream 最多读取 `maxBytes + 1`；
- malformed UTF-8 在 parser 前失败。

输出是一个不可变 `ProcessDefinitionSnapshot`，包含 exact bytes、解码内容、安全 source description 与 SHA-256
digest。解析、Runtime identity、retry 与编译复用同一 snapshot；path 或 resource locator 绝不替代 content identity。

## 7. Cache 与 Single-Flight

`DefaultProcessRuntimeLoader` 先检查 exact `ProcessRuntimeIdentity` cache。Miss 后进入
`InflightRuntimeLoadRegistry`：

```text
putIfAbsent(ProcessRuntimeIdentity, proposed future)
  -> 已有 future：join
  -> proposed future：只提交一次编译
```

所有 waiter 看到同一 success 或 failure。完成后删除 entry，使后续请求可以重试一次失败编译。

ProcessRuntimeLoader 不在公共调用路径持有 lifecycle lock：

- `AtomicBoolean` 在 close 开始后拒绝新调用；
- `InflightRuntimeLoadRegistry` 的短临界区严格排序任务注册与关闭，并取消未完成 future；
- 只有编译结果发布与 close 之间使用独立的短临界区，保证 service 关闭后结果不会再安装。

## 8. 编译

格式特定 `ProcessRuntimeFactory` 执行：

```text
ProcessDefinitionSnapshot
  -> DefaultProcessDefinitionLoader.load byte snapshot
  -> parser 与 model conversion
  -> 结构与语义校验
  -> Java source generation
  -> compiler
  -> ProcessRuntime
```

单次编译不会在内部自动重试。Parser、generator、javac、timeout 与 configuration failure 保留各自的 typed error boundary，同一次
single-flight 的全部等待者会观察到相同失败。完成后 in-flight entry 会被移除，因此定义或环境修正后，后续请求可以发起一次新的编译。

只有显式配置时才导出 debug 产物。导出 source/class 属于诊断制品，使用 session directory，不能影响 Runtime identity 或执行行为。

## 9. 条件安装

编译后，`runtimeCache.install` 执行条件 binding 更新：

- immutable version 绑定到不同 exact content 是 conflict；
- unversioned binding 只有在 expected wrapper 仍拥有它时才能变化；
- 不覆盖并发产生的更新 binding；
- exact runtime 仍可被其他 binding 与 owner 共享。

Cache 有界且 retain-aware，只有没有 ownership claim 的 entry 可被淘汰。

每次执行都会在业务代码开始前准备并持有完整静态 Process call graph。Published call 使用各 call site 声明的被调流程 exact
version；Direct call 绑定 exact Classpath 或 Version target。路由收敛可以立即释放过期 ownership，但被 retain 的 runtime 会保留到其
in-flight lease 关闭。直接 `execute`、多格式 Alias 分发、嵌套调用与 `trigger`
共用这条边界。多格式 Registry 在路由选择、格式接管与执行期间持有同一个无锁准入令牌；Registry 关闭先原子停止准入，再扫描
线程固定的非负分片计数，因此会等待已经准入的调用完成。它把同一个剩余 deadline 传给各 Engine，不会在每层重新开始宽限期。

Preflight 请求 dry-run compilation 时使用 `runtimeCheckSync`。它可以复用或创建 exact runtime，但不会发布持久 version 或
route。

## 10. Runtime 执行

`DefaultProcessEngine` 调用已获取的 `ProcessRuntime`。顺序节点按图顺序执行；并行分支使用 Engine execution strategy，并在下游继续前
join。Timeout cancellation 是 cooperative；适用时 action code 必须响应 interruption。

Engine 将已知执行失败转换为 `ProcessError` 并返回 `ProcessResult.failure(...)`。JVM fatal `Error` 永远不会被归一成普通结果。成功返回
output variable map，并携带同一个受控执行归因。

嵌套流程调用保留外层 execution scope，同时返回自身结果。生成代码只标识 call site，execution context 在 resolved graph 中做一次
exact lookup；它不会根据 child code 推导 classpath，也不会通过 Alias/latest 路由 child。嵌套
`ProcessExecutionException` 在不丢失 typed error 的情况下翻译。

## 11. Trigger 流程

`trigger(...)` 使用完全相同的请求归一化、路由、runtime 解析、execution context、错误与结果管线。

获取 runtime 后，Engine 要求 `TriggerableProcess` 并调用触发入口节点 ID 与可选 event。每次调用都会依据传入 context
创建新的内存实例，不提供持久流程实例、resume、外部消息关联或崩溃恢复。

## 12. 本地 Admin 流程

`ProcessRuntimeManager` 是 Engine 本地 capability view：

- `warmUp(ProcessDefinition...)` 编译精确定义，但不创建 public binding；
- `load(ProcessRef.Version, ProcessDefinition)` 创建一个 immutable local version binding；
- `unload(ProcessRef.Version...)` 释放精确版本 binding 的本地 ownership。

`ProcessToolingService.preflight(...)` 执行不运行流程的校验和可选 dry-run compilation。只有配置开启且有多个 definition
时才并行 warm-up。Admin load 与 warm-up 都不是
`ProcessDeploymentService.publish`：它们既不写 durable publication state，也不修改 Alias。

## 13. 敏感数据边界

允许记录：

- invocation ID；
- trace ID；
- namespace 与 process code；
- requested reference；
- effective version/Alias；
- Alias revision 与 stable/candidate target；
- model type 与 source digest；
- 有界 error code 与安全 message；
- timing。

禁止记录：

- routing key；
- source content；
- 完整本地路径；
- credential；
- raw variable；
- 任意应用对象；
- 公共结果中的 raw exception context。

## 14. 源码导航

- `compileflow-core/.../DefaultProcessEngine.java`
- `compileflow-core/.../runtime/ProcessRuntimeRequest.java`
- `compileflow-core/.../runtime/resolution/ProcessRuntimeResolver.java`
- `compileflow-core/.../source/loader/DefaultProcessDefinitionLoader.java`
- `compileflow-core/.../source/ProcessDefinitionSnapshot.java`
- `compileflow-core/.../runtime/ProcessRuntimeIdentity.java`
- `compileflow-core/.../runtime/loading/DefaultProcessRuntimeLoader.java`
- `compileflow-core/.../runtime/loading/InflightRuntimeLoadRegistry.java`
- `compileflow-core/.../runtime/context/EngineExecutionContext.java`

## 15. Durable 执行流程

Durable 使用独立 API 和事务状态机：

```text
start(显式 Definition | exact Version | Alias)
  -> 加载 exact definition 或单次解析 Version/Alias
  -> 持久 immutable Process semantics 与 exact static-call bindings
  -> 当前部署准备可丢弃代码与当前能力
  -> 提交 processId-bound RUNNABLE Run
  -> Turn Worker 领取带 revision 的随机 token lease
  -> 执行到 Wait / Timer / Effect Action / terminal boundary
  -> Store 原子提交 Run + boundary + Journal + 所选 Integration Event
  -> Trigger / Timer / Effect result 形成唯一 resume envelope
  -> 下一 Turn 从可移植的 committed checkpoint 恢复
```

它不会调用普通 `trigger(...)` 来恢复流程，也不会把 Java 栈或任意对象写入数据库。Alias 只在准入时被接受并单次解析为
exact Version；恢复只使用 stored Process ID，永不重新解析 Alias、Version 或 definition locator。完整时序、状态机、故障恢复和数据所有权见
[Durable 架构](10-DURABLE_ARCHITECTURE.zh.md)。

## 下一步

- [版本路由](05-VERSION_ROUTING.zh.md)
- [Durable 架构](10-DURABLE_ARCHITECTURE.zh.md)
- [高级特性](../zh/advanced-features.md)
- [资源管理](../zh/resource-management.md)
