# CompileFlow Glossary

Use these terms consistently in English and Chinese documentation. Java type names, enum values, property keys, and HTTP
fields remain unchanged in prose.

## Engine And Compilation

| English               | Chinese      | Meaning                                                              |
|-----------------------|--------------|----------------------------------------------------------------------|
| Process engine        | 流程引擎     | Long-lived, format-bound `ProcessEngine`                             |
| Process reference     | 流程引用     | Identity of an existing code, exact version, or Alias                |
| Process definition    | 流程定义     | Explicit inline or classpath content                                 |
| Preflight             | 预检         | Parse, validate, and optionally dry-run compile without installation |
| Warm-up               | 预热         | Compile an explicit definition into the engine cache                 |
| Compile-then-execute  | 编译后执行   | Generate and compile Java before running a process                   |
| Generated runtime     | 生成运行时   | Engine-owned compiled process class and runtime wrapper              |
| Runtime identity      | 运行时身份   | Engine-local exact compilation identity (`ProcessRuntimeIdentity`)   |
| Runtime cache         | 运行时缓存   | Bounded cache of generated runtimes                                  |
| Invocation ID         | 调用 ID      | Stable identifier for one accepted invocation                        |
| Process execution     | 流程执行记录 | Controlled attribution returned with an outcome                      |

## Routing And Deployment

| English           | Chinese    | Meaning                                                     |
|-------------------|------------|-------------------------------------------------------------|
| Version           | 版本       | Immutable published process identity                        |
| Alias             | Alias      | Named stable/candidate route for an environment             |
| Stable version    | 稳定版本   | Default version selected by an Alias                        |
| Candidate version | 候选版本   | Version receiving canary traffic                            |
| Rollout           | Rollout    | Revision-checked operation that changes Alias traffic       |
| Canary            | 灰度       | Weighted traffic to a candidate version                     |
| Promote           | 提升       | Make the candidate the sole stable version                  |
| Abort             | 中止       | Restore the stable route captured by an active canary       |
| Rollback          | 回滚       | New rollout to a baseline captured by a completed rollout   |
| Routing key       | 路由键     | Request metadata used for deterministic cohort selection    |
| Route revision    | 路由修订号 | Monotonic concurrency token for one Alias route             |
| Control plane     | 控制面     | Authoritative publication, route, rollout, and outbox state |
| Data plane        | 数据面     | Runtime installation and published execution                  |

## Convergence And Reliability

| English              | Chinese      | Meaning                                                   |
|----------------------|--------------|-----------------------------------------------------------|
| Desired state        | 期望状态     | Newest authoritative Alias state observed by a runtime    |
| Local-ready state    | 本地就绪状态 | Alias state whose required runtimes are installed locally |
| Runtime installation | 运行时安装   | Resolve, verify, compile, and retain an exact version     |
| Transactional outbox | 事务 Outbox  | Delivery record committed with authoritative state        |
| Reconciliation       | 对账         | Repair a projection from authoritative state              |
| Tombstone            | 删除标记     | Revisioned removal that prevents stale state resurrection |
| Dead letter          | 死信         | Delivery or execution exhausted after bounded attempts    |
| Fencing token        | 隔离令牌     | Lease-specific token that must match at commit time and rejects stale workers |
| At-least-once        | 至少一次     | Retry model that can repeat external side effects         |

## Durable Execution

The [Durable terminology](../architecture/11-TERMINOLOGY.en.md) page is the single source for run, wait, effect,
checkpoint, journal, outbox, and result terminology. Keeping those definitions in one place prevents the user glossary
and architecture contract from drifting apart.

## Process Structure

| English           | Chinese     | Meaning                                                            |
|-------------------|-------------|--------------------------------------------------------------------|
| Split gateway     | 分支网关    | Gateway with one incoming and multiple outgoing transitions        |
| Join gateway      | 汇聚网关    | Gateway with multiple incoming and one outgoing transition         |
| Branch frame      | 分支帧      | Isolated generated-process state for one concurrent branch         |
| Continuation      | 后续路径    | Shared path generated once after branch convergence                |
| Invocation policy | 调用策略    | Retry, timeout, and failure handling for one synchronous action call |
| Trigger entry     | 触发入口    | Named entry for a new trigger invocation, not a durable checkpoint |
| Process variable  | 流程变量    | Typed value declared by the process definition                     |
| Routing attribute | 路由属性    | Policy input kept separate from process variables                  |

## Documentation Terms

| English             | Chinese  |
|---------------------|----------|
| Quick Start         | 快速开始 |
| API Reference       | API 参考 |
| Extension Guide     | 扩展指南 |
| Supported Surfaces  | 支持范围 |
| Operations Playbook | 运维手册 |
| Release Handbook    | 发布手册 |
| Design decision     | 设计决策 |
| Proposal            | 提案     |
