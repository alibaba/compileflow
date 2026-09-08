# 监控与可观测性

CompileFlow 提供三类相互独立的观测能力，可按需分别配置。

| 表面             | 所有者                         | 配置方式                                                       |
| ---------------- | ------------------------------ | -------------------------------------------------------------- |
| 引擎生命周期事件 | 每个 `ProcessEngine`           | `ProcessEngineConfig` 能力与 `ProcessObservabilityConfig` 行为 |
| JVM 指标导出     | Spring 应用                    | Micrometer `MeterRegistry` 与标准指标过滤器                    |
| 服务端健康和诊断 | `compileflow-workbench-server` | Spring Actuator 与服务端接口                                   |

## 引擎事件

事件类型和 `ProcessEventListener` 位于 `compileflow-api` 的事件 API。普通 Java 应用通过引擎构建器注册监听器；Spring 应用声明监听器 Bean。完整示例见[扩展指南](extension-guide.md)。

```yaml
compileflow:
    engine:
        observability:
            events:
                async: true
            mdc-propagation-enabled: false
```

- `events.async` 让全部生命周期事件通过有界的事件执行器分发；
- `mdc-propagation-enabled` 只在引擎自有执行器之间复制 MDC，不会让应用线程池自动传播上下文；
- 同步监听器位于执行调用路径，只适合耗时短且资源消耗有界的操作；
- 监听器失败会被记录并隔离，不影响引擎行为或其他监听器；
- 异步分发采用尽力而为语义：不同事件可能并发或乱序；有界执行器拒绝任务时会丢弃事件并记录告警。同一事件内的监听器顺序保持确定。

生命周期事件不能作为正确性、审计、计费或可靠集成的依据。`ProcessEngine` 执行应使用应用管理的事务或 Outbox；
Durable 执行使用自身的事件日志和 Outbox。

`ProcessEvent` 是一组封闭且不可变的记录。普通执行开始事件包含命名空间、流程编码和调用 ID；触发执行开始事件还包含请求的
`ProcessTrigger`。完成和失败事件包含引擎返回的 `ProcessExecution`，以及独立的运维信息 `ExecutionAttribution`；
失败事件还包含类型化的 `ProcessError`。公共事件只覆盖执行与触发执行的生命周期。

追踪 ID 和事件时间是公共字段。事件不会携带路由键、流程变量、流程源码、任意元数据或原始异常对象。

`TraceIdProvider` 只负责提供关联标识，不定义完整的链路追踪或上下文传播机制。宿主应用可以从当前追踪上下文中读取追踪 ID，而无需让核心模块依赖具体追踪 SDK。普通 Java 应用通过 `ProcessEngineConfig.Builder` 配置，Spring 应用可以声明唯一的 `TraceIdProvider` Bean。应用未提供时，Spring 会读取 MDC 中的 `traceId`；
超过 128 字符的 ID 会被视为不可用而不会被截断。没有合法上游 ID 时引擎生成
32 位十六进制本地 ID，提供方失败也不会中断执行。

## Micrometer

使用标准 Spring Starter 时，只有以下条件全部满足，`CompileFlowEngineMetricsAutoConfiguration` 才创建指标绑定器：

1. 类路径中存在 Micrometer；
2. 存在 `MeterRegistry` Bean；
3. 存在 `ProcessEngine` Bean。

容量仪表读取默认引擎实际使用的配置，包括应用自行创建的引擎。另行构造的引擎应为自身配置提供指标绑定；内置绑定器不会根据无关的 `ProcessEngineConfig` Bean 推测容量。JVM 级事件丢弃计数器始终可用。

启用 Durable 且存在 Durable 引擎后，其 Starter 会在相同的 Micrometer 条件下注册独立指标绑定器。

Spring Boot 的 `management.metrics.enable.*` 配置是标准 meter filter。binder 注册 meter 时，filter 可以拒绝其中某项；它不决定
指标绑定器 Bean 是否存在。

