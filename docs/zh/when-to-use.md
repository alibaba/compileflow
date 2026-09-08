# 何时使用 CompileFlow

CompileFlow 是面向 JVM 应用的嵌入式流程引擎，适合流程定义会被重复执行的业务场景。它支持 TBBPM 和文档列出的
BPMN 子集，并提供直接执行、版本化部署、持久化执行和可视化建模能力。接入时应根据场景选择相应能力。

## 先选择执行面

| 需求                              | 使用                      | 重要边界                                                             |
| --------------------------------- | ------------------------- | -------------------------------------------------------------------- |
| 在进程内执行可信流程定义          | `ProcessEngine`           | 不提供应用崩溃后的流程恢复                                           |
| 发布不可变版本并按别名路由        | CompileFlow Deploy        | 管理版本和流量，不保存流程执行状态                                   |
| 重启后从持久化节点恢复流程        | CompileFlow Durable       | 官方支持 PostgreSQL 和 MySQL；自定义 Store SPI 属于 Provider Preview |
| 持久化并重试完整的 Workbench 请求 | Workbench Server 异步调用 | 重试完整的引擎调用，不等同于 Durable 恢复                            |
| 建模和查看支持的流程定义          | Workbench                 | 提供设计与运维界面，Java 引擎负责实际执行                            |

## 适用场景

- 定价、库存校验、准入判断、订单校验和风控信号等高频业务规则。
- 业务与工程团队需要共同审查同一份流程定义。
- JVM 应用需要通过 `ProcessEngine.execute(...)` 和 `ProcessResult<T>` 建立类型化调用边界。
- 流程需要不可变版本、带修订号前置条件的别名路由、确定性灰度和可审计的发布记录。
- 使用 `while`、`break`、`continue` 以及 Java、Spring Bean 或脚本动作的自动化 TBBPM 流程。
- TBBPM 或 BPMN 流程需要通过 Durable 持久化等待、定时器、Effect、精确子流程调用、取消与恢复状态。
- Spring Boot 应用需要应用级引擎和有界可观测性。

## 不适用场景

- **完整人工任务管理。** CompileFlow 不提供分派、候选组、领取/完成、表单、升级、委派或审批历史。
- **完整 BPMN 2.0 执行语义。** CompileFlow 只执行文档列出的子集。人工任务、事务、编排和事件网关不在支持范围内；
  `cf:` 扩展仅适用于 CompileFlow。
- **几乎每次调用都使用唯一定义。** 编译成本需要通过重复执行摊销，一次性临时定义可能准备时间长于执行时间。
- **DMN 决策表。** CompileFlow 不包含 DMN 引擎。
- **非 JVM 运行时。** 执行环境需要使用受支持的 JDK（17、21 或 25），并包含 `jdk.compiler` 模块。

## 集成边界

- CompileFlow 负责进程内自动化执行，应用负责其外围的人工任务生命周期。
- `ProcessEngine.trigger(...)` 根据应用事件启动新执行，不会恢复已存储的 CompileFlow Run。
- Durable 保存流程实例和每次等待的状态，应用负责分派与授权。`WaitToken` 是一次性凭据，不得写入日志、
  指标标签、浏览器 URL 或外部元数据。
- 决策表和分布式协调属于应用职责。

## 决策清单

| 问题                                              | 是                                | 否                       |
| ------------------------------------------------- | --------------------------------- | ------------------------ |
| 每份定义会重复执行多次吗？                        | 可以摊销编译成本                  | 编译复用收益可能有限     |
| 重启后必须恢复流程状态吗？                        | CompileFlow Durable               | `ProcessEngine` 可能足够 |
| 重试完整请求是否足够？                            | Workbench Server 异步调用可能适用 | 恢复流程状态需要 Durable |
| 需要托管人工任务或完整 BPMN 标准吗？              | 超出支持范围                      | 继续核对其他需求         |
| 需要不可变版本和灰度路由吗？                      | 增加 CompileFlow Deploy           | 使用内联或类路径定义     |
| 应用能否运行在含 `jdk.compiler` 的受支持 JDK 上？ | 满足运行条件                      | 不支持该运行环境         |

## 后续阅读

- [支持面清单](architecture/supported-surfaces.md)
- [快速开始](quick-start.md)
- [流程格式参考](specifications/process-formats.md)
- [Durable Process](durable-process.md)
