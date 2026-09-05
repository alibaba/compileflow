# CompileFlow 配置参考

CompileFlow 只在应用边界解析一次外部配置。配置完成类型转换和校验后形成不可变快照，再交给负责该配置的组件。核心执行路径
不会读取环境变量、JVM 系统属性或可变的全局配置解析器。

以下设置构成受支持配置面。默认值和约束属于契约；内部实现字段和未记录的别名不属于受支持配置。

## 边界与优先级

| 边界             | 命名空间                         | 生命周期              | 权威对象                                      |
|------------------|----------------------------------|-----------------------|-----------------------------------------------|
| Java 引擎        | `compileflow.engine.*`           | 单个 `ProcessEngine`  | 不可变 `ProcessEngineConfig`                  |
| 部署系统         | `compileflow.deploy.*`           | 控制面/数据面 bean    | 不可变 deploy 配置子树                        |
| Durable Runtime  | `compileflow.durable.*`          | Durable Runtime       | 不可变 `CompileFlowDurableProperties`         |
| Workbench Server | `compileflow.workbench.server.*` | Java 应用             | 不可变 `CompileFlowWorkbenchServerProperties` |
| 开发网关         | `COMPILEFLOW_DEV_GATEWAY_*`      | 仅回环的 Node.js mock | 冻结的 `DevGatewayConfig`                     |
| Web 应用         | `VITE_COMPILEFLOW_*`             | Vite 构建产物         | 冻结的 `AppBuildConfig`                       |

本地评估交付模板还接受少量插值输入，但它们不是第七个应用配置边界。Compose 将
`COMPILEFLOW_WORKBENCH_DATABASE_*` 转换为 PostgreSQL 与 Spring 标准 datasource 配置；回环 nginx 容器只接收由同一个
Workbench Server key 派生的
`COMPILEFLOW_WORKBENCH_LOCAL_GATEWAY_UPSTREAM_API_KEY`。这些值不会进入浏览器构建，也不会在 Java 或 Node 业务代码中形成第二套配置
resolver。

Spring Boot 使用标准 property source 优先级和 relaxed binding。未提供的值使用本文默认值；显式非法值、可枚举 Spring
配置源在受支持命名空间中的未知字段、受管 Server/开发网关/Web 变量中的未知名称或非法跨字段组合都会使启动或 Web
构建失败。Spring 会把原始 system environment 与 system property 排除在 unknown-field 枚举之外；其中已知的 relaxed-binding
名称仍可使用，但只有 Workbench Server 的
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` 受管别名额外提供环境变量白名单。engine、deploy 和 Durable 配置不支持
动态刷新；需要变更时应新建引擎或重启拥有该配置的组件。

在 AWS Lambda 或阿里云函数计算中，应在执行环境初始化阶段构造并保留 Java 引擎，让后续热调用复用已经校验的配置快照和已编译
runtime。环境变量只承载少量部署差异，两类平台的环境变量总量上限均为 4 KB；凭据应从平台秘密服务或执行角色获取，不能把环境变量当成秘密仓库。部署控制面和
Workbench Server 拥有 scheduler、lease、数据库协调和后台分发，应以长生命周期服务或容器部署，不能假定函数 handler
返回后这些生命周期仍会持续。

## 引擎

最小配置：

```yaml
compileflow:
  engine:
    enabled: true
    model-type: TBBPM
