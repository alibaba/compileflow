# 热部署集成指南

本实现指南定义发布平台接入 CompileFlow 版本化控制面、独立执行 worker 时必须满足的进程、存储、传输和恢复契约。应用负责人执行
常规发布与 rollout 时应阅读[热部署](hot-deploy.md)；其中的 `EMBEDDED` 示例适用于单进程 Server 部署。

## 进程契约

每个分布式进程都要显式声明职责，不能依赖“恰好缺少某个 bean”来推断角色。

| 进程     | `control-plane-enabled` | `runtime-worker-enabled` | 必需基础设施                                                                       |
|----------|-------------------------|--------------------------|------------------------------------------------------------------------------------|
| 控制面   | true                    | false                    | JDBC 仓储、`DeploymentSyncChannel`                                                 |
| 数据面   | false                   | true                     | `ProcessRuntimeManager`、`DeploymentSyncChannel`、subscriptions、artifact resolver |
| 组合进程 | true                    | true                     | 两者并集；只在有意设计时使用                                                       |

所有配置都要求 `compileflow.deploy.enabled=true` 和 `compileflow.deploy.topology=DISTRIBUTED`。

## 控制面

受支持的命令 API 是 `ProcessDeploymentService`：

- `publish(PublishProcessVersionCommand)`
- `createRollout(CreateRolloutCommand)`
- `updateCanaryWeight(UpdateCanaryWeightCommand)`
- `promoteRollout(PromoteRolloutCommand)`
- `abortRollout(AbortRolloutCommand)`
- `rollbackRollout(RollbackRolloutCommand)`

控制面把 version、alias route、rollout history 与 outbox 状态存入 JDBC 仓储。Route 状态和对应 outbox 事件在同一连接上提交；
`RoutingOutboxDispatcher` 只向通道发布已提交记录。交付失败会重试，并保持为可观测的 backlog 或 dead-letter 状态。

CompileFlow 的生产数据库只支持 PostgreSQL。在 `compileflow-workbench-server` 之外嵌入 deploy 模块的应用，需要通过 Flyway
引入打包位置 `classpath:db/compileflow-deploy/migration`。其中完整的 PostgreSQL V1 同时拥有 Deploy schema 与物理不可变约束，
是 deploy 表结构的唯一 DDL owner；Server 只组合该 location，不复制 SQL。H2 仅用于测试，不是受支持的部署数据库。

Deploy 控制面制品是可嵌入库，因此不会替宿主决定迁移策略，也不会自行启动 Flyway。产品宿主必须在注册命令入口或
启动后台投递之前，应用打包的原始迁移，或者校验其 checksum 并拒绝 pending migration。关闭应用自主 DDL 不等于可以关闭这项
fail-closed schema admission。Workbench Server 是官方组合实现；其他宿主必须提供等价的启动门禁。

```yaml
compileflow:
  deploy:
    enabled: true
    topology: DISTRIBUTED
    control-plane-enabled: true
    runtime-worker-enabled: false
    artifact:
      mode: DATABASE
```

平台必须声明一个经过审计的 `DeploymentSyncChannel` bean。CompileFlow 有意不内置远程实现，其余控制面和数据面保持与传输无关。

## 数据面

每个 worker 承载一个由 Spring 管理的 `DeployRuntime`。在组合进程中，Spring 会先启动通道订阅，再启动
控制面后台交付；停机时先停发布端，最后关闭数据面。应用代码不应自行构造或启动流水线。

```yaml
compileflow:
  deploy:
    enabled: true
    topology: DISTRIBUTED
    control-plane-enabled: false
    runtime-worker-enabled: true
    artifact:
      mode: DATABASE
    routing:
      namespaces: [default]
      codes: [order.rule]
      aliases: [production]
```

Routing subscriptions 是显式的容量与所有权声明。CompileFlow 根据规范 namespaces、codes 与 aliases 的笛卡尔积派生内部
transport key；wire-key 编码不属于公共配置。

## Artifact 传输

| 模式       | 发布与解析                                                                  | 必需信任边界                                                  |
|------------|-----------------------------------------------------------------------------|---------------------------------------------------------------|
| `DATABASE` | 控制面把 content 存入版本仓储；worker 通过 `ProcessArtifactSource` 解析。   | worker 需要版本内容的只读数据库权限。                         |
| `CHANNEL`  | 控制面发布不可变 artifact payload；worker 从 `DeploymentSyncChannel` 解析。 | 通道容量、保留期、访问控制和 payload 持久性必须满足制品要求。 |

两种模式都把必填 digest 作为 artifact 一等字段，并在 runtime 安装前校验。调用方可选的 digest assertion
与发布内容不一致时，会在任何路由变化前拒绝发布。使用 `CHANNEL` 时，UTF-8 definition、metadata 与 JSON envelope
必须满足所选后端公开的单项容量；adapter 必须在开始远程 I/O 前拒绝超限内容。

如果不可变数据库记录落库后 channel 投影失败，publish 会返回 typed failure，精确重试可安全续做投影。Create 与 rollback 也会在
route 事务之前重新确认持久化目标的投影，因此不会仅因数据库行存在就提交一个新引用但不可解析的版本。

## 路由协议

Routing state 表达较小的路由意图，不承载流程制品：

- `compileflow.deployment.alias.{identityDigest}`

`CHANNEL` artifact key：

- `compileflow.process.version.{identityDigest}`

`identityDigest` 是有序 identity 元组的 UTF-8 分段在加入长度前缀后的小写 SHA-256。每个 payload 都保留完整
identity，消费者会重新计算摘要再接收。每个 alias payload 还包含完整 stable/candidate 状态和唯一单调 `revision`
。控制面用精确内容的原子 CAS 推进每个 channel key，因此迟到的低 revision 不能覆盖当前投影。通知仍可能乱序，所以消费者还会拒绝重复和较低
revision。

自定义 `DeploymentSyncChannel` 必须提供服务端原子 CAS，包括原子 create-if-absent，不能用客户端 read 后 write 模拟。例如
Redis 可在一个 Lua script 内完成 compare、store 和 publish，etcd 可使用带 value/version compare 的 transaction。

## 启动与恢复

- 分布式控制面缺少 channel 时启动失败。
- 数据面缺少 subscriptions、channel 或所选 artifact source 时启动失败。
- runtime 安装失败时不会回退到无版本源码或进程本地状态。
- channel 恢复后重放 routing state。控制面 reconciliation 修复 repository-to-channel 路由漂移；在
  `CHANNEL` artifact 模式下，还会检测权威 Alias 当前 stable/candidate 的缺失 artifact 投影，并在
  `reconciliation.mode=REPAIR` 时重建。
- 路由修复会合并同一精确 outbox 交付；artifact 修复使用不可变 create-if-absent CAS。已存在但冲突或损坏的 artifact 会
  fail-closed 并报告，不会被覆盖。
- 运维需要分别监控 outbox pending、processing、过期 claim 与 dead-letter、runtime 安装失败和收敛时间。claim lease 必须长于交付
  transport 的最坏请求超时。

继续阅读[运维手册](operations-playbook.md)。
