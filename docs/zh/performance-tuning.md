# 性能与可扩展性指南

以下建议适用于运行时（`execute`/`trigger`）的高并发热路径；日常运维与组件接入分别见运维手册和集成指南。

## 目标

- 降低每次请求的热路径开销
- 避免冷启动尖刺与仓库/编译雪崩
- 在高负载下保持可观测与可预测

## 热路径基本原则

- 稳态生产热路径使用已发布的 `ProcessRef.Version` 或 `ProcessRef.Alias`。所有 `ProcessDefinition` 变体都会在精确缓存匹配前
  重新解析源码，适合开发、工具或较低频的显式执行。
- 若调用方已知版本（回放/任务等），显式设置 `version` 以跳过选择器。
- Targeting policy 必须无远程 I/O、少分配，热路径日志仅在 DEBUG。

## Alias target 选择

- 只有 serving route 命名 targeting policy 时，运行时才构造 `ProcessAliasTargetingContext`。不要把 routing key 或 attributes
  放入流程变量 Map。
- `ProcessAliasTargetingPolicy` 只做确定且有界的本地决策；外部策略状态应在热路径之外异步刷新。
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

- 所有 targeting policy 保持纯净快速，只读取不可变 context。
- alias 通过 `ProcessRef.Alias` 传递，routing identity 与自定义策略输入通过不可变
  `ProcessExecutionOptions` 传递；Policy 不会收到无关业务变量。
- 在 Alias rollout 中显式绑定 Policy 名称。已注册但未被引用的 Policy 不生效；Alias 指定的 Policy 不可用时不能进入 local-ready。
- 标准金丝雀发布使用协议固定的确定性百分比分桶；Targeting 只能覆盖或放行，不能替换百分比选择。

## 冷路径削峰

- 引擎会合并相同的进行中 runtime load。使用 `compileflow.engine.executor.runtime-load.max-concurrency` 调节活动加载数，
  使用 `compileflow.engine.executor.runtime-load.max-pending` 调节等待槽位的额外加载数；需要立即拒绝过载时将 pending 设为 `0`。
- 只有性能测量表明仓库存在压力时，才为不可变的 `(namespace, code, version)` artifact 查询增加秒级热点
  缓存。路由选择应读取在热路径之外异步更新的本地内存快照。

## 运行时缓存容量

- 调大 `compileflow.engine.max-resident-runtimes`，减少驱逐与 ClassLoader 抖动。
- 避免在热路径手动清理缓存。

## Runtime-load/驱逐节流

- 避免同时触发大量冷 runtime load（分批预热/逐步引流）。
- 缓存替换或删除会释放运行时与生成 ClassLoader 的强引用；活动执行结束且无应用引用后由 JVM 回收。稳定的缓存容量仍可减少重编译和类加载抖动。

## 日志与 MDC

- 默认关闭 MDC 传播（性能更优）：
    - `compileflow.engine.observability.mdc-propagation-enabled=false`
- 热路径日志仅在 DEBUG；指标/事件采样上报，避免峰值放大开销。

## Repository 访问规则

- 请求热路径不查数据库路由；在热路径之外异步更新不可变的本地路由快照。
- 只有测量证明需要时才缓存不可变 artifact，并以 `(namespace, code, version)` 为 key，同时限制容量与过期时间。路由缓存必须携带权威
  revision。

## 启动预热

```java
import com.alibaba.compileflow.engine.ProcessRuntimeManager;
import com.alibaba.compileflow.engine.ProcessDefinition;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

class FlowWarmup {
    private final ProcessRuntimeManager admin;

    FlowWarmup(ProcessRuntimeManager admin) {
        this.admin = admin;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmProcessCache() {
        admin.warmUp(ProcessDefinition.classpath(
                "order.rule", "flows/order-rule.bpm"));
    }
}
```

## 快速核对清单

- [ ] 运行时仅传 `code`/`version`（不携带 content）
- [ ] 选择器纯内存，并且只读取本地快照
- [ ] 编译 worker、队列和 in-flight registry 已按负载设置有界容量
- [ ] 运行时缓存容量合理，驱逐较少
- [ ] 关闭 MDC；热路径日志 DEBUG；指标采样
- [ ] artifact 缓存经过测量、容量有界，并按不可变版本建立 key
- [ ] 启动时显式预热已知定义或路由版本
