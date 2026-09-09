# CompileFlow 配置参考

CompileFlow 只在应用边界解析一次外部配置。配置完成类型转换和校验后形成不可变快照，再交给负责该配置的组件。核心执行路径
不会读取环境变量、JVM 系统属性或可变的全局配置解析器。

Java 配置快照只引用外部传入的协作组件，其生命周期仍由应用管理；引擎只关闭工厂自行创建的资源。复用同一份配置创建
多个引擎时，传入的协作组件必须满足各自文档约定的线程安全契约。

以下设置构成受支持的配置范围。名称、类型、单位、枚举值、归属、语义和取值范围属于契约。语义、安全和启用默认值保持稳定；运维容量与性能默认值可依据有记录的验证结果演进，不构成延迟保证。实现字段和未记录的别名不属于受支持配置。详见[兼容性策略](compatibility-policy.md)。

## 边界与优先级

| 边界             | 命名空间                         | 生命周期                  | 权威对象                                                       |
| ---------------- | -------------------------------- | ------------------------- | -------------------------------------------------------------- |
| Java 引擎        | `compileflow.engine.*`           | 多格式装配与引擎生命周期  | `ProcessEngineProperties` -> `ProcessEngineConfig`             |
| 部署系统         | `compileflow.deploy.*`           | 控制面和运行时 Bean       | 不可变 Deploy 配置子树                                         |
| Durable 运行时   | `compileflow.durable.*`          | Durable 运行时            | `CompileFlowDurableProperties` -> `DurableProcessEngineConfig` |
| Workbench 服务端 | `compileflow.workbench.server.*` | Java 应用                 | 不可变 `CompileFlowWorkbenchServerProperties`                  |
| 开发网关         | `COMPILEFLOW_DEV_GATEWAY_*`      | 仅回环的 Node.js 模拟服务 | 冻结的 `DevGatewayConfig`                                      |
| Web 应用         | `VITE_COMPILEFLOW_*`             | Vite 构建产物             | 冻结的 `AppBuildConfig`                                        |

本地部署模板还接受少量插值输入，但它们不构成独立的应用配置接口。Compose 将
`COMPILEFLOW_WORKBENCH_DATABASE_*` 转换为 PostgreSQL 与 Spring 标准 datasource 配置；回环 nginx 容器只接收由同一个
Workbench Server 密钥派生的
`COMPILEFLOW_WORKBENCH_LOCAL_GATEWAY_UPSTREAM_API_KEY`。这些值不会进入浏览器构建，也不会在 Java 或 Node 业务代码中形成第二套配置
解析器。

Spring Boot 使用标准属性源优先级和宽松绑定。未提供的值使用本文默认值；显式非法值、可枚举 Spring
配置源在受支持命名空间中的未知字段、受管服务端、开发网关或 Web 变量中的未知名称，以及非法的跨字段组合，都会使启动或 Web
构建失败。Spring 不会枚举原始系统环境变量和系统属性中的未知字段；其中已知的宽松绑定名称仍可使用，但只有 Workbench Server 的
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` 受管别名额外提供环境变量白名单。引擎、Deploy 和 Durable 配置不支持
动态刷新；需要变更时应新建引擎或重启拥有该配置的组件。

## 引擎

最小配置：

```yaml
compileflow:
    engine:
        enabled: true
```

全部引擎 `Duration` 配置统一使用整毫秒精度，并且必须能表示为 Java `long` 毫秒值。正时长至少为 `1ms`；明确允许非负的配置可以使用
`0ms`。精度更细或数值过大的配置会在校验阶段失败，不会被静默截断，也不会等到引擎构造时才暴露为算术异常。

### 请求级执行选项

`ProcessExecutionOptions` 是单次调用数据，不是外部应用配置。把关联 ID、不透明的确定性路由键和策略真正需要的最小自定义属性直接传给
`execute(...)` 或 `trigger(...)`：

```java
ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .invocationId("order-20260716-42")
        .aliasRouting(new AliasRoutingOptions("user-7", Map.of("region", "eu-west")))
        .build();

engine.execute(
        ProcessRef.alias("default", "order.process", "prod"),
        variables,
        options).orElseThrow();
