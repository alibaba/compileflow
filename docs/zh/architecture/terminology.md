# Durable 术语

以下术语适用于 Durable 公共 API 与架构文档。通用引擎和部署词汇见[项目术语表](../glossary.md)。Java
类型名、枚举值、配置项和线协议字段在正文中保留源码拼写。

流程定义及其 XML 语言统称为**流程模型**，不称“流程协议”。“协议”仅用于线协议、数据库协议和同步协议。

| 术语                              | 含义                                                                                                                                                                      |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 流程定义（Process definition）    | 调用方提供的流程模型。`ProcessDefinition.Inline` 包含模型类型、流程编码和精确文本，不携带版本、别名或部署元数据。                                                         |
| 流程制品（Process artifact）      | Deploy 管理的可执行投影，包括精确版本、校验后的定义、digest 与直接 Call binding；发布元数据留在 Control Plane 的发布记录中。Durable 不公开另一套制品包装类型。            |
| 流程版本（Process version）       | 不可变的长期身份 `(namespace, code, version)`；版本永不复用。                                                                                                             |
| Alias                             | Deploy 管理的可变指针，只在新调用或新流程实例准入时使用；恢复时不会再次解析 Alias。                                                                                       |
| 流程调用（Process invocation）    | 一次无状态的 `ProcessEngine.execute` 或 `trigger` 调用。跨调用的后续执行由应用负责。                                                                                      |
| Durable 流程实例（Durable run）   | 由 Durable 内核管理、可恢复且永久绑定一个精确已存储 Process 身份的执行实例。Version 只是可选准入归因，不是恢复权威。                                                      |
| Wait 实例（Wait occurrence）      | 一次已物化的外部等待。`WaitToken` 标识该实例，不标识 worker 尝试、租约、投递或业务操作。                                                                                  |
| Complete Wait                     | `DurableProcessEngine.completeWait` 提交一个 Wait 实例的类型化结果，不会同步推进或完成所属 Run；后续进展由 Durable Worker 执行。                                          |
| Trigger                           | 为本地 `ProcessEngine` 调用选择入口，不用于完成 Durable Wait。                                                                                                            |
| Effect                            | 可能访问外部系统的 Action 所采用的 Durable 执行语义，不是另一类业务节点。                                                                                                 |
| 语义检查点（Semantic checkpoint） | 流程级恢复位置，由恢复点以及作用域和控制帧组成，不包含流程变量。                                                                                                          |
| 延续快照（Continuation snapshot） | 继续执行所需的持久化状态，包括语义检查点和类型化流程变量，不包含生成代码或当前应用能力。                                                                                  |
| 前沿（Frontier）                  | 延续快照中一个可独立运行或等待的流程延续。Frontier 顺序是持久化的确定性调度状态。                                                                                         |
| 机器轮次（Machine turn）          | 对一个选中 Frontier 进行一次有界推进，随后通过一次受 fencing 保护的 Store 事务提交。Turn 可以 Yield、等待、发出 occurrence 或到达终态；它不是应用事务或 Worker 轮询尝试。 |
| Journal                           | 只追加的审计与诊断事实；既不是恢复日志，也不与 Outbox 一一对应。                                                                                                          |
| Outbox                            | 对封闭集成事件集合执行至少一次投递；消费者按事件 ID 去重。                                                                                                                |
| 流程实例视图（Run view）          | `getRun` 和 `listRuns` 返回的不含 payload 的脱敏生命周期视图。                                                                                                            |
| 流程实例结果（Run result）        | `getRunResult` 返回、显式包含 payload 的封闭结果：不存在、执行中、成功、失败或取消。                                                                                      |
| Command                           | 用于特权操作或控制面操作的结构化变更请求，把 revision、actor、reason 或审计上下文作为一个整体传递。高频应用调用直接使用语义参数。                                         |
| Query                             | 读取操作使用的不可变过滤条件、排序、边界和游标。                                                                                                                          |

普通中文叙述不必机械大写英文概念。只有 Java 类型、枚举值或正式产品面名称的拼写本身具有技术含义时，才保留其原始大小写。
