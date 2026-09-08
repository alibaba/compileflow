# 支持面清单

以下产品契约构成 CompileFlow 支持面。未列出的内容属于内部实现，即使 JVM 可见性为 `public`，应用也不应依赖。

## 稳定性分层

| 分层                    | 含义                                              |
| ----------------------- | ------------------------------------------------- |
| Supported               | 受支持的应用与集成接口；有正式文档和契约测试      |
| Supported by deployment | 在文档指定的 Workbench Server 部署方式下受支持    |
| Provider Preview        | 面向能力提供方的预览契约，可能独立于应用 API 演进 |
| Internal                | 内部实现；应用代码不应依赖                        |

## 目标平台基线

| 边界                       | 目标基线                                                                                                                                             |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java 产物与生成流程        | Java 17 源代码/API/字节码；受支持的 Java 17、21、25 LTS 均参与构建或验证，其中 Java 17 运行完整测试套件，Java 21/25 运行定向并发与动态代码运行时契约 |
| Spring 集成                | Spring Boot 4.1.1                                                                                                                                    |
| Engine API/Core/TBBPM/BPMN | 不要求 CompileFlow 自有部署数据库                                                                                                                    |
| Deploy 第一方事实源        | PostgreSQL 16.15/17.11/18.6 与 MySQL 8.4.7 契约矩阵；能力提供方共享与版本绑定的 JDBC 状态机，并分别负责方言选择与数据库变更脚本                      |
| Workbench 生产持久化       | 同一可执行产物支持 PostgreSQL 16.15/17.11/18.6 与 MySQL 8.4.7；Compose 推荐 PostgreSQL 18.6                                                          |
| Durable 第一方实现         | PostgreSQL 16.15/17.11/18.6 与 MySQL 8.4.7 契约矩阵；H2 仅用于测试                                                                                   |
| Workbench 构建             | Node.js 24 LTS 与 pnpm 11.11.0；Node 精确补丁版本固定在 `release-baselines.json`                                                                     |
| Workbench Java 运行时      | 固定的 Java 17 JDK 容器镜像；完整版本标签与 OCI 索引摘要记录在 `release-baselines.json`                                                              |

表中列出的是经过测试的支持基线。未列出的平台可能可以运行，但不属于受支持矩阵。精确版本与镜像摘要记录在根目录的
`release-baselines.json` 中。

## Java API 与 SPI（`compileflow-api`）

受支持类型位于 `com.alibaba.compileflow.engine` 及其已文档化的子包下：

API 与 SPI 位于同一个无运行时依赖的制品中：引擎配置使用 SPI 类型，SPI 回调又使用 API 值类型。二者通过包结构
区分职责。Core 提供引擎实现；格式模块提供语义编译器，框架适配器位于集成模块。

| 包 / 类型                                                                                                       | 职责                                                                            |
| --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `ProcessEngine`, `ProcessEngineFactory`                                                                         | 引擎生命周期与执行入口                                                          |
| `ProcessRef`, `ProcessDefinition`                                                                               | 已有流程身份与显式定义来源                                                      |
| `ProcessResult`, `ProcessError`, `ProcessExecution`, `ProcessExecutionOptions`                                  | 类型化执行结果、执行信息与请求选项                                              |
| `ProcessAliasTarget`、`AliasRoutingOptions`、`ProcessTrigger`、`ProcessDataMapper`、`ProcessExecutionException` | 执行辅助值、路由控制、类型映射与失败传播                                        |
| `ProcessDefinitionDigest`、`ProcessIdentifiers`、`ProcessText`                                                  | 稳定摘要与身份/文本校验契约                                                     |
| `ProcessRuntimeManager`, `ProcessToolingService`                                                                | 本地运行时生命周期与不执行流程的工具链                                          |
| `CompileFlowException`, `ErrorCode`, `ProcessModelType`                                                         | 根包错误分类与格式身份                                                          |
| `config.*`                                                                                                      | 不可变 `ProcessEngineConfig` 树与校验器                                         |
| `preflight.*`                                                                                                   | `ProcessPreflightOptions`, `ProcessPreflightReport`                             |
| `spi.*`                                                                                                         | `ProcessEnginePlugin`, `ProcessEnginePluginContext`, `ProcessComponentResolver` |
| `spi.event.*`                                                                                                   | 封闭的类型化 `ProcessEvent` 生命周期记录与 `ProcessEventListener`               |
| `spi.execution.*`                                                                                               | 重试策略、终态失败处理器、应用上下文传播及有界执行值                            |
| `spi.observability.TraceIdProvider`                                                                             | Trace ID 协作                                                                   |
| `spi.routing.*`                                                                                                 | 别名路由权威来源与路由绑定的具名目标选择契约                                    |
| `spi.script.*`                                                                                                  | `ScriptExecutor`、脚本程序、准备请求和与能力提供方无关的脚本失败分类            |