```

全部 engine `Duration` 配置统一使用整毫秒精度，并且必须能表示为 Java `long` 毫秒值。正时长至少为 `1ms`；明确允许非负的配置可以使用
`0ms`。精度更细或数值过大的配置会在校验阶段失败，不会被静默截断，也不会等到引擎构造时才暴露为算术异常。

### 请求级执行选项

`ProcessExecutionOptions` 是单次调用数据，不是外部应用配置。把关联 ID、不透明的确定性 routing key 和策略真正需要的最小自定义属性直接传给
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

引擎会取得不可变防御性快照。路由属性只能是字符串，并与流程变量和输出隔离；routing key 还不会进入日志、
事件、结果或持久化。不要把请求身份、alias 或 routing key 放进环境变量或流程变量 Map。`__cf_` 前缀保留给 Engine-owned
metadata，应用 routing attribute 与 root Process variable 都不能使用。

配置按三层展示：**Essential** 用于选择引擎契约，**Operational** 控制生产行为，**Advanced** 才暴露具体 executor
或诊断调优。Quickstart 只需要 Essential；生产 owner 通常增加 Operational；Advanced 应在有测量依据时再调整。

### 通用

| 层级        | 属性                                  | 默认值     | 约束/用途                                                                                                   |
|-------------|---------------------------------------|------------|-------------------------------------------------------------------------------------------------------------|
| Essential   | `compileflow.engine.enabled`          | `true`     | 是否启用引擎自动配置。                                                                                      |
| Essential   | `compileflow.engine.model-type`       | `TBBPM`    | `TBBPM` 或 `BPMN`。                                                                                         |
| Advanced    | `compileflow.engine.runtime-mode`     | `COMPILED` | 选择可丢弃的 runtime realization；`INTERPRETED` 用于诊断与差分验证。                                      |
| Operational | `compileflow.engine.call.max-depth`   | `32`       | 包含根流程在内的同步流程调用深度，范围 `1` 到 `256`；更深的嵌套或递归调用会在动作执行前失败并报告调用路径。 |
| Operational | `compileflow.engine.shutdown.timeout` | `15s`      | 公共操作排空与 executor 关闭共享的总预算；引擎会在内部预留强制终止阶段，至少 `1ms`。                       |

### Advanced Executor 调优

| 属性                                                                 | 默认值                     | 约束/用途                                                                                              |
|----------------------------------------------------------------------|----------------------------|--------------------------------------------------------------------------------------------------------|
| `compileflow.engine.executor.runtime-load.max-concurrency`                | `min(2, max(1, CPUs / 8))` | 本地 runtime load 的最大并发数，必须为正数。                                                         |
| `compileflow.engine.executor.runtime-load.max-pending`            | `4`                        | 等待 runtime-load 槽位的额外任务上限，必须为非负数；所有槽位忙时设为 `0` 会立即拒绝。             |
| `compileflow.engine.executor.action-timeout.max-concurrency`               | `max(4, CPUs)`             | 需要 timeout enforcement 的 action 最大并发数，必须为正数。                                           |
| `compileflow.engine.executor.action-timeout.max-pending`            | `32`                       | 等待 action-timeout 槽位的额外尝试数上限，必须为非负数；排队时间计入 action timeout。                 |
| `compileflow.engine.executor.action-timeout.cancellation-grace-period` | `2s`                       | timeout 或取消后等待 action 协作停止的最长时间；旧尝试未停止时 fail-closed，不启动可能重叠的新重试。    |
| `compileflow.engine.executor.parallel.cancellation-grace-period`     | `2s`                       | 并行分支失败后等待兄弟分支协作停止的最长时间；超时会报告执行中断并列出仍在运行的分支。                  |

可复用 Spring starter 根据 `model-type` 创建一个引擎。Workbench Server 始终同时承载一个 TBBPM 引擎和一个 BPMN 引擎；
`model-type` 仅选择传统的 primary `ProcessEngine`、
`ProcessRuntimeManager` 与 `ProcessToolingService` bean。其余引擎配置分别应用到两个实例，因此 executor 与 cache
限额是每个引擎的限额，而不是整个进程的合计值。
`COMPILED` 仍是默认值。`INTERPRETED` 是参考实现，不代表可以使用没有编译器的宿主：支持的 2.0 宿主仍需提供
`jdk.compiler`，用于定义校验和 prepare。

每个并行分支使用一个编排任务线程。Java 21 及以上使用虚拟线程；Java 17 使用有限、无队列的平台线程
执行器，内部 CPU 自适应上限为 `max(8, min(64, CPUs * 4))`；达到上限后由提交流程线程直接执行分支，从而提供背压，并避免把嵌套任务排在等待中的父分支之后。
这个兼容机制不是公开的并发控制项。未设置 timeout 的 action 在当前流程线程执行；设置 timeout 后使用有界 action-timeout 池。编译、action-timeout 与事件池过载时都会拒绝任务，绝不会回退到提交线程。preflight
使用独立的固定有界协调池，内部上限为两个 worker 和四个 pending task；排队时间计入端到端 timeout，过载时拒绝任务而不是由调用线程执行。
caller execution 只用于嵌套分支编排，以在不破坏 timeout 计时的前提下保证进展。

### 缓存

| 属性                                                             | 默认值 | 约束/用途                                               |
|------------------------------------------------------------------|--------|---------------------------------------------------------|
| `compileflow.engine.max-resident-runtimes`                       | `2048` | 该引擎缓存及 owner 保留的流程运行时总数硬上限，必须为正数。 |

Java Code 以 `Script(language="java", source)` 表示，并由 `compileflow-core` 默认支持。
其带类型 wrapper 在 Process runtime load 时编译，并按完整的声明 Script signature 由精确 runtime 持有。精确 source
仍是 Process Version 的事实来源；不存在持久化 bytecode cache 或执行期编译配置。

### 脚本

core 默认注册 QLExpress 4 和可信的 Java Code executor。QL 使用固定语言 profile：隔离宿主对象访问、固定安全函数集、一秒
deadline 和 10,000 的单维数组上限。这些是语言能力保证，不是应用调参项。内建 `qlexpress` 和 `java` 不允许同名覆盖；自定义 QL
方言、Groovy、MVEL 等语言必须使用独立的 `ScriptExecutor` 名称，provider 所有者负责安全、超时、缓存、ClassLoader 和生命周期策略。

### 流程定义加载

| 属性                                     | 默认值 | 约束/用途                                                                                       |
|------------------------------------------|--------|-------------------------------------------------------------------------------------------------|
| `compileflow.engine.definition.max-size` | `4MB`  | 单个 inline 或 classpath 流程定义允许的最大 UTF-8/二进制大小，范围为 `1B` 至硬性安全上限 `100MB`。 |

loader 最多读取 `max-size + 1` 字节，并在 XSD 校验前冻结一份不可变字节快照，因此 schema 校验和模型
解析看到的内容完全一致。解析到 HTTP、HTTPS、FTP 或 FTPS 的 classpath 资源会被拒绝；远程制品必须由具备认证、网络策略、超时和摘要校验的应用或部署 resolver
获取。

### Runtime 加载与 Java 诊断

| 属性                                                            | 默认值      | 约束/用途                                                    |
|-----------------------------------------------------------------|-------------|--------------------------------------------------------------|
| `compileflow.engine.runtime-load-timeout`                       | `10s`       | 同步调用等待 runtime load 的最长时间，至少 `1ms`。            |
| `compileflow.engine.java-diagnostics.debug.symbols`            | `LINES`     | `NONE`、`LINES` 或 `FULL`；本身不会写文件。                  |
| `compileflow.engine.java-diagnostics.debug.output-directory`   | 未设置      | 设置后按生成类名导出稳定的 `source/` 与 `metadata/` 目录树。 |
| `compileflow.engine.java-diagnostics.debug.bytecode-enabled`   | `false`     | 同时导出 `.class` 文件；启用时必须设置输出目录。             |

默认 runtime 加载全程在内存中完成。调试目录可能包含生成源码、脚本、表达式、常量和 class 文件，应使用操作系统权限或 ACL
保护配置根目录，并限制访问与保留周期，也不能放在 Web 可访问目录下。生成源码按包路径稳定写入 `source/`，可选字节码写入对应的
`classes/`，sidecar 属性写入 `metadata/`。再次编译同一个生成类时会原子替换这些文件，因此 IDE 断点始终绑定到同一个文件 URL。在
IntelliJ IDEA 中，将 `<output-directory>/source` 标记为 Sources Root，并在可执行代码行设置断点。Durable 生成类名包含标准化后的完整流程
code 和 program digest 前缀，源码头同时保留原始流程 code，便于直接识别。

每个 single-flight runtime load 只尝试一次 Java 编译。对同一份已解析源码快照而言，解析、代码生成、类解析与 Java
编译诊断都是确定性的，因此引擎不会盲目重试。流程定义或运行环境修复后，后续调用可以发起新一次编译；部署收敛与持久化异步调用分别拥有自己的有界重试策略。

`runtime-load-timeout` 只限制同步调用的等待时间。超时不会由任意一个等待者取消共享 runtime load；已接纳的任务会
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

| 层级        | 属性                                                              | 默认值  | 约束/用途                                                               |
|-------------|-------------------------------------------------------------------|---------|-------------------------------------------------------------------------|
| Operational | `compileflow.engine.observability.events.async`                   | `true`  | 是否使用有界、best-effort 的异步生命周期事件分发。                      |
| Operational | `compileflow.engine.observability.events.max-concurrency`             | `2`     | 生命周期事件分发的最大并发数，必须为正数。                              |
| Operational | `compileflow.engine.observability.events.max-pending`          | `16`    | 等待事件分发槽位的额外任务上限，必须为非负数；饱和时设为 `0` 会拒绝投递。  |
| Operational | `compileflow.engine.observability.mdc-propagation-enabled`        | `false` | 是否跨引擎 executor 边界复制 MDC。                                      |
| Advanced    | `compileflow.engine.plugins.discovery-enabled`                    | `false` | 仅在显式启用时通过 `ServiceLoader` 自动发现 `ProcessEnginePlugin`。     |
| Operational | `compileflow.engine.components.allowed-beans`                     | `[]`    | 暴露给流程定义的精确 Spring bean 名；空列表拒绝自动 bean 访问。         |

存在 `MeterRegistry` 时会自动注册 Micrometer binder。指标启停和过滤使用 Spring Boot 标准的
`management.metrics.enable.*`，不再创建重复的 CompileFlow 开关。程序化 `ProcessObservabilityConfig`
只包含事件分发与 MDC 传播行为。

事件监听器是构造期协作者，不是字符串配置。独立运行时通过
`ProcessEngineConfig.Builder.eventListener(...)` 注册；Spring starter 会按顺序收集
`ProcessEventListener` bean，并写入不可变引擎配置快照。

生命周期事件只用于 best-effort 观测，可能在异步队列饱和或关闭时被丢弃，不能作为业务正确性、审计、计费或可靠集成的权威。
ProcessEngine 执行应使用应用事务/Outbox，Durable 执行应使用 Durable Journal/Outbox。

Workbench Server 为终态执行日志有意将 `events.async` 覆盖为 `false`，使请求在返回前尝试写入数据库，
避免事件队列饱和造成样本偏差。监听器失败仍与流程结果隔离，因此这不构成持久化审计保证。

trace 捕获同样是构造期协作者。`TraceIdProvider` 只是轻量日志/事件关联钩子，不是 CompileFlow 自研 tracing 或 context
propagation 抽象。独立运行时可通过
`ProcessEngineConfig.Builder.traceIdProvider(...)` 设置一个 `TraceIdProvider`；Spring 存在应用声明的唯一
`TraceIdProvider` bean 时使用它，否则读取标准 MDC `traceId`。引擎只接受 1–128 字符且首尾无空白的标识；provider/MDC
返回空值、首尾带空白、超长值或 provider 失败时都不会归一化或截断错误 identity，而是继续回退到 32 位十六进制本地 ID。非法观测输入不能改变流程执行结果。

独立应用通过一个显式 `ProcessComponentResolver` 暴露组件；Spring 应用也可在
`compileflow.engine.components.allowed-beans` 中列出精确 bean 名。默认空列表拒绝全部自动 bean 访问；以
`&` 开头的 FactoryBean 解引用名、空值、重复值和带首尾空白的别名都会被拒绝。自定义 resolver 与非空 allowlist
互斥，避免任一策略被静默忽略。解析同时使用声明的组件名和所需 Java 类型；缺失或类型不兼容直接失败，不会转而实例化流程中声明的类型。

独立应用可通过 `ProcessEngineConfig.Builder.contextPropagator(...)` 设置一个 `ProcessContextPropagator`。它只在
ProcessEngine 自有线程切换时捕获应用环境上下文，并在结束时恢复 worker 原上下文；Durable 不会持久化或恢复该上下文。
classpath 存在 Micrometer Context Propagation 时，Spring 自动提供适配器，自定义 `ProcessContextPropagator` bean 会替换它。

其余类型化扩展点（`ScriptExecutor`、`RetryPolicy`、`FailureHandler`、`ProcessEnginePlugin`）同样是构造期协作者。
注册、排序、所有权和失败语义见[扩展指南](extension-guide.md)。

## 部署系统

可复用 starter 默认不启动部署系统，应用必须显式选择。CompileFlow Workbench Server 则在自身 `application.yml` 中显式
启用完整的嵌入式拓扑。

### 拓扑与进程职责

| 属性                                        | 默认值     | 约束/用途                                                                                                                  |
|---------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.enabled`                | `false`    | deployment 仓储、路由、控制面、数据面和指标的总开关。                                                                      |
| `compileflow.deploy.topology`               | `EMBEDDED` | `EMBEDDED` 在命令返回前激活已提交 Alias，并保留 outbox 恢复；`DISTRIBUTED` 使用同步通道。                                  |
| `compileflow.deploy.control-plane-enabled`  | `true`     | 当前进程承载发布命令、outbox 分发与分布式对账。                                                                            |
| `compileflow.deploy.runtime-worker-enabled` | `false`    | 仅在 `DISTRIBUTED` 拓扑下启用通道订阅、需求规划、产物解析与安装角色；Embedded 本地收敛由 `topology` 选择，不由该开关启用。 |