```

引擎会取得不可变防御性快照。路由属性只能是字符串，并与流程变量和输出隔离；路由键也不会进入日志、
事件、结果或持久化。不要把请求身份、别名或路由键放进环境变量或流程变量 Map。`__cf_` 前缀保留给引擎内部
元数据，应用路由属性和根流程变量都不能使用。

配置分为三层：**基础**配置用于选择引擎契约，**运维**配置控制生产行为，**高级**配置用于执行器或诊断调优。快速开始只需要基础配置；生产环境通常还需要运维配置；高级配置应在有测量依据时再调整。

### 通用

| 层级 | 属性                                  | 默认值     | 约束/用途                                                                                                   |
| ---- | ------------------------------------- | ---------- | ----------------------------------------------------------------------------------------------------------- |
| 基础 | `compileflow.engine.enabled`          | `true`     | 是否启用引擎自动配置。                                                                                      |
| 高级 | `compileflow.engine.runtime-mode`     | `COMPILED` | 选择运行模式；`COMPILED` 和 `INTERPRETED` 均为正式支持的公开模式。                                          |
| 运维 | `compileflow.engine.call.max-depth`   | `32`       | 包含根流程在内的同步流程调用深度，范围 `1` 到 `256`；更深的嵌套或递归调用会在动作执行前失败并报告调用路径。 |
| 运维 | `compileflow.engine.shutdown.timeout` | `15s`      | 公共操作排空与执行器关闭共享的总预算；引擎会在内部预留强制终止阶段，至少 `1ms`。                            |

### 执行器高级调优

| 属性                                                                   | 默认值                     | 约束/用途                                                                                                            |
| ---------------------------------------------------------------------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------- |
| `compileflow.engine.executor.runtime-load.max-concurrency`             | `min(2, max(1, CPUs / 8))` | 本地运行时加载的最大并发数，必须为正数。                                                                             |
| `compileflow.engine.executor.runtime-load.max-pending`                 | `4`                        | 等待运行时加载槽位的额外任务上限，必须为非负数；所有槽位忙时设为 `0` 会立即拒绝。                                    |
| `compileflow.engine.executor.action-timeout.max-concurrency`           | `max(4, CPUs)`             | 需要超时控制的动作最大并发数，必须为正数。                                                                           |
| `compileflow.engine.executor.action-timeout.max-pending`               | `0`                        | 等待超时动作槽位的额外尝试数上限，必须为非负数；默认无可用工作线程时立即拒绝。显式配置队列后，排队时间计入动作超时。 |
| `compileflow.engine.executor.action-timeout.cancellation-grace-period` | `2s`                       | 超时或取消后等待动作协作停止的最长时间；旧尝试尚未停止时拒绝启动可能重叠的新尝试。                                   |
| `compileflow.engine.executor.parallel.cancellation-grace-period`       | `2s`                       | 并行分支失败后等待兄弟分支协作停止的最长时间；超时会报告执行中断并列出仍在运行的分支。                               |

与流程格式无关的 Spring Boot Starter 和 Workbench Server 会创建一个支持所有已安装格式前端的引擎。
流程定义明确指定模型类型，执行器和缓存限额由引擎共享，不按流程格式分别配置。只使用一种格式时，选择
`compileflow-spring-boot-starter-tbbpm` 或 `compileflow-spring-boot-starter-bpmn`；同时使用两种格式时，引入基础 Starter
和两个格式模块。
默认执行模式为 `COMPILED`。`INTERPRETED` 同样受到支持，但两种模式都要求宿主提供 `jdk.compiler`，用于定义校验和运行时准备。

每个并行分支使用一个编排任务线程。Java 21 及以上使用虚拟线程；Java 17 使用有限、无队列的平台线程
执行器，内部 CPU 自适应上限为 `max(8, min(64, CPUs * 4))`；达到上限后由提交流程线程直接执行分支，从而提供背压，并避免把嵌套任务排在等待中的父分支之后。
这个调度策略不是公开的并发控制项。未设置超时的动作在当前流程线程执行；设置超时后使用有界的动作执行器。编译、超时动作与事件池过载时都会拒绝任务，绝不会回退到提交线程。预检
使用独立的固定有界协调池，内部为 `min(2, max(1, CPUs / 8))` 个工作线程和四个等待任务；排队时间计入端到端超时，过载时拒绝任务而不是由调用线程执行。
调用方线程执行只用于嵌套分支编排，以在不破坏超时计时的前提下保证进展。

并行执行器不限制对下游服务的总并发调用。Java 21 虚拟线程没有上述平台线程数量上限；Java 17 使用调用方线程执行时，
也会在这个上限之外占用宿主请求线程。共享依赖应在应用边界通过连接池或显式并发准入限制，并计算所有引擎实例及副本的总占用。
虚拟线程池化不能替代资源隔离策略，参见 [OpenJDK 指导](https://openjdk.org/jeps/444#Do-not-pool-virtual-threads)。

### 缓存

| 属性                                       | 默认值 | 约束/用途                                                  |
| ------------------------------------------ | ------ | ---------------------------------------------------------- |
| `compileflow.engine.max-resident-runtimes` | `2048` | 该引擎缓存及所有者保留的流程运行时总数硬上限，必须为正数。 |

Java 代码以 `Script(language="java", source)` 表示，并由 `compileflow-core` 默认支持。
其类型包装器在流程运行时加载期间编译，并由确定的运行时按完整脚本签名持有。原始源码
仍是流程版本的事实来源；不存在持久化字节码缓存或执行期编译配置。

### 脚本

核心模块默认注册 QLExpress 4 和可信的 Java 代码执行器。QL 使用固定语言配置：隔离宿主对象访问、固定安全函数集、一秒
截止时间和 10,000 的单维数组上限。这些是语言能力保证，不是应用调参项。内建 `qlexpress` 和 `java` 不允许同名覆盖；自定义脚本语言
必须使用独立的 `ScriptExecutor` 名称，提供方负责安全、超时、缓存、类加载器和生命周期策略。

### 流程定义加载

| 属性                                     | 默认值 | 约束/用途                                                                                          |
| ---------------------------------------- | ------ | -------------------------------------------------------------------------------------------------- |
| `compileflow.engine.definition.max-size` | `4MB`  | 单个 inline 或 classpath 流程定义允许的最大 UTF-8/二进制大小，范围为 `1B` 至硬性安全上限 `100MB`。 |

加载器最多读取 `max-size + 1` 字节，并在 XSD 校验前冻结一份不可变字节快照，因此 Schema 校验和模型
解析看到的内容完全一致。解析到 HTTP、HTTPS、FTP 或 FTPS 的类路径资源会被拒绝；远程制品必须由具备认证、网络策略、超时和摘要校验的应用或部署解析器
获取。
Schema 校验和流式解析均强制 XML 元素嵌套不超过 128 层，即使关闭 schema 校验也生效；超限返回定义校验错误，
不限制同层节点数量。这是解析安全边界，不是公共并发配置，也不是 ProcessCall 深度设置。

### 运行时加载与 Java 诊断

| 属性                                                         | 默认值  | 约束/用途                                                    |
| ------------------------------------------------------------ | ------- | ------------------------------------------------------------ |
| `compileflow.engine.runtime-load-timeout`                    | `10s`   | 同步调用等待运行时加载的最长时间，至少 `1ms`。           |
| `compileflow.engine.java-diagnostics.debug.symbols`          | `LINES` | `NONE`、`LINES` 或 `FULL`；本身不会写文件。                  |
| `compileflow.engine.java-diagnostics.debug.output-directory` | 未设置  | 设置后按生成类名导出稳定的 `source/` 与 `metadata/` 目录树。 |
| `compileflow.engine.java-diagnostics.debug.bytecode-enabled` | `false` | 同时导出 `.class` 文件；启用时必须设置输出目录。             |

默认在内存中加载运行时。调试目录可能包含生成源码、脚本、表达式、常量和 class 文件，应使用操作系统权限或 ACL
保护配置根目录，并限制访问与保留周期，也不能放在 Web 可访问目录下。生成源码按包路径稳定写入 `source/`，可选字节码写入对应的
`classes/`，配套属性写入 `metadata/`。再次编译同一个生成类时会原子替换这些文件，因此 IDE 断点始终绑定到同一个文件 URL。在
IntelliJ IDEA 中，将 `<output-directory>/source` 标记为 Sources Root，并在可执行代码行设置断点。Durable 生成类名包含标准化后的完整流程
code 和 program digest 前缀，源码头同时保留原始流程 code，便于直接识别。

每次合并后的运行时加载只尝试一次 Java 编译。对同一份已解析源码快照而言，解析、代码生成、类解析与 Java
编译诊断都是确定性的，因此引擎不会盲目重试。流程定义或运行环境修复后，后续调用可以发起新一次编译；部署收敛与持久化异步调用分别拥有自己的有界重试策略。

`runtime-load-timeout` 只限制同步调用的等待时间。超时不会由任意一个等待者取消共享的运行时加载任务；已接纳的任务会
继续执行，成功后进入节点本地缓存，后续调用可直接复用。JDK 编译器没有可靠的强制终止契约，资源上限由流程定义大小、runtime-load 并发与 pending 准入共同保证。

```yaml
compileflow:
    engine:
        runtime-load-timeout: 20s
        executor:
            runtime-load:
                max-concurrency: 2
                max-pending: 4
        java-diagnostics:
            debug:
                symbols: FULL
                output-directory: /var/lib/compileflow/debug-runtime
                bytecode-enabled: true
