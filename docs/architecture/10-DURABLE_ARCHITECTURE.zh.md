# CompileFlow 2.0 Durable 架构

状态：Durable Developer Preview 的当前架构。

## 核心约束

CompileFlow 只持久化引擎负责的流程语义和已经提交的 Durable 执行事实。应用代码、`ScriptExecutor`
provider、handler、生成制品和运行时能力均由当前部署提供；Durable 内核不负责这些部署组件的历史兼容性，
应用兼容性元数据也绝不参与 Run identity 或 Store worker routing。

两个执行面共享流程语言，但后续执行的负责人不同：

| 执行面       | 公开引用                              | 后续执行负责人           |
| ------------ | ------------------------------------- | ------------------------ |
| 无状态调用   | `Version`、`Alias` 或 direct definition | 应用（如果需要继续执行） |
| Durable 执行 | `definition`、`Version` 或 `Alias`    | Durable 内核             |

`ProcessEngine.execute/trigger` 每次创建一个新的无状态调用。应用若要跨调用继续同一业务过程，应保存
`ProcessExecution.getProcessVersion()` 并在后续使用该精确 Version；若调用由 direct definition 发起，
则由应用保留该 definition。

`DurableProcessEngine.start` 创建新 Run。Definition、Version 和 Alias 只是获取方式；准入将它们收敛为
一份 exact immutable stored Process，Run 只通过 `processId` 绑定其恢复语义。Alias 属于 Deploy 控制面，
只在准入时选择一次 Version，不会成为快照、Wait、Effect、租约或恢复身份。
每个 Run 只保存一个精确的根 `processId`；Version 只是可选的准入归因信息。

## 准入与精确版本执行

```text
ProcessEngine façade
  Version / Alias / definition
  -> 单次归一或 Alias resolve-once
  -> exact invocation runtime acquisition
  -> execute

Durable façade
  definition / Version / Alias
  -> 加载已给 definition、解析 Version，或单次路由 Alias
  -> 校验并持久 exact immutable Process semantics
  -> exact-bind 所有静态 Process call
  -> 创建 processId-bound Run

Durable Worker
  Run.processId -> stored Process semantics -> 可丢弃 Loaded Runtime
```

Invocation Alias 路由读取本节点 local-ready projection；Durable Alias Start 读取 Deploy committed
authority。稳定的 `ProcessEngine` 主链继续是 `DefaultProcessEngine -> ProcessRuntimeResolver ->
cache/compile -> executor`：Provider 在 exact cache match、编译和执行前完成本次 invocation 的 Alias
解析。Durable 不复用这条有状态 acquisition 路径，只用 runtime internal admission policy 解析一次，后续 Worker
全部只依赖 `processId`；不新增 shared `InvocationService` 或通用 admission resolver。

当 Deploy 与 Durable 同时存在时，第一方薄适配器把 `ProcessDeploymentService.getAlias(...)` 映射为
`DurableAliasStateSource`，并把 Deploy `ProcessArtifactSource` 中通过 exact identity 与 source digest
校验的内容映射为 `DurableVersionDefinitionSource.VersionDefinition`。Adapter 保留 `ProcessModelType`，
Durable 接受文档定义的严格 TBBPM/BPMN profile，并对其他 model type 或不支持的构造 fail closed。Durable 自己负责
UTF-8 编码、digest 计算与恢复 replica。这是组合边界：Alias、Rollout 和 Deploy
revision 不进入 Run identity、Checkpoint 或 recovery authority；Alias Start 只允许把当次准入归因写入
`RUN_CREATED` 审计 fact。

## 流程身份与不可变流程定义

Durable Kernel 内部的恢复身份是 `processId -> exact model type + process code + definition bytes`。
`processId` 由完整 definition digest 确定性派生，Store 仍保存并校验完整 SHA-256 digest。immutable stored
Process 保存 `ProcessModelType`、process code、exact definition bytes 和 digest。namespace 与 Version 只是
可选的 Run 准入归因，不选择恢复语义。冲突注册必须拒绝。

Alias、Rollout、Application Build ID、Program ABI、Provider identity、custom codec、generated source、
bytecode 和 Loaded Program 都不参与恢复。Deploy 继续独立拥有 publish、Alias CAS、BPS 灰度、
promotion、abort、rollback、audit、routing outbox、install-before-route 与 local-ready。

