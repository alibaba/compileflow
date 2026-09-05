# Durable 运维手册

本手册面向 CompileFlow 2.0 PostgreSQL Durable Kernel。PostgreSQL V1 当前使用七张表；这是运维恢复布局，
不是 Kernel 不变量。

## 1. 安全规则

- PostgreSQL 是唯一执行 Authority。
- Store-Level Restore、Migration Repair 或需要锁的人工检查前，停止全部 Worker。
- 禁止手改 Process、Run-Process、Wait、Effect、Journal 或 Outbox Row。
- Pause/Resume、Effect Resolution、Outbox Resolution 必须走带审计的 Operator API。
- 禁止记录 Snapshot Envelope、Effect Payload、Wait Token、传输凭据或 Page Token。
- Alias 修改只影响未来 Admission，不能用 Alias“迁移”已有 Run。

## 2. 最低健康证据

监控并告警：

- 最老 Eligible `RUNNABLE` Age 与 Queue Depth；
- 超过 `lease_until` 的 `RUNNING` Row；
- 到期后仍未解决的 Active Timer；
- `PENDING/RUNNING/UNKNOWN` Effect，尤其 Review-Required Age；
- `PENDING/DELIVERING/ABANDONED` Outbox；
- Store Transaction Latency、Pool Exhaustion、Deadlock、Migration Validation；
- Durable Health 的 `workerMachinery=degraded` 或 `leaseRenewal=degraded`、各 lane 的 Active Authority/连续故障数，
  以及长期不更新的最近成功/进展时间；
- 精确已存储 Process ID 重复出现的准备故障。

诊断使用脱敏 Run/Timeline/Outbox API，Raw Envelope 不是 Operator Projection。
Worker machinery 故障与流程级 capability readiness 是两类信号：前者可以让节点降级，后者只定位受影响的已存储 Process ID。

Run、Effect、Outbox 独立按 `lease-duration / 3` 续租。Lease 应覆盖实测 JVM Pause 与 Store Tail Latency 之和；连接获取、
网络、锁和 Statement Timeout 应保证失败的续租调用在下一周期前返回。这些属于 DataSource/PostgreSQL 边界，不增加 Kernel 调参项。

## 3. PostgreSQL 故障

1. Worker 必须 Fail Closed，不能建立本机 Shadow State。
2. 恢复连接后校验 Database Time、Primary Role、Migration Checksum 与连接池。
3. 由 Maintenance 回收过期 Run、Effect、Outbox Lease。
4. 确认旧 Worker 使用过期 Token 无法提交。
5. 观察 Backlog Age 恢复正常。

不要人工把 `RUNNING` 改成 `RUNNABLE`；Lease Reclaim 才是保留 Authority 的路径。

## 4. 协调 PITR

1. 停止所有能执行 Start、Trigger、Operator Command、Worker Claim、Outbox Delivery 或 Maintenance 的进程。
2. 记录 Restore Point，并丢弃所有本地 Prepared Cache。
3. 将 Process、Run、Run-Process、Wait、Effect、Journal、Outbox 七表作为一个单元恢复。
4. 校验 Flyway Version/Checksum 与 Schema Constraint。
5. 先启动一个 Runtime，为保留的 Run 所需的已存储 Process ID 准备 runtime，并检查诊断信息。
6. 启动其余 Worker，监控 Lease Reclaim 与 Backlog。
7. 用稳定 Effect Occurrence/Event ID 与外部系统对账。

禁止单表恢复，禁止 Restore 前后两个 Authority 并行。

## 5. UNKNOWN Effect

1. 读取脱敏 Effect 与 Timeline。
2. 确认当前部署已 Prepare exact Process，且 Action/Reconcile Target 可用。
3. 使用 Effect Occurrence ID 或映射后的 Business Key 查询远端 Provider。
4. 能证明 Succeeded/Not Executed 时优先自动 Reconcile。
5. 必须人工决定时，由外层 Adapter 完成认证授权，再携带当前 Review Revision、Actor、明确 Reason 和可选
   Audit-Context ID 调用 `resolveEffect`。