```

### 可观测性与插件

| 层级        | 属性                                                       | 默认值  | 约束/用途                                                                 |
| ----------- | ---------------------------------------------------------- | ------- | ------------------------------------------------------------------------- |
| 运维        | `compileflow.engine.observability.events.async`            | `true`  | 是否使用有界、尽力投递的异步生命周期事件分发。                            |
| 运维        | `compileflow.engine.observability.events.max-concurrency`  | `2`     | 生命周期事件分发的最大并发数，必须为正数。                                |
| 运维        | `compileflow.engine.observability.events.max-pending`      | `16`    | 等待事件分发槽位的额外任务上限，必须为非负数；饱和时设为 `0` 会拒绝投递。 |
| 运维        | `compileflow.engine.observability.mdc-propagation-enabled` | `false` | 是否跨引擎执行器边界复制 MDC。                                            |
| 高级        | `compileflow.engine.plugins.discovery-enabled`             | `false` | 仅在显式启用时通过 `ServiceLoader` 自动发现 `ProcessEnginePlugin`。       |
| 运维        | `compileflow.engine.components.allowed-beans`              | `[]`    | 暴露给流程定义的精确 Spring bean 名；空列表拒绝自动 bean 访问。           |

存在 `MeterRegistry` 时会自动注册 Micrometer 指标绑定器。指标启停和过滤使用 Spring Boot 标准的
`management.metrics.enable.*`，不创建重复的 CompileFlow 开关。程序化 `ProcessObservabilityConfig`
只包含事件分发与 MDC 传播行为。

事件监听器是构造期协作者，不是字符串配置。独立运行时通过
`ProcessEngineConfig.Builder.eventListener(...)` 注册；Spring starter 会按顺序收集
`ProcessEventListener` bean，并写入不可变引擎配置快照。

生命周期事件只用于尽力观测，可能在异步队列饱和或关闭时被丢弃，不能作为业务正确性、审计、计费或可靠集成的依据。
ProcessEngine 执行应使用应用事务/Outbox，Durable 执行应使用 Durable Journal/Outbox。

Workbench Server 为终态执行日志有意将 `events.async` 覆盖为 `false`，使请求在返回前尝试写入数据库，
避免事件队列饱和造成样本偏差。监听器失败仍与流程结果隔离，因此这不构成持久化审计保证。

追踪标识提供方也在构造时配置。`TraceIdProvider` 只是轻量日志/事件关联钩子，不是完整的链路追踪或上下文
传播机制。独立运行时可通过
`ProcessEngineConfig.Builder.traceIdProvider(...)` 设置一个 `TraceIdProvider`；Spring 存在应用声明的唯一
`TraceIdProvider` bean 时使用它，否则读取标准 MDC `traceId`。引擎只接受 1–128 字符且首尾无空白的标识；提供方或 MDC
返回空值、首尾带空白、超长值或提供方失败时都不会归一化或截断非法标识，而是继续回退到 32 位十六进制本地 ID。非法观测输入不能改变流程执行结果。

独立应用通过一个显式 `ProcessComponentResolver` 暴露组件；Spring 应用也可在
`compileflow.engine.components.allowed-beans` 中列出精确 bean 名。默认空列表拒绝全部自动 bean 访问；以
`&` 开头的 FactoryBean 解引用名、空值、重复值和带首尾空白的别名都会被拒绝。自定义解析器与非空允许列表
互斥，避免任一策略被静默忽略。解析同时使用声明的组件名和所需 Java 类型；缺失或类型不兼容直接失败，不会转而实例化流程中声明的类型。

独立应用可通过 `ProcessEngineConfig.Builder.contextPropagator(...)` 设置一个 `ProcessContextPropagator`。它只在
ProcessEngine 自有线程切换时捕获应用环境上下文，并在结束时恢复工作线程原有的上下文；Durable 不会持久化或恢复该上下文。
类路径中存在 Micrometer Context Propagation 时，Spring 自动提供适配器，自定义 `ProcessContextPropagator` bean 会替换它。

其余类型化扩展点（`ScriptExecutor`、`RetryPolicy`、`FailureHandler`、`ProcessEnginePlugin`）同样是构造期协作者。
注册、排序、所有权和失败语义见[扩展指南](extension-guide.md)。

## 部署系统

可复用 starter 默认不启动部署系统，应用必须显式选择。CompileFlow Workbench Server 则在自身 `application.yml` 中显式
启用完整的嵌入式拓扑。

### 拓扑与进程职责

| 属性                                        | 默认值     | 约束/用途                                                                                                                  |
| ------------------------------------------- | ---------- | -------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.deploy.enabled`                | `false`    | 部署仓储、路由、控制面、运行时和指标的总开关。                                                                      |
| `compileflow.deploy.topology`               | `EMBEDDED` | `EMBEDDED` 在命令返回前激活已提交 Alias，并保留 outbox 恢复；`DISTRIBUTED` 使用同步通道。                                  |
| `compileflow.deploy.control-plane-enabled`  | `true`     | 当前进程承载发布命令、outbox 分发与分布式对账。                                                                            |
| `compileflow.deploy.runtime-worker-enabled` | `false`    | 仅在 `DISTRIBUTED` 拓扑下启用通道订阅、需求规划、产物解析与安装角色；Embedded 本地收敛由 `topology` 选择，不由该开关启用。 |
| `compileflow.deploy.database.provider`      | 未设置     | 仅当两个官方数据库自动配置模块同时存在时必填；取值 `POSTGRESQL` 或 `MYSQL`，选择不可用的数据库实现会启动失败。         |
| `compileflow.deploy.database.migrate`       | `false`    | 为 `true` 时执行所选 Deploy 数据库迁移；否则校验由外部更新的数据库结构。                                                         |

启用部署系统后至少要选择一个进程职责。`EMBEDDED` 要求
`control-plane-enabled=true`、`runtime-worker-enabled=false`；`DISTRIBUTED`
支持纯控制面、纯运行时工作节点和显式配置的组合进程。数据库型纯运行时工作节点应设置
`control-plane-enabled=false`，此时即使存在 `DataSource` 也不会启动发布服务和 Outbox 工作节点。

只有部署系统已启用、选中匹配的数据库实现且存在 `DataSource` 时才自动创建一个完整 `DeployStore`，不存在内存回退。别名状态与路由事件始终在 Store 拥有的同一事务中提交；拓扑必需基础设施不完整时启动失败，不会选择非事务或断连路径。这些拓扑和一致性要求属于受支持的配置契约。

### 发布、产物与运行时

