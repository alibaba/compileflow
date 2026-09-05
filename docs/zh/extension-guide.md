# CompileFlow 扩展指南

CompileFlow 的扩展契约位于 `com.alibaba.compileflow.engine.spi`。只有一个 Engine 需要某项能力时，直接注册对应接口；需要打包一组相关能力时，使用
`ProcessEnginePlugin`。

扩展可以来自 classpath plugin、直接 builder 调用或 Spring bean。
`ProcessEngineConfig.Builder.build()` 负责校验并冻结结果。系统没有全局可变 registry，引擎启动后也不能修改扩展集合。

外部传入的扩展实例由应用或依赖注入容器管理。

## 包结构

| 包                         | 内容                                                                                                     |
|----------------------------|----------------------------------------------------------------------------------------------------------|
| `engine`                   | Engine 入口和公共值类型                                                                                  |
| `engine.config`            | 不可变配置与扩展注册                                                                                     |
| `engine.spi`               | `ProcessEngineProvider`、`ProcessEnginePlugin`、`ProcessEnginePluginContext`、`ProcessComponentResolver` |
| `engine.spi.event`         | `ProcessEvent`、`ProcessEventListener`                                                                   |
| `engine.spi.execution`     | `RetryPolicy`、`FailureHandler`、`ProcessContextPropagator` 与执行上下文值类型                           |
| `engine.spi.script`        | `ScriptExecutor`                                                                                         |
| `engine.spi.observability` | `TraceIdProvider`                                                                                        |
| `engine.spi.routing`       | Alias route 权威、route-bound targeting 配置与具名 targeting policy                                            |

## 扩展点一览

| 能力                       | 配置入口                               | 语义                                                                        |
|----------------------------|----------------------------------------|-----------------------------------------------------------------------------|
| `ProcessEventListener`     | `builder.eventListener(...)`           | 生命周期事件按 predicate 过滤后有序广播；单个 listener 失败被隔离并记录日志 |
| `TraceIdProvider`          | `builder.traceIdProvider(...)`         | 提供执行 traceId；回退顺序为 MDC `traceId`、随机 ID                         |
| `ProcessComponentResolver` | `builder.componentResolver(...)`       | 解析生成代码引用的应用组件                                                  |
| `ProcessContextPropagator` | `builder.contextPropagator(...)`       | 在 Engine 自有线程边界传播应用环境上下文                                    |
| `ProcessAliasRouteSource`  | `builder.aliasRouteSource(...)`        | 提供 Engine 唯一的 serving-ready Alias 权威；不由 plugin 贡献               |
| `ProcessAliasTargetingPolicy` | `builder.aliasTargetingPolicy(...)` | 具名 route-bound override；返回 empty 时进入固定百分比分桶                   |
| `ScriptExecutor`           | `builder.scriptExecutor(...)`          | 供显式 script action 按语言名选择；同一名称必须唯一                         |
| `RetryPolicy`              | `builder.retryPolicy(name, ...)`       | 按名称索引的异常 predicate，由 invocation policy 的 `retryOn` 引用          |
| `FailureHandler`           | `builder.failureHandler(name, ...)`    | 按名称索引的终态决策，由 invocation policy 的 `onFailure` 引用              |
| `ProcessEnginePlugin`      | `builder.plugin(...)` 或 ServiceLoader | 组合 listener 及具名 script、targeting、retry、failure 能力                 |

Retry policy 与 failure handler 是受信任的同步执行协作者，必须线程安全、确定、非阻塞并返回合法结果。CompileFlow 会在调用
action 前解析当前 invocation policy 可能使用的全部协作者，因此未知 ID 会在应用副作用产生前失败。协作者抛出异常，或 failure
handler 返回 `null`，统一报告为 `CF_CONFIG_003`。FailureHandler 不能承担可靠外部投递；best-effort 遥测使用
`ProcessEventListener`，可靠集成使用 Durable Outbox。

