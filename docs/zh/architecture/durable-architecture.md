# Durable 架构

Durable 面向需要跨应用重启保存状态的流程。它是独立的执行产品，不是 `ProcessEngine` 的持久化开关。

## 核心不变量

Store 是 Run 的权威状态源。Worker 读取已提交的语义状态，执行一个有界 turn，并以原子事务提交下一段 continuation 和该 turn 产生的全部事实。提交前崩溃会重试；提交后崩溃从已提交边界恢复。

Kernel 不持久化生成的 Java source、class、bytecode、存活对象实例、executor 状态或内存路由。声明的应用值以类型化状态序列化。编译只是可丢弃的准备工作。恢复根据精确的流程定义和已提交的语义 checkpoint 重建可执行状态。

## 准入与执行

Start admission 物化一个精确且不可变的已存储 Process，并将 Run 绑定到它的 `processId`。它把精确的 `ProcessRef.Version` 或已发布 Alias 解析为精确版本。Alias 是 Deploy control-plane 状态，不是恢复权威。Engine 在解析一次 Alias 之前先拒绝重复使用的 Run ID。Alias 变化只影响后续 admission，不会重定向已经准入的 Run。`DurableProcessEngine` 和 `ProcessEngine` 是并列执行面。

Durable 只支持文档化的 TBBPM 和 BPMN profile。Action 声明 `execution=replayable|effect`，它与同步 retry policy 相互独立。Durable API 提供 Run、Effect 和 Operator 契约；定义注册只是 start 准入准备，不是应用生命周期 API。Run 的恢复不要求 Deploy，其路由状态也不是恢复权威。

## Worker 生命周期

`compileflow-durable-runtime` 中的 `DurableWorkerCoordinator` 负责轮询、有界执行容量、维护调度和
Worker 健康状态，不依赖 Spring。`DurableWorkerLifecycle` 只通过拥有它的 `DurableProcessEngine` 适配 Spring 启停时序；
禁用 Worker 时，既不创建 Worker 对象图，也不创建生命周期适配器。

停止时不再安排新任务，等待已准入任务退出，不中断应用代码；完成回调只在这些任务退出后触发。
组装宿主必须在等待期间保留 Store、续租和 Runtime 资源；停止调度不代表外部 Effect 已取消。
应用 API 与运维 API 继续保持能力分离。

## 流程身份

已存储流程定义由内容寻址的 `processId` 标识，process code、model type、精确字节与 digest 不可变。namespace 和可选 Version 属于 Run 准入归因，不属于已存储语义身份。同 code 的 Direct definition 可以产生不同 `processId`，无需 Version；已发布 Version 的内容仍不可变。每个 Run 保存精确 root `processId`，保留策略必须保证可恢复或保留 Run 引用的定义持续存在。

## Run 与 invocation 边界

每次 Start 创建一个稳定 Run ID。Process Call 在同一 Run 中创建 invocation frame，不会创建另一个 Run。Invocation frame 携带所属 turn 所需的语义输入、输出、状态和 continuation。

Root input 是已声明 `param` 变量的封闭 partial map；`return` 和 `inner` 由 Process 自己拥有。未声明 key 在应用代码运行前失败。Wait completion 为已有 Run 提交类型化结果，不创建新的 invocation 或 Run。

## 恢复权威

恢复使用已提交的语义 checkpoint、精确流程身份、类型化 invocation 状态、pending request、Wait/Timer 事实、Effect 事实和有界 ownership lease。数据库时间与 fencing token 保护 ownership；ownership 变化后，旧 Worker 的迟到完成会被拒绝。

准备阶段可以编译可丢弃 Runtime。Runtime 必须准备完成后才能执行；准备失败不能推进 Run。Worker 在拥有 turn 时续租，完成或失败时释放 ownership。

缺少应用 class、component、script executor 或 serializer 是 application/runtime capability 问题。它应在当前部署中修复，不得改写已存储语义或引入历史 build 路由。

## Action 与 Effect

Action 可以是确定性的流程逻辑，也可以是受治理的外部边界。可 replay 的 Action 可以用相同语义输入再次运行。外部 Effect request 在 dispatch 前以稳定 occurrence identity 提交，结果再独立解决。该身份便于应用实现去重，但不能证明应用已幂等；Kernel 不会把中断的外部调用假设成自动可逆。

Durable 拒绝非默认 `invocationPolicy`；同步 retry 与 timeout policy 属于 `ProcessEngine` invocation。Durable Effect recovery 使用已提交的 `effectPolicy` 与带 fencing 的 Store transition。外部调用失败或中断可能留下未知结果，不能据此证明外部操作没有发生。

## Wait 完成与查询

`WaitToken` 是一个已物化 Wait occurrence 的不透明、一次性 bearer capability。Store 只把它的 digest 保存为权威，并根据该 digest 找到所属 Run；调用方不能提供恢复坐标。Kernel 不要求应用建立 token 表；外部系统只暴露自身 job ID 时，integration 可以保留映射。Raw token 是凭据，不得进入日志、URL、metric 或 operator view。

Run、timeline 和 Outbox 查询使用类型化精确过滤与 keyset cursor。Durable 不提供模糊搜索；adapter 可以建立独立的非权威搜索 projection。

## 状态、事务与 Store

Run、invocation、request、lease 以及 Effect/Outbox 事实都使用显式状态转换。Store Provider 必须保持转换原子性、锁顺序、compare-and-set、数据库时间语义和 token fencing。PostgreSQL 和 MySQL 是带独立 migration 的一方 Provider；H2 仅用于测试。

物理表结构由 Provider 负责。Kernel 契约是事务和恢复语义，不是固定表数量。与 Provider 无关的 testkit 是受支持 Store 实现的验证依据。

## 保留与可观测性

只有在没有保留 Run 引用某个流程定义，且备份和回滚窗口允许时，才能删除该定义。生成 Runtime 可以更早释放。Retention 按引用决定；只保留最近 N 个版本并不充分。

Run view 和 metric 暴露受控状态和结果，不得暴露 payload 变量、凭据、路由 key、lease token 或其他秘密。参见[Durable Process](../durable-process.md)、[Durable 运维](../durable-operations-runbook.md)和[支持面清单](supported-surfaces.md)。