6. 不得把 Timeout 当成失败，也不得用 Cancel 抹掉未决外部 Authority。

部署兼容问题可能需要发布一个具备相应能力的实现，但不能因此把历史 Build Routing 加回 Kernel。

## 6. Outbox 故障

1. 确认稳定 `eventId`、Run、Event Type、Attempt Count 与 Status。
2. 检查经过认证的 Sink 与下游 Dedup Ledger。
3. 可安全重投时使用 Operator Retry。
4. 确认永不投递时，使用带审计授权的 Abandon。
5. Consumer 必须幂等，因为 Timeout 可能发生在下游已接收之后。

Journal Fact 不会自动重发；Journal 与 Outbox 语义不同。

## 7. Stuck Run 或 Capability 缺失

- `RUNNABLE` 持续 Fault Backoff：检查已存储 Process 的 runtime 准备状态、ScriptExecutor、Java/Spring Target 与编译诊断；
- `RUNNING` Lease 过期：检查 Maintenance、Database Time 与 Reclaim Index；
- `WAITING` Event：检查经过认证的 `WAIT_COMMITTED` 投递和 Token 处理；
- `WAITING` Timer 已过 Due：检查 Timer Sweep 与数据库时钟；
- `WAITING` Effect：检查 Pending/Unknown/Review 状态。

不得根据基础设施 Retry Count 自动把 Poison Run 标记为 `FAILED`。需要隔离时先 Pause，修复当前部署后 Resume。

## 8. Pause 与 Cancel

Pause 用于阻止ProcessEngine 执行，同时保留 Trigger、Timer、Reconciliation、Outbox 与 Maintenance 推进。
`PAUSE_REQUESTED` 表示仍有 In-Flight Authority，需要等待 Fenced Outcome。

Cancel 是协作式的；关闭事故前必须确认 Effect 是否仍有不确定的外部 Authority。

## 9. Capability 与传输 Secret 事故

Kernel 不持有 Request-Identity HMAC Root 或 Page-Token Signing Key。HTTP/RPC Credential 与 opaque page-token key
按所属 Adapter 的协议轮换。Wait Token 是随机 Bearer Capability；发生泄露时应阻断其投递，核实是否已经提交，并按应用事故流程
处置。禁止为同一 Wait Boundary 构造第二个 Kernel Result。

## 10. 滚动重启、升级与回滚

优雅停机时，Worker 先停止领取并取消延迟探针，再等待正在执行的 Turn、Effect 与 Outbox；这些调用不会被中断，Spring
Lifecycle Phase 排空期间续租仍可工作。`spring.lifecycle.timeout-per-shutdown-phase` 与容器终止宽限期必须大于受支持的最长
在途调用。超过该外层期限后若进程被强杀，则由 Lease Expiry 与 Fencing 恢复；外部可见工作仍须幂等。

CompileFlow 不承诺持久化 Run 的 Application/Provider/Runtime 兼容。部署前：

1. 用目标构建验证所有 Active Run 引用的已存储 Process 均可完成 runtime 准备；
2. 对目标构建运行 Store 与 Race Contract；
3. 确认 Migration Forward-Only 且已有备份；
4. Canary 验证运维容量，而不是历史 Build Identity；
5. 若应用 Migration，回滚方案必须一致恢复完整应用与 Store。

部署后缺 Capability 时，发布合适的当前代码或 Script Provider。Kernel 不按 Application Build ID 路由。

## 11. 事故完成条件

只有以下条件全部满足才算完成：

- 不再存在异常 Expired Lease；
- Eligible Backlog Age 恢复；
- 每个 UNKNOWN/Review Effect 都有 Owner 和下一步；
- 每个 Abandoned Outbox 都有审计决定；
- 目标 Worker 已正确准备所需的已存储 Process runtime；
- 外部 Effect/Event Dedup Ledger 与 Kernel Identity 一致；
- Timeline 与变更记录不含秘密。

参见 [Durable 架构](../architecture/10-DURABLE_ARCHITECTURE.zh.md)。