启用 deployment 后至少要选择一个进程职责。`EMBEDDED` 要求
`control-plane-enabled=true`、`runtime-worker-enabled=false`；`DISTRIBUTED`
支持纯控制面、纯 worker 和有意配置的组合进程。数据库型纯 worker 应设置
`control-plane-enabled=false`，此时即使存在 `DataSource` 也不会启动发布服务和 outbox worker。

只有 deployment 已启用且存在 `DataSource` 时才自动创建 JDBC 仓储，不存在内存回退。alias 状态与 routing 事件始终在同一个
JDBC 连接中提交；拓扑必需基础设施不完整时启动失败，不会选择非事务或断连路径。这些拓扑和一致性要求属于受支持的配置契约。

### 发布、产物与运行时

| 属性                                             | 默认值                 | 约束/用途                                                                                                                                    |
|--------------------------------------------------|------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.artifact.mode`               | `DATABASE`             | `DATABASE` 或 `CHANNEL`，发布端和运行时 resolver 共用。                                                                                      |
| `compileflow.deploy.artifact.key-prefix`         | `compileflow.process.` | 最长 128 个字符的可移植 channel 前缀，只允许 ASCII 字母、数字、`.`、`_`、`:` 与 `-`。                                                        |
| `compileflow.deploy.artifact.operation-timeout`  | `5s`                   | 每次 channel 产物读取或原子 CAS 的 deadline，至少 `1ms`。                                                                                    |
| `compileflow.deploy.runtime.failure-backoff`     | `5m`                   | 两种拓扑中安装失败后的重试等待时间，必须为正整毫秒；运行时使用单调时钟 deadline，因此还必须能表示为 Java `long` 纳秒区间。                   |
| `compileflow.deploy.runtime.convergence-timeout` | `30s`                  | 嵌入式按需准备允许请求等待的最长时间；请求超时后收敛仍继续，分布式 runtime 节点不使用该值。                                                  |
| `compileflow.deploy.runtime.concurrency`         | `1`                    | 分布式产物安装最大并发数，范围 `1..256`；Embedded 同步交付也不消费该值。                                                                    |
| `compileflow.deploy.runtime.queue-capacity`      | `256`                  | 分布式安装队列容量，范围 `1..10000`；它与 `concurrency` 一起限定分布式 runtime 进程准入的安装记录数。嵌入式准入上限由引擎 runtime-load 容量推导。 |

全部 deployment `Duration` 配置都以毫秒精度消费，因此必须是可表示为 Java `long` 的整毫秒值；
`runtime.failure-backoff` 还必须能表示为 `long` 纳秒区间，以支持单调时钟 deadline。正时长至少为 `1ms`；只有明确标注为非负的属性（例如
outbox `retention`）允许 `0ms`。精度更细或数值过大的值会在绑定阶段失败，不会被截断或进入运行时算术。

嵌入式拓扑要求 `DATABASE` artifact 模式，并能在内存状态丢失后从共享仓储恢复 alias、版本和源码。分布式 runtime 必须存在
`DeploymentSyncChannel`；`DATABASE` 模式还必须存在产物仓储，并且必须配置至少一个显式或派生路由订阅。缺少依赖时启动失败。

发布会插入一条不可变 `(namespace, code, version)` 事实，其中保存精确 UTF-8 源码、model type、SHA-256 digest、metadata、actor
和数据库时间。row 存在本身就是发布事实，不再维护可变发布状态或准备租约。同一 identity
与内容的重试返回原记录，不改写审计字段；不同内容直接冲突。在第一次仓储写入之前，发布必须解析精确源码，并通过对应格式的
schema 与模型结构校验；该校验不会生成、缓存或安装节点本地 runtime。编辑器或发布流水线需要生成代码编译诊断时，可以额外使用
strict options 调用
`ProcessToolingService.preflight(...)`。控制面上的这两类检查都不能证明具有不同应用 classpath 或 component 环境的运行节点已经
ready。发布不会修改 route。

Digest 是 version 与 artifact 的必填一等字段，不是可选 metadata 或功能开关。Artifact payload 解析、数据库解析和 runtime
load 都会校验 identity 与完整性。运行时安装在数据面信任边界执行真实引擎部署；安装失败会阻止该节点进入 local-ready 和激活对应
route。

Spring 在 ApplicationContext refresh 完成后通过 `SmartLifecycle` 启动 deploy runtime、outbox dispatcher 和
reconciler。数据面先于控制面后台任务启动；关闭时顺序相反，先停止发布，再关闭订阅与安装器。Bean 构造阶段不会启动工作线程。

### 路由、Outbox 与对账

| 属性                                               | 默认值                    | 约束/用途                                                                                                                                                                                                                              |
|----------------------------------------------------|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.routing.key-prefix`            | `compileflow.deployment.` | 最长 128 个字符的可移植 alias route 前缀，只允许 ASCII 字母、数字、`.`、`_`、`:` 与 `-`。                                                                                                                                              |
| `compileflow.deploy.routing.namespaces`            | `[default]`               | 用于派生键的非空、唯一、规范 namespace 列表。                                                                                                                                                                                          |
| `compileflow.deploy.routing.codes`                 | `[]`                      | 用于派生键的唯一规范流程 code。                                                                                                                                                                                                        |
| `compileflow.deploy.routing.aliases`               | `[production]`            | 用于派生 route 键的唯一规范 alias；显式空列表会关闭 alias 订阅。                                                                                                                                                                       |
| `compileflow.deploy.routing.operation-timeout`     | `5s`                      | 每次路由状态读取、原子 CAS 或订阅建立的 deadline，至少 `1ms`。                                                                                                                                                                         |
| `compileflow.deploy.outbox.dispatch-interval`      | `1s`                      | 至少 `1ms`。                                                                                                                                                                                                                           |
| `compileflow.deploy.outbox.dispatch-batch-size`             | `50`                      | 单个有界投递片段的记录上限，范围 `1..1000`；片段饱和时 Runtime 会短暂让步后自动续跑，因此它不是持续吞吐上限。                                                                                                                               |
| `compileflow.deploy.outbox.retention`              | `24h`                     | 不得为负；`0ms` 表示关闭有界清理。                                                                                                                                                                                                     |
| `compileflow.deploy.outbox.lease-duration`         | `1m`                      | 已领取交付记录的 authority TTL，至少 `1ms`；在 `EMBEDDED` 中必须长于 `runtime.convergence-timeout`，在 `DISTRIBUTED` 中必须长于 `routing.operation-timeout`。分布式交付保持幂等并受 lease token fencing；该租约应高于实测的 channel 端到端延迟。 |
| `compileflow.deploy.outbox.retry.initial-delay`    | `5s`                      | 首次重试窗口上限；交付使用带 Runtime 内部 full jitter 的有界指数退避。                                                                                                                                                                    |
| `compileflow.deploy.outbox.retry.max-delay`        | `5m`                      | jitter 窗口上限，必须不小于初始延迟。                                                                                                                                                                                                     |
| `compileflow.deploy.outbox.retry.max-attempts`     | `10`                      | 进入死信前允许的失败交付次数，必须为正数。                                                                                                                                                                                                |
| `compileflow.deploy.reconciliation.mode`           | `REPAIR`                  | `DISABLED`、`DETECT` 或 `REPAIR`；只对账派生的 routing/artifact projection。                                                                                                                                                            |
| `compileflow.deploy.reconciliation.interval`       | `1m`                      | 至少 `1ms`。                                                                                                                                                                                                                           |