| 属性                                                  | 默认值                 | 约束/用途                                                                                                                  |
| ----------------------------------------------------- | ---------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.deploy.artifact.mode`                    | `SOURCE`               | `SOURCE` 或 `PROJECTION_STORE`，发布端和运行时解析器共用。                                                             |
| `compileflow.deploy.artifact.key-prefix`              | `compileflow.process.` | 最长 128 个字符的可移植投影存储前缀，只允许 ASCII 字母、数字、`.`、`_`、`:` 与 `-`。                             |
| `compileflow.deploy.artifact.operation-timeout`       | `5s`                   | 每次投影存储制品读取或原子 CAS 的截止时间，至少 `1ms`。                                                         |
| `compileflow.deploy.runtime.failure-backoff`          | `5m`                   | 两种拓扑中安装失败后的重试等待时间，必须为正整毫秒；运行时使用单调时钟截止时间，因此还必须能表示为 Java `long` 纳秒区间。 |
| `compileflow.deploy.runtime.convergence-timeout`      | `30s`                  | 嵌入式调用方的最长等待时间，覆盖别名查询、产物解析、运行时加载及本地就绪状态发布；分布式运行时节点不使用该值。       |
| `compileflow.deploy.runtime.installation-concurrency` | `1`                    | 分布式产物安装最大并发数，或嵌入式收敛操作的最大准入数，范围 `1..256`。                                                    |

嵌入式收敛由运行时管理的有界执行器执行。调用方超时或被中断不会取消共享工作，也不会释放其准入槽位；
后台操作完成后才释放槽位。容量耗尽时立即返回 `CONVERGENCE_FAILED`。该配置限制调用方的等待时间，
不强制终止提供方 I/O 或编译；卡住的提供方会持续占用容量，直到其返回，因此应另行配置 I/O 超时。
执行器关闭后停止新准入，并允许已提交的工作完成。

全部 Deploy `Duration` 配置都使用毫秒精度，因此必须是能以 Java `long` 表示的整毫秒值；
`runtime.failure-backoff` 还必须能以 `long` 表示纳秒值，以支持单调时钟截止时间。正时长至少为 `1ms`；只有明确标注为非负的属性（例如
Outbox 保留时间）允许 `0ms`。精度更细或数值过大的值会在绑定阶段失败，不会被截断或进入运行时计算。

嵌入式拓扑要求使用 `SOURCE` 制品模式，并能在内存状态丢失后从共享仓储恢复别名、版本和源码。分布式运行时必须存在
`DeploymentProjectionStore`；`SOURCE` 模式还必须存在产物仓储，并且必须配置由 `namespaces × codes × aliases` 派生的非空路由订阅集（最多 10,000 个键）。
不另设键列表配置。缺少依赖时启动失败。

发布会写入一条不可变的 `(namespace, code, version)` 记录，其中保存原始 UTF-8 源码、模型类型、SHA-256 摘要、元数据、操作者
和数据库时间。记录一旦存在即表示版本已经发布，不再维护可变发布状态或准备租约。使用相同身份和内容重试时返回原记录，不改写审计字段；内容不同则直接报告冲突。首次写入仓储前，系统会解析源码，并按对应格式校验 Schema 和模型结构；这一步不会生成、缓存或安装节点本地运行时。需要 Java 编译诊断时，可以额外调用
`ProcessToolingService.preflight(...)` 并使用严格选项。控制面校验不能证明具有不同应用类路径或组件环境的运行节点已经就绪。发布也不会修改路由。

摘要是版本和制品的必填字段，不是可选元数据或功能开关。解析制品载荷、读取数据库和加载运行时都会校验身份与完整性。运行时安装会在相应信任边界内执行真实引擎部署；安装失败时，该节点不能进入本地就绪状态，也不会激活对应路由。

Spring 在 `ApplicationContext` 刷新完成后通过 `SmartLifecycle` 启动 Deploy 运行时、Outbox 分发器和
对账器。运行时先于控制面后台任务启动；关闭时顺序相反，先停止发布，再关闭订阅与安装器。Bean 构造阶段不会启动工作线程。

### 路由、Outbox 与对账

| 属性                                            | 默认值                    | 约束/用途                                                                                                                                                                                                                             |
| ----------------------------------------------- | ------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.deploy.routing.key-prefix`         | `compileflow.deployment.` | 最长 128 个字符的可移植别名路由前缀，只允许 ASCII 字母、数字、`.`、`_`、`:` 与 `-`。                                                                                                                                                  |
| `compileflow.deploy.routing.namespaces`         | `[default]`               | 用于派生键的非空、唯一、规范命名空间列表。                                                                                                                                                                                            |
| `compileflow.deploy.routing.codes`              | `[]`                      | 用于派生键的唯一规范流程编码。                                                                                                                                                                                                        |
| `compileflow.deploy.routing.aliases`            | `[production]`            | 用于派生路由键的唯一规范别名；显式空列表会关闭别名订阅。                                                                                                                                                                              |
| `compileflow.deploy.routing.operation-timeout`  | `5s`                      | 每次路由状态读取、原子比较并设置或订阅建立的截止时间，至少 `1ms`。                                                                                                                                                                    |
| `compileflow.deploy.outbox.dispatch-interval`   | `1s`                      | 至少 `1ms`。                                                                                                                                                                                                                          |
| `compileflow.deploy.outbox.dispatch-batch-size` | `50`                      | 单个有界投递批次的记录上限，范围 `1..1000`；批次满载时运行时会短暂让步后自动续跑，因此它不是持续吞吐上限。                                                                                                                            |
| `compileflow.deploy.outbox.retention`           | `24h`                     | 不得为负；`0ms` 表示关闭有界清理。                                                                                                                                                                                                    |
| `compileflow.deploy.outbox.lease-duration`      | `1m`                      | 已领取交付记录的权威有效期，至少 `1ms`；在 `EMBEDDED` 中必须长于 `runtime.convergence-timeout`，在 `DISTRIBUTED` 中必须长于 `routing.operation-timeout`。分布式交付保持幂等并受租约令牌隔离保护；租约应长于实测的投影存储端到端延迟。 |
| `compileflow.deploy.outbox.retry.initial-delay` | `5s`                      | 首次重试窗口上限；交付使用带完全抖动的有界指数退避。                                                                                                                                                                                  |
| `compileflow.deploy.outbox.retry.max-delay`     | `5m`                      | 抖动窗口上限，必须不小于初始延迟。                                                                                                                                                                                                    |
| `compileflow.deploy.outbox.retry.max-attempts`  | `10`                      | 进入死信前允许的失败交付次数，必须为正数。                                                                                                                                                                                            |
| `compileflow.deploy.reconciliation.mode`        | `REPAIR`                  | `DISABLED`、`DETECT` 或 `REPAIR`；只对账派生的路由和制品投影。                                                                                                                                                                        |
| `compileflow.deploy.reconciliation.interval`    | `1m`                      | 至少 `1ms`。                                                                                                                                                                                                                          |