`ProcessEngineProvider` 是 API 与 Core 之间随版本绑定的引导契约，不是受支持的应用 SPI。
格式前端模块注册语义编译器，不会分别创建与格式绑定的引擎。

应用不应依赖 `com.alibaba.compileflow.engine.core`、格式解析器 AST、部署协调器、Spring 自动配置内部类型或服务端控制器类。

`compileflow-core` 默认注册 `java` 脚本语言。Java 脚本是可信进程内代码，只能用于可信定义；executor 实现仍属于
Core 内部类型。

## 部署 API 与 SPI（`compileflow-deploy-api`）

受支持的部署契约位于 `com.alibaba.compileflow.deploy.api`：

| 包 / 类型                             | 职责                                           |
| ------------------------------------- | ---------------------------------------------- |
| `ProcessDeploymentService`            | 不可变版本发布与带修订号前置条件的发布操作门面 |
| `command.*`                           | 已校验的版本发布、灰度、提升、中止和回滚命令   |
| `artifact.*`, `release.*`             | 可执行制品值与控制面发布审计、元数据值         |
| `routing.*`, `rollout.*`, `version.*` | 不可变路由与发布状态、约束、查询和分页         |
| `error.*`                             | 有界部署错误码与结构化异常上下文               |
| `observability.*`                     | 实例级部署计数器与稳定、低基数的有界维度       |
| `spi.ProcessArtifactSource`           | 不可变制品查询边界                             |

`compileflow-deploy-spi` 是与版本绑定的能力提供方契约。其 `DeployStore` 描述控制面所需的版本发布、发布操作、路由和
路由 Outbox 原子状态变更，但不暴露 JDBC 或 SQL。第一方 PostgreSQL 与 MySQL 实现共享一套与版本绑定的
JDBC 状态机，同时分别保留方言选择、数据库变更脚本和真实数据库契约证据；这不代表支持任意 JDBC 数据库。
其中 `projection.DeploymentProjectionStore` 也属于 **Provider Preview**，不是 Supported 应用 API。
它保留逐键线性一致的 CAS 与最终订阅收敛契约。远程实现必须正确处理订阅建立、重连、历史丢失、
并发写入和网络分区；内存测试工具不能代替这些验证。

## 部署线协议（`compileflow-deploy-protocol`）

受支持的线协议辅助类型位于 `com.alibaba.compileflow.deploy.protocol`，负责带架构版本的制品和路由载荷、解析器、
规范化有界 JSON 与投影键。协议模块依赖部署领域 API；领域 API 不反向依赖线格式。

应用不得依赖 Deploy 管理端、运行时和集成实现、仓储实现、Spring 组合根内部类型或服务端适配器。

Maven `tests` classifier 仅包含供 CompileFlow 自身测试套件使用的确定性内存夹具；它们不是生产 API、运行时适配器或降级实现。

发布元数据只承载描述信息。完整性前置条件使用
`PublishProcessVersionCommand.getExpectedArtifactDigest()`，不能通过元数据传递。`compileflow.` 元数据键
命名空间只保留给 `ReleaseMetadataKeys` 声明的框架键；应用使用自己的命名空间。发布元数据不投影到可执行制品载荷。

## Durable API、SPI 与交付面

