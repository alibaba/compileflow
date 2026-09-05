# 发布与运行运维手册

不可变版本发布、Alias rollout、事务 Outbox 交付、运行时收敛和 Workbench 持久化异步执行遵循本手册。数据库保存
控制面和异步队列的权威状态；本地快照、投递通道和 worker 内存均可重建，不是权威数据源，也不得静默降级。

## 1. 拓扑准备

发布前确认进程职责：

| 进程          | 必需配置                                                                             |
|---------------|--------------------------------------------------------------------------------------|
| 嵌入式 Server | `topology=EMBEDDED`、`control-plane-enabled=true`、`runtime-worker-enabled=false`    |
| 分布式控制面  | `topology=DISTRIBUTED`、`control-plane-enabled=true`、`runtime-worker-enabled=false` |
| 分布式 worker | `topology=DISTRIBUTED`、`control-plane-enabled=false`、`runtime-worker-enabled=true` |

所有生产拓扑都应完成：

1. 运行 Flyway，确认应用 schema 位于预期 baseline。
2. 验证 datasource、分布式 sync channel 凭据和 artifact resolver 可用。
3. 确认 outbox 没有原因不明的 `FAILED` 或过期 `PROCESSING` 记录。
4. 切流前确认时钟、日志、指标和告警链路工作正常。

## 2. 发布版本

1. 校验 BPMN/TBBPM 定义并运行聚焦本次发布的测试。
2. 通过 `ProcessDeploymentService.publish(PublishProcessVersionCommand)` 或对应 Workbench 接口发布。
3. 核对 `(namespace, code, version)`、model type、精确 content digest、actor 与 release metadata。
4. 确认不可变 version row 已存在，并在切流前完成发布流水线要求的 preflight policy。
5. 禁止同一 version identity 对应不同内容；内容变化必须使用新版本。

发布版本不会切流，也不安装节点本地 runtime。不要编辑已发布记录。

## 3. 创建 Rollout

变更前立即读取当前 Alias 路由，并将其 `routeRevision` 作为 `expectedRouteRevision`。每次创建还必须使用在 route 与 operation
内稳定的 idempotency key。Java deployment command 将同一个 CAS 值命名为 `expectedAliasRevision`；Workbench HTTP 契约使用
`expectedRouteRevision`。

### 全量发布

创建目标为已发布版本的 `ALL_AT_ONCE` rollout。更新路由、追加历史、插入 outbox 的同一事务提交后，rollout 进入 `COMPLETED`。

### 灰度发布

以 1 到 9,999 个基点（0.01%–99.99%）的初始权重创建 `CANARY` rollout。当前稳定版本保留为 baseline，目标版本成为 candidate。只有当前步骤健康时才调用
`updateCanaryWeight(...)` 提高比例；最终通过 `promoteRollout(...)` 将 candidate 提升为唯一稳定版本。

把 `412 Precondition Failed` 当作并发信号：读取最新 route 与 rollout，重新决策后提交新请求。不要只替换 revision 盲目重放。
`409 Conflict` 表示语义冲突，需要操作人处理。

## 4. 验证收敛

Rollout 请求成功表示控制面事务已提交；outbox 交付与 runtime 安装仍是异步过程。

必须分别验证：

- 控制面：route revision、rollout phase、rollout events 与 outbox 状态。
- 嵌入式数据面：本地 route snapshot、有效执行 metadata 与一次真实执行。
- 分布式数据面：channel 交付、`DeployRuntime.snapshot()`、demanded/in-flight/deployed/backed-off 版本，以及带 route 归因的执行日志。
- 指标：`compileflow.deploy.operations`、`compileflow.deploy.runtime.install.attempts`、
  `compileflow.deploy.alias.convergence`、`compileflow.deploy.reconciliation.*`，以及 desired/local-ready/pending/retained 聚合
  gauge。

收敛期间，节点的 desired state 可以领先于 local-ready state；执行继续读取最后一次原子发布的 local-ready revision。若节点从未形成
local-ready revision，则 alias 解析 fail-closed。执行路径不会看到尚未完整持有所选制品的 desired route。

## 5. 评估灰度

为每个步骤声明观察窗口与最小样本数，同时使用技术指标和业务指标。

Server 内置健康接口：

- 只纳入 namespace、flow、route alias 都匹配的日志；
- 排除早于 rollout 创建时间的样本；
- 分别报告 baseline 与 candidate；
- 支持 candidate 绝对错误率和 p95 延迟阈值；
- 只读，不会提升、中止或以其他方式修改流量。

没有归因的日志不会进入评估结果。该响应只是操作人决策的证据，不是部署控制器。外部 SLO、业务 KPI、相对 baseline
分析和任何自动决策都需要单独设计 policy 与 authority。

## 6. 中止与回滚

### 中止进行中的灰度

1. 停止继续提高比例。
2. 使用最新 rollout revision 和明确原因调用 `abortRollout(AbortRolloutCommand)`。
3. 确认路由恢复到创建灰度时捕获的 baseline。
4. 确认新 route revision 已交付，candidate 流量归零。

