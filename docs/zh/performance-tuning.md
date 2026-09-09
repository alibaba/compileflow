# 性能与可扩展性指南

以下建议适用于 `execute` 和 `trigger` 的高并发调用。日常运维与组件接入分别见[运维手册](operations-playbook.md)和[热部署集成](hot-deploy-integration.md)。

## 目标

- 降低单次请求的执行开销。
- 避免冷启动带来的延迟尖峰，以及集中读取和编译造成的资源争用。
- 在高负载下保持可观测、可预测的运行状态。

## 热路径基本原则

- 由 CompileFlow Deploy 管理的高并发调用应使用已发布的 `ProcessRef.Version` 或 `ProcessRef.Alias`。
  生产环境也可以直接执行 `ProcessDefinition`，但在匹配精确缓存前需要读取流程内容。流程内容应保持不变，并在预期请求量下验证这部分开销。
- 若调用方已知版本（回放/任务等），显式设置 `version` 以跳过选择器。
- 路由策略不能执行远程 I/O，并应尽量减少对象分配；高频路径的详细日志只在 DEBUG 级别记录。

## 交易场景

多个请求线程应共享一个长生命周期且已预热的引擎。在两种运行模式下，没有超时设置的串行动作都在调用线程执行；网关只选中一个分支时也直接执行。设置动作超时后使用专用执行器，选中多个并行分支时使用并行执行器。这些辅助执行器不会限制无超时串行流程的总调用并发数。存在阻塞操作的流程必须由允许阻塞的线程调用，不能阻塞网络事件循环。

| 配置           | 默认值与调优建议                                                                                                       |
| -------------- | ---------------------------------------------------------------------------------------------------------------------- |
| 运行模式       | 交易热路径使用 `COMPILED`，同时支持 `INTERPRETED`。                                                                    |
| 动作超时与重试 | 默认不设置引擎超时，只尝试一次。在 HTTP、RPC 或 JDBC 边界分别配置连接获取和实际操作超时。                              |
| 超时动作并发   | `max(4, CPUs)` 是保守的辅助容量。大量使用带超时动作时，应根据请求到达率、工作线程占用时长和下游容量明确设置。          |
| 超时动作排队   | `0`，没有可用工作线程时立即拒绝。只有压测证明需要吸收短时突发，且等待时间仍在请求预算内时，才配置较小的队列。          |
| 运行时加载     | 根据 CPU 计算 1..2 个并发加载、4 个等待任务，调用方最多等待 10 秒。应在接入流量前加载热点版本；10 秒不是业务请求超时。 |
| 事件           | 异步投递，默认 2 个并发任务和 16 个等待任务。适合耗时与资源消耗有界的观测监听器，并应监控事件丢弃数和处理时间。        |
| 取消           | 超时动作和失败的并行分支默认最多协作排空 2 秒，实际返回时间可能超过动作超时。                                          |
| 关闭           | 引擎总预算为 15 秒。宿主先停止接收请求，容器预算还需覆盖宿主排空和资源关闭。                                           |

带超时动作的平均工作线程占用数可按 `每秒尝试数 × 平均占用秒数` 估算，其中包括重试以及取消后仍未停止的任务。
例如每秒 1,000 次尝试、每次占用 20ms，平均需要 20 个工作线程。将并发上限设为 32 时，仍需满足下游分配的并发与 CPU
预算；20 或 32 都不能保证尾延迟。如果下游预算小于所需占用，应先减少接纳量、降低耗时或扩充下游容量。
多副本及同一进程内其他连接池共同消耗依赖的总容量。

队列只能吸收短时突发，不能提高持续吞吐。例如系统饱和时有 4 个工作线程、每个任务耗时 50ms、队列中有 32 个等待任务，队尾可能增加约
400ms 等待。实际尾延迟还取决于到达和耗时分布。零排队也可能在平均容量尚有余量时拒绝短暂调度突发，因此应比较稳态、
突发和持续过载下的有效完成交易数、拒绝率与 p99。压测到达计划不能随服务变慢而自动降速；仅测闭环吞吐会掩盖排队增长。

Spring 命令式事务绑定当前线程。带超时 Action 与并行分支需要明确设计事务边界；MDC 传播不会传播事务，也不会使共享 JDBC
连接变得安全。Engine 执行失败可能返回失败的 `ProcessResult`；应在事务方法内部按应用回滚规则处理，例如调用
`orElseThrow()`。超时或中断不会撤销外部副作用。重试需要幂等、明确的可重试条件及总预算；只开启重试会继承 Policy 默认值，
并不代表事务可以安全重放。

上面的数字只用于说明容量计算方法，不是 CompileFlow 的性能测试结果。

## 别名目标选择

