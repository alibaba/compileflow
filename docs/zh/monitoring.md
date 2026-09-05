# 监控与可观测性

CompileFlow 提供三类相互独立的观测能力。分别配置它们，可以避免用一个布尔开关表达含糊的“全局可观测性”。

| 表面              | 所有者                         | 配置方式                                                       |
|-------------------|--------------------------------|----------------------------------------------------------------|
| 引擎生命周期事件  | 每个 `ProcessEngine`           | `ProcessEngineConfig` 能力与 `ProcessObservabilityConfig` 行为 |
| JVM 指标导出      | Spring 应用                    | Micrometer `MeterRegistry` 与标准 meter filter                 |
| Server 健康和诊断 | `compileflow-workbench-server` | Spring Actuator 与 server endpoint                             |

## 引擎事件

事件类型和 `ProcessEventListener` 位于 `compileflow-api` 的 event API。独立应用在 engine builder 上注册 listener；Spring
应用声明 listener bean。完整示例见[扩展指南](extension-guide.md)。

```yaml
compileflow:
  engine:
    observability:
      events:
        async: true
      mdc-propagation-enabled: false
```

- `events.async` 让全部生命周期事件经有界引擎 event executor 分发；
- `mdc-propagation-enabled` 只跨引擎拥有的 executor 复制 MDC，不会让任意应用线程池自动传播上下文；
- 同步 listener 位于执行调用路径，只适合短小、有界的操作；
- listener 失败会被记录并隔离，不影响引擎行为或其他 listener。
- 异步分发是 best-effort：不同事件可并发或乱序；有界 executor 拒绝时丢弃该事件并记录告警。同一事件内的 listener 顺序仍保持确定。

`ProcessEvent` 是封闭的不可变 record 集合。普通 execution start 事件包含流程 code 与 invocation ID；trigger start
事件还包含请求的 `ProcessTrigger`。完成和失败事件携带引擎返回的同一份受控
`ProcessExecution`，以及独立的运维 `ExecutionAttribution`；失败事件携带类型化 `ProcessError`，编译事件携带不可变流程 code
列表。trace ID 与事件时间是公共字段。任何事件都无法携带 routing key、流程变量、source 内容、任意 metadata 或原始异常对象。

`TraceIdProvider` 只是轻量关联入口，不是 CompileFlow tracing、span 或 context propagation 抽象。OpenTelemetry 集成可以读取
宿主应用当前 span context，无需让 Core 依赖 OTel SDK。它是引擎构造期协作者；独立应用在
`ProcessEngineConfig.Builder` 中配置，Spring 应用可以声明唯一 provider bean。应用未提供时 Spring 读取 MDC `traceId`；
超过 128 字符的 ID 会被视为不可用而不会被截断。没有合法上游 ID 时引擎生成
32 位十六进制本地 ID，provider 失败也不会中断执行。

## Micrometer

使用标准 Spring starter 时，只有以下条件全部满足，`CompileFlowMetricsAutoConfiguration` 才创建 binder：

1. classpath 中存在 Micrometer；
2. 存在 `MeterRegistry` bean；

启用 Durable 后，其 starter 在相同的两个 Micrometer 条件下注册独立 binder。

Spring Boot 的 `management.metrics.enable.*` 配置是标准 meter filter。binder 注册 meter 时，filter 可以拒绝其中某项；它不决定
binder bean 是否存在。

内建 binder 导出有界 counter 与节点聚合 gauge：

指标名称遵循 Micrometer 的点号 namespace，并不等同于 Spring 配置 key。例如，
`compileflow.engine.executor.runtime.load.max.concurrency` 反映配置项
`compileflow.engine.executor.runtime-load.max-concurrency`；`action.timeout` 指标对应配置中的 `action-timeout` 分组。
应用配置继续使用 kebab-case，指标过滤器继续使用点号形式。

