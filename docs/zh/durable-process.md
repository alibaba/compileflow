# Durable Process

Durable Process 是基于 PostgreSQL 的可选持久化执行能力。它不会把普通 `ProcessEngine` 调用隐式转换为 Workflow，
也不把应用兼容性写入持久化身份。

## 启用

```xml
<dependency>
  <groupId>com.alibaba.compileflow</groupId>
  <artifactId>compileflow-durable-spring-boot-starter-postgres</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

使用仓库快照时，只需安装 starter 及其 Reactor 依赖：

```bash
./mvnw install -pl compileflow-durable/compileflow-durable-spring-boot-starter-postgres -am -DskipTests
```

```yaml
compileflow:
  durable:
    enabled: true
  durable-postgres:
    migrate: true
```

配置 PostgreSQL `DataSource`。应用同时存在业务 `DataSource` 时，把 Durable authority 连接声明为名为
`compileFlowDurableDataSource` 的 Bean；否则使用唯一或 Primary `DataSource`。生产通常由外部 Flyway 迁移并设置
`durable-postgres.migrate: false`；启动会拒绝非 PostgreSQL 数据源并校验 Schema，存在 pending migration 时 fail closed。

## 配置 exact Version 获取

```java
ProcessRef.Version process = ProcessRef.version("sales", "approval", "v17");
```

`DurableVersionDefinitionSource` 为每次新的 Version 或 Alias 准入提供权威的 exact definition。它返回只含
`ProcessModelType` 与 `ProcessDefinition.Inline` 的极小 `VersionDefinition`；与 Alias selection 分离，
也不是新 Artifact 类型。Durable 把 content 编码成 exact UTF-8，自行计算 SHA-256，并将 model type、
process code、exact bytes 与 digest 保存为 immutable Process semantics。内容寻址的 `processId` 是恢复与
Program cache identity；namespace 和 Version 只是可选 Run 准入归因。恢复直接读取 stored semantics，绝不查询 Version source。

Durable 接受文档定义的严格 TBBPM/BPMN profile，并在注册前拒绝其他 model type 或不支持的构造。schema
仍保存封闭的 `ProcessModelType` 事实，恢复绝不通过 XML 猜格式。两个 frontend 只把 Kernel 已证明的语义
lower 到同一套 format-neutral Durable semantic backend。通用 message correlation、user task、boundary event 与 event-based gateway 仍是
独立产品选择。

## 用显式 definition Start

```java
ProcessRun run = durable.start(
    ProcessRunId.random(),
    ProcessDefinition.inline("approval", definitionText),
    Map.of("orderId", "o-42"));
```

调用方只提供 definition。Durable 使用已配置的 `ProcessEngineConfig` 与 Core source loader，将 definition 冻结为
带有配置 `ProcessModelType` 的不可变 inline 快照；显式被调流程 definition 使用 caller 的格式。

## 用 Version 或 Alias Start

```java
ProcessRun exact = durable.start(
    ProcessRunId.random(), process, Map.of("orderId", "o-42"));

ProcessRunId knownId = ProcessRunId.random();
ProcessRun addressable = durable.start(
    knownId, process, Map.of("orderId", "o-44"));

ProcessRun started = durable.start(
    ProcessRunId.random(),
    ProcessRef.alias("sales", "approval", "prod"),
    Map.of("orderId", "o-43"),
    new AliasRoutingOptions("customer-43", Map.of("region", "cn")));