QLExpress 4 与可信 Java Code 是内建脚本语言。QL 默认不能调用宿主对象方法；Java Code 使用宿主 JVM
权限执行，不是安全沙箱。其他脚本引擎需要显式注册为 `ScriptExecutor`。有状态执行器需要使用有界缓存和目标
ClassLoader，并由应用或容器释放资源。

`ScriptExecutor.name()` 必须本身就是 1-256 字符的小写 kebab-case key，Core 不会对它做任何重写。只有
Script Task 会引用该 key。TBBPM `scriptTask` 使用
`<action type="script" language="language-name">`；BPMN 使用
`<scriptTask scriptFormat="language-name">`。未知语言会在代码生成或 preflight 阶段失败，不会拖到执行阶段。
TBBPM 在嵌套 Action 上声明 Durable 执行语义；BPMN 直接在 `scriptTask` 上声明
`cf:execution="replayable|effect"`。Kernel 不拥有 Provider 的历史兼容性。排他网关、While、Timer
与 transition 表达式仍拼接为生成 Java 源码，不经过 `ScriptExecutor`。生成 Runtime
代码时，script Action 文本与变量名都会按 Java 字面量转义。

编译器、parser、图分析和格式分派类型都属于内部实现。公共契约以
[支持面清单](../architecture/06-SUPPORTED_SURFACES.zh.md)为准。

`ProcessEngineProvider` 是 `ProcessEngineFactory` 使用的引导 SPI。provider 缺失或存在歧义时，引擎创建失败。由于
`ProcessModelType` 是封闭集合，provider 可以替换已知格式的实现，但不能增加格式。Spring 应用若要替换完整引擎，应声明
`ProcessEngine` bean，由 auto-configuration 退让。

每个 Engine 只允许一个显式配置的 `ProcessAliasRouteSource`。它必须返回完整、serving-ready 的不可变 route，且不能 fallback
到另一权威。Source 不参与 plugin 聚合，避免构造期间悄然在多个 route authority 之间选择。

`ProcessAliasTargetingPolicy` 以稳定的小写 kebab-case 名称注册；只有权威 route 通过 `AliasTargeting` 显式引用该名称时才生效。
它接收获授权的 stable/candidate version、不可变 route 参数，以及调用方通过 `ProcessExecutionOptions` 显式提供的 routing key
和不可变字符串属性；Context 刻意不暴露 candidate weight。Policy 可以强制 `STABLE`、`CANDIDATE`，或返回 empty 进入
CompileFlow 固定的确定性百分比分桶；不存在公开的 percentage-selection SPI。

Routing input 不会进入流程变量，Policy 也不能读取无关业务输入。正常路径只读取 Source 一次；只有精确 runtime handoff 失败时
才允许重读并完整 admission 一次，接管成功后本次调用固定到已选版本。具名 Policy 缺失会阻止 route 进入 local-ready；已注册
Policy 返回 null `Optional` 或在运行时抛出异常时，选择 stable 并记录 `TARGETING_ERROR`，不会进入百分比分桶。Policy 必须
确定、线程安全、有界且无阻塞远程 I/O。Routing key 与 attributes 都是可能敏感的 admission-only 输入，禁止记录或持久化。

## 显式配置

```java
ProcessComponentResolver components = new ProcessComponentResolver() {
    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        return serviceLocator.lookup(name, requiredType);
    }
};

ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
        .eventListener(new MetricsListener(meterRegistry))
        .traceIdProvider(TraceIdProvider.random())
        .componentResolver(components)
        .scriptExecutor(new AviatorScriptExecutor())
        .retryPolicy("optimisticConflict", error -> error instanceof OptimisticLockException)
        .failureHandler("continueOptional", context -> FailureResolution.CONTINUE_PROCESS)
        .build();

ProcessEngine engine = ProcessEngineFactory.create(config);
```

扩展实现必须线程安全：同一份不可变配置可以创建多个 Engine，单个 Engine 也可能并发调用扩展。
`engine.close()` 不关闭外部传入的扩展实例。