Outbox dispatch 不提供静态启停开关或动态暂停状态。版本或 alias 状态与对应事件属于同一事务；如果继续接受
命令却关闭交付，就会制造陈旧执行视图。计划维护或事故停发应停止整个控制面进程，让命令入口与交付共同停止。dispatcher 通过持久化
`PROCESSING` lease 与 fencing token claim 记录，网络交付期间不持有数据库锁。交付是 at-least-once。`DISTRIBUTED` 通过精确内容的原子
CAS 单调推进 channel projection；迟到的低 revision 和重复交付都是成功的空操作。由于通知仍可能乱序，消费者还会独立过滤单调
revision。自定义 channel adapter 必须使用服务端原子 CAS，不能用客户端 read 后 write 模拟。健康度分别报告
pending、processing、过期 claim 与 dead-letter 数量。
`EMBEDDED` 把已提交事件应用到本地 `LocalRoutingState`，`DISTRIBUTED` 则把相同事件投影到
`DeploymentSyncChannel`。

### 分布式传输

CompileFlow 不通过 `compileflow.deploy.*` 选择远程传输。分布式进程必须提供且仅提供一个
`DeploymentSyncChannel` bean；提供方的连接、认证、namespace 与容量配置归属于该 adapter 自己的配置前缀。

只有真实后端同时提供精确当前值读取、包含 create-if-absent 的精确内容原子 CAS，以及最终可达的更新通知，adapter
才满足契约。Starter 不内置 Nacos adapter，因为 Nacos 在 CAS digest 缺失时执行 create-or-update，普通 client read 还可能返回本地
failover 或 snapshot。严格部署配置根会拒绝已删除的
`compileflow.deploy.sync.nacos.*`，不会静默忽略一个实际未生效的传输配置。