```

API 使用强类型 Version/Alias overload：Code 不是可调用的 Durable reference，exact Version
也没有接受 routing inputs 的 overload；Alias Start 读取 committed state，先执行 route 显式绑定的具名
`ProcessAliasTargetingPolicy`，未覆盖时再进入协议固定的百分比分桶。Routing key 与 attributes 只用于准入，不随 Run 持久化。
Run 可保留选中 Version 作为准入归因；准入把 exact semantics 持久为 stored Process，Worker/恢复只使用
`processId`，绝不再查 Alias。

Start 输入是所选 exact Process Definition 中 `param` 变量构成的封闭、部分 Map。`return`、`inner` 或未声明字段会在 Run
提交前失败；缺失参数由首次 Machine Turn 应用定义默认值，存在但值为 null 的键保持显式 null。后续 checkpoint 包含完整流程
状态，但它只来自 Kernel 已提交 continuation，不会重新开放给调用方注入。

每个 Start 都要求调用方预先分配 RunId，使 admission 在请求前即可寻址。
RunId 不是通用 idempotency key：重复值返回 `RUN_ALREADY_EXISTS`，响应不确定时用 `getRun(runId)` 核实；Alias
重复检测发生在 mutable Alias resolve 之前。调用方分配的值永久绑定一个 Run occurrence，即使原 Run 已被 retention 删除也绝不能复用。
HTTP、MQ 或业务操作若必须只准入一次，外层
Application/Admission Inbox 仍负责 request equivalence 与自身 identity 到 RunId 的映射；Kernel 没有通用
idempotency key 或 command receipt。

replayable Action、Effect input 或 Wait-description callback 进入应用代码前，exact Process Serializer
会构造 typed detached graph，包括只读容器内的 mutable POJO。修改 callback input 因而不能修改 Segment
state；状态变化必须通过声明的 Action output mapping 或 committed typed boundary result 返回。

## 持久化状态类型与升级

Exact stored Process semantics 固定流程源码、控制语义、变量声明、Action 声明与语义恢复坐标，但不固定 Spring bean、Java Action
字节码、第三方库、ScriptExecutor 实现或应用 POJO class shape。持久化变量、scope frame、Effect 输入/输出或 Wait
结果中可达的每个 Java 类型都属于应用需要维护的持久化 schema。

兼容升级必须让历史值继续可解码；破坏 class shape/provider 语义的升级应先 drain 相关 Run、迁移状态或受控切换。
CompileFlow 不持久化 ApplicationBuildId，也不按应用 build 路由恢复。

CompileFlow 自己负责 parser、semantic compiler、resume coordinate 与 Engine envelope format 的兼容。Envelope
携带紧凑内部版本头，只用于 fail-closed decode/migration，不是 Process/codec identity。每个版本都必须用仓库内冻结的
Definition + continuation + Wait/Timer/Effect compatibility corpus 验证当前代码仍可恢复历史事实。

Durable Developer Preview 当前只支持 coordinated homogeneous upgrade：停止 admission/Worker，验证可恢复备份，应用经审查的
migration/application 变更，再以单一版本重启。升为 Supported 前必须明确并验证 mixed-version 合同（优先 N/N-1），不能因启动成功就
推断支持 rolling upgrade。

## Wait 与 Complete

`WAIT_COMMITTED` 负责把 raw 256-bit Wait token 可靠交给授权调用方；数据库只存 SHA-256 digest，
不得记录 raw token。

```java
durable.completeWait(
    new WaitToken(rawToken),
    Map.of("approved", true));
```

scalar token 全局唯一，Kernel 先用它发现所属 Run，再进入 Run-first authority transaction。element/event
等恢复坐标已由 committed Wait fact 持有，调用方不需要也不能在完成时重复声明。该 token 是
opaque、one-shot continuation capability，不是业务身份。可控 RPC/MQ 可原样透传；外部只返回
自身 job ID 时，Integration 可保护 `externalJobId -> WaitToken` 映射。订单、支付、库存系统应继续使用自己的
domain identity 与事实库；Kernel 不要求业务建立 token 表。Raw token 不得进入 metric label、普通 URL、第三方
metadata 或 Workbench 表示层。CompileFlow 不提供 message subscription、buffer、TTL 或 early-arrival correlation。

payload 是该 Run exact Process 声明变量的 typed partial update，在 Wait fact 提交前完成校验。同 token +
同 canonical typed result 是 zero-write current equivalent；不同 result 冲突；取消或无关 occurrence 不接受该 token。

## Effect Action

外部 observation 用 Action 的 `execution="effect"`：

```xml
<effectPolicy recovery="reconcile"
              maxAttempts="3"
              maxReconcileAttempts="10"
              recoveryDelay="PT5S"
              maxRecoveryDuration="PT30M">
  <reconcileAction type="spring-bean" bean="payment"
                   class="com.example.PaymentService" method="queryCharge">
    <input source="requestId" target="requestId" dataType="java.lang.String"/>
    <input source="__cf_effect_id" target="effectId" dataType="java.lang.String"/>
  </reconcileAction>
