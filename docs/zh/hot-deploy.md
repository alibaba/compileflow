# 热部署

CompileFlow 热部署用于发布不可变流程版本，并在不重启应用的情况下调整别名路由。本文介绍版本发布、灰度和回滚操作；分布式部署、传输要求和恢复配置见[集成指南](hot-deploy-integration.md)。版本、路由、发布记录、Outbox 投递和节点本地安装由不同组件分别管理。

## 本地定义预热

开发工具可以先将更新后的流程定义加载到当前引擎的有界运行时缓存，再执行同一份定义：

```java
ProcessDefinition updated =
        ProcessDefinition.inline(ProcessModelType.TBBPM, "bpm.order.process", updatedXml);

engine.runtime().warmUp(updated);
engine.execute(updated, variables).orElseThrow();
```

`warmUp` 不会创建流程编码或版本绑定。后续执行必须使用同一份显式定义，或通过精确的 `ProcessRef.Version` 加载对应内容。预热只影响当前引擎的本地缓存，不等同于生产发布，也不会改变多节点路由。

## 选择拓扑

| 使用场景                              | 拓扑          | 控制面 | 执行节点 | 交付方式                                    |
| ------------------------------------- | ------------- | ------ | -------- | ------------------------------------------- |
| CompileFlow Workbench Server 或单 JVM | `EMBEDDED`    | true   | false    | 提交后同步达到本地就绪；Outbox 负责持久恢复 |
| 发布 API 进程                         | `DISTRIBUTED` | true   | false    | Outbox 投递到投影存储                       |
| 执行节点                              | `DISTRIBUTED` | false  | true     | 由投影存储订阅驱动运行时安装                |
| 组合节点                              | `DISTRIBUTED` | true   | true     | 通过投影存储同时发布和订阅                  |

Starter 的 `compileflow.deploy.enabled` 默认为 `false`。启用 Deploy 时必须至少声明一个进程职责。

### 嵌入式

```yaml
compileflow:
    deploy:
        enabled: true
        topology: EMBEDDED
        control-plane-enabled: true
        runtime-worker-enabled: false
        artifact:
            mode: SOURCE
```

数据库保存不可变版本、别名路由、发布记录和 Outbox 状态。路由事务提交后，命令重新读取最新别名，安装制品并发布本地就绪状态，然后返回；Outbox 使用同一修订号提供持久恢复。内存只用于缓存，重启或缓存驱逐后仍以数据库为准。

### 分布式控制面

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: true
        runtime-worker-enabled: false
        artifact:
            mode: SOURCE
```

控制面通过 `compileflow-deploy-spi` 中的 `DeploymentProjectionStore` 分发已经提交的 Outbox 记录，并将当前路由状态同步到投影存储。
缺少必需组件时启动失败。该 SPI 属于 Provider Preview；自定义实现需要自行验证原子更新、订阅收敛、故障恢复与容量边界。

### 分布式运行时

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: false
        runtime-worker-enabled: true
        runtime:
            failure-backoff: 5m
            installation-concurrency: 1
        artifact:
            mode: SOURCE
        routing:
            namespaces: [default]
            codes: [order.rule]
            aliases: [production]
            operation-timeout: 5s
```

必须配置非空的 `routing.namespaces × routing.codes × routing.aliases` 订阅集合，最多包含 10,000 个键。数据库制品模式要求版本仓库可读；投影存储模式通过配置的同步传输读取不可变制品。

## 发布不可变版本

```java
PublishedProcessVersion published = deploymentService.publish(
        new PublishProcessVersionCommand(
                ProcessRef.version("default", "order.rule", "2026-07-15-001"),
                ProcessDefinition.inline(ProcessModelType.TBBPM, "order.rule", flowXml),
                "alice",
                metadata));
```

发布操作会校验版本身份、内容大小、摘要断言和操作者，再保存原始流程内容。发布不执行运行时编译，也不改变路由。运行时安装和本地就绪检查由后续步骤独立完成，失败时拒绝执行。

## 调整发布流量

使用刚从数据库读取的别名修订号创建灰度发布：

```java
ProcessRollout rollout = deploymentService.createRollout(CreateRolloutCommand.canary(
        "order-v2-release",
        ProcessRef.alias("default", "order.rule", "production"),
        ProcessRef.version("default", "order.rule", "2026-07-15-001"),
        currentAliasRevision,
        1_000,
        "alice",
        "ticket-4821"));

rollout = deploymentService.updateCanaryWeight(new UpdateCanaryWeightCommand(
        rollout.getId(), 5_000, rollout.getRevision(), "alice"));

rollout = deploymentService.promoteRollout(new PromoteRolloutCommand(
        rollout.getId(), rollout.getRevision(), "alice"));
```

全量发布使用 `RolloutStrategy.ALL_AT_ONCE`，不设置灰度权重。进行中的灰度可通过 `AbortRolloutCommand` 中止，并恢复创建时记录的基线。回滚已经完成的发布时，应创建一个指向较早不可变版本的新全量发布；已有记录保持不变。

每次创建发布都需要一个在命名空间、流程、别名和操作类型范围内稳定的幂等键，以及预期的别名修订号。在同一范围内使用不同请求参数或操作者复用幂等键会产生冲突。调整灰度时必须提供当前发布修订号，并校验别名前置条件；过期请求直接失败，不会覆盖并发变更。

## 交付契约

- 路由、发布、审计事件和 Outbox 记录在同一个事务中提交。
- 提交后始终根据当前别名激活路由，不会使用可能已经过期的发布状态重建路由。
- 在 `EMBEDDED` 拓扑中，发布命令成功返回表示该别名已在当前进程就绪。若事务提交后安装失败，命令返回收敛失败；幂等重试会激活最新提交的修订号，Outbox 继续保留恢复任务。
- 在 `DISTRIBUTED` 拓扑中，命令成功只表示控制面事务已经提交。路由激活会立即触发 Outbox 调度，各执行节点随后异步收敛，并可观测其就绪状态。
- `RoutingOutboxDispatcher` 是两种拓扑的持久重放路径，也是控制面写入分布式投影存储的唯一入口。
- Outbox 通过数据库唯一键合并相同投递任务；发布事件作为只追加的审计记录保留。
- 别名状态载荷只使用一个正数 `revision` 排序；重复或更旧的消息会被忽略。
- 非法初始状态会阻止启动；运行中收到非法更新时，继续保留最后一个有效路由。
- 安装运行时会校验制品摘要，所选版本不可用时拒绝执行。
- 必须停发时停止整个控制面角色，让命令入口与交付共享同一生命周期。

别名路由键：

```text
compileflow.deployment.alias.{identityDigest}
```

投影存储制品键：

```text
compileflow.process.version.{identityDigest}
```

键的编码和校验规则见[分布式集成](hot-deploy-integration.md)。在 `PROJECTION_STORE` 模式下，单个制品载荷不能超过所选投影存储的容量限制。

继续阅读[配置指南](configuration.md)、[分布式集成](hot-deploy-integration.md)和
[运维手册](operations-playbook.md)。