| Micrometer 名称                                        | 含义                                                                                                                                                   |
|--------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.engine.executor.runtime.load.max.concurrency` | 配置的 runtime-load 并发上限                                                                                                                            |
| `compileflow.engine.executor.runtime.load.max.pending` | 等待 runtime-load 槽位的额外任务上限                                                                                                                   |
| `compileflow.engine.executor.action.timeout.max.concurrency` | 配置的 action-timeout 并发上限                                                                                                                         |
| `compileflow.engine.executor.action.timeout.max.pending` | 等待 action-timeout 槽位的额外尝试数上限                                                                                                                |
| `compileflow.engine.executor.event.delivery.max.concurrency` | 配置的事件投递并发上限                                                                                                                                |
| `compileflow.engine.executor.event.delivery.max.pending` | 等待事件投递槽位的额外任务上限                                                                                                                        |
| `compileflow.engine.events.dropped`                    | JVM 进程内所有 Engine 实例被有界事件 executor 拒绝的 best-effort 异步生命周期事件累计数                                                               |
| `compileflow.deploy.runtime.install.attempts`          | 按终态 `outcome` 与有界 `reason` 标记的 runtime 安装尝试数                                                                                             |
| `compileflow.deploy.alias.convergence`                 | 按终态 `outcome` 与有界 `reason` 标记的节点 alias 收敛尝试数                                                                                           |
| `compileflow.deploy.alias.desired.count`               | 当前节点已知的有效 desired alias 数                                                                                                                    |
| `compileflow.deploy.alias.local_ready.count`           | 当前节点可立即执行的有效 alias 数                                                                                                                      |
| `compileflow.deploy.alias.pending.count`               | 当前节点仍在收敛的 desired alias 数                                                                                                                    |
| `compileflow.deploy.runtime.retained.count`            | 托管部署数据面持有的精确 runtime 数                                                                                                                    |
| `compileflow.deploy.errors`                            | 按稳定 `error.code` 标记的部署错误数                                                                                                                   |
| `compileflow.deploy.operations`                        | 控制面变更操作数；`operation` 只取 `publish`、`create_rollout`、`rollback`、`update_canary`、`promote`、`abort`，`outcome` 只取 `success` 或 `failure` |
| `compileflow.deploy.reconciliation.runs`               | 已完成的控制面投影对账周期数                                                                                                                           |
| `compileflow.deploy.reconciliation.routing.mismatches` | 与路由投影不一致的权威 Alias 状态数                                                                                                                    |
| `compileflow.deploy.reconciliation.routing.repairs`    | 通过 outbox 入队的路由投影修正数                                                                                                                       |
| `compileflow.deploy.reconciliation.artifact.checks`    | `CHANNEL` 模式下已检查的有效 stable/candidate artifact 投影数                                                                                          |
| `compileflow.deploy.reconciliation.artifact.missing`   | 检测到的缺失有效 artifact 投影数                                                                                                                       |
| `compileflow.deploy.reconciliation.artifact.repairs`   | 通过不可变 CAS 重建的缺失有效 artifact 投影数                                                                                                          |
| `compileflow.deploy.reconciliation.artifact.conflicts` | 检测到且未覆盖的冲突或损坏有效 artifact 投影数                                                                                                         |
| `compileflow.deploy.reconciliation.artifact.failures`  | 有效 artifact 投影对账失败总数，包含冲突                                                                                                               |
| `compileflow.durable.operations`                        | 按有界 `operation` 与 `outcome` 标记的 Durable Runtime 操作数                                                                                           |
| `compileflow.durable.loaded.runtimes`                   | 当前节点已加载的可丢弃 Durable process runtime 数                                                                                                      |

不同 registry backend 会按自身规则转换名称，例如 Prometheus 使用下划线。文档不能把某个 backend 的名称误写成通用 Java
meter 名称。

默认部署指标绝不使用 namespace、process code、alias、version、digest、routing key、invocation ID 或 node ID 作为标签。这些维度应通过部署
runtime 诊断接口、结构化日志或 trace 按需查询。

执行延迟、结果和业务指标应由应用 listener 采集，因为标签和保留策略属于应用决策。标签必须有界：模型类型、
经过筛选的流程族通常合理；invocation ID、user ID、URL 和原始流程变量不适合作为指标标签。

## 健康与诊断

Starter 不提供伪造的 engine health indicator：engine bean 存在本身不能证明用户代码、容量或外部依赖健康。启用部署后会提供基于持久化
outbox 状态的 deployment-pipeline indicator。

`compileflow-workbench-server` 启用 Kubernetes 风格 liveness/readiness probe，并默认隐藏 health component 和
detail。只暴露部署平台真正需要的 Actuator endpoint。部署 runtime diagnostics 是运行状态，不是配置，应通过诊断 API 查询，不能
编码成 property。

`/api/deployment-control/health` 同时报告共享 outbox 计数和本地 dispatcher 状态。`UP` 表示仓储可读、dispatcher 正在运行且不存在
failed 或过期 claim；`DEGRADED` 表示存在投递失败或过期 claim；`DOWN` 表示 outbox 状态不可用或 dispatcher 已停止。Pending
深度是原始事实，不使用与负载无关的固定阈值解释。

`/api/async-invocations/health` 将共享持久队列计数与只属于当前 Server 进程的 `workerId`、`localRunningCount`、
`dispatchedCount` 分开。`degraded` 表示存在 dead letter 或过期 running lease；`healthy` 不承诺队列延迟或吞吐目标，告警应按应用
SLO 判断 ready queue 的深度与年龄。仓储查询失败会使 endpoint 请求失败，不会返回伪造的零计数快照。

Workbench Operate 的执行看板查询共享的持久化执行日志。指标、趋势、热门流程、分组错误与有效版本分布统一使用显式的 `1h`、`6h`、
`24h`、`7d` 或 `30d` 窗口，默认 `24h`。共享同一数据库的 Server 实例因此会返回一致的已保留执行事实；日志保留策略和显式删除决定可查询历史的边界。Workbench
Server 使用同步终态事件分发，避免事件队列饱和静默扭曲样本；持久化失败仍与流程结果隔离。这些运维记录不是 exactly-once 审计账本。

部署 runtime 诊断接口刻意采用不同作用域：它报告当前 Server 节点的 desired/local-ready 收敛、runtime 保留、
重试和容量。不能把这些节点本地值与数据库执行看板混合聚合，也不能把任一作用域描述成另一个。

## 生产规则

- 日志和指标应发送到应用拥有的 sink，不得在执行路径执行无界 I/O；
- 告警阈值由部署决定，库无法替业务流程选择可接受的延迟或失败率；
- 用 trace/invocation ID 关联日志，但不能把这些高基数值用作 metric tag；
- 可观测行为属于发布契约时，应测试 listener 顺序、失败隔离和异步行为；
- 生产环境应关闭或严格控制 health detail 与 debug compilation artifact。
