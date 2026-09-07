# CompileFlow 扩展指南

CompileFlow 的扩展契约位于 `com.alibaba.compileflow.engine.spi`。只有一个引擎需要某项能力时，直接注册对应接口；需要组合一组相关能力时，使用
`ProcessEnginePlugin`。

扩展可以通过类路径插件、构建器调用或 Spring Bean 注册。
`ProcessEngineConfig.Builder.build()` 负责校验并冻结结果。系统没有全局可变注册表，引擎启动后也不能修改扩展集合。

外部传入的扩展实例由应用或依赖注入容器管理。

## 包结构

| 包                         | 内容                                                                            |
| -------------------------- | ------------------------------------------------------------------------------- |
| `engine`                   | 引擎入口和公共值类型                                                            |
| `engine.config`            | 不可变配置与扩展注册                                                            |
| `engine.spi`               | `ProcessEnginePlugin`、`ProcessEnginePluginContext`、`ProcessComponentResolver` |
| `engine.spi.event`         | `ProcessEvent`、`ProcessEventListener`                                          |
| `engine.spi.execution`     | `RetryPolicy`、`FailureHandler`、`ProcessContextPropagator` 与执行上下文值类型  |
| `engine.spi.script`        | `ScriptExecutor`                                                                |
| `engine.spi.observability` | `TraceIdProvider`                                                               |
| `engine.spi.routing`       | 别名路由权威来源、与路由绑定的目标选择配置和具名目标选择策略                    |

## 扩展点一览

| 能力                          | 配置入口                               | 语义                                                                           |
| ----------------------------- | -------------------------------------- | ------------------------------------------------------------------------------ |
| `ProcessEventListener`        | `builder.eventListener(...)`           | 生命周期事件按条件过滤后有序广播；单个监听器失败会被隔离并记录日志             |
| `TraceIdProvider`             | `builder.traceIdProvider(...)`         | 提供执行 traceId；依次尝试 MDC `traceId` 和随机 ID                             |
| `ProcessComponentResolver`    | `builder.componentResolver(...)`       | 解析生成代码引用的应用组件                                                     |
| `ProcessContextPropagator`    | `builder.contextPropagator(...)`       | 在引擎自有线程边界传播应用上下文                                               |
| `ProcessAliasRouteSource`     | `builder.aliasRouteSource(...)`        | 提供引擎唯一且可用于执行的别名路由权威来源；不能由插件提供                     |
| `ProcessAliasTargetingPolicy` | `builder.aliasTargetingPolicy(...)`    | 覆盖与路由绑定的具名目标选择策略；返回 `Optional.empty()` 时使用固定百分比分桶 |
| `ScriptExecutor`              | `builder.scriptExecutor(...)`          | 供显式脚本动作按语言名选择；同一名称必须唯一                                   |
| `RetryPolicy`                 | `builder.retryPolicy(name, ...)`       | 按名称索引的异常条件，由调用策略的 `retryOn` 引用                              |
| `FailureHandler`              | `builder.failureHandler(name, ...)`    | 按名称索引的终态决策，由调用策略的 `onFailure` 引用                            |
| `ProcessEnginePlugin`         | `builder.plugin(...)` 或 ServiceLoader | 组合监听器以及具名脚本、目标选择、重试和失败处理能力                           |

重试策略和失败处理器是受信任的同步执行协作者，必须线程安全、行为确定、非阻塞并返回合法结果。CompileFlow 会在调用
动作前解析当前调用策略可能使用的全部协作者，因此未知 ID 会在应用副作用产生前失败。协作者抛出异常，或失败处理器返回
`null`，统一报告为 `CF_CONFIG_003`。`FailureHandler` 不能承担可靠的外部投递；尽力而为的遥测使用
`ProcessEventListener`，可靠集成使用 Durable Outbox。

QLExpress 4 与可信 Java Code 是内建脚本语言。QL 默认不能调用宿主对象方法；Java Code 使用宿主 JVM
权限执行，不是安全沙箱。其他脚本引擎需要显式注册为 `ScriptExecutor`。有状态执行器需要使用有界缓存和目标
ClassLoader，并由应用或容器释放资源。