Durable 通过同一套 format-neutral Process semantic backend 接受文档定义的严格 TBBPM/BPMN profile；
不引入公开 compiler SPI 或通用 BPMN message/task/event 平台。

## Start 与 Run ID

```java
ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input);
ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input);
ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input);
ProcessRun start(
    ProcessRunId runId,
    ProcessRef.Alias alias,
    Map<String, ?> input,
    AliasRoutingOptions options);
```

可调用 Start surface 只有显式 `ProcessDefinition`、exact `Version` 与 selected `Alias` admission。显式 definition source
把权威格式与解析后的 inline 快照一起返回；显式 child 继承且必须匹配 caller 格式。`AliasRoutingOptions` 只存在于
Alias overload。每个 Start 都要求调用方在准入前分配 Run occurrence identity。RunId 是
寻址与恢复 handle，不是通用 idempotency key。对已经存在的 RunId 再次 Start 必须返回
`RUN_ALREADY_EXISTS`：Kernel 没有保存完整 Start request digest，不能声称两次请求等价。响应不确定时调用方使用
`getRun(runId)` 查权威事实。RunId 永久绑定一个 Run occurrence；即使原 Run 已被 retention 删除，调用方也绝不能复用该值。
Alias Start 必须在读取 mutable Alias 之前检测重复 RunId。

Kernel 仍然没有默认 idempotencyKey 或 businessKey。HTTP、MQ 或 Application 若要求“一个业务请求只准入一次”，
仍由该边界保存自己的 request/message/domain identity 到 RunId 映射。

Alias Start 把 requested Alias、权威 revision 与 selected target 写入 `RUN_CREATED` 审计 fact。选中的
Version 可作为准入归因保留在 Run 上，但 Checkpoint 与恢复只依赖 exact stored `processId`。

## 恢复所需的权威状态

恢复只依赖 exact immutable Process Definition、现有 `DurableStore.Envelope`（`CFD + formatVersion`）内的 opaque
continuation、Wait/Timer/Effect occurrence，以及 Run lifecycle/control/cancel/availability/lease facts。Store
只持久化和 fence continuation bytes，绝不解释 element ID、scope、frontend token、branch 或 frontier。

不要新增 `machine_semantics_version`、compiler/generator/runtime version、Program ABI、codec registry、upcaster
或双读写框架。当前 Envelope 已经可以识别并拒绝未知格式；只有将来两个真实有效的持久化表示必须共存时，
才根据实际迁移证据引入最小 discriminator。CompileFlow 自己拥有的语义兼容由历史 Definition + continuation +
committed-fact fixtures 和发布门禁保障，不转嫁成 Run 字段或 Worker 路由。

最终 2.0 目标是 **Durable Machine**：
共享 format-neutral semantic frontend，ProcessEngine 执行与 Durable 分别 lowering；Durable Machine Plan 是 disposable
semantic input，compiled 与 interpreted 都是正式生产 realization。每次 `advance` 执行一个 bounded Turn；一个
continuation writer 可以维护多个 deterministic frontiers，并在同一 fenced commit 中消费精确已解析结果、写新
continuation、发出零到多个 Wait/Timer/Effect occurrence。Turn budget 耗尽产生 checkpoint-only Yield 并回到
`RUNNABLE`，绝不是 Turn fault。状态单写，外部工作并行；completion order 不参与 branch merge。

### 编译与准备生命周期

Durable 只准备 exact immutable Process semantic program，并供多个 Run 复用。stored `processId` 是节点本地
Program cache 与 single-flight key。只有 Version/Alias 准入需要获取 definition semantics 时才查询
`DurableVersionDefinitionSource`；恢复直接按 `processId` 读取已持久 definition。Start admission
校验并持久化静态图，然后按需构建当前部署的 disposable program；重复准备必须命中缓存。

配置的 runtime mode 将每个 exact Process 准备为 generated Java + javac，或直接解释同一 Machine Plan。
mode 选择属于 Host policy，不是 Start option，也不是 Run 持久化事实。

准备为一个 stored `processId` 产生一个 disposable `DurableProcessRuntime`。正常 Start advancement、Turn、retry、
resume 和 boundary completion 不得触发 Kernel Program compilation。准备缺失或失败只是当前部署诊断，不能
转成业务 FAILED。

