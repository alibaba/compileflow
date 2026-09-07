# Durable 存储测试

`compileflow-durable-testkit` 提供针对 `DurableStore` 原子协议的第一方可执行契约。它只依赖
`durable-spi`，保持 `runtime -> spi <- postgres|mysql` 的依赖方向。该协议不是通用的持久化抽象，第三方存储不会因此自动成为受支持实现。
运行时只依赖职责明确的存储接口，每个第一方实现负责完整的事务组合。
契约测试验证可观察的状态转换；各数据库实现分别约束自身的 V1 七表结构。

存储 SPI 与 Durable 内核构成一个强契约。权威的持久化状态转换可以要求 Store 提供新的抽象方法，不能通过在默认方法中抛出
`UnsupportedOperationException` 来隐藏缺失能力。每个存储实现都必须完整实现当前契约并通过测试工具包。构建或启动时直接失败优于运行中的部分支持。

## 契约覆盖

`DurableStoreContract` 当前证明：

- Process 注册不可变且幂等；
- Start 保留调用方提供的 Run ID，并拒绝复用；
- Wait 提交、基于令牌的完成、Resume、终态提交和过期 Run 租约拒绝；
- Pause、Resume 与 Cancel 彼此独立；
- Timer 触发和 Effect 完成会作为已提交的恢复事实；
- Outbox 事件的身份、类型和载荷保持稳定，正确记录重试次数，并通过隔离令牌拒绝过期操作；
- Run、Effect 和 Outbox 领取的有界批量续租、部分执行权丢失、整毫秒时长校验与崩溃恢复；
- 发生次数修订、终态保留，以及以 Run 为先的竞争与死锁安全。

运行时续租测试另行验证 Run、Effect 与 Outbox 通道相互隔离、有界分块可以继续执行、Store 出现未知故障后的重试、卡住调用检测，以及健康状态
降级与恢复；Spring 测试证明优雅停机先停止领取，再无中断地排空在途工作。

存储实现验证还必须覆盖数据库变更约束、查询计划与索引、高竞争并发、最小权限数据库角色、备份与恢复，
以及所有受支持的 PostgreSQL/MySQL 版本。

每个第一方存储实现的契约测试都会调用 `createEmptyStore()`，测试方法之间不共享状态。

## 第一方数据库测试

有 Docker 时，普通 Maven Suite 通过 Testcontainers 执行
`PostgresDurableStoreContractTest`：

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgresql \
  -am
```

没有 Docker 时，可以提供一个可随时清空的 PostgreSQL 数据库。测试会在每个用例前清理并重新初始化 `public` 数据库结构。
该通配模式还会执行进程级 ACK 丢失测试：子 JVM 先通过 `fsync` 持久化已接受的 `WAIT_COMMITTED` 事件，再在 Outbox 完成前被
强制终止；恢复后必须重发同一逻辑事件，随后才能提交 `DELIVERED`。

```bash
export COMPILEFLOW_DURABLE_POSTGRES_URL='jdbc:postgresql://127.0.0.1:5432/compileflow_test'
export COMPILEFLOW_DURABLE_POSTGRES_USER='postgres'
export COMPILEFLOW_DURABLE_POSTGRES_PASSWORD='postgres'

./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgresql \
  -am \
  -Dtest='LocalPostgresDurable*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

绝不能把该测试指向含有重要数据的数据库。

MySQL 8.4 契约由独立实现提供，并通过 Testcontainers 执行：

```bash
./mvnw test \
  -pl compileflow-durable/compileflow-durable-mysql \
  -am
```

测试已有的可丢弃 MySQL 数据库时，设置 `COMPILEFLOW_DURABLE_MYSQL_URL`、
`COMPILEFLOW_DURABLE_MYSQL_USER` 和 `COMPILEFLOW_DURABLE_MYSQL_PASSWORD`。
通过 `COMPILEFLOW_DURABLE_MYSQL_MIGRATION_USER` 和 `COMPILEFLOW_DURABLE_MYSQL_MIGRATION_PASSWORD`
提供独立的数据库结构变更身份。该测试在每个用例前清空数据库，并使用非 UTC 会话；绝不能指向含有重要数据的数据库。
数据库结构变更身份负责 DDL 和触发器创建，运行身份不需要 `SUPER` 权限，也不需要修改全局信任设置。

```bash
./mvnw test \
  -pl compileflow-durable/compileflow-durable-mysql \
  -am \
  -Dtest=LocalMySqlDurableStoreContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## 必需的并发测试

受支持的存储实现至少需要通过以下并发测试：

1. 两个工作节点同时领取同一 Run、Effect 或 Outbox 记录；
2. 租约过期后由新工作节点成功领取，旧令牌随后尝试完成；
3. Wait 完成与 Cancel 竞争；
4. Pause 与执行中的 Segment 或 Effect 竞争；
5. Timer 触发与 Cancel 竞争；
6. Effect 完成与运维裁决竞争；
7. Outbox 完成与重试或放弃竞争；
8. 并发 Start 产生彼此不同且合法的 Run ID；
9. 外部系统确认持久化后，在 Outbox 完成前对进程发送 `SIGKILL`，恢复后重放完全相同的事件。

不变量始终是只能有一个提交成功的操作；工作节点的执行时序不能决定最终结果。

## 非目标

Store Contract 不承诺：

- Application Build 或 Runtime/Provider Version 兼容；
- Alias Resolve 或 Rollout；
- Generated Program/Bytecode 持久化；
- Custom Persisted Codec；
- Exactly-Once Remote Effect；
- Generic External Outbox 副作用 Exactly-Once。

这些能力会扩大内核身份边界，但不会加强原子状态机契约。

## 验证结果

共享的[生产演练证据 Schema](../specs/compileflow-durable-production-drill-evidence-v1.schema.json)仅认证 PostgreSQL
演练，必须提供 PostgreSQL System 与 Timeline Identity。MySQL 契约测试结果不能替代该 Provider 的故障切换、恢复及生产容量证据。

验证记录包括：

- Maven 与精确数据库 Provider/版本；
- Migration Checksum 与 Schema Contract 结果；
- 继承的 Kernel Store Contract 结果；
- Race/Stress 时长与 Seed；
- PITR 演练结果；
- Claim 与 Operator Index 的 Query Plan；
- Stale Token 未修改任何 Row 的证明；
- ACK-loss 进程级证据：重放前后 Outbox event ID、type 与 logical payload 不变。

参见 [Durable 架构](architecture/durable-architecture.md)。