`compileflow-durable-api` 下已文档化的以下类型是 `durable-strict@1` 的最终用户 Supported 契约：

| 包 / 类型                | 职责                                                                                       |
| ------------------------ | ------------------------------------------------------------------------------------------ |
| `DurableProcessEngine`   | 面向应用的执行入口，负责可选工作节点、运行时缓存与编译资源在本地节点上的生命周期           |
| `DurableOperatorService` | Timeline、Pause/Resume、Effect 裁决与 Outbox 管理                                          |
| `command.*`              | 带审计的 Pause/Resume、Effect 裁决与 Outbox 裁决 Operator 命令                             |
| `model.*`                | 流程实例身份与结果、类型化游标、控制、Timeline、Wait、Timer、Effect 和 Outbox 生命周期模型 |
| `effect.*`               | Effect 对账结果                                                                            |
| `error.*`                | 稳定 Durable 错误分类                                                                      |
| `validation.*`           | 供不可变 Durable API 值对象复用的校验契约                                                  |

`compileflow-durable-spi` 属于 Provider Preview，不与最终用户 API 共享同一级别的长期兼容承诺。它的 `DurableStore`
契约与数据库无关，但每个受支持的能力提供方都必须独立证明完整事务、并发、崩溃、数据库变更和保留语义。它还公开
`DurableWaitDescriptionProvider` 与 `DurableWaitDescriptionContext`、`DurableOutboxSink`，以及彼此分离的准入边界 `DurableAliasStateSource`、
`DurableVersionDefinitionSource`。
`DurableVersionDefinitionSource` 只在准入时提供缺失的权威精确版本副本；流程实例恢复不会调用它。
可选的具名 `ProcessAliasTargetingPolicy` 只能覆盖路由的稳定或候选目标；CompileFlow 协议固定的百分比分桶不可替换。
PostgreSQL 与 MySQL 的 SQL、数据库架构、索引和事务实现都是第一方能力提供方的实现细节，不是通用 JDBC 或方言抽象。
托管定义来源返回 `VersionDefinition`，其中包含类型化内联定义和精确的 `callBindings`。
调用绑定用于证明源码与子流程版本在准入时一致，不是第二套可变执行图。定义拥有模型类型；
精确版本调用可以跨格式前端，类路径调用继承调用方的格式前端。恢复使用已存储的 `processId` 语义，不查询准入来源。
Durable 通过同一语义执行后端接受文档定义的严格 TBBPM 和 BPMN 子集。

受支持的接入入口是：

- 纯 Java 节点本地生命周期：`compileflow-durable-runtime` 中的 `DurableProcessEngineConfig` 与 `DurableProcessEngineFactory`；
- 不绑定存储实现的应用与维护运行时：`compileflow-durable-spring-boot-starter` 加一个完整的 `DurableStore`；
- 第一方 PostgreSQL 发行入口：`compileflow-durable-spring-boot-starter-postgresql`；
- 第一方 MySQL 发行入口：`compileflow-durable-spring-boot-starter-mysql`；
- 应用能力与集成接口：`compileflow-durable-spi`。

嵌入式查询公开类型化键集游标；认证、授权、请求去重、审批和不透明分页令牌的编码、签名属于传输层或应用适配器。
可移植流程载荷只有一种由内核固定的编码；系统不提供应用编解码器或载荷保护 SPI。

除上述配置和工厂装配入口外，`compileflow-durable-runtime` 的包、PostgreSQL/MySQL 实现类、
`compileflow-durable-testkit`、Spring 自动配置组合 bean
和 Durable 数据库表都不是应用 API。第一方跨制品装配所需的 JVM `public` 可见性不会形成 Store 扩展契约。公共 Durable API
属于受支持界面；生产部署必须使用支持面清单列出的组合。

完整边界与维护规则见 [Durable 架构](durable-architecture.md)。

## Spring 配置