Generated Java + javac 是默认生产 realization，Supported V1 Host profile 必须包含 `jdk.compiler`；解释器也是
基于同一 `DurableMachinePlan` 的正式生产 realization，并承担 differential oracle。配置显式选择其一，准备过程
绝不在两种 mode 之间静默 fallback。V1 不增加 AOT Program source、公开 compiler SPI、持久化 bytecode/class registry、分布式
compiler、磁盘 class cache、ECJ backend 或 compiler module。只有真实产品消费者和包体/启动数据证明收益后，
才基于完整工具链与 differential tests 重新评估 AOT 或 ECJ。

Decision、While、Timer 和 guard 继续使用原有 source/expression 与 generated-Java 体系。不自研
expression parser、typed AST 或 DSL。Durable 静态校验在 javac prepare 中完成。

Agent 的 prompt、conversation、model、当前 tools、ToolCalls/results、workspace state 与动态并行选择都属于
state/capability 或 occurrences；只有稳定 Agent control algorithm 属于 Process semantics。合规 Agent lowering
使用带动态参数的通用 tool Effect，绝不按 tool inventory、request、plan、step 或 turn 生成新节点或 Process
Version。

TBBPM `foreach execution="parallel"` 与 BPMN parallel multi-instance 通过同一代数实现结构化动态集合多实例，
不增加 Kernel `Map` 节点。它们只冻结一次已声明的 Process `List`；每个 input index 拥有独立 iteration frame；使用有界滚动
issue；配置输出时按 input order 而不是 completion order 汇聚。窗口宽度属于当前 Runtime，
不持久化为 Process identity；continuation 只保存一份父状态和各 iteration 的差量。ProcessEngine 执行对该 Durable-only
profile 失败关闭。Agent 安全标签不进入 Kernel scheduling semantics。
Parallel collection 可以由顺序 `foreach` 或 `while` scope 持有，也可以从外层 lexical frame 读取 collection。任一并发祖先下的
第二层 Parallel collection，以及 concurrent region 内 Process call，在 state-write 与 deterministic merge 法则被
完整定义并证明前继续 fail closed。

同一 occurrence algebra 也支持带 PostgreSQL-time optional deadline 的 EVENT Wait，并 first-wins 地产出 typed
triggered/expired 结果；它不是 generic `anyOf` 或 event inbox。TBBPM `bpmCall` 与 BPMN `callActivity` 是同 Run
Process call。初始 Run admission 解析完整静态图并持久化每条 `callSiteId -> 精确流程目标` edge；执行时 continuation
压入 `ProcessInvocation` frame，完成后把 typed result 返回 caller frame。admission 后不再路由或读取 source，恢复时
绝不读取 Alias、当前 classpath 或 latest state。调用共享 Run 的 lifecycle、cancellation、lease、fencing 与运维边界，
不创建 Child Run 或 Child 表。

只有 compiled Machine 显式产出的 typed Process failure 才能把 Run 终结为 `FAILED`。任意 Action、
ScriptExecutor、reflection、compiler、Runtime 或 Store exception 仍是有界 operational retry evidence；CompileFlow
不通过 Throwable classifier 把当前应用兼容性持久化成业务结果。

Journal 只做审计和诊断，不是 Event Sourcing；Outbox 不是 Journal 1:1 镜像。应用 capability 缺失只做
有界 operational backoff，不改变 Process identity，也不自动产生业务 FAILED。Active-work 投影只暴露封闭、脱敏的
capability code 与下次 eligible 时间；两者都不参与恢复 authority。

## Action 和 Effect

Effect 是 `Action.execution="effect"`，不是新的节点类型。Decision、While、Timer 和 Action 的源码仍
属于 Process 模型；脚本语言由 Action type/ScriptExecutor Provider 选择，Kernel 不自研固定表达式语言。
对 Java control expression，Kernel 使用 javac 类型分析和 AST/symbol allowlist 拒绝已知不安全语法；
应用 value accessor 的无副作用性是模型作者契约，不宣称 Kernel 能静态证明任意应用方法的纯度。

任何应用 Action 或 Wait-description callback 执行前，Runtime 都用 exact Process typed Serializer
生成独立的 input/state/scope graph。只把容器设为只读并不足以隔离其中的可变 POJO；应用代码只能修改
自己的 detached copy，Process state 只能通过声明的 Action output mapping 或已提交 typed boundary result
进入 Turn。