Outbox 分发不提供独立启停开关或动态暂停状态。版本或别名状态与对应事件在同一事务中提交；如果继续接受
命令却关闭交付，就会产生陈旧的执行视图。计划维护或事故停发应停止整个控制面进程，让命令入口与交付共同停止。分发器通过持久化的
`PROCESSING` 租约、隔离令牌和领取记录协调工作，网络交付期间不持有数据库锁。交付采用至少一次语义。`DISTRIBUTED` 通过按精确内容比较并设置，单调推进投影存储；迟到的低修订号和重复交付都是成功的空操作。由于通知仍可能乱序，消费者还会独立过滤修订号。自定义投影存储适配器必须使用服务端原子比较并设置，不能用客户端先读后写模拟。健康状态分别报告等待处理、正在处理、领取过期与死信数量。
`EMBEDDED` 把已提交事件应用到本地 `LocalRoutingState`，`DISTRIBUTED` 则把相同事件投影到
`DeploymentProjectionStore`。

### 分布式传输

CompileFlow 不通过 `compileflow.deploy.*` 选择远程传输。分布式进程必须提供且仅提供一个
`DeploymentProjectionStore` Bean；提供方的连接、认证、命名空间与容量配置归属于该适配器自己的配置前缀。

后端必须支持精确值读取、包含“值不存在时创建”的精确内容原子比较并设置，以及最终可达的更新通知。
不受支持的传输配置会直接被拒绝，不会被静默忽略。

## Durable 配置

Durable 运行时通过 `compileflow-durable-spring-boot-starter` 显式启用，并要求存在 `DurableStore`。
第一方发行入口是 `compileflow-durable-spring-boot-starter-postgresql` 与
`compileflow-durable-spring-boot-starter-mysql`。Durable starter 不创建 `ProcessEngine`。
`DurableProcessEngineConfig` 独立管理能力和资源配置，显式定义自带模型类型。
通过版本或别名启动时，如果托管版本尚无已存储的流程定义副本，
`DurableVersionDefinitionSource` 会提供权威的类型化内联定义；恢复流程实例时不会调用该来源。
通过版本、别名和显式定义启动是三条独立路径；别名启动还需要由已提交 Deploy 状态支持的
`DurableAliasStateSource`。CompileFlow 先应用路由显式绑定的具名 `ProcessAliasTargetingPolicy`，再使用固定百分比分桶；
别名准入策略不是另一项 Durable SPI。
应用能力以及提供方与运行时的兼容信息不属于 Durable 身份。只有需要可靠发送一组固定的内核集成事件时，才提供 `DurableOutboxSink`。完成等待时提交的载荷必须是流程已声明变量的类型化部分更新，并在提交前校验；`DurableWaitDescriptionProvider` 只能根据
`DurableWaitDescriptionContext` 提供的流程编码、语义摘要、等待节点、分离状态和词法绑定，为
`WAIT_COMMITTED` 生成确定性的只读描述属性。默认实现不增加属性。