| 前缀                             | 归属                           | 说明                                            |
| -------------------------------- | ------------------------------ | ----------------------------------------------- |
| `compileflow.engine.*`           | 引擎实例                       | 严格绑定（`ignoreUnknownFields=false`）         |
| `compileflow.deploy.*`           | 部署控制/运行时                | 拓扑与角色开关均显式声明                        |
| `compileflow.durable.*`          | Durable 应用/维护运行时        | 默认关闭；角色、迁移、Worker 和留存必须显式选择 |
| `compileflow.workbench.server.*` | `compileflow-workbench-server` | 认证、HTTP 限制、持久化异步调用                 |

权威用户指南：[configuration.md](../configuration.md)。生成的 Spring 配置元数据必须与属性类保持一致。

## 部署行为

受支持的集成路径：Spring Boot 自动配置与 `ProcessDeploymentService`。

不变量（见 [Deploy 协议](../../../compileflow-deploy/docs/PROTOCOL.md)）：

- 控制面与运行时保持分离
- 已发布的 Deploy `ProcessArtifact` 与 exact Process Version 不可变
- PostgreSQL 与 MySQL 是不同的第一方事实源，在应用组合阶段只选择一次；共享 JDBC 状态机不会合并数据库架构、
  变更脚本、运行时选择或数据库专属证据
- `compileflow-deploy-spring-boot-autoconfigure` 只拥有数据库无关的 Deploy 组合
- `compileflow-deploy-spring-boot-starter` 是宿主自行提供完整 `DeployStore` 时使用的流程格式和存储实现中立入口
- `compileflow-deploy-spring-boot-starter-postgresql` 与 `compileflow-deploy-spring-boot-starter-mysql` 只选择存储实现；应用显式添加所需的流程格式模块
- `DeployStore` 是能力提供方的语义契约；SQL、数据库变更、数据库架构与物理事务策略均不是公共 API
- H2 只是测试替身，不是 Deploy 生产数据库
- 不允许生产环境静默回退到内存实现
- `DeploymentRuntime` 通过 Spring 生命周期启动，不通过公开的引导 API
- Deploy 协议代际是精确的；当前解析器只接受文档指定的架构版本，遇到未知代际时拒绝处理

## Server REST 与 Workbench Operate

| 面                    | 权威                                                                         |
| --------------------- | ---------------------------------------------------------------------------- |
| Workbench REST 线格式 | 匹配版本前后端契约，由仓库中的 Workbench Server OpenAPI 描述                 |
| 服务端实现            | `compileflow-workbench-server` 控制器与传输记录生成该描述                    |
| Web 契约              | 生成的 OpenAPI 类型、收窄后的领域类型、运行时校验与编译期一致性              |
| 开发网关/Web 环境变量 | `COMPILEFLOW_DEV_GATEWAY_*`（仅开发）与 `VITE_COMPILEFLOW_*`（仅公开构建时） |

浏览器绝不持有共享 API 密钥。具备认证能力的网关只在受保护的上游链路注入私有 Server 服务凭据；Node 开发网关不进入生产拓扑。
官方本地全栈路径：[DEPLOYMENT.md](../../../compileflow-workbench/DEPLOYMENT.md)。

Workbench `/api/**` 是官方前端与匹配 Server 版本之间的配套后端契约，不是跨次版本的第三方集成 ABI。
OpenAPI 仍是同版本线格式及其一致性的权威定义。第三方集成 API 不属于此支持面，必须使用显式版本化根路径并单独声明兼容等级。

## 明确不支持的能力

- 进程级可变配置或扩展注册表
- API 制品中的公开解析器或模型 AST 类型
- 面向整个类路径的注解扫描扩展发现
- 运行时插件安装、替换或卸载
- 通过 `ProcessEngine` 配置开关获得 Durable 语义
- 应用直接读写 Durable 数据库表或依赖 Durable 运行时实现包
- 把精确版本需求观测当作基础设施变更命令
- 把 Workbench 配套 `/api/**` 端点当作跨次版本的公开集成 API

## 相关文档

- [Durable 架构](durable-architecture.md)
- [API 参考](../api-reference.md)
- [配置指南](../configuration.md)
- [扩展指南](../extension-guide.md)
- [兼容性策略](../compatibility-policy.md)