`ScriptExecutor.name()` 必须本身就是 1-256 字符的小写 kebab-case key，Core 不会对它做任何重写。只有
Script Task 会引用该 key。TBBPM `scriptTask` 使用
`<action type="script" language="language-name">`；BPMN 使用
`<scriptTask scriptFormat="language-name">`。未知语言会在代码生成或预检阶段失败，不会拖到执行阶段。
TBBPM 在嵌套动作上声明 Durable 执行语义；BPMN 直接在 `scriptTask` 上声明
`cf:execution="replayable|effect"`。内核不负责能力提供方的持久化或安全语义。排他网关、While、Timer
与 transition 表达式仍拼接为生成 Java 源码，不经过 `ScriptExecutor`。生成 Runtime
代码时，脚本动作文本与变量名都会按 Java 字面量转义。

编译器、解析器、图分析和格式分派类型都属于内部实现。公共契约以
[支持面清单](architecture/supported-surfaces.md)为准。

`ProcessEngineProvider` 是 API 与 Core 之间随版本绑定的引导契约，不属于受支持的扩展 SPI。
Core 提供唯一引导入口；缺失或歧义会导致构造失败。语义前端独立发现。
Spring 应用若要替换完整引擎，应声明 `ProcessEngine` Bean，由自动配置退让。

每个引擎只允许显式配置一个 `ProcessAliasRouteSource`。它必须返回完整、不可变且可用于执行的路由，不能回退到其他权威来源。
该来源不参与插件聚合，避免构造期间在多个路由权威来源之间隐式选择。

`ProcessAliasTargetingPolicy` 以稳定的小写 kebab-case 名称注册；只有权威路由通过 `AliasTargeting` 显式引用该名称时才生效。
它接收已授权的稳定版本和候选版本、不可变路由参数，以及调用方通过 `ProcessExecutionOptions` 显式提供的路由键
和不可变字符串属性；上下文不暴露候选权重。策略可以强制选择 `STABLE` 或 `CANDIDATE`，也可以返回
`Optional.empty()` 进入 CompileFlow 固定的确定性百分比分桶；系统不提供公开的百分比选择 SPI。

路由输入不会进入流程变量，策略也不能读取无关业务输入。正常路径只读取路由来源一次；只有精确运行时接管失败时，
才允许重新读取并完整执行一次准入。接管成功后，本次调用固定到已选版本。缺少具名策略会阻止路由进入本地就绪状态；
已注册策略返回 `null` 而不是 `Optional`，或在运行时抛出异常时，系统选择稳定版本并记录 `TARGETING_ERROR`，不会进入百分比分桶。
策略必须行为确定、线程安全、耗时有界且不执行阻塞式远程 I/O。路由键和属性都可能包含敏感信息，且仅用于准入，禁止记录或持久化。

## 显式配置

```java
ProcessComponentResolver components = new ProcessComponentResolver() {
    @Override
    public <T> T resolve(String name, Class<T> requiredType) {
        return serviceLocator.lookup(name, requiredType);
    }
};

ProcessEngineConfig config = ProcessEngineConfig.builder()
        .eventListener(new MetricsListener(meterRegistry))
        .traceIdProvider(TraceIdProvider.random())
        .componentResolver(components)
        .scriptExecutor(new CustomScriptExecutor())
        .retryPolicy("optimistic-conflict", error -> error instanceof OptimisticLockException)
        .failureHandler("continue-optional", context -> FailureResolution.CONTINUE_PROCESS)
        .build();

ProcessEngine engine = ProcessEngineFactory.create(config);
```

扩展实现必须线程安全：同一份不可变配置可以创建多个引擎，单个引擎也可能并发调用扩展。
`engine.close()` 不关闭外部传入的扩展实例。