自定义重试策略和失败处理器名称会被 trim，并按大小写精确匹配；长度必须为 1 到 256 个字符，且不能包含 ISO 控制字符。内建名称
`never`、`transient`、`always`、`propagate`、`continue` 按大小写不敏感保留。自定义名称不存在、策略或处理器抛出异常、失败处理器返回
`null` 都会使执行失败，不会静默回退到内建行为。

### 应用组件与 action

`ProcessComponentResolver` 是名称与类型的最终解析权威。只解析显式暴露的名称；组件缺失或类型不兼容会使解析失败，引擎不会实例化
流程声明的 class 作为回退：

```java
OrderFlowActions actions = new OrderFlowActions(orderService);
ProcessComponentResolver resolver = new ProcessComponentResolver() {
    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        if (!"orderFlowActions".equals(name)) {
            throw new IllegalArgumentException("Unknown process component: " + name);
        }
        return requiredType.cast(actions);
    }
};
```

Java action 生成直接的 `new Type().method(...)` 源码，因此目标必须是可访问 class，并提供 public 无参构造器；它不使用依赖注入或
private 反射。需要注入的组件应使用 Spring Bean action，通过
`compileflow.engine.components.allowed-beans` 暴露精确名称，并保持 bean 接口足够窄，因为 allowlist 控制的是 bean
可达性，不是方法级授权。

`ProcessContextPropagator` 在提交线程捕获不透明的应用上下文，在 Engine 自有的 action-timeout、parallel 与 event-delivery
线程中打开，并在结束时恢复 worker 原上下文。action/parallel 边界的 capture/open 失败会使本次 invocation 失败；best-effort
event delivery 则丢弃该事件。Durable 不会持久化或恢复该上下文。
classpath 存在 Micrometer Context Propagation 时，Spring Boot 自动提供适配器，实际传播内容由已注册的 Micrometer
`ThreadLocalAccessor` 决定；应用声明一个自定义 `ProcessContextPropagator` bean 即可替换默认适配器。

## Classpath 插件

实现 `ProcessEnginePlugin`，注册任意能力组合：

```java
public final class AviatorPlugin implements ProcessEnginePlugin {
    @Override
    public void apply(ProcessEnginePluginContext context) {
        if (context.getModelType() == ProcessModelType.TBBPM) {
            context.scriptExecutor(new AviatorScriptExecutor());
            context.eventListener(new AviatorCompilationListener());
            context.retryPolicy("aviatorTransient", new AviatorRetryPolicy());
        }
    }

    @Override
    public String id() {
        return "com.example.aviator";
    }

    @Override
    public int priority() {
        return 100; // 数值小先应用；同一脚本语言的重复贡献始终失败
    }
}
```

在标准服务文件 `META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEnginePlugin` 中声明：

```text
com.example.AviatorPlugin
```

Classpath 就是配置：只要新增依赖中包含 ServiceLoader `ProcessEnginePlugin`，即使应用源码未变，也可能改变构建出的
Engine。ServiceLoader 插件是可信应用代码。构造器和 `apply(...)` 使用宿主进程权限执行；CompileFlow 不提供沙箱、签名校验或依赖隔离。生产策略要求显式
allowlist 时，应关闭自动发现。

每份配置使用自身的 ClassLoader。发现插件先执行，按 `priority()` 升序、稳定 `id()` 排序；显式插件随后按相同规则执行；直接
builder 贡献最后执行。listener 采用追加语义；plugin 间重复的 script、targeting、retry 或 failure 名称直接失败，不由优先级或
bean 顺序选择语义 winner。直接 retry/failure 注册可以显式替换 plugin 贡献。每次构建配置只读取一次插件 ID 和 priority。

发现默认关闭；仅当依赖图本身是经过批准的扩展边界时显式开启：

```java
ProcessEngineConfig.tbbpmBuilder().discoverPlugins(true).build();
```

插件在 `apply` 中抛出异常会使引擎配置快速失败——配置期错误绝不静默跳过；与之相对，运行期 listener 失败会被隔离并记录日志。

## 调用职责

扩展宿主负责发现、排序、匹配、诊断、失败隔离和实例生命周期。公开 SPI 只暴露类型化能力契约，
不提供通用调用工具，从而让每个宿主将领域特定的选择与失败语义保留在本地。普通 plugin 通过
`ProcessEnginePluginContext` 贡献能力。