## Durable 严格能力档

Durable Runtime 组合通过 `compileflow-durable-spring-boot-starter` 显式启用，并要求存在 `DurableStore`。
第一方 PostgreSQL 发行入口是 `compileflow-durable-spring-boot-starter-postgres`。显式 definition 按已配置的
`ProcessEngineConfig` 直接通过 Core 加载。Version 或 Alias admission 发现托管 Version 尚无已存储 Process 副本时，
由 `DurableVersionDefinitionSource` 提供权威的精确 model type 与 inline definition；Run 恢复不会调用该 source。
Version、Alias Start 与显式 definition Start 相互分离；Alias admission 还需要由已提交 Deploy 状态支持的
`DurableAliasStateSource`。CompileFlow 先应用 route 显式绑定的具名 `ProcessAliasTargetingPolicy`，再使用固定百分比分桶；
Alias admission policy 不是另一个 Durable SPI。
应用能力与 Provider/Runtime 兼容性不进入 Durable
identity。需要把封闭的 Kernel Integration Event 可靠外发时才提供 `DurableOutboxSink`。Wait completion payload 必须是 exact
Process 声明变量的 typed partial update，并在提交前校验；`DurableWaitDescriptionProvider` 只允许从
`DurableWaitDescriptionContext` 传入的 process code、semantic digest、Wait node、分离 state 与 lexical bindings 为
`WAIT_COMMITTED` 生成确定性的只读描述属性，默认实现不增加属性。

| 属性                                                 |       默认值 | 契约                                                                                             |
|------------------------------------------------------|-------------:|--------------------------------------------------------------------------------------------------|
| `compileflow.durable.enabled`                          |      `false` | 显式启用 Durable 组装                                                                                                     |
| `compileflow.durable.runtime-mode`                     |   `COMPILED` | 为同一 Durable Machine 选择 `COMPILED` 或 `INTERPRETED` 可丢弃 runtime 实现；持久化行为与身份不变                        |
| `compileflow.durable-postgres.migrate`                 |      `false` | 为 true 时应用 PostgreSQL baseline；否则校验由外部迁移的 Schema                                                          |
| `compileflow.durable.worker.enabled`                   |       `true` | 启动自适应 Turn、Effect、maintenance 和可选 Outbox 工作                                                             |
| `compileflow.durable.worker.id`                        |     自动生成 | 所有 worker kind 共享的可选 Runtime identity，最多 96 字符                                                              |
| `compileflow.durable.worker.lease-duration`            |        `30s` | Runtime 级正整毫秒 token lease duration，不超过 1 小时；Run、Effect、Outbox 分别使用独立的有界批量续租 lane，周期固定由三分之一时长推导，不另设续租周期参数 |
| `compileflow.durable.worker.idle-poll-delay`        |      `100ms` | 初始空闲获取延迟，不超过 1 分钟；Runtime 自动指数退避并加 jitter                                                          |
| `compileflow.durable.worker.turn-fault-backoff`             |         `1s` | Turn 未预期故障后的重试延迟，不超过 1 小时                                                                            |
| `compileflow.durable.worker.turn-max-steps`            |      `10000` | 单次有界 Machine Turn 最多推进的 Process 节点数，范围 `1..1000000`；耗尽时持久化让出执行权，不把 Run 判为故障          |
| `compileflow.durable.worker.max-active-iterations`     |         `32` | 每个 Durable 并行集合范围最多保留的活动迭代数，范围 `1..64`；属于当前 Runtime 策略，不进入 Process 身份              |
| `compileflow.durable.worker.turn-concurrency`       |          `2` | Turn 最大并行执行数，范围 `1..256`；不是数据库 poller 数                                                              |
| `compileflow.durable.worker.effect-concurrency`        |          `8` | Effect 最大并行执行数，范围 `1..256`；不是数据库 poller 数                                                               |
| `compileflow.durable.outbox.concurrency`               |          `2` | Outbox 最大并行投递数，范围 `1..256`；不是数据库 poller 数                                                               |
| `compileflow.durable.outbox.retry.initial-delay`       |         `1s` | 首次重试窗口上限；投递使用有界指数 full jitter                                                                           |
| `compileflow.durable.outbox.retry.max-delay`           |         `1m` | 重试窗口上限；不得小于初始延迟，且不超过 1 小时                                                                           |
| `compileflow.durable.outbox.retry.max-attempts`        |        `100` | 范围 `1..10000`；必达的 `WAIT_COMMITTED` authority delivery 永不自动 abandon                                          |
| `compileflow.durable.maintenance.interval`             |         `1s` | 正数有界 maintenance/scheduler sweep 间隔，不超过 1 小时；每个 Runtime 的初始 phase 自动错开                             |
| `compileflow.durable.maintenance.batch-size`           |        `100` | 范围 `1..10000`                                                                                                           |
| `compileflow.durable.retention.terminal-run`           |         关闭 | 可选正数终态 Run retention，不超过 3650 天；active Run 和仍有必达 Outbox 待投递的 Run 永不入选                          |
| `compileflow.durable.retention.unused-process`         |         关闭 | 可选正数无 Run 引用 Process Definition retention，不超过 3650 天；Start 以事务保护完整 recovery set                |
| `compileflow.durable.retention.consumed-occurrence`    |         关闭 | 可选正数已消费 Wait/Effect retention，不超过 3650 天；未解析工作与仍有待投递 Outbox 的 occurrence 不会删除             |
| `compileflow.durable.retention.interval`               |         `1h` | 有界 retention sweep 的正数间隔，不超过 1 天                                                                              |
| `compileflow.durable.cache.runtime-max-size`           |        `256` | 有界、节点本地、可丢弃的 Durable runtime cache，范围 `1..10000`                                                         |