内置绑定器导出计数器和节点级聚合仪表：

指标名称遵循 Micrometer 的点号 namespace，并不等同于 Spring 配置 key。例如，
`compileflow.engine.executor.runtime.load.max.concurrency` 反映配置项
`compileflow.engine.executor.runtime-load.max-concurrency`；`action.timeout` 指标对应配置中的 `action-timeout` 分组。
应用配置继续使用 kebab-case，指标过滤器继续使用点号形式。

| Micrometer 名称                                              | 含义                                                                                                                                                   |
| ------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `compileflow.engine.executor.runtime.load.max.concurrency`   | 配置的 runtime-load 并发上限                                                                                                                           |
| `compileflow.engine.executor.runtime.load.max.pending`       | 等待 runtime-load 槽位的额外任务上限                                                                                                                   |
| `compileflow.engine.executor.action.timeout.max.concurrency` | 配置的 action-timeout 并发上限                                                                                                                         |
| `compileflow.engine.executor.action.timeout.max.pending`     | 等待 action-timeout 槽位的额外尝试数上限                                                                                                               |
| `compileflow.engine.executor.event.delivery.max.concurrency` | 配置的事件投递并发上限                                                                                                                                 |
| `compileflow.engine.executor.event.delivery.max.pending`     | 等待事件投递槽位的额外任务上限                                                                                                                         |
| `compileflow.engine.executor.<pool>.active`                  | 指定引擎线程池中正在执行的任务数；`<pool>` 可取 `runtime.load`、`action.timeout` 或 `event.delivery`                                                   |
| `compileflow.engine.executor.<pool>.pending`                 | 指定引擎线程池中等待准入或执行的任务数                                                                                                                 |
| `compileflow.engine.executor.<pool>.rejected`                | 指定引擎线程池因容量耗尽或关闭而拒绝的任务累计数                                                                                                       |
| `compileflow.engine.events.dropped`                          | JVM 进程内所有引擎实例被有界事件执行器拒绝的尽力投递异步生命周期事件累计数                                                                             |
| `compileflow.deploy.runtime.install.attempts`                | 按终态 `outcome` 与有界 `reason` 标记的 runtime 安装尝试数                                                                                             |
| `compileflow.deploy.alias.convergence`                       | 按终态 `outcome` 与有界 `reason` 标记的节点 alias 收敛尝试数                                                                                           |
| `compileflow.deploy.alias.desired.count`                     | 当前节点已知的有效 desired alias 数                                                                                                                    |
| `compileflow.deploy.alias.local_ready.count`                 | 当前节点可立即执行的有效 alias 数                                                                                                                      |
| `compileflow.deploy.alias.pending.count`                     | 当前节点仍在收敛的 desired alias 数                                                                                                                    |
| `compileflow.deploy.runtime.retained.count`                  | 托管部署运行时持有的精确 runtime 数                                                                                                                    |
| `compileflow.deploy.errors`                                  | 按稳定 `error.code` 标记的部署错误数                                                                                                                   |
| `compileflow.deploy.operations`                              | 控制面变更操作数；`operation` 只取 `publish`、`create_rollout`、`rollback`、`update_canary`、`promote`、`abort`，`outcome` 只取 `success` 或 `failure` |
| `compileflow.deploy.reconciliation.runs`                     | 已完成的控制面投影对账周期数                                                                                                                           |
| `compileflow.deploy.reconciliation.routing.mismatches`       | 与路由投影不一致的权威 Alias 状态数                                                                                                                    |
| `compileflow.deploy.reconciliation.routing.repairs`          | 通过 outbox 入队的路由投影修正数                                                                                                                       |
| `compileflow.deploy.reconciliation.artifact.checks`          | `PROJECTION_STORE` 模式下已检查的有效 stable/candidate artifact 投影数                                                                                 |
| `compileflow.deploy.reconciliation.artifact.missing`         | 检测到的缺失有效 artifact 投影数                                                                                                                       |
| `compileflow.deploy.reconciliation.artifact.repairs`         | 通过不可变 CAS 重建的缺失有效 artifact 投影数                                                                                                          |
| `compileflow.deploy.reconciliation.artifact.conflicts`       | 检测到且未覆盖的冲突或损坏有效 artifact 投影数                                                                                                         |
| `compileflow.deploy.reconciliation.artifact.failures`        | 有效 artifact 投影对账失败总数，包含冲突                                                                                                               |
| `compileflow.durable.operations`                             | 按有界 `operation` 与 `outcome` 标记的 Durable Runtime 操作数                                                                                          |
| `compileflow.durable.loaded.runtimes`                        | 当前节点已加载的可丢弃 Durable process runtime 数                                                                                                      |