自定义重试策略和失败处理器的注册映射键必须是 1 到 256 字符的精确小写 kebab-case 标识符；首尾空白会被拒绝，不会 trim。
内建重试键 `never`、`transient`、`always` 与失败处理键 `propagate`、`continue` 分别在各自注册表中保留。自定义名称不存在、策略或处理器抛出异常、失败处理器返回
`null` 都会使执行失败，不会静默回退到内建行为。

### 应用组件与动作

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

Java 动作会直接生成 `new Type().method(...)` 源码，因此目标必须是可访问的类，并提供 `public` 无参构造器；它不使用依赖注入或
`private` 反射。需要注入的组件应使用 Spring Bean 动作，通过
`compileflow.engine.components.allowed-beans` 暴露精确名称，并保持 Bean 接口足够窄，因为允许列表控制的是 Bean
可达性，不是方法级授权。

`ProcessContextPropagator` 在提交线程捕获不透明的应用上下文，在引擎自有的动作超时、并行执行和事件分发线程中打开，
并在结束时恢复工作线程原有的上下文。在动作或并行执行边界捕获、打开上下文失败，会使本次调用失败；尽力而为的事件分发
则会丢弃该事件。Durable 不会持久化或恢复该上下文。
类路径中存在 Micrometer Context Propagation 时，Spring Boot 自动提供适配器，实际传播内容由已注册的 Micrometer
`ThreadLocalAccessor` 决定；应用声明一个自定义 `ProcessContextPropagator` Bean 即可替换默认适配器。

## 类路径插件

实现 `ProcessEnginePlugin`，注册任意能力组合：

```java
public final class CustomPlugin implements ProcessEnginePlugin {
    @Override
    public void apply(ProcessEnginePluginContext context) {
        context.scriptExecutor(new CustomScriptExecutor());
        context.eventListener(new CustomCompilationListener());
        context.retryPolicy("custom-transient", new CustomRetryPolicy());
    }

    @Override
    public String id() {
        return "com.example.custom";
    }

    @Override
    public int priority() {
        return 100; // 数值小先应用；同一脚本语言的重复贡献始终失败
    }
}
```

在标准服务文件 `META-INF/services/com.alibaba.compileflow.engine.spi.ProcessEnginePlugin` 中声明：

```text
com.example.CustomPlugin
```

类路径也是配置的一部分：只要新增依赖中包含 ServiceLoader `ProcessEnginePlugin`，即使应用源码未变，也可能改变构建出的
引擎。ServiceLoader 插件是可信应用代码。构造器和 `apply(...)` 使用宿主进程权限执行；CompileFlow 不提供沙箱、签名校验或依赖隔离。
生产策略要求显式允许列表时，应关闭自动发现。

每份配置使用自身的 ClassLoader。自动发现的插件先执行，按 `priority()` 升序、稳定 `id()` 排序；显式插件随后按相同规则执行；
构建器直接注册的能力最后执行。监听器采用追加语义；插件间重复的脚本、目标选择、重试或失败处理名称会直接导致失败，
不会根据优先级或 Bean 顺序决定采用哪一项。直接注册的重试策略和失败处理器可以显式替换插件提供的同名能力。
每次构建配置只读取一次插件 ID 和优先级。

发现默认关闭；仅当依赖图本身是经过批准的扩展边界时显式开启：

```java
ProcessEngineConfig.builder().discoverPlugins(true).build();
```

插件在 `apply` 中抛出异常会使引擎配置立即失败；配置期错误不会被静默跳过。运行期监听器失败则会被隔离并记录日志。

## 调用职责

扩展宿主负责发现、排序、匹配、诊断、失败隔离和实例生命周期。公开 SPI 只暴露类型化能力契约，
不提供通用调用工具，从而让每个宿主将领域特定的选择与失败语义保留在本地。普通插件通过
`ProcessEnginePluginContext` 贡献能力。

## Spring Boot

声明类型化 Bean，Starter 会自动将其加入引擎配置：