| 属性                                                          |     默认值 | 契约                                                                                                                                                              |
| ------------------------------------------------------------- | ---------: | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.durable.enabled`                                 |    `false` | 显式启用 Durable 组装                                                                                                                                             |
| `compileflow.durable.database.provider`                       |     未设置 | 仅当两个官方存储实现同时存在时必填；取值 `POSTGRESQL` 或 `MYSQL`                                                                                                  |
| `compileflow.durable.runtime-mode`                            | `COMPILED` | 为同一 Durable 状态机选择 `COMPILED` 或 `INTERPRETED` 运行模式；持久化行为与身份不变                                                                              |
| `compileflow.durable.call.max-depth`                          |       `32` | 包含根流程的最大子流程调用深度，范围 1 到 256。                                                                                                                   |
| `compileflow.durable.shutdown.timeout`                        |      `15s` | 停止和关闭时用于排空工作节点的预算；必须为正的整毫秒，最长 1 天。不包含已接收应用操作的排空及后续资源清理。                                                       |
| `compileflow.durable.definition.max-size`                     |      `4MB` | 准入大小上限为 `1B` 到 `4MB`（4 MiB），受可移植存储契约约束；恢复时不重新应用更小的准入限制。                                                                     |
| `compileflow.durable.java-diagnostics.debug.symbols`          |    `LINES` | 生成类调试符号：NONE、LINES 或 FULL。                                                                                                                             |
| `compileflow.durable.java-diagnostics.debug.output-directory` |   `未设置` | 可选的生成源码导出目录。                                                                                                                                          |
| `compileflow.durable.java-diagnostics.debug.bytecode-enabled` |    `false` | 导出生成的字节码，必须配置输出目录。                                                                                                                              |
| `compileflow.durable.database.migrate`                        |    `false` | 为 `true` 时执行所选 Durable 数据库变更脚本；否则校验由外部更新的数据库结构                                                                                       |
| `compileflow.durable.worker.enabled`                          |     `true` | 启动自适应执行轮次、外部操作、维护和可选 Outbox 工作                                                                                                              |
| `compileflow.durable.worker.id`                               |   自动生成 | 所有工作类型共享的可选运行时标识，最多 96 个字符                                                                                                                  |
| `compileflow.durable.worker.lease-duration`                   |      `30s` | 运行时令牌租约时长，范围 2 毫秒至 1 小时；流程实例、外部操作和 Outbox 分别使用独立的有界批量续租通道，周期为租约时长的三分之一（最短 1 毫秒），不另设续租周期参数 |
| `compileflow.durable.worker.idle-poll-delay`                  |    `100ms` | 初始空闲获取延迟，不超过 1 分钟；运行时自动指数退避并加入抖动                                                                                                     |
| `compileflow.durable.worker.turn-fault-backoff`               |       `1s` | 执行轮次发生非预期故障后的重试延迟，不超过 1 小时                                                                                                                 |
| `compileflow.durable.worker.turn-max-steps`                   |    `10000` | 单次有界执行轮次最多推进的流程节点数，范围 `1..1000000`；耗尽时持久化让出执行权，不把流程实例判为故障                                                             |
| `compileflow.durable.worker.max-active-iterations`            |       `32` | 每个 Durable 并行集合范围最多保留的活动迭代数，范围 `1..64`；属于运行时策略，不进入流程身份                                                                       |
| `compileflow.durable.worker.turn-concurrency`                 |        `2` | 执行轮次最大并行数，范围 `1..256`；不是数据库轮询器数量                                                                                                           |
| `compileflow.durable.worker.effect-concurrency`               |        `8` | 外部操作最大并行数，范围 `1..256`；不是数据库轮询器数量                                                                                                           |
| `compileflow.durable.outbox.concurrency`                      |        `2` | Outbox 最大并行投递数，范围 `1..256`；不是数据库轮询器数量                                                                                                        |
| `compileflow.durable.outbox.retry.initial-delay`              |       `1s` | 首次重试窗口上限；投递使用带完全抖动的有界指数退避                                                                                                                |
| `compileflow.durable.outbox.retry.max-delay`                  |       `1m` | 重试窗口上限；不得小于初始延迟，且不超过 1 小时                                                                                                                   |
| `compileflow.durable.outbox.retry.max-attempts`               |      `100` | 范围 `1..10000`；必须送达的 `WAIT_COMMITTED` 事件不会自动放弃                                                                                                     |
| `compileflow.durable.maintenance.interval`                    |       `1s` | 维护与调度扫描间隔，必须为正数且不超过 1 小时；每个运行时的初始扫描时间自动错开                                                                                   |
| `compileflow.durable.maintenance.batch-size`                  |      `100` | 范围 `1..1000`                                                                                                                                                    |
| `compileflow.durable.retention.terminal-run`                  |       关闭 | 可选的终态流程实例保留时长，必须为正数且不超过 3650 天；活动实例及仍有必达 Outbox 事件的实例不会被清理                                                            |
| `compileflow.durable.retention.unused-process`                |       关闭 | 可选的无引用流程定义保留时长，必须为正数且不超过 3650 天；启动流程时通过事务保护完整恢复数据                                                                      |
| `compileflow.durable.retention.consumed-occurrence`           |       关闭 | 可选的已消费等待或外部操作保留时长，必须为正数且不超过 3650 天；未完成操作及仍有待投递 Outbox 事件的记录不会被清理                                                |
| `compileflow.durable.retention.interval`                      |       `1h` | 有界 retention sweep 的正数间隔，不超过 1 天                                                                                                                      |
| `compileflow.durable.cache.runtime-max-size`                  |      `256` | 节点本地、可丢弃的 Durable 运行时缓存上限，范围 `1..10000`                                                                                                        |

执行容量与存储获取成本相互独立：存在积压的工作类型会扩展到配置的并发数；空闲时则收敛为单个自适应、
带抖动的探针。能力未就绪时的抑制逻辑属于运行时内部策略，不作为公开调优项。Outbox 重试会把带完全抖动的正延迟
持久化在指数增长且有上限的窗口内，因此接收端故障不会形成同步的固定间隔重试波。

租约应覆盖实测的 JVM 暂停时间与存储尾延迟之和；连接获取、网络、锁和语句超时应让失败的续租调用在租约三分之一时长内返回，从而在过期前保留下一次机会。工作节点停机时先停止领取任务并取消延迟探针，再按 `shutdown.timeout` 等待正在执行的工作。
预算耗尽后请求会被中断，并报告未退出的工作节点；这些节点退出前禁止重启。
关闭引擎时，先排空已接收的应用操作，再停止工作节点并清理资源。
存储调用（包括续租）必须自行设置 I/O 超时：`shutdown.timeout` 不限制整个关闭过程，也不能强行终止应用代码。
应将这些预算与宿主生命周期及容器终止宽限期对齐；Spring 生命周期超时不会强行中断阻塞的 Bean 清理方法。

存在 Micrometer 时，自动装配会注册只含有界 `operation/outcome` 标签的
`compileflow.durable.operations`，以及节点本地 `compileflow.durable.loaded.runtimes` 仪表。存在
Spring Boot Health 时，Durable 健康检查会探测所选存储的权威时间。启用的工作通道连续出现三次非预期内部故障时报告 `DEGRADED`；正在使用的续租通道连续两次失败，或两个派生周期内没有成功完成时，也会报告 `DEGRADED`。健康信息只包含有界、脱敏的计数、时间戳、数据陈旧程度与故障类型；工作节点故障日志会限流，续租故障通过指标和健康检查持续可见。某个流程缺少动作或外部操作能力，或者没有配置可选 Outbox 接收端，只会显示在分项详情中，不会直接将整个引擎判定为不健康。

`compileflow.durable` 下的未知字段会被拒绝。`Duration` 必须为整毫秒精度；所选存储实现的 Schema 缺失或不匹配时启动失败。嵌入式查询
使用类型化键集游标，不需要内核分页令牌密钥环。等待令牌是随机凭据；权威记录只持久化摘要，
有效的 Outbox 记录可能为崩溃安全投递临时保存原始令牌，必须按凭据保护。流程快照使用内核固定的
可移植值封装，不提供应用编解码或载荷加密 SPI。传输认证、授权、请求去重和
不透明分页令牌保护由外层适配器配置。

## Workbench Server

下列 `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` 变量构成由专用前缀管理的公开配置。独立的 `CONFIG_` 段可避免与自动注入的
`COMPILEFLOW_WORKBENCH_SERVER_SERVICE_HOST` 等服务链接变量冲突。启动初期的适配器只拒绝自有前缀下的未知名称，并按
操作系统环境变量的优先级转换已知名称。Spring Boot 不会从原始系统环境变量中枚举未知字段，但适配器生成的规范别名属性源仍接受严格绑定检查。适配器不定义默认值；不可变的 `CompileFlowWorkbenchServerProperties` 仍是默认值与校验规则的唯一来源。

| 属性                                                                    | 默认值             | 环境变量                                                                                                                                           |
| ----------------------------------------------------------------------- | ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.workbench.server.authentication.mode`                      | `API_KEY`          | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE`；`API_KEY` 或 `DISABLED`                                                                 |
| `compileflow.workbench.server.authentication.api-key`                   | `API_KEY` 模式必填 | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`；配置后须为 32..256 个 URL-safe ASCII 字符（`A-Z`、`a-z`、`0-9`、`.`、`_`、`~`、`-`） |
| `compileflow.workbench.server.authentication.service-principal`         | 必填               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL`；凭据所代表的稳定服务主体，1..128 个可见 ASCII 字符                         |
| `compileflow.workbench.server.http.max-request-size`                    | `10MB`             | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_HTTP_MAX_REQUEST_SIZE`；有效范围 `1B..100MB`                                                                  |
| `compileflow.workbench.server.database.provider`                        | `POSTGRESQL`       | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_PROVIDER`；在同一个可执行制品中选择匹配的 `POSTGRESQL` 或 `MYSQL` 持久化实现                         |
| `compileflow.workbench.server.database.migrate`                         | `false`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE`；显式允许本进程执行 Workbench 数据库变更脚本                                                |
| `compileflow.workbench.server.preview-execution.enabled`                | `false`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED`；设为 `true` 时开放受信任的草稿执行                                                |
| `compileflow.workbench.server.execution-log.max-query-rows`             | `10000`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS`；有效范围 `1..100000`                                                           |
| `compileflow.workbench.server.execution-log.purge-batch-size`           | `1000`             | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_PURGE_BATCH_SIZE`；单次清理请求按稳定顺序删除的行上限，范围 `1..10000`                          |
| `compileflow.workbench.server.async-invocation.concurrency`             | `4`                | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY`                                                                                 |
| `compileflow.workbench.server.async-invocation.dispatch-interval`       | `1s`               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_INTERVAL`                                                                           |
| `compileflow.workbench.server.async-invocation.lease-duration`          | `30s`              | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_DURATION`；续租节拍固定由三分之一时长推导。                                            |
| `compileflow.workbench.server.async-invocation.lease-recovery-interval` | `5s`               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_RECOVERY_INTERVAL`                                                                     |

Workbench 只使用一个产品命名空间：`default`。托管执行请求因此只接受版本或别名，不接受调用方选择
命名空间；响应和日志仍会记录实际命名空间。支持多个命名空间需要同时处理草稿标识、URL、授权、持久化和界面选择，不能仅增加一个配置开关。

草稿执行会在服务端以受信任代码真实运行提交的定义，既不是沙箱，也不只是校验接口。除非显式设置
`preview-execution.enabled=true`，该端点不会注册；`dev` 配置环境仅为本地开发显式开启。生产环境应保持关闭；确需使用时，网关必须只向受信任流程作者授予该能力，并且
服务端已经按最小权限限制宿主、网络、Spring Bean 与数据库访问。

本地执行尝试由范围为 `1..256` 的并发数限制，不另设公开的队列或派发批次容量配置。
派发、租约续期和过期恢复使用三个独立调度线程，避免慢扫描阻塞续租。部署运行时、控制面、对账器、异步调度器和
工作节点在 Spring Boot Web 生命周期窗口内按依赖顺序启动；工作节点进入运行状态前，所有调度周期均为空操作。收到
`ContextClosedEvent` 后，工作节点立即停止派发和过期租约恢复，但调度器会继续为本进程已经持有的执行尝试续租。Spring
Boot 随后关闭 HTTP 入站，并在标准 `spring.lifecycle.timeout-per-shutdown-phase` 预算内排空工作节点；工作节点停止后所有调度周期均为空操作，
Bean 销毁阶段再关闭调度器。这样既不会在排空期间领取新任务，也不会因为调度器提前关闭而重放仍在执行的调用。

异步队列是以数据库为权威的状态机。调度器只处理持久化 `available_at` 截止时间已经到达的记录。每次运行尝试都使用唯一
隔离令牌和可续租租约；只有令牌有效且租约未过期时才能提交完成结果，因此已被恢复的任务不会被旧工作节点
的迟到结果覆盖。重试延迟持久化到数据库，不通过占用工作线程休眠实现；多个服务节点统一使用数据库时钟。请求级
`maxAttempts` 范围为 `1..100`，`retryDelayMs` 范围为
`0..604800000` 毫秒。
`maxAttempts` 默认值为 `1`；重试必须显式开启，因为超时或响应丢失不能证明流程没有产生副作用。

配置多次尝试后，同一请求可能执行一次或多次，不保证外部操作只发生一次。包含外部操作的动作必须使用流程参数中的稳定业务键实现幂等。HTTP 字段及取值边界由
[Workbench Server OpenAPI 契约](specifications/workbench-server-openapi.md)定义。

认证采用安全默认值：默认 `API_KEY` 模式要求有效密钥。只有显式启用 `dev` 或 `test` 配置环境时才允许 `DISABLED`；无论认证模式如何，
`prod` 都不能与这两个本地配置环境同时启用。`dev` 使用 PostgreSQL；生产环境可通过文档化的数据库属性选择 PostgreSQL 或 MySQL。`dev` 提供本地 URL 和用户名默认值，但要求最终生效的
`spring.datasource.password` 非空，并在创建连接池之前完成校验。密码可来自任意 Spring 标准属性源，推荐使用环境变量
`SPRING_DATASOURCE_PASSWORD`。H2 仅用于测试，不会打入服务端可执行 JAR，因此未指定配置环境时不会回退到嵌入式数据库。

异步调用的积压保留在数据库。`async-invocation.concurrency` 限制全部本地执行尝试；投递查询按空闲执行槽位取数，满载时停止查询。执行器的有界交接缓冲不增加准入容量。分布式 Deploy 同样由 `VersionRuntimeManager` 保留等待需求，以 `runtime.installation-concurrency` 限制准入，不另设可配置执行器队列。

全部异步调用时长都必须是可表示为 Java `long` 的正整数毫秒值。租约时长至少为 `2ms`，
续租节拍固定为 `max(1ms, floor(leaseMillis / 3) ms)`，没有独立配置项。Workbench 不提供
服务端 CORS 配置；生产环境通过具备认证能力的网关提供单一浏览器源，不支持浏览器跨源直连服务端。生产数据库没有默认密码，使用
Spring 标准的
`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`。平台支持挂载 secret 时，生产环境应优先使用
密钥管理服务和 Spring Boot
`spring.config.import=configtree:/run/secrets/`，避免把长期凭据放在环境变量中。名为
`spring.datasource.password` 与 `compileflow.workbench.server.authentication.api-key` 的文件会直接绑定到
规范属性。环境变量仍适用于本地评估和能够安全注入环境变量的平台。

服务端在消息转换之前按照 `compileflow.workbench.server.http.max-request-size` 限制请求体；带 `Content-Length` 和
chunked/未知长度请求都受约束，超限返回 HTTP 413。同一个权威配置也同时设置 Servlet multipart 解析器的单文件与整请求上限，因此
XML 导入不存在第二套独立大小策略。这里不使用 Tomcat `maxPostSize` 充当通用限制，因为该参数只约束表单参数解析，不约束任意
JSON 请求体。

CSV 导出与灰度健康评估需要在内存中读取完整的执行日志样本。每次查询最多读取
`compileflow.workbench.server.execution-log.max-query-rows` 条；只要存在额外匹配行，请求就以 HTTP 422 和 RFC 9457 Problem
Detail（code 为 `EXECUTION_LOG_QUERY_LIMIT_EXCEEDED`）明确失败，不返回被截断或误导性的
结果。应先缩小时间范围或过滤条件，再考虑提高上限。硬上限用于防止一次同步请求让服务端承担无界的内存分析或导出任务。Workbench
监控聚合由数据库在指定时间窗口内分组计算，不受这个明细查询行数上限约束。

执行日志的 Retention 清理同样有界：单次 `POST /api/execution-logs/purge` 最多按稳定的 `(logged_at, id)` 顺序删除
`compileflow.workbench.server.execution-log.purge-batch-size` 条最老匹配记录。应重复请求直到返回 `hasMore=false`，并观察 WAL、Dead
Tuple、Lock 与 Autovacuum 等数据库指标，避免一次删除全部积压记录。

Flyway 是服务端应用唯一的数据库结构迁移工具。默认
`compileflow.workbench.server.database.migrate=false`，因此 DDL 不属于运行时身份；生产环境由独立授权的部署身份执行已提交的
Deploy V1 与 Workbench V1 数据库变更脚本。MySQL 开启二进制日志时，迁移须使用获准创建 V1 触发器的 DDL 管理员；仅有数据库结构
级 DDL 授权可能不足，不应全局放宽 `log_bin_trust_function_creators`。本地开发和 bundled Compose 显式设置
`database.migrate=true`。默认模式仍会在应用
就绪前校验 Flyway 校验和并拒绝所有待执行变更，不能关闭 `spring.flyway.enabled`。运行时 DML 身份因此需要只读访问
`cf_deploy_schema_history` 和 `cf_workbench_schema_history`，但不需要创建数据库结构或执行迁移 DDL 的权限。破坏性的 `clean` 已关闭；Hibernate 使用
`ddl-auto=validate`，数据库结构漂移会直接导致启动失败。默认服务还启用优雅停机（每个停机阶段 30 秒），且只暴露 Actuator `health`。
只有 `/actuator/health`、`/actuator/health/liveness` 和 `/actuator/health/readiness` 可匿名访问；健康组件、详情
与其他健康检查组仍被隐藏或要求认证。数据库连接池、HTTP 服务、Actuator 和日志调优继续使用 Spring 标准的
`spring.datasource.hikari.*`、`server.*`、`management.*` 和 `logging.*`，不创建重复的 `compileflow.workbench.server.*` 别名。
容器或 Pod 的终止宽限必须长于 Spring 生命周期与引擎执行器的实际关停预算；本地 Compose 按默认配置预留 75 秒。

## Workbench

Node 开发网关只是在回环地址上运行的草稿接口模拟服务。它只解析一次配置、拒绝所属前缀下的未知变量、始终绑定
`127.0.0.1`，并拒绝 `NODE_ENV=production`。它不提供上游 URL、认证、代理、队列或生产镜像。

| 变量                                        | 默认值     | 用途                                                                                          |
| ------------------------------------------- | ---------- | --------------------------------------------------------------------------------------------- |
| `COMPILEFLOW_DEV_GATEWAY_PORT`              | `3001`     | 回环 TCP 端口，范围 `1..65535`。                                                              |
| `COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL`         | `info`     | 结构化日志阈值：`error`、`warn`、`info` 或 `debug`。                                          |
| `COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES` | `10485760` | 草稿 JSON 请求体上限，范围 `1..10485760` 字节；默认值与 Workbench Server 的默认请求上限一致。 |

覆盖端口时，开发网关进程与 Vite 进程必须收到相同的
`COMPILEFLOW_DEV_GATEWAY_PORT`。Vite 只在 Node.js 开发代理配置中消费该值，不会把它暴露给浏览器代码。

Web 的 `VITE_COMPILEFLOW_*` 是公开 **构建时输入**，会嵌入 JavaScript 构建产物，不能保存凭据；变更后必须重新构建镜像。
该应用命名空间内的未知变量会使构建失败，其他工具仍可使用无关的 `VITE_*` 名称。浏览器固定使用同源
`/api` 和 `/health` 路径；端点路由属于生产网关、本地 nginx 或 Vite 开发代理的责任，不是 Web 配置。

| 变量                                     | 默认值                                   | 用途                                                                                                                        |
| ---------------------------------------- | ---------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- |
| `VITE_COMPILEFLOW_OPERATE_MODE`          | 开发为 `mock`，其他模式为 `real`         | `mock` 或 `real`；production 构建必须为 `real`。                                                                            |
| `VITE_COMPILEFLOW_DEBUG`                 | `false`                                  | 严格布尔值；启用脱敏诊断日志。                                                                                              |
| `VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES` | `mock` 模式为 `true`，其他模式为 `false` | 是否使用 Web 构建内置的 Learn 示例目录，而不是 Server 目录。开发网关不提供 Server 示例目录，因此 `mock` 模式必须为 `true`。 |

Workbench 展示版本在 Vite 构建时直接取自 `apps/web/package.json`。它属于产物身份，不是部署配置，因此不提供 `VITE_*` 覆盖入口。

Web 构建模式只有 `development`、`production` 与 `test`。包括 staging 在内的所有可部署环境统一运行同一份 production
构建，使用真实 API、仅同源的 `connect-src`、适用于生产环境的脚本策略，且不产出 source map。development 与 test 保留本地代理和诊断所需的
放宽策略。`staging` 是部署领域值，不是 Web 构建模式。

## Java 编程式配置

```java
JavaDiagnosticsConfig diagnostics = JavaDiagnosticsConfig.builder()
        .debugSymbols(JavaDiagnosticsConfig.DebugSymbols.LINES)
        .build();