</effectPolicy>
```

Reconcile input 的 source 只能是原始 Effect 请求中已持久化的字段或已定义的 Effect metadata，
不是 Process expression。该 adapter 没有 default、output、execution mode 或 invocation policy；
确认结果仍通过原始 Effect Action 的 output mapping 写回。

未声明时默认 manual。业务 invocation 需要时，retry/reconcile 可映射每次 attempt 共用的稳定
`__cf_effect_id`；这个固定映射本身不是幂等性证明。
Structural preparation 不 resolve 或构造应用组件。Spring resolve、Java 构造、脚本求值和方法调用会跨过
possible-dispatch boundary；越界后无法证明结果的异常、超时或 worker loss 产生 UNKNOWN。同一不确定性
episode 的 `unknownSince` 在 Reconcile claim 间保持不变，`maxRecoveryDuration` 从首次不确定开始计算；
业务拒绝必须是正常 typed return 并由 Decision 路由。

Operator resolution 必须携带当前 `reviewRevision`，且只允许确认成功、确认未执行并重试、或 FAIL_RUN。
重复确认成功只有在 canonical typed result 与已提交结果相同时才是 zero-write current-equivalent。
重复 `FAIL_RUN` 只有在该 Effect resolution 确实导致 Run 失败时才等价，Run Cancel 导致的 Effect
取消不能冒充同一结果。

## Pause、Resume、Cancel 与 Outbox

Pause/Resume 是带 `expectedControlRevision` fencing 的 control-state transition。运行中的 authority 会通过
`PAUSE_REQUESTED` 收敛。Resume 必须基于最新 control revision，并且只能在当前 Run 仍可继续时生效。应用取消使用
`cancel(runId)` 且 first-intent-wins。认证、principal attribution 与请求审计属于暴露该操作的外层边界。
Outbox operator mutation 使用 `eventId + expectedRevision`。

Outbox 只暴露以下事件：

- `WAIT_COMMITTED`
- `EFFECT_REVIEW_REQUIRED`
- `RUN_SUCCEEDED`
- `RUN_FAILED`
- `RUN_CANCELLED`

`DurableOutboxSink` 只是通用外部投递适配器：at-least-once 重试始终携带相同的 event ID、类型和逻辑 payload；
正常返回必须表示目标 delivery boundary 已持久接受事件，而不是仅进入 JVM 内存队列。Sink 必须持久去重 event ID，
或将其原样传给下游去重者；不对任意外部副作用承诺 exactly-once。未来若有 CompileFlow 自有 Run-to-Run 协议，
也不强制复用该 SPI。

## Query

Embedded Java Query/Page 返回 typed keyset cursor（`ProcessRunCursor`、`ProcessTimelineCursor` 和
`OutboxEventCursor`）；Web adapter 可自行编码和签名 opaque page token。

`getRun(runId)` 与 `listRuns(query)` 返回 payload-blind Run 数据；只有 `getRunResult(runId)` 返回 payload-bearing
封闭的 `ProcessRunResult`：`NotFound`、`NotCompleted`、`Succeeded`、`Failed` 或 `Cancelled`，不会把 Run
不存在与尚未结束混为一谈。

## 运维规则

- PostgreSQL DB time 是可用性与 lease 的权威时钟。
- 锁顺序固定为 Run first、exact occurrence second。
- claim/reclaim 生成新的随机 lease token；renew 保持现有 token；completion 必须匹配当前 token。
- 不得记录 continuation、Effect、Wait payload 或 raw token。
- 备份和恢复必须覆盖全部七张 Durable 表。执行 PITR 时先停止所有 Runtime，恢复后重建可丢弃的 Program cache。
- 仍被可恢复 Run、备份或 WAL/PITR 恢复点引用的 exact Process Definition 必须保留。可选的有界 unused-Process 策略
  只能删除没有 Run 引用的 Definition；Run-Process 外键和 Start 对引用 Definition 的 key-share lock 保证 Admission/GC 并发安全。
- authentication、authorization、approval policy、request dedupe、rate limiting、TLS 和数据库访问控制属于传输或应用边界。
