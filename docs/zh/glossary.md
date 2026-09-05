# CompileFlow 术语表

中英文文档统一使用以下术语。Java 类型名、枚举值、配置 key 和 HTTP field 在正文中保持原样。

## 引擎与编译

| English               | 中文         | 含义                                                 |
|-----------------------|--------------|------------------------------------------------------|
| Process engine        | 流程引擎     | 长期复用、绑定流程格式的 `ProcessEngine`             |
| Process reference     | 流程引用     | 已有 code、精确版本或 Alias 的 identity              |
| Process definition    | 流程定义     | 显式 inline 或 classpath 内容                       |
| Preflight             | 预检         | 解析、校验并可选执行 dry-run compile，不安装 runtime |
| Warm-up               | 预热         | 把显式定义编译进 engine cache                        |
| Compile-then-execute  | 编译后执行   | 执行前先生成并编译 Java                              |
| Generated runtime     | 生成运行时   | Engine 管理的编译流程类及 runtime wrapper            |
| Runtime identity      | 运行时身份   | Engine 本地的精确编译标识（`ProcessRuntimeIdentity`）       |
| Runtime cache         | 运行时缓存   | 有界生成 runtime cache                               |
| Invocation ID         | 调用 ID      | 一次已接收调用的稳定标识                             |
| Process execution     | 流程执行记录 | 与执行结果一起返回的受控归因                         |

## 路由与部署

| English           | 中文       | 含义                                             |
|-------------------|------------|--------------------------------------------------|
| Version           | 版本       | 不可变的已发布流程 identity                      |
| Alias             | Alias      | 某个环境的命名 stable/candidate route            |
| Stable version    | 稳定版本   | Alias 默认选择的版本                             |
| Candidate version | 候选版本   | 接收灰度流量的版本                               |
| Rollout           | Rollout    | 使用 revision 检查修改 Alias 流量的操作          |
| Canary            | 灰度       | 分配给候选版本的加权流量                         |
| Promote           | 提升       | 把候选版本变为唯一稳定版本                       |
| Abort             | 中止       | 恢复 active canary 创建时捕获的稳定路由          |
| Rollback          | 回滚       | 新建 rollout，指向已完成 rollout 捕获的 baseline |
| Routing key       | 路由键     | 用于确定性 cohort 选择的请求 metadata            |
| Route revision    | 路由修订号 | 一个 Alias route 的单调并发 token                |
| Control plane     | 控制面     | 权威发布、route、rollout 与 outbox state         |
| Data plane        | 数据面     | Runtime 安装与已发布执行                           |

## 收敛与可靠性

| English              | 中文         | 含义                                    |
|----------------------|--------------|-----------------------------------------|
| Desired state        | 期望状态     | Runtime 观察到的最新权威 Alias state    |
| Local-ready state    | 本地就绪状态 | 所需 runtime 已在本地安装的 Alias state |
| Runtime installation | 运行时安装   | 解析、校验、编译并持有精确版本          |
| Transactional outbox | 事务 Outbox  | 与权威 state 一起提交的交付记录         |
| Reconciliation       | 对账         | 根据权威 state 修复 projection          |
| Tombstone            | 删除标记     | 防止旧 state 复活的带 revision 删除记录 |
| Dead letter          | 死信         | 有界尝试耗尽后的交付或执行              |
| Fencing token        | 隔离令牌     | 与单次租约绑定、提交时必须精确匹配的令牌，用于拒绝过期 worker |
| At-least-once        | 至少一次     | 可能重复外部副作用的 retry model        |

## Durable 执行

流程实例、Wait、Effect、检查点、Journal、Outbox 和执行结果的定义统一维护在
[Durable 术语](../architecture/11-TERMINOLOGY.zh.md)中，避免用户术语表与架构契约出现两套解释。

## 流程结构

| English           | 中文        | 含义                                               |
|-------------------|-------------|----------------------------------------------------|
| Split gateway     | 分支网关    | 一个入边、多个出边的网关                           |
| Join gateway      | 汇聚网关    | 多个入边、一个出边的网关                           |
| Branch frame      | 分支帧      | 一个并发分支独立使用的生成流程状态                 |
| Continuation      | 后续路径    | 分支收敛后只生成一次的共享路径                     |
| Invocation policy | 调用策略    | 一次同步 action 调用的 retry、timeout 与失败处理 |
| Trigger entry     | 触发入口    | 新 trigger 调用的命名入口，不是 durable checkpoint |
| Process variable  | 流程变量    | 流程定义声明的类型化值                             |
| Routing attribute | 路由属性    | 与流程变量分离的策略输入                           |

## 文档术语

| English             | 中文     |
|---------------------|----------|
| Quick Start         | 快速开始 |
| API Reference       | API 参考 |
| Extension Guide     | 扩展指南 |
| Supported Surfaces  | 支持范围 |
| Operations Playbook | 运维手册 |
| Release Handbook    | 发布手册 |
| Design decision     | 设计决策 |
| Proposal            | 提案     |
