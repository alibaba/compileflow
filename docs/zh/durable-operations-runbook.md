# Durable 运维手册

本手册适用于使用 PostgreSQL 或 MySQL 存储的 CompileFlow Durable。各存储实现负责自身的 V1 数据库结构；表的物理布局属于运维恢复细节，不是 Durable 内核的公共契约。

## 1. 安全规则

- 选定的 Durable 存储是唯一的执行事实源。同一组流程实例不得同时由 PostgreSQL 和 MySQL 承载。
- 执行数据库级恢复、迁移修复或需要加锁的人工检查前，停止全部工作节点。
- 禁止直接修改 Process、Run、Run-Process、Wait、Effect、Journal 或 Outbox 表记录。
- Pause/Resume、Effect 处置和 Outbox 处置必须通过带审计的运维 API 完成。
- 禁止记录状态快照、Effect 载荷、Wait 令牌、传输凭据或分页令牌。
- Alias 变更只影响之后启动的流程实例，不能用来改变已有实例的版本。

## 2. 最低健康证据

监控并告警：

- 最早可执行的 `RUNNABLE` 记录的等待时长和队列深度；
- 租约已超过 `lease_until` 的 `RUNNING` 记录；
- 到期后仍未处理的活动 Timer；
- `PENDING`、`RUNNING` 或 `UNKNOWN` 状态的 Effect，尤其是等待人工处置的时长；
- `PENDING/DELIVERING/ABANDONED` Outbox；
- 存储事务延迟、连接池耗尽、死锁和迁移校验失败；
- Durable 健康状态中的 `workerMachinery=degraded` 或 `leaseRenewal=degraded`、各工作通道的活动租约数和连续故障数，以及长期未更新的最近成功或进展时间；
- 同一已存储 Process ID 反复出现的运行时准备失败。

诊断时使用脱敏的 Run、Timeline 和 Outbox API；原始状态数据不属于运维查询接口。
工作节点故障与流程级能力未就绪是两类不同信号：前者会使节点进入降级状态，后者只标识受影响的 Process ID。

Run、Effect 和 Outbox 分别按 `lease-duration / 3` 的周期续租。租约时长应大于实测 JVM 停顿与数据库尾延迟之和；连接获取、网络、锁和语句超时应确保失败的续租调用能在下一个周期前返回。这些限制通过 DataSource 和数据库配置，不增加 Durable 内核配置项。

## 3. 存储故障

1. 工作节点在存储不可用时必须停止推进流程，不能在本机创建替代状态。
2. 恢复连接后，校验数据库时间、主库角色、迁移校验和与连接池状态。
3. 由维护任务回收过期的 Run、Effect 和 Outbox 租约。
4. 确认旧工作节点无法使用过期令牌提交结果。
5. 持续观察积压时长，直到恢复正常。

不要手工将 `RUNNING` 改为 `RUNNABLE`；应由租约回收机制恢复执行权。

## 4. 时间点恢复（PITR）

1. 停止所有能够启动流程、完成 Wait、取消流程、执行运维命令、领取任务、投递 Outbox 或执行维护任务的进程。
2. 记录恢复点，并清空所有节点本地的运行时缓存。
3. 将 Process、Run、Run-Process、Wait、Effect、Journal、Outbox 七表作为一个单元恢复。
4. 校验 Flyway 版本、校验和以及数据库约束。
5. 先启动一个运行时，加载保留实例所需的 Process ID，并检查诊断信息。
6. 启动其余工作节点，监控租约回收和任务积压。
7. 使用稳定的 Effect occurrence ID 或事件 ID 与外部系统对账。

禁止只恢复部分表，也不得让恢复前后的两套执行实例同时运行。

## 5. UNKNOWN Effect

1. 读取脱敏的 Effect 和 Timeline 信息。
2. 确认当前部署已加载对应的精确流程，并且 Action 和 reconcile 目标可用。
3. 使用 Effect occurrence ID 或映射后的业务键查询外部系统。
4. 能够证明操作已成功或未执行时，优先自动对账。
5. 必须人工判断时，由外层适配器完成认证和授权，再携带当前 `reviewRevision`、操作者、明确原因和可选审计上下文 ID 调用 `resolveEffect`。
6. 不得把超时直接视为失败，也不得通过取消流程掩盖尚未确认的外部操作结果。

如果当前部署缺少所需能力，应在当前运行时注册相应实现。Durable 状态不会记录或选择应用构建版本。

## 6. Outbox 故障

1. 确认稳定的 `eventId`、流程实例、事件类型、尝试次数和状态。
2. 检查已认证的投递端和下游去重记录。
3. 确认可以安全重投后，使用运维重试操作。
4. 确认事件不应再投递时，通过带审计和授权的操作将其标记为放弃。
5. 消费方必须保持幂等，因为超时可能发生在下游已经接收事件之后。

Journal 记录不会自动重新发布；Journal 与 Outbox 的语义不同。

## 7. 流程实例停滞或能力缺失

- `RUNNABLE` 持续退避：检查 Process 运行时的加载状态、`ScriptExecutor`、Java/Spring 调用目标和编译诊断；
- `RUNNING` 租约过期：检查维护任务、数据库时间和回收索引；
- `WAITING` Event：检查 `WAIT_COMMITTED` 事件投递和令牌处理；
- `WAITING` Timer 已到期：检查 Timer 扫描任务和数据库时钟；
- `WAITING` Effect：检查待执行、未知或待人工处置状态。

不得仅根据基础设施重试次数自动将异常流程实例标记为 `FAILED`。需要隔离时应先暂停，修复当前部署后再恢复。

## 8. Pause 与 Cancel

暂停用于停止流程实例的业务执行，同时允许 Wait 完成、Timer、对账、Outbox 和维护任务继续推进。
`PAUSE_REQUESTED` 表示仍有任务正在执行，需要等待受租约保护的结果返回。

取消采用协作式语义。关闭事故前，必须确认 Effect 是否仍存在结果不确定的外部操作。

## 9. 凭证泄露

Durable 内核不持有请求身份 HMAC 根密钥或分页令牌签名密钥。HTTP/RPC 凭据和不透明分页令牌密钥按所属适配器的协议轮换。Wait 令牌属于持有者凭证；发生泄露时应阻断其投递，核实是否已经提交，并按应用安全事件流程处置。禁止为同一个 Wait 边界生成第二个结果。

## 10. 优雅停机

优雅停机时，工作节点先停止领取任务并取消延迟探测，再等待正在执行的轮次、外部操作和 Outbox 任务完成。这些调用不会被中断，Spring 生命周期排空期间仍会续租。`spring.lifecycle.timeout-per-shutdown-phase` 与容器终止宽限期必须大于最长的在途调用时间。超过期限后进程若被强制终止，将由租约过期和隔离令牌机制恢复；对外部系统可见的操作仍须保持幂等。

Durable 不按应用构建 ID 选择恢复节点。已存储流程必须能由当前引擎和已注册能力执行；否则流程实例仍可恢复，但在所需能力可用前不会继续推进。

## 11. 事故完成条件

只有以下条件全部满足才算完成：

- 没有异常的过期租约；
- 可执行任务的积压时长恢复正常；
- 每个 UNKNOWN 或待人工处置的 Effect 都有明确负责人和下一步动作；
- 每个已放弃的 Outbox 事件都有审计记录；
- 目标工作节点已正确加载所需的流程运行时；
- 外部 Effect 和事件去重记录与 Durable 身份一致；
- Timeline 与变更记录不包含敏感信息。

参见 [Durable 架构](architecture/durable-architecture.md)。