Effect recovery 为 `manual | retry | reconcile`，可带 `maxAttempts`、`maxReconcileAttempts`、
`recoveryDelay`、`maxRecoveryDuration`；默认 manual。retry/reconcile 共用 Effect ID token。业务拒绝必须是
正常 typed return。Structural preparation 只检查类型、方法签名和 Provider 是否存在，绝不 resolve 或
构造应用组件；Spring Bean resolve、Java 构造、脚本求值和方法调用都会跨过 possible-dispatch boundary。
越界后无法证明结果的异常、超时或 worker loss 一律产生 UNKNOWN。`maxRecoveryDuration` 从本次 dispatch
episode 第一次进入不确定性开始计算，`unknownSince` 在 Reconcile claim 期间保持不变，直到确定结果、
Operator 决策或确认未执行后重新 dispatch 才结束。

Operator 只可 `CONFIRM_SUCCEEDED(result)`、`CONFIRM_NOT_EXECUTED_RETRY` 或 `FAIL_RUN(reason)`。
每个 review epoch 增加 `cf_durable_effect.review_revision`，旧决策必须冲突。确认成功的重试只有在 revision 和 canonical
typed result 都与已提交 Effect fact 相同时才是 current-equivalent；不同结果必须冲突。`FAIL_RUN` 重试只有
在该 Effect resolution 确实使 Run 失败时才等价；因 Run Cancel 而取消的 Effect 不是同一结果。

## 状态机、幂等与并发隔离

Run status 为 `RUNNABLE/RUNNING/WAITING/SUCCEEDED/FAILED/CANCELLED`；control state 独立为
`ACTIVE/PAUSE_REQUESTED/PAUSED`。Cancel 是 first-intent-wins 的单调事实。

Complete 使用 exact 256-bit Wait token；Wait authority row 只存 SHA-256 digest。为保证崩溃安全交付，raw bearer
token 只在 active authority 期间暂存于 `WAIT_COMMITTED` Outbox payload；交付成功、Wait 完成或 authority
撤销时立即清除。Wait 为 ACTIVE 时该 delivery obligation 不得 auto-abandon；完成或撤销后必须结清并 scrub capability。
这是固定 Kernel 语义，不是可配置的 delivery-policy DSL。payload 是 exact Process 声明变量的 typed partial update，在进入 Store 前校验和 canonical encode：同 token + 同 canonical result 返回 zero-write current
equivalent，不同 result 冲突。恢复 Turn 只解码并应用已提交的 typed update，不调用应用 payload mapper。Pause/Resume 使用 `control_revision`，Effect review
使用 `review_revision`，Outbox operator 使用 `revision`，Worker completion 使用 occurrence ID + random
lease token。无需通用 Run revision、Command Receipt、request HMAC root 或 numeric fence。

WaitToken 是精确指向一次已提交 External Wait occurrence 的 opaque、one-shot bearer capability。
Kernel 不要求业务建立 token 表，只要求 Integration 保证该 handle 能跨越异步过程并在完成时返回：可控
RPC/MQ 可原样透传；外部系统只返回自身 job ID 时，Integration 可保存受保护的
`externalJobId -> WaitToken` 映射；订单、支付、库存等成熟领域继续使用自己的 domain identity 与事实库。
Raw token 不得进入日志、metric label、普通 URL、第三方 metadata 或 Workbench view；长期落盘时必须按凭据保护。
Kernel 不提供模糊 correlation、message subscription/buffer、TTL 或 early-arrival event lookup。

事务统一 Run-first、occurrence-second；PostgreSQL DB time 是 lease/schedule authority。claim/reclaim
生成新 token，renew 保持 token，completion 必须匹配。
一个 Runtime 只拥有一份 lease-duration 策略。节点内协调器以该时长的三分之一为周期，在三个独立调度 lane 中分别续租
Run、Effect 与 Outbox；初始 phase 带 jitter，单批最多 256 个 authority。成功批次必须返回仍有效的精确子集：被拒绝的
authority 停止续租；Store exception 不是丢失证明，仍会重试。批量只是运行优化：过期判定与所有 outcome commit 继续使用
PostgreSQL time 和 token fencing；进程暂停、网络分区或数据库故障一旦超过 lease，authority 仍会丢失。
DataSource 获取连接、建连、socket、statement 与 lock wait 的组合超时预算应小于一个派生续租周期。运维方根据实测
PostgreSQL 尾延迟和 JVM 暂停时间确定 lease；这些属于部署层 DataSource 控制，不再增加 Durable lease 策略参数。
Effect Worker 在 Dispatch 与 Reconcile 两条 lane 间轮换首选顺序，空 lane 才回退；Store 通过
`FOR UPDATE OF run SKIP LOCKED` 直接领取不同 Run，避免 UNKNOWN recovery 被持续 Dispatch backlog 饿死。