- 只有当前路由指定了目标选择策略，运行时才会创建 `ProcessAliasTargetingContext`。不要将路由键或属性放入流程变量 Map。
- `ProcessAliasTargetingPolicy` 只应执行确定、耗时有界的本地决策；外部策略状态应在高频调用路径之外异步刷新。
- Spring Boot 应用可以暴露多个具名 `ProcessAliasTargetingPolicy` bean。只有应用拥有不同 serving-route 权威时才配置自定义
  `ProcessAliasRouteSource`。

### 示例：应用侧 Targeting Policy Bean

```java
import com.alibaba.compileflow.engine.ProcessAliasTarget;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingContext;
import com.alibaba.compileflow.engine.spi.routing.ProcessAliasTargetingPolicy;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RoutingConfiguration {

    @Bean
    ProcessAliasTargetingPolicy tierTargetingPolicy() {
        return new ProcessAliasTargetingPolicy() {
            @Override
            public String name() {
                return "tier-targeting";
            }

            @Override
            public Optional<ProcessAliasTarget> target(ProcessAliasTargetingContext context) {
                return "vip".equals(context.getAttributes().get("tier"))
                        ? Optional.of(ProcessAliasTarget.CANDIDATE)
                        : Optional.empty();
            }
        };
    }
}
```

注意：

- 所有目标选择策略都应保持无副作用且执行迅速，只读取不可变上下文。
- Alias 通过 `ProcessRef.Alias` 传递，路由身份和自定义策略输入通过不可变的 `ProcessExecutionOptions` 传递；策略不会收到无关业务变量。
- 在别名发布中显式绑定策略名称。已注册但未被引用的策略不会生效；别名指定的策略不可用时，路由不能进入本地就绪状态。
- 标准灰度发布使用协议固定的确定性百分比分桶；自定义策略只能选择目标或放行，不能替代百分比选择。

## 冷路径削峰

- 引擎会合并内容相同且正在进行的运行时加载。使用 `compileflow.engine.executor.runtime-load.max-concurrency` 调节活动加载数，
  使用 `compileflow.engine.executor.runtime-load.max-pending` 调节等待槽位的额外加载数；需要立即拒绝过载时将 pending 设为 `0`。
- 只有性能测量表明版本仓库存在压力时，才为不可变的 `(namespace, code, version)` 制品查询增加秒级热点
  缓存。路由选择应读取在热路径之外异步更新的本地内存快照。

## 运行时缓存容量

- 根据热点精确版本、ProcessCall 依赖以及发布或回滚期间并存的版本数量设置 `compileflow.engine.max-resident-runtimes`。
  默认 2048 是数量上限，不是预分配或字节预算。调整前同时测量 heap、metaspace、驱逐和重加载。
  被引用的运行时也占用容量；全部容量都被占用时，新加载会失败，不会驱逐仍在使用的运行时。
- 避免在热路径手动清理缓存。

## 运行时加载与驱逐

- 避免同时触发大量冷运行时加载，应分批预热并逐步引入流量。
- 缓存替换或删除会释放对运行时和生成类加载器的强引用；活动执行结束且应用不再引用后，由 JVM 回收。稳定的缓存容量可以减少重复编译和类加载抖动。

## 日志与 MDC

- 默认关闭 MDC 传播（性能更优）：
    - `compileflow.engine.observability.mdc-propagation-enabled=false`
- 逐请求过载日志使用 DEBUG，通过 executor 拒绝与事件丢弃计数告警。在应用边界记录请求结果与延迟；
  尽力投递的异步事件不能提供精确交易计数。

## 版本仓库访问

- 高频请求不直接查询数据库路由；应在请求路径之外异步更新不可变的本地路由快照。
- 只有测量证明有必要时才缓存不可变制品，并以 `(namespace, code, version)` 为键，同时限制容量和过期时间。路由缓存必须携带权威
  revision。

## 启动预热

```java
import com.alibaba.compileflow.engine.ProcessEngine;
import com.alibaba.compileflow.engine.ProcessDefinition;
import com.alibaba.compileflow.engine.ProcessModelType;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class FlowWarmup {
    private final ProcessEngine engine;

    FlowWarmup(ProcessEngine engine) {
        this.engine = engine;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmProcessCache() {
        engine.runtime().warmUp(ProcessDefinition.classpath(
                ProcessModelType.TBBPM, "order.rule", "flows/order-rule.bpm"));
    }
}
```

## 快速核对清单

- [ ] Deploy 管理的高吞吐链路使用版本或别名引用，不在请求中携带流程内容
- [ ] 选择器纯内存，并且只读取本地快照
- [ ] 编译线程、队列和在途任务注册表已按负载设置有界容量
- [ ] 运行时缓存容量合理，驱逐较少
- [ ] 关闭 MDC；热路径日志 DEBUG；指标采样
- [ ] 制品缓存有测量依据、容量有界，并按不可变版本建立键
- [ ] 启动时显式预热已知定义或路由版本
