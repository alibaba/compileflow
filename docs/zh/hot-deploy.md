# 热部署

CompileFlow 热部署用于发布不可变流程版本，并在不重启应用的情况下修改 Alias 路由。本指南面向应用负责人和运维人员，说明发布
与 rollout 操作；分布式平台嵌入、传输要求和恢复接线请阅读[集成指南](hot-deploy-integration.md)。版本发布、路由变更、rollout
历史、Outbox 投递和节点本地安装分别由独立组件负责。

## 本地定义预热

开发工具可以先把更新后的精确定义编译进当前 Engine 的有界 runtime cache，再执行同一份定义：

```java
ProcessDefinition updated =
        ProcessDefinition.inline("bpm.order.process", updatedXml);

engine.runtime().warmUp(updated);
engine.execute(updated, variables).orElseThrow();
```

`warmUp` 不会创建 code 或 version binding。后续通过显式 `ProcessDefinition` 调用时仍会解析配置的定义来源；
调用方必须执行同一份显式内容，或通过精确 `ProcessRef.Version` 加载该内容。预热只改变当前 Engine 的 cache，不是生产发布或多节点路由机制。

## 选择拓扑

| 使用场景                              | Topology      | Control plane | Runtime worker | 交付方式                                    |
|---------------------------------------|---------------|---------------|----------------|---------------------------------------------|
| CompileFlow Workbench Server 或单 JVM | `EMBEDDED`    | true          | false          | 提交后同步 local-ready；outbox 负责持久恢复 |
| 发布 API 进程                         | `DISTRIBUTED` | true          | false          | outbox 交付到 sync channel                  |
| 执行 worker                           | `DISTRIBUTED` | false         | true           | channel 订阅驱动 runtime 安装               |
| 有意组合的节点                        | `DISTRIBUTED` | true          | true           | 通过 channel 同时发布与订阅                 |

Starter 的 `compileflow.deploy.enabled` 默认为 `false`。启用 deploy 却没有任何进程职责属于非法配置。

### 嵌入式

```yaml
compileflow:
  deploy:
    enabled: true
    topology: EMBEDDED
    control-plane-enabled: true
    runtime-worker-enabled: false
    artifact:
      mode: DATABASE
```

数据库保存不可变版本、alias route、rollout 历史和 outbox 状态。Route 事务提交后，命令会读取最新权威 Alias，完成制品安装并发布
local-ready snapshot 后再返回；outbox 以同一 revision 提供持久恢复。内存只是缓存，重启或 eviction 后 JDBC 仍是权威。

### 分布式控制面

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

控制面要求 `DeploymentSyncChannel`，只分发已提交 outbox 记录，并将当前 route 权威对账到 channel。缺少必需
基础设施时启动失败；应用必须提供一个经过审计的 channel bean。

### 分布式数据面

```yaml
compileflow:
  deploy:
    enabled: true
    topology: DISTRIBUTED
    control-plane-enabled: false
    runtime-worker-enabled: true
    runtime:
      failure-backoff: 5m
      convergence-timeout: 30s
      concurrency: 1
      queue-capacity: 256
    artifact:
      mode: DATABASE
    routing:
      namespaces: [default]
      codes: [order.rule]
      aliases: [production]
      operation-timeout: 5s
```

必须至少配置一个显式 key 或派生 code 订阅。Database artifact 模式要求版本仓储可读；channel 模式从配置的 sync transport
解析不可变制品。

## 发布不可变版本

```java
PublishedProcessVersion published = deploymentService.publish(
        new PublishProcessVersionCommand(
                ProcessRef.version("default", "order.rule", "2026-07-15-001"),
                ProcessModelType.TBBPM,
                ProcessDefinition.inline("order.rule", flowXml),
                "alice",
                metadata));
```

发布会校验不可变 identity、大小、digest assertion 与 actor，再保存精确源码。它没有可变 readiness 状态，不执行 runtime
编译，也不改变 route。数据面安装与 local-ready 是后续独立的 fail-closed 步骤。

## 通过 Rollout 切流

使用刚从权威状态读到的 Alias revision 创建灰度：

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

立即发布使用 `RolloutStrategy.ALL_AT_ONCE` 且不提供灰度权重。进行中灰度通过
`AbortRolloutCommand` 中止并恢复创建时捕获的 baseline。回滚已完成发布时，创建目标为更早不可变版本的新 all-at-once
rollout；完成历史永不编辑。

每次创建 rollout 都需要一个在 namespace、process、Alias 和 operation kind 内稳定的 retry key，以及 expected Alias
revision。同一 scope 下用不同请求字段或 actor 重用 key 会冲突。灰度变更需要当前 rollout revision，并校验 Alias
前置条件。过期请求直接失败，不会覆盖并发变更。

## 交付契约

- Route、rollout、审计事件和 outbox row 原子提交。
- 提交后激活始终读取当前权威 Alias，不根据可能已被后续变更覆盖的 rollout 重建路由。
- `EMBEDDED` 拓扑中，rollout 命令成功返回表示该 Alias 已在当前进程 local-ready。若事务提交后安装失败，
  命令返回收敛失败；幂等重试会激活最新已提交 revision，outbox 仍保留恢复工作。
- `DISTRIBUTED` 拓扑中，命令成功只表示控制面事务已提交。路由激活会立即请求 outbox 调度，但各 runtime node 的 readiness
  仍是可观测的异步收敛。
- `RoutingOutboxDispatcher` 在两种拓扑中都是持久重放路径，也是控制面写入分布式 sync channel 的唯一入口。
- Outbox 通过数据库唯一键合并相同交付工作；rollout event 仍是 append-only 审计日志。
- Alias state payload 只使用一个正 `revision` 排序；重复和更旧消息会被忽略。
- 非法初始状态会使启动失败；非法实时更新保留最后一个有效的 local-ready route。
- Runtime 安装校验 artifact digest，所选版本不可用时 fail-closed。
- 必须停发时停止整个控制面角色，让命令入口与交付共享同一生命周期。

Alias route key：

```text
compileflow.deployment.alias.{identityDigest}
```

Channel artifact key：

```text
compileflow.process.version.{identityDigest}
```

`identityDigest` 是有序 identity 元组的 UTF-8 分段在加入长度前缀后的小写 SHA-256。Payload 保留完整 identity，消费者会根据
key 重新校验。`CHANNEL` 模式下，artifact payload 必须满足所选后端公开的单项容量。

继续阅读[配置指南](configuration.md)、[分布式集成](hot-deploy-integration.md)和
[运维手册](operations-playbook.md)。