ProcessEngineConfig config = ProcessEngineConfig.builder()
        .maxResidentRuntimes(4096)
        .runtimeLoadTimeout(Duration.ofSeconds(20))
        .javaDiagnostics(diagnostics)
        .build();

try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
    // Deploy and execute flows with this engine-owned immutable snapshot.
}
```

每份配置应对应一个长生命周期引擎，并在应用关闭时关闭引擎。同一 JVM 可以创建配置不同的多个引擎，它们不会共享可变配置。

## 支持的 Spring Bean 扩展点

Spring 自动配置支持通过以下 API 和存储 SPI 类型的 Bean 提供或替换组件：

| 领域         | 支持的 bean 类型                                                                                                                                                                                                                                                  |
| ------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Process 执行 | `ProcessDataMapper`、`ProcessEngineConfig`、`ProcessEngine`、`ProcessEventListener`、`TraceIdProvider`、`ProcessComponentResolver`、`ProcessContextPropagator`、`ProcessAliasRouteSource`、`ProcessAliasTargetingPolicy`、`ScriptExecutor`、`ProcessEnginePlugin` |
| Deploy       | `ProcessDeploymentService`、`DeploymentProjectionStore`、`ProcessArtifactSource`                                                                                                                                                                                  |
| Durable 执行 | `DurableProcessEngine`、`DurableOperatorService`、`DurableStore`、`DurableWaitDescriptionProvider`、`DurableVersionDefinitionSource`、`DurableAliasStateSource`、`DurableOutboxSink`                                                                              |

自定义 Bean 只影响对应组件。其他 `@ConditionalOnMissingBean` 检查、Bean 方法名、实现类、
执行器、调度器、协调器、仓储和生命周期适配器均为自动配置的内部实现，不属于受兼容性承诺保护的扩展点。
自定义组装必须依赖这里列出的 API/SPI 类型，而不是实现包。

具名 `RetryPolicy` 与 `FailureHandler` 通过 `ProcessEngineConfig.Builder` 或 `ProcessEnginePlugin` 注册，Plugin 本身
可以是 Spring Bean。名称由显式注册映射键决定；独立的 retry/failure Bean 不会按 Spring Bean 名自动注册。