执行容量与 PostgreSQL 获取成本相互独立：有 backlog 的 work kind 会扩展到配置的 concurrency；空闲时则收敛为单个自适应、
带 jitter 的探针。capability-not-ready 抑制属于 Runtime 内部策略，不作为公开调优项。Outbox 重试把正数 full-jitter 延迟
持久化在指数增长且有上限的窗口内，因此 sink 故障不会形成同步的固定间隔重试波。

Lease 应覆盖实测 JVM Pause 与 Store Tail Latency 之和；连接获取、网络、锁和 Statement Timeout 应让失败的续租调用在推导出的
三分之一周期内返回，从而在过期前保留下一次机会。优雅停机先停止 Claim 并取消延迟探针，再无中断地排空正在执行的工作；
外层期限统一由 `spring.lifecycle.timeout-per-shutdown-phase` 与容器终止宽限期控制。

存在 Micrometer 时，自动装配注册只含有界 `operation/outcome` tags 的
`compileflow.durable.operations`，以及节点本地 `compileflow.durable.loaded.runtimes` gauge。存在
Spring Boot Health 时，Durable contributor 探测 PostgreSQL authority time。启用的 Worker lane 连续出现三次非预期
machinery 故障时报告 `DEGRADED`；Active Lease-Renewal lane 连续两次失败，或两个派生周期内没有成功完成时即报告
`DEGRADED`。Health 只携带有界、脱敏的计数、时间戳、staleness 与故障类型；Worker 故障日志会限流，续租故障通过 Metrics 与
Health 持续可见。某个流程的 Action/Effect
capability 缺失或未配置可选 Outbox sink 只作为分项详情，不会让整个 Engine unhealthy。

`compileflow.durable` 下的未知字段会被拒绝。Duration 必须为整毫秒精度；PostgreSQL Schema 缺失时启动失败。Embedded Query
使用 typed keyset cursor，不需要 Kernel page-token keyring。Wait token 是随机 capability，只持久化其摘要。Process snapshot
使用 Kernel 固定 portable-value envelope，刻意不提供应用 codec 或 payload-encryption Provider SPI。传输认证、授权、请求去重与
opaque page-token 保护由外层 adapter 配置。

## Workbench Server

下列可读的 `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` 变量构成显式、由前缀所有者管理的 API。专用的 `CONFIG_` 段可避免与
Kubernetes 注入的
`COMPILEFLOW_WORKBENCH_SERVER_SERVICE_HOST` 等 service-link 变量冲突。启动早期 adapter 只会拒绝自有前缀内的未知名称，并以
OS environment 优先级翻译已知名称。Spring Boot 不从原始 system environment 枚举未知字段，而 adapter 生成的规范 alias
source 仍接受严格绑定检查。adapter 不定义默认值；不可变 `CompileFlowWorkbenchServerProperties` schema 仍是默认值与校验规则的唯一事实源。