## Spring Boot

声明类型化 bean，starter 自动收集进引擎配置：

```java
@Configuration(proxyBeanMethods = false)
class EngineExtensions {

    @Bean
    ProcessEventListener metricsListener(MeterRegistry registry) {
        return event -> registry.counter("flow." + event.getType().name()).increment();
    }

    @Bean
    ScriptExecutor aviatorExecutor() {
        return new AviatorScriptExecutor();
    }

    @Bean
    RetryPolicy optimisticConflict() {
        return error -> error instanceof OptimisticLockException;
    }

    @Bean
    FailureHandler continueOptional() {
        return context -> FailureResolution.CONTINUE_PROCESS;
    }

    @Bean
    TraceIdProvider traceIdProvider() {
        return TraceIdProvider.random();
    }
}
```

- 多个 `ProcessEventListener` bean 遵循 Spring 排序；同一个不区分大小写的脚本语言只能有一个
  `ScriptExecutor` bean，存在歧义时配置直接失败，而不是依赖偶然的 bean 顺序选择 winner；plugin 在 Spring 和独立模式下使用相同的
  `ProcessEnginePlugin.priority()`、稳定 `id()` 排序；
- `RetryPolicy`、`FailureHandler` bean 使用其精确 Spring bean 名注册；同名时，直接 builder 或 Spring bean 贡献覆盖插件贡献；
- `TraceIdProvider`、自定义 `ProcessComponentResolver` 与 `ProcessContextPropagator` 各至多一个 bean；
- classpath 存在 Micrometer Context Propagation 时，starter 会提供 context propagator，除非应用声明自定义 bean；
- Spring 组件自动访问默认拒绝；通过 `compileflow.engine.components.allowed-beans` 暴露精确 bean 名，非空 allowlist 不能与自定义
  resolver 同时配置；
- ServiceLoader 插件发现由属性 `compileflow.engine.plugins.discovery-enabled` 控制（默认 `false`）。

## 事件模型

`ProcessEventListener.supports(ProcessEvent)` 可在调用 `onEvent(ProcessEvent)` 前拒绝无关的不可变生命周期事件；predicate
与事件处理异常都会被隔离并记录：

| 事件类型                                     | 载荷要点                                                         |
|----------------------------------------------|------------------------------------------------------------------|
| `ExecutionStarted`                           | 流程 code 与 invocation ID                                       |
| `ExecutionCompleted` / `ExecutionFailed`     | 受控 `ProcessExecution`、运维 `ExecutionAttribution`、耗时，以及失败时的类型化 `ProcessError` |
| `TriggerStarted`                             | 流程 code、invocation ID 与类型化 `ProcessTrigger`               |
| `TriggerCompleted` / `TriggerFailed`         | 受控 `ProcessExecution`、运维 `ExecutionAttribution`、trigger、耗时，以及失败时的类型化错误   |

`ProcessObservabilityConfig.eventsAsync` 为 `true`（默认）时，事件经引擎持有的事件线程池分发，该线程池随引擎关闭。traceId
在发布线程上捕获后才进行异步移交。使用 `event.getType()` 做低成本过滤，并通过 subtype pattern matching 访问载荷。Event
record 无法携带 routing key、变量、source 内容、任意 metadata 或原始异常。异步分发是
best-effort：事件可并发或乱序；饱和时丢弃被拒绝的事件，不在流程调用线程运行应用 listener；同一事件内的 listener 顺序保持确定。

## 运行时边界

不支持在运行中安装、升级或卸载 plugin JAR。增加 JAR 后需要重启应用。流程定义热部署由
`compileflow-deploy` 提供。受支持的扩展边界见
[支持面清单](../architecture/06-SUPPORTED_SURFACES.zh.md)。

ServiceLoader 插件应是可复用的配置 provider，不应创建需要独立关闭的资源。需要持有资源的 collaborator 应通过应用显式配置或
Spring Bean 注入，由其 owner 确定性关闭。