```java
@Configuration(proxyBeanMethods = false)
class EngineExtensions {

    @Bean
    ProcessEventListener metricsListener(MeterRegistry registry) {
        return event -> registry.counter("flow." + event.getType().name()).increment();
    }

    @Bean
    ScriptExecutor customScriptExecutor() {
        return new CustomScriptExecutor();
    }

    @Bean
    ProcessEnginePlugin invocationPolicies() {
        return ProcessEnginePlugin.of("com.example.invocation-policies", context -> {
            context.retryPolicy("optimistic-conflict",
                    error -> error instanceof OptimisticLockException);
            context.failureHandler("continue-optional",
                    failure -> FailureResolution.CONTINUE_PROCESS);
        });
    }

    @Bean
    TraceIdProvider traceIdProvider() {
        return TraceIdProvider.random();
    }
}
```

- 多个 `ProcessEventListener` Bean 遵循 Spring 排序；同一个不区分大小写的脚本语言只能有一个
  `ScriptExecutor` Bean，存在歧义时配置直接失败，不依赖偶然的 Bean 顺序作出选择；插件在 Spring 和独立模式下使用相同的
  `ProcessEnginePlugin.priority()`、稳定 `id()` 排序；
- `RetryPolicy`、`FailureHandler` 通过 `ProcessEngineConfig.Builder` 或 `ProcessEnginePlugin` 注册，Spring 可提供上例的
  插件 Bean；名称由显式注册映射键决定，不取 Spring Bean 名。未命名的重试策略或失败处理器 Bean 不会被收集；构建器直接注册可覆盖插件提供的同名能力；
- `TraceIdProvider`、自定义 `ProcessComponentResolver` 与 `ProcessContextPropagator` 各至多一个 Bean；
- 类路径中存在 Micrometer Context Propagation 时，Starter 会提供上下文传播器，除非应用声明自定义 Bean；
- Spring 组件自动访问默认拒绝；通过 `compileflow.engine.components.allowed-beans` 暴露精确 Bean 名，非空允许列表不能与自定义
  组件解析器同时配置；
- ServiceLoader 插件发现由属性 `compileflow.engine.plugins.discovery-enabled` 控制（默认 `false`）。

## 事件模型

`ProcessEventListener.supports(ProcessEvent)` 可在调用 `onEvent(ProcessEvent)` 前拒绝无关的不可变生命周期事件；过滤条件
与事件处理异常都会被隔离并记录：

| 事件类型                                 | 载荷要点                                                                                      |
| ---------------------------------------- | --------------------------------------------------------------------------------------------- |
| `ExecutionStarted`                       | 流程编码与调用 ID                                                                             |
| `ExecutionCompleted` / `ExecutionFailed` | 受控 `ProcessExecution`、运维 `ExecutionAttribution`、耗时，以及失败时的类型化 `ProcessError` |
| `TriggerStarted`                         | 流程编码、调用 ID 与类型化 `ProcessTrigger`                                                   |
| `TriggerCompleted` / `TriggerFailed`     | 受控 `ProcessExecution`、运维 `ExecutionAttribution`、trigger、耗时，以及失败时的类型化错误   |

`ProcessObservabilityConfig.eventsAsync` 为 `true`（默认）时，事件经引擎持有的事件线程池分发，该线程池随引擎关闭。traceId
在发布线程上捕获后才进行异步移交。使用 `event.getType()` 做低成本过滤，并通过子类型模式匹配访问载荷。事件记录
不包含路由键、变量、来源内容、任意元数据或原始异常。异步分发采用尽力而为语义：事件可以并发或乱序；饱和时丢弃被拒绝的事件，
不会在流程调用线程中运行应用监听器；同一事件内的监听器顺序保持确定。

## 运行时边界

不支持在运行中安装、升级或卸载插件 JAR。增加 JAR 后需要重启应用。流程定义热部署由
`compileflow-deploy` 提供。受支持的扩展边界见
[支持面清单](architecture/supported-surfaces.md)。

ServiceLoader 插件应是可复用的配置提供方，不应创建需要独立关闭的资源。需要持有资源的协作者应通过应用显式配置或
Spring Bean 注入，由资源所有者确定性关闭。