| 属性                                                                    | 默认值             | 环境变量                                                                                                                                                  |
|-------------------------------------------------------------------------|--------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.workbench.server.authentication.mode`                      | `API_KEY`          | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE`；`API_KEY` 或 `DISABLED`                                                                        |
| `compileflow.workbench.server.authentication.api-key`                   | `API_KEY` 模式必填 | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`；配置后须为 32..256 个 URL-safe ASCII 字符（`A-Z`、`a-z`、`0-9`、`.`、`_`、`~`、`-`）        |
| `compileflow.workbench.server.authentication.service-principal`         | 必填               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL`；凭据所代表的稳定服务 actor，1..128 个可见 ASCII 字符                              |
| `compileflow.workbench.server.http.max-request-size`                    | `10MB`             | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_HTTP_MAX_REQUEST_SIZE`；有效范围 `1B..100MB`                                                                         |
| `compileflow.workbench.server.database.migrate`                         | `false`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE`；显式授权本进程应用 Deploy V1 与 Workbench V2 migration                                           |
| `compileflow.workbench.server.preview-execution.enabled`                | `false`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED`；设为 `true` 时开放受信任的草稿执行                                                       |
| `compileflow.workbench.server.execution-log.max-query-rows`             | `10000`            | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS`；有效范围 `1..100000`                                                                  |
| `compileflow.workbench.server.execution-log.purge-batch-size`           | `1000`             | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_PURGE_BATCH_SIZE`；单次清理请求按稳定顺序删除的行上限，范围 `1..10000`                              |
| `compileflow.workbench.server.async-invocation.concurrency`             | `4`                | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY`                                                                                        |
| `compileflow.workbench.server.async-invocation.queue-capacity`          | `256`              | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_QUEUE_CAPACITY`                                                                                     |
| `compileflow.workbench.server.async-invocation.dispatch-batch-size`     | `50`               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_BATCH_SIZE`；单个有界投递片段的记录上限，有积压时自动追赶。                                                |
| `compileflow.workbench.server.async-invocation.dispatch-interval`       | `1s`               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_INTERVAL`                                                                                  |
| `compileflow.workbench.server.async-invocation.lease-duration`          | `30s`              | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_DURATION`；续租节拍固定由三分之一时长推导。                                                    |
| `compileflow.workbench.server.async-invocation.lease-recovery-interval` | `5s`               | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_RECOVERY_INTERVAL`                                                                            |

Workbench 2.0 只拥有一个产品 namespace：`default`。托管执行请求因此只接受 version 或 Alias，不接受调用方选择
namespace；响应和日志仍保留实际 namespace 作为归因事实。多 namespace 必须同时设计草稿标识、URL、授权、持久化和 UI
选择，不能靠一个孤立配置开关声称支持。

草稿 preview 会把提交的定义作为受信任代码在 Server 上真实执行，它既不是沙箱，也不是只校验接口。除非显式设置
`preview-execution.enabled=true`，该端点不会注册；`dev` profile 仅为本地开发显式开启。生产环境应保持关闭，除非网关只向受信任流程作者授予该能力，并且
Server 已按最小权限限制宿主、网络、Spring Bean 与数据库访问。

worker 线程范围为 `1..256`，本地队列范围为 `1..10000`，单次派发批次范围为 `1..1000`，且批次不能超过本地 worker
与队列总容量。派发、租约续期和过期恢复使用三个独立调度线程，避免慢扫描阻塞续租。部署数据面、控制面、对账器、异步 scheduler 和
worker 在 Spring Boot Web 生命周期窗口内按依赖顺序启动；worker 进入 running 前，所有调度周期均为空操作。收到
`ContextClosedEvent` 后，worker 立即停止派发和过期租约恢复，但 scheduler 会继续为本进程已经持有的 attempt 续租。Spring
Boot 随后关闭 HTTP 入站，并在标准 `spring.lifecycle.timeout-per-shutdown-phase` 预算内排空 worker；worker 停止后所有调度周期均为空操作，
bean 销毁阶段再关闭 scheduler。这样既不会在 drain 期间领取新任务，也不会因为 scheduler 提前关闭而重放仍在执行的调用。

异步队列是以数据库为权威的状态机。调度器只处理持久化 `available_at` 截止时间已经到达的记录。每次运行尝试都使用唯一
fencing token 和可续租 lease；只有当前 token 且 lease 未过期时才能提交完成结果，因此已被恢复的任务不会被旧 worker
的迟到结果覆盖。重试延迟持久化到数据库，不通过占用 worker 线程 sleep 实现；多 Server 节点统一使用数据库时钟。请求级
`maxAttempts` 范围为 `1..100`，`retryDelayMs` 范围为
`0..604800000` 毫秒。
`maxAttempts` 默认值为 `1`；重试必须显式开启，因为超时或响应丢失不能证明流程没有产生副作用。

配置多次尝试后，该机制提供 at-least-once 执行，不承诺外部副作用 exactly-once。包含外部副作用的 action 必须使用流程参数
中的稳定业务键实现幂等。HTTP 字段及取值边界由
[Workbench Server OpenAPI 契约](../specs/openapi/README.md)定义。

认证默认 fail-closed：默认 `API_KEY` 模式要求有效 key。只有显式启用 `dev` 或 `test` profile 时才允许 `DISABLED`；无论认证模式如何，
`prod` 都不能与这两个本地 profile 同时启用。开发与生产均使用 PostgreSQL；dev profile 提供本地 URL 和用户名默认值，但要求最终生效的
`spring.datasource.password` 非空，并在创建连接池之前完成校验。密码可来自任意 Spring 标准属性源，推荐使用环境变量
`SPRING_DATASOURCE_PASSWORD`。H2 仅为 test scope，且不会打入 Server 可执行 JAR，因此无 profile 的应用不存在嵌入式数据库回退。

全部 async invocation 时长都必须是可表示为 Java `long` 的正整毫秒值，续租间隔不得超过租约时长的一半。Workbench 不提供
Server CORS 配置；生产环境通过具备认证能力的网关提供单一浏览器 origin，不支持浏览器跨 origin 直连 Server。生产数据库没有默认密码，使用
Spring 标准的
`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`。平台支持挂载 secret 时，生产环境应优先使用
secret manager 加 Spring Boot
`spring.config.import=configtree:/run/secrets/`，避免把长期凭据放在环境变量中。名为
`spring.datasource.password` 与 `compileflow.workbench.server.authentication.api-key` 的文件会直接绑定到
规范属性。环境变量仍适用于本地评估和能够安全注入环境变量的平台。

Server 在消息转换之前按照 `compileflow.workbench.server.http.max-request-size` 限制请求体；带 `Content-Length` 和
chunked/未知长度请求都受约束，超限返回 HTTP 413。同一个权威配置也同时设置 Servlet multipart 解析器的单文件与整请求上限，因此
XML 导入不存在第二套独立大小策略。这里不使用 Tomcat `maxPostSize` 充当通用限制，因为该参数只约束表单参数解析，不约束任意
JSON 请求体。

CSV 导出与金丝雀健康评估要求在内存中得到完整的执行日志样本。每次查询最多读取
`compileflow.workbench.server.execution-log.max-query-rows` 条；只要存在额外匹配行，请求就以 HTTP 422 和 RFC 9457 Problem
Detail（code 为 `EXECUTION_LOG_QUERY_LIMIT_EXCEEDED`）明确失败，不返回被截断或误导性的
结果。应先缩小时间范围或过滤条件，再考虑提高上限。硬上限用于防止一次同步请求把 Server 变成无界的内存分析或导出任务。Workbench
监控聚合由数据库在显式时间窗口内分组计算，不受这个行物化上限约束。

执行日志的 Retention 清理同样有界：单次 `POST /api/execution-logs/purge` 最多按稳定的 `(logged_at, id)` 顺序删除
`compileflow.workbench.server.execution-log.purge-batch-size` 条最老匹配记录。应重复请求直到返回 `hasMore=false`，并观察 WAL、Dead
Tuple、Lock 与 Autovacuum，而不是执行一次无界追赶删除。

Flyway 是 server 应用唯一的 schema 迁移权威。默认
`compileflow.workbench.server.database.migrate=false`，因此 DDL 不属于 Runtime identity；生产由独立授权的部署身份应用已提交的
Deploy V1 与 Workbench V2 migration。本地开发和 bundled Compose 显式设置 `database.migrate=true`。默认模式仍会在应用
ready 前校验 Flyway checksum 并拒绝所有 pending migration，不能关闭 `spring.flyway.enabled`。runtime DML 身份因此需要只读访问
`flyway_schema_history`，但不需要 schema 创建或 migration DDL 权限。破坏性的 `clean` 已关闭；Hibernate 使用
`ddl-auto=validate`，schema 漂移会直接导致启动失败。默认服务还启用优雅停机（每个停机阶段 30 秒），且只暴露 Actuator `health`
。只有 `/actuator/health`、`/actuator/health/liveness` 和 `/actuator/health/readiness` 可匿名访问；health component、detail
与其他 health group 仍被隐藏或要求认证。数据库连接池、HTTP server、Actuator 和日志调优继续使用 Spring 标准的
`spring.datasource.hikari.*`、`server.*`、`management.*` 和 `logging.*`，不创建重复的 `compileflow.workbench.server.*` 别名。
容器或 Pod 的终止宽限必须长于 Spring lifecycle 与引擎 executor 的实际关停预算；本地 Compose 按当前默认值预留 75 秒。

## Workbench

Node 开发网关只是回环地址上的 preview mock。它只解析一次配置、拒绝所属前缀下的未知变量、始终绑定
`127.0.0.1`，并拒绝 `NODE_ENV=production`。它没有 upstream URL、认证、代理、队列或生产镜像。

| 变量                                        | 默认值     | 用途                                                                                             |
|---------------------------------------------|------------|--------------------------------------------------------------------------------------------------|
| `COMPILEFLOW_DEV_GATEWAY_PORT`              | `3001`     | 回环 TCP 端口，范围 `1..65535`。                                                                 |
| `COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL`         | `info`     | 结构化日志阈值：`error`、`warn`、`info` 或 `debug`。                                             |
| `COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES` | `10485760` | preview JSON 请求体上限，范围 `1..10485760` 字节；默认值与 Workbench Server 的默认请求上限一致。 |

覆盖端口时，开发网关进程与 Vite 进程必须收到相同的
`COMPILEFLOW_DEV_GATEWAY_PORT`。Vite 只在 Node.js 开发代理配置中消费该值，不会把它暴露给浏览器代码。

Web 的 `VITE_COMPILEFLOW_*` 是嵌入 JavaScript bundle 的公开 **构建时输入**
，不能保存秘密；变更后必须重新构建镜像。该应用命名空间内的未知变量会使构建失败，其他工具仍可使用无关的 `VITE_*` 名称。浏览器固定使用同源
`/api` 和 `/health` 路径；端点路由属于生产网关、本地 nginx 或 Vite 开发代理的责任，不是 Web 配置。

| 变量                                     | 默认值                                   | 用途                                                                                                                        |
|------------------------------------------|------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|
| `VITE_COMPILEFLOW_OPERATE_MODE`          | 开发为 `mock`，其他模式为 `real`         | `mock` 或 `real`；production 构建必须为 `real`。                                                                            |
| `VITE_COMPILEFLOW_DEBUG`                 | `false`                                  | 严格布尔值；启用脱敏诊断日志。                                                                                              |
| `VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES` | `mock` 模式为 `true`，其他模式为 `false` | 是否使用 Web 构建内置的 Learn 示例目录，而不是 Server 目录。开发网关不提供 Server 示例目录，因此 `mock` 模式必须为 `true`。 |

Workbench 展示版本在 Vite 构建时直接取自 `apps/web/package.json`。它属于产物身份，不是部署配置，因此不提供 `VITE_*` 覆盖入口。

Web 构建模式只有 `development`、`production` 与 `test`。包括 staging 在内的所有可部署环境统一运行同一份 production
构建，使用真实 API、仅同源的 `connect-src`、适用于生产环境的脚本策略，且不产出 source map。development 与 test 保留本地代理和诊断所需的
放宽策略。`staging` 是部署领域值，不是 Web 构建模式。

## 构建与交付工具链

构建和交付输入遵守与运行配置相同的单一来源与 fail-fast 原则：

- Java 源码、发布字节码与动态流程字节码使用 Java 17。同一份产物在最新补丁版本的 Java 17、21 与 25 LTS JDK 上执行强制兼容验证。Maven
  `release=17` 与运行时编译器的 `--release 17` 边界共同阻止误用更高版本 API。Java 21 及以上使用虚拟线程编排，Java 17
  使用无队列的平台线程实现。Server 镜像采用 Java 17 产品基线，所有运行镜像都必须包含标准 `jdk.compiler` 模块。Maven
  Wrapper 固定 Maven 3.9.16，并校验 distribution 与 wrapper JAR 的校验和。
- `.node-version` 固定 Node.js 24.18.0，根 `packageManager` 固定 pnpm 11.11.0，pnpm 的 `nodeVersion` 使用同一 Node
  目标校验新依赖；package `engines`、CI 与 Docker 构建由自动检查保持一致。
- pnpm 强制 engine 范围、关闭 peer 自动安装，不使用宽泛 `ignoreMissing` 名单，并对缺失或不兼容的必需 peer 直接失败；同时通过
  `minimumReleaseAge=1440` 对发布不足一天的普通依赖执行冷静期，registry 缺失发布时间元数据时同样失败。必需 peer 必须直接声明，可选
  peer 必须逐项审查。
- 依赖 lifecycle script 默认禁止，只有 `allowBuilds` 中记录审查决定后才可执行；当前仅允许构建必需的 `esbuild` 二进制校验脚本。
- Compose 默认使用 PostgreSQL 18.6；数据库与 API key secret 在 Compose 插值阶段强制提供，不设置源码默认值。CI 还会在
  PostgreSQL 16.15、17.11 上验证 Deploy 与 Workbench 契约。

这些是仓库级契约，不是应用运行参数。升级时必须同时修改权威版本源和自动一致性检查。

## Java 编程式配置

```java
JavaDiagnosticsConfig diagnostics = JavaDiagnosticsConfig.builder()
        .debugSymbols(JavaDiagnosticsConfig.DebugSymbols.LINES)
        .build();

ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
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

