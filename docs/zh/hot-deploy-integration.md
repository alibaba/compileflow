# 热部署集成

本文面向需要嵌入 Deploy，并将控制面与执行进程分开部署的应用。发布、灰度、全量切换、中止和回滚操作见[热部署](hot-deploy.md)。

## 进程角色

每个分布式进程都必须明确声明角色。缺少必需组件时启动失败，不会自动切换到其他模式。

| 角色     | `control-plane-enabled` | `runtime-worker-enabled` | 必需基础设施                                  |
| -------- | ----------------------- | ------------------------ | --------------------------------------------- |
| 控制面   | `true`                  | `false`                  | `DeployStore`、`DeploymentProjectionStore`    |
| 执行节点 | `false`                 | `true`                   | `DeploymentProjectionStore`、订阅和制品解析器 |
| 组合节点 | `true`                  | `true`                   | 上述两组组件；仅在确有需要时配置              |

所有角色都要求 `compileflow.deploy.enabled=true` 和 `topology=DISTRIBUTED`。

## 控制面

使用 `ProcessDeploymentService` 执行发布和 Rollout 命令。控制面通过一个 `DeployStore` 保存不可变版本、Alias 路由、Rollout 记录和 Outbox 状态。路由变更及对应的 Outbox 记录在同一个存储事务中提交。

`RoutingOutboxDispatcher` 只向投影存储投递已经提交的状态。投递失败会重试，并通过 pending、processing、expired 或 dead-letter 状态进行观测。控制面必须配置完整的数据库存储实现和经过审计的 `DeploymentProjectionStore`；CompileFlow 不内置远程投影存储实现。

Deploy 支持 PostgreSQL 和 MySQL 8.4。应用应引入一个匹配的存储 Starter。H2 仅用于测试。通用 Starter 供自行实现完整 `DeployStore` 的应用使用，不包含数据库驱动、存储实现或迁移脚本。

启用命令入口或后台投递前，必须执行随模块提供的数据库迁移。存储 Starter 默认设置 `compileflow.deploy.database.migrate=false`，启动时校验外部迁移结果；存在待执行迁移或数据库结构不一致时会拒绝启动。显式设为 `true` 后，应用才会使用所配置的 DataSource 账号执行迁移。

MySQL 开启 binlog 后，创建 V1 Trigger 可能需要数据库服务器要求的额外 DDL 权限。应使用独立的迁移账号执行，再以仅具备 DML 权限的应用账号运行服务；不要通过全局启用 `log_bin_trust_function_creators` 规避权限边界。

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: true
        runtime-worker-enabled: false
```

## 执行节点

每个执行节点由 Spring 管理一个 `DeploymentRuntime`。订阅、对账、安装和关闭均由 Spring 负责，应用代码不应自行创建或启动这套处理流程。

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: false
        runtime-worker-enabled: true
        artifact:
            mode: SOURCE
        routing:
            namespaces: [default]
            codes: [order.rule]
            aliases: [production]
```

订阅明确限定节点负责的路由范围和容量。执行节点只安装其订阅的 Alias 路由所需版本，并在校验摘要后发布本地就绪状态。路由收敛期间继续使用最后一个有效版本；没有有效版本时拒绝执行。

## 制品模式

| 模式               | 解析方式                                                            | 所需访问                                                  |
| ------------------ | ------------------------------------------------------------------- | --------------------------------------------------------- |
| `SOURCE`           | 执行节点通过 `ProcessArtifactSource` 从版本仓库读取不可变内容。     | 版本存储的读取权限。                                      |
| `PROJECTION_STORE` | 控制面投影不可变制品，执行节点从 `DeploymentProjectionStore` 读取。 | 投影存储的持久性、容量、访问控制和原子 create-if-absent。 |

两种模式都会携带并校验内容摘要。投影模式必须在远程 I/O 前拒绝超过容量限制的制品。发现已有制品冲突或损坏时应停止处理，不能覆盖。

## 投影存储契约

控制面只发布已经提交的状态。投影存储必须提供服务端原子 compare-and-set，包括 create-if-absent；客户端先读后写不能满足并发要求。

路由键由完整身份元组生成：

```text
compileflow.deployment.alias.{identityDigest}
compileflow.process.version.{identityDigest}
```

`identityDigest` 是对按顺序编码、带长度前缀的 UTF-8 身份元组计算得到的小写 SHA-256。载荷包含完整身份，消费方会根据键进行校验。Alias 载荷包含单调递增的 revision；重复或更旧的 revision 会被忽略。

## 启动与恢复

- 控制面未配置投影存储时启动失败；
- 执行节点缺少订阅、投影存储或所选制品来源时启动失败；
- 安装失败时不会回退到无版本定义或进程内临时状态；
- 对账任务使用同一套幂等投递契约修复版本仓库与投影存储之间的偏差；
- 运维应监控 Outbox 积压、运行时安装失败和路由收敛时间。

运维响应请看[运维手册](operations-playbook.md)。
