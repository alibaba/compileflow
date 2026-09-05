# Durable Kernel Store 测试

`compileflow-durable-testkit` 提供针对原子 `DurableStore` Provider 协议的第一方可执行契约。它只依赖
`durable-spi`，从而保持 `runtime -> spi <- postgres`，但并不把该协议变成通用持久化抽象，也不承诺第三方 Store
属于 Supported。Runtime collaborator 只消费窄语义 Store role，第一方 PostgreSQL Provider 负责完整事务组合。
契约测试验证可观察的状态转换；PostgreSQL 模块另行约束当前 V1 七表布局。

Provider SPI 与 Kernel 作为一个强契约共同演进。新增权威持久化 transition 的版本可以要求 Store 实现新的抽象方法，不能通过默认方法在
运行时抛出 `UnsupportedOperationException` 来伪装兼容。Provider 必须显式升级并通过当前完整 Testkit，才能声明兼容对应的
CompileFlow 版本。构建或启动期失败优于运行中的部分支持。

## Contract 覆盖

`DurableStoreContract` 当前证明：

- Process 注册不可变且幂等；
- 简化 Start 重载生成新的 Run ID；caller-identified 重载保留传入 ID，并拒绝复用；
- Wait Commit、Token Trigger、Resume、Terminal Commit 与过期 Run Lease 拒绝；
- Pause/Resume/Cancel 正交；
- Timer 与 Effect 作为 Committed Resume Fact；
- Outbox Event Identity/Type/Payload 稳定、Retry Attempt 计数与 Stale Token Fencing；
- Run、Effect、Outbox Claim 的有界批量 Lease Renewal、部分 Authority 丢失、整毫秒时长校验与 Crash Recovery；
- Occurrence Revision、Terminal Retention 与 Run-First Race/Deadlock Safety。

Runtime 续租测试另行证明 Run/Effect/Outbox Lane 隔离、有界分块继续执行、Store 未知故障后的重试、卡住调用检测，以及健康状态
降级与恢复；Spring 测试证明优雅停机先停止领取，再无中断地排空在途工作。

发布证据还必须覆盖 Migration Constraint、Query Plan/Index、竞争并发、最小权限数据库角色、Backup/Restore
与支持的 PostgreSQL 版本。

每个第一方 PostgreSQL Contract Test 都会调用 `createEmptyStore()`，测试方法之间不共享状态。

## 第一方 PostgreSQL 路径

有 Docker 时，普通 Maven Suite 通过 Testcontainers 执行
`PostgresDurableStoreContractTest`：

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgres \
  -am
```

没有 Docker 时，提供一个可丢弃 PostgreSQL 数据库。测试会在每个 Case 前 Clean 并重新 Migrate public schema。
该 wildcard 还执行进程级 ACK-loss 测试：子 JVM 先 fsync 已接受的 `WAIT_COMMITTED` 事件，再在 Outbox complete 前被
强制终止；恢复必须重发同一 logical event，随后才能提交 DELIVERED。

```bash
export COMPILEFLOW_DURABLE_POSTGRES_URL='jdbc:postgresql://127.0.0.1:5432/compileflow_test'
export COMPILEFLOW_DURABLE_POSTGRES_USER='postgres'
export COMPILEFLOW_DURABLE_POSTGRES_PASSWORD='postgres'

./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgres \
  -am \
  -Dtest='LocalPostgresDurable*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

绝不能把该测试指向含有重要数据的数据库。

## 必需 Race 证据

Production Release Evidence 至少覆盖以下并发测试：

1. 两个 Worker 同时 Claim 同一 Run/Effect/Outbox；
2. Lease 过期、新 Claim 成功、旧 Token Late Completion；
3. Trigger 与 Cancel；
4. Pause 与 In-Flight Segment/Effect；
5. Timer Fire 与 Cancel；
6. Effect Completion 与 Operator Resolution；
7. Outbox Completion 与 Retry/Abandon；
8. 并发 Start 产生彼此不同且合法的 Run ID；
9. 外部 durable acceptance 后在 Outbox complete 前 `SIGKILL`，恢复后重放 exact event。

不变量始终是只能有一个 Committed Winner；Worker Timing 不是 Authority。

## Contract 刻意不测试

Store Contract 不承诺：

- Application Build 或 Runtime/Provider Version 兼容；
- Alias Resolve 或 Rollout；
- Generated Program/Bytecode 持久化；
- Custom Persisted Codec；
- Exactly-Once Remote Effect；
- Generic External Outbox 副作用 Exactly-Once；
- 可移植的 Alternative Store Contract。

这些内容会扩张 Kernel Identity，却不增强 Atomic State-Machine Contract。

## 发布证据

发布前保留：

- Maven 与 PostgreSQL 版本；
- Migration Checksum 与 Schema Contract 结果；
- 继承的 Kernel Store Contract 结果；
- Race/Stress 时长与 Seed；
- PITR 演练结果；
- Claim 与 Operator Index 的 Query Plan；
- Stale Token 未修改任何 Row 的证明；
- ACK-loss 进程级证据：重放前后 Outbox event ID、type 与 logical payload 不变。

参见 [Durable 架构](../architecture/10-DURABLE_ARCHITECTURE.zh.md)。