只有下列面向应用的 API 与 provider SPI bean 类型属于 Supported Spring 组装扩展点：

| 领域 | 支持的 bean 类型 |
| --- | --- |
| Process 执行 | `ProcessDataMapper`、`ProcessEngineConfig`、`ProcessEngine`、`ProcessEventListener`、`TraceIdProvider`、`ProcessComponentResolver`、`ProcessContextPropagator`、`ProcessAliasRouteSource`、`ProcessAliasTargetingPolicy`、`ScriptExecutor`、`RetryPolicy`、`FailureHandler`、`ProcessEnginePlugin` |
| Deploy | `ProcessDeploymentService`、`DeploymentSyncChannel`、`ProcessArtifactSource` |
| Durable 执行 | `DurableProcessEngine`、`DurableOperatorService`、`DurableStore`、`DurableWaitDescriptionProvider`、`DurableVersionDefinitionSource`、`DurableAliasStateSource`、`DurableOutboxSink` |

定义其中一种 bean 会替换或提供该精确组装职责。其他 `@ConditionalOnMissingBean` 检查、bean 方法名、实现类、
executor、scheduler、coordinator、repository 与 lifecycle adapter 都是自动配置实现细节，不属于兼容扩展点。
自定义组装必须依赖这里列出的 API/SPI 类型，而不是实现包。