中止会把当前灰度置为 `ABORTED`，不会改写此前事件。

### 回滚已完成发布

1. 选择仍拥有当前 route revision 且捕获了 baseline 的已完成 rollout。
2. 使用稳定 idempotency key、该 route revision 和 authenticated actor 调用
   `rollbackRollout(RollbackRolloutCommand)`。
3. 确认新的 `ROLLBACK` rollout 指向 source rollout 捕获的 baseline，再验证路由收敛。

原完成记录保持不变；已被后续 route intent 取代或已 aborted 的 rollout 不能作为含义模糊的回滚 source。

## 7. 运维持久化异步调用

`POST /api/processes/{code}/async-invocations` 只有在请求及其路由选择器持久化后才返回 `202`。精确 version 始终保持精确；Alias
请求由取得所有权的 worker 选择 stable target，并在进入流程代码前持久化精确 version 和 route revision。后续每次尝试都使用已固定的
精确根 version，Alias 切换不能改变已准入请求。

使用 `/api/async-invocations/health` 判断队列状态：

1. 对比 `readyQueuedCount` 与 `delayedQueuedCount`。前者在等待执行容量，后者在等待声明的重试时间。
2. `localRunningCount`、`dispatchedCount` 和 `workerId` 是节点本地事实；持久化的 queued、running、succeeded、dead-letter
   和过期租约计数是共享数据库事实。
3. `degraded` 表示至少存在 dead letter 或过期 running lease。`healthy` 不代表满足延迟 SLO；可接受的队列深度
   与年龄必须按实际负载单独告警。
4. 先使用 invocation record 和 `/api/async-invocations/{invocationId}/attempts` 关联问题。即使进程在引擎生成 trace
   前退出，attempt ledger 仍保留完整事实；对于带 `traceId` 的 attempt，再继续关联 execution log、effective version、Alias
   revision 和 runtime diagnostics。不得记录 routing key、payload variable 或 lease token。

处理 dead letter 时，先修复模型、制品、容量或依赖问题，再执行 requeue。单条与有界批量接口都以 CAS 将
`dead_letter` 转为 `queued`；并发或重复操作不能重置已经重新入队的请求。Requeue 保留 invocation identity 与已固定
version，将本轮 attempt count 归零以授予新的重试预算；累计 attempt 数、redrive 次数以及此前的 attempt ledger
均保持单调且不可删除。只有真正进入引擎的 attempt 才会产生 execution log 和 `traceId`。

进程退出后 queued record 仍然存在。Running record 只能在 lease 到期后恢复；新 owner 获取新 token，fencing
会拒绝旧进程迟到的完成结果。不得直接修改队列表或缩短 lease 强制恢复，应先检查数据库时间、scheduler 活性和 lease 配置。

## 8. 事故预案

### 发布被拒绝

1. 查看 `DeploymentException` 错误码和 request id。
2. 核对 reference/content identity、model type、UTF-8 大小、actor 和 caller digest assertion。
3. 需要 model-specific 定位时使用 preflight；内容变化使用新 version identity。

### 摘要不一致

1. 停止发布并保留 payload 供审计。
2. 对比入库 digest 与可信源重新计算的 digest。
3. 从可信 artifact source 发布新的不可变版本。

### Outbox 堆积

1. 检查 `/api/deployment-control/health`。
   `outboxStateAvailable=false` 表示快照为 `DOWN`；仓储不可用时不会用零计数伪装正常。
2. 分别检查 `PENDING`、`PROCESSING`、过期 claim、重试次数和 `FAILED`。
3. 分布式模式验证 channel 可用性、凭据与写延迟。
4. 修复根因后再 requeue dead letters。Route revision 提供幂等性，因此重复交付是预期且安全的；requeue 失败是操作失败，不会返回伪造的
   `requeued=0` 成功响应。

### Runtime 安装失败

1. 从 `DeployRuntime.snapshot()` 找到准确 backed-off version 与原因。
2. 验证已发布 artifact 存在、digest 匹配，且该 worker 可以解析。
3. 检查编译资源和 executor 容量。
4. 不要绕过本地安装检查，也不要启用 previous-version 降级。

### Server 或 Worker 重启

1. 确认部署 outbox dispatcher 和 reconciliation cycle 已重新启动。
2. 确认 desired route 收敛为 local-ready snapshot 后，再测试 Alias 执行。
3. 确认过期 async lease 只恢复一次，queued work 恢复执行且 effective version 不变。
4. 分别执行一次 Alias 请求和一次持久异步请求，在 execution log 中核对路由归因。

## 9. 日常演练

- 每周：巡检发布冲突、rollout 冲突、outbox backlog、dead letter、runtime 安装失败，以及 ready async backlog、过期 lease 与
  async dead letter。
- 大流量前：在 staging 演练 publish、canary、promote、abort、已完成 rollout 回滚和节点重启恢复。
- 事故后：记录命令 identity、route/rollout revision、outbox sequence、样本范围与收敛耗时。
- 按明确策略保留不可变版本；仍被 route 或保留的 rollout 历史引用时不得删除。