## 物理闭包

PostgreSQL V1 当前把六类 Durable authority 映射为七张表；表数量只是第一方物理布局，不是公共 Kernel 契约：

1. `cf_durable_process`：以 `process_id` 为键的 immutable stored Process semantics；
2. `cf_durable_run`：root `process_id`、可选准入归因、opaque continuation、lifecycle/control/cancel/lease；
3. `cf_durable_run_recovery_process`：为每个 Run 恢复保留的 exact stored Process 集合；
4. `cf_durable_wait`：EVENT/TIMER occurrence；
5. `cf_durable_effect`：Effect outcome、attempt、UNKNOWN、review revision；
6. `cf_durable_journal`：append-only audit/diagnostics；
7. `cf_durable_outbox`：封闭 Integration Events、delivery revision/lease。

Outbox 只允许 `WAIT_COMMITTED`、`EFFECT_REVIEW_REQUIRED`、`RUN_SUCCEEDED`、`RUN_FAILED`、
`RUN_CANCELLED`。每个事件携带 event ID、Run ID、process code 与可选 Version 准入归因；Wait/Effect 事件还携带 exact occurrence
ID，Run terminal 事件不携带。event ID 仍是 Sink dedupe identity；occurrence ID 只负责精确 correlation 与 authority
清理，确保解决一个 Wait/Effect 绝不会 settle sibling event。交付为 at-least-once，每次 retry 保持
相同 event ID、event type 与 logical payload，由 Sink 持久去重或原样传给下游去重者；不保证顺序，包括同一 Run，
也不承诺外部副作用 exactly-once。它是通知/authority delivery，不是 Event Sourcing change log 或通用内部消息协议。
Deploy 另外保留六张控制面表。PITR 必须停全部 Runtime，并把七张 Durable 表作为同一
一致性单元恢复；Program cache 全部丢弃重建。

## 数据保留

终态 Run retention 必须显式配置，默认关闭。每次 sweep 只按 `(completed_at, run_id)` 稳定顺序、通过
`SKIP LOCKED` 有界选择超过 retention 的终态 Run；仍有 `PENDING` 或 `DELIVERING` 必达 Outbox 的 Run
不可清理。同一事务先删 Journal、Outbox、Wait、Effect，再删 Run，active Run 永不入选。任何仍被 Run
引用的 Process Definition 都由 Run-Process FK 保护；可单独显式配置有界的 unused-Process sweep，只删除超过保留期且
没有 Run 引用的 Definition。Start 锁定完整 recovery set，因此并发 Admission 与 GC 不会产生悬空引用。Wait token authority 只持续到对应 Wait 完成或撤销；失效的
`WAIT_COMMITTED` 必须进入终态且不得阻断 retention。

## 可选可观测性

Runtime 内部只维护无外部依赖、`operation/outcome` 两个有界维度的计数器。存在 Micrometer 时，Spring
自动注册 `compileflow.durable.operations` 和 `compileflow.durable.loaded.runtimes`；存在 Spring Boot
Health 时，Durable contributor 探测 PostgreSQL authority time，并分别报告 Worker、cache、capability、
Outbox sink 的组合状态。Worker machinery 首次出现非预期故障会输出脱敏告警，重复告警会限流，恢复也会记录；任一启用 lane
连续故障三次时报告 `DEGRADED`，并以有界详情暴露各 lane 最近成功周期、最近进展、最近故障和异常类型，绝不暴露异常消息或流程数据。
某个流程的 Action/Effect capability 缺失或未配置可选 Outbox sink 不会让整个
Engine unhealthy；PostgreSQL authority 探测失败才会判定 DOWN。

## 传输层边界

Embedded Java API 返回 typed keyset cursor；HTTP/Workbench 自己编码和签名 page token。
authentication、authorization、four-eyes policy、rate limit、HTTP/MQ request dedupe、TLS、数据库与
备份访问控制均属于外层。特权 Operator 审计只接收 actor、reason 和可选 auditContextId。
