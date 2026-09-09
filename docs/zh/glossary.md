# CompileFlow 术语表

以下术语用于统一中文文档中的表达。Java 类型名、枚举值、配置键和 HTTP 字段保持源码拼写。

## 引擎与编译

| 英文                 | 中文       | 含义                                                       |
| -------------------- | ---------- | ---------------------------------------------------------- |
| Process engine       | 流程引擎   | 长期复用、支持多种流程格式的 `ProcessEngine`               |
| Process reference    | 流程引用   | 指向已发布版本或别名的引用                                 |
| Process definition   | 流程定义   | 以内联文本或类路径资源提供的流程内容                       |
| Preflight            | 预检       | 解析并校验流程，可选择准备运行时缓存；不发布版本或修改别名 |
| Warm-up              | 预热       | 在引擎缓存中准备指定流程定义                               |
| Compile-then-execute | 编译后执行 | 执行前先生成并编译 Java                                    |
| Generated runtime    | 生成运行时 | 引擎管理的流程类及运行时包装                               |
| Runtime identity     | 运行时身份 | 当前引擎中的精确编译标识（`ProcessRuntimeIdentity`）       |
| Runtime cache        | 运行时缓存 | 容量受限的已准备运行时缓存                                 |
| Invocation ID        | 调用 ID    | 一次已接收调用的稳定标识                                   |
| Process execution    | 执行信息   | 随结果返回的流程标识、调用标识、实际版本及起止时间         |

## 路由与部署

| 英文              | 中文       | 含义                                         |
| ----------------- | ---------- | -------------------------------------------- |
| Version           | 版本       | 已发布流程的不可变身份                       |
| Alias             | 别名       | 某个环境中指向稳定版本和候选版本的命名路由   |
| Stable version    | 稳定版本   | 别名默认选择的版本                           |
| Candidate version | 候选版本   | 接收灰度流量的版本                           |
| Rollout           | 发布操作   | 使用修订号检查修改别名流量的操作             |
| Canary            | 灰度       | 分配给候选版本的加权流量                     |
| Promote           | 全量切换   | 把候选版本变为唯一稳定版本                   |
| Abort             | 中止       | 恢复开始灰度时记录的稳定路由                 |
| Rollback          | 回滚       | 新建发布操作，重新指向此前记录的稳定版本     |
| Routing key       | 路由键     | 用于确定性分组的请求元数据                   |
| Route revision    | 路由修订号 | 别名路由的单调并发令牌                       |
| Control plane     | 控制面     | 管理发布、路由、灰度和 Outbox 状态的权威组件 |
| Runtime           | 运行时     | 安装并执行已发布流程的组件                   |

## 收敛与可靠性

| 英文                 | 中文         | 含义                                                           |
| -------------------- | ------------ | -------------------------------------------------------------- |
| Desired state        | 期望状态     | 运行时观察到的最新权威别名状态                                 |
| Local-ready state    | 本地就绪状态 | 所需版本已在当前节点安装的别名状态                             |
| Runtime installation | 运行时安装   | 解析、校验、编译并持有精确版本                                 |
| Transactional outbox | 事务 Outbox  | 与权威状态在同一事务中提交的投递记录                           |
| Reconciliation       | 对账         | 根据权威状态修复投影                                           |
| Tombstone            | 删除标记     | 防止旧状态恢复的带修订号删除记录                               |
| Dead letter          | 死信         | 有界尝试耗尽后的交付或执行                                     |
| Fencing token        | 隔离令牌     | 与单次租约绑定、提交时必须精确匹配的令牌，用于拒绝过期工作节点 |
| At-least-once        | 至少一次     | 允许重复投递的重试模型                                         |

## Durable 执行

Run、Wait、Effect、Journal、Outbox、检查点和执行结果的定义见 [Durable 术语](architecture/terminology.md)。

## 流程结构

| 英文              | 中文     | 含义                                      |
| ----------------- | -------- | ----------------------------------------- |
| Split gateway     | 分支网关 | 一个入边、多个出边的网关                  |
| Join gateway      | 汇合网关 | 多个入边、一个出边的网关                  |
| Branch frame      | 分支帧   | 一个并发分支独立使用的生成流程状态        |
| Continuation      | 后续路径 | 分支收敛后只生成一次的共享路径            |
| Invocation policy | 调用策略 | 一次同步动作调用的重试、超时与失败处理    |
| Trigger entry     | 触发入口 | 新触发调用的命名入口，不是 Durable 恢复点 |
| Process variable  | 流程变量 | 流程定义声明的类型化值                    |
| Routing attribute | 路由属性 | 与流程变量分离的策略输入                  |

## 文档术语

| 英文                | 中文       |
| ------------------- | ---------- |
| Quick Start         | 快速开始   |
| API Reference       | API 参考   |
| Extension Guide     | 扩展指南   |
| Supported Surfaces  | 支持范围与兼容性 |
| Operations Playbook | 运维手册   |