不同指标后端可能按自身规则转换名称。上表使用的是通用 Java 指标名称。

默认部署指标不使用命名空间、流程编码、别名、版本、摘要、路由键、调用 ID 或节点 ID 作为标签。这些高基数信息应通过部署运行时诊断接口、结构化日志或追踪信息按需查询。

执行延迟、结果和业务指标应由应用监听器采集，因为标签和保留策略由应用决定。标签取值范围必须有界：模型类型和经过筛选的流程族通常适合；调用 ID、用户 ID、URL 和原始流程变量不适合作为指标标签。

## 健康与诊断

Starter 不提供仅检查引擎 Bean 是否存在的健康指标，因为这不能证明流程代码、执行容量或外部依赖正常。启用部署功能后，会提供基于持久化 Outbox 状态的部署管道健康指标。

`compileflow-workbench-server` 提供存活与就绪探针，并默认隐藏健康检查的组件和详情，只暴露部署平台所需的 Actuator
端点。部署运行时诊断信息属于运行状态，应通过诊断 API 查询，不能编码成配置项。

`/api/deployment-control/health` 同时报告共享 Outbox 计数和本地分发器状态。`UP` 表示存储可读、分发器正在运行且不存在失败或过期的领取记录；`DEGRADED` 表示存在投递失败或过期领取；`DOWN` 表示 Outbox 状态不可用或分发器已停止。待处理数量只反映当前状态，不使用与实际负载无关的固定阈值解释。

`/api/async-invocations/health` 将共享持久队列计数与当前 Server 进程的 `workerId`、`localRunningCount` 和
`dispatchedCount` 分开。`degraded` 表示存在死信或过期的运行租约；`healthy` 不代表队列延迟或吞吐已经达到目标。告警应依据应用
SLO 判断就绪队列的深度和等待时间。存储查询失败时接口会返回错误，不会伪造零计数快照。

Workbench Operate 的执行看板查询共享的持久化执行日志。指标、趋势、热门流程、分组错误与有效版本分布统一使用显式的 `1h`、`6h`、
`24h`、`7d` 或 `30d` 窗口，默认 `24h`。共享同一数据库的 Server 实例因此会返回一致的已保留执行事实；日志保留策略和显式删除决定可查询历史的边界。Workbench
Server 使用同步终态事件分发，避免事件队列饱和静默扭曲样本；持久化失败仍与流程结果隔离。这些运维记录不是 exactly-once 审计账本。

部署运行时诊断接口以节点为单位，报告当前服务节点的期望状态、本地就绪状态、运行时保留、重试和容量。
这些节点本地数据不能与数据库执行看板混合聚合，也不能替代共享状态。

## 生产规则

- 日志和指标应发送到应用管理的接收端，不得在执行路径执行无界 I/O；
- 告警阈值由部署决定，库无法替业务流程选择可接受的延迟或失败率；
- 使用 trace ID 或调用 ID 关联日志，但不能将这些高基数值用作指标标签；
- 可观测行为属于公开契约时，应测试监听器顺序、失败隔离和异步行为；
- 生产环境应关闭或严格控制健康详情和调试编译产物。
