# 支持面清单

以下产品契约构成 CompileFlow 支持面。未列出的内容属于内部实现，即使 JVM 可见性为 `public`，应用也不应依赖。

英文页是规范版本；中文翻译、用户指南和 ADR 必须与其保持一致。

## 稳定性分层

| 分层                    | 含义                                                                         |
|-------------------------|------------------------------------------------------------------------------|
| Supported               | 应用与集成代码可安全使用；已文档化；由契约测试覆盖                           |
| Supported by deployment | 在文档化的 Workbench/Server 拓扑下运行时可安全使用                           |
| Developer Preview       | 可按文档评估；行为与发布边界显式记录，但稳定兼容和 Production Ready 尚未承诺 |
| Provider Preview        | 面向应用能力与集成 Provider 的预览契约；可能独立于最终用户 API 演进         |
| Internal                | 实现细节；应用代码不应依赖                                                   |

## 目标平台基线

| 边界                                             | 目标基线                                                                         |
|--------------------------------------------------|----------------------------------------------------------------------------------|
| Java 产物与生成流程                              | Java 17 source/API/bytecode；全部 LTS JDK 构建，Java 17 跑完整套件，Java 21/25 跑定向并发与动态代码运行时契约 |
| Spring 集成                                      | Spring Boot 4.1.1                                                                |
| Engine API/Core/TBBPM/BPMN                       | 不要求 CompileFlow 自有部署数据库                                                |
| Deploy 第一方事实源                              | PostgreSQL 16.15、17.11、18.6 契约矩阵；JDBC 是访问机制，不代表支持任意数据库    |
| Workbench 生产持久化                             | PostgreSQL 16.15、17.11、18.6 契约矩阵；捆绑部署与新部署推荐版本均为 18.6        |
| Durable PostgreSQL 实现（Developer Preview）     | PostgreSQL 16.15、17.11、18.6 契约矩阵；H2 仅为 test scope，不是受支持部署数据库 |
| Workbench 构建                                   | Node.js 24.18.0 与 pnpm 11.11.0                                                  |
| Workbench Java runtime                          | Temurin 官方 `17-jdk-noble` 通道；完整版本标签与 OCI index digest 固定在 `release-baselines.json` |

这些值定义 2.0 的发布目标。只有对应的同 Commit Gate 产生非跳过证据后，才能成为发布声明；列出版本本身不代表开放 Gate
已经完成。未列出的平台可能可以运行，但不属于受支持矩阵。当前精确版本和镜像 digest 统一记录在根目录
`release-baselines.json`；定时与发布日 baseline checker 会将其与官方上游事实比较。对 Java 容器而言，“新鲜”表示固定镜像的
digest 仍与 Temurin 官方容器通道的当前 digest 相同。独立 Adoptium 二进制即使先发布，在进入官方多架构容器通道前也不会阻断
容器交付。

## Java API 与 SPI（`compileflow-api`）

受支持类型位于 `com.alibaba.compileflow.engine` 及其已文档化的子包下：

API 与 SPI 位于同一个无运行时依赖的 artifact：引擎配置使用 SPI 类型，SPI 回调又使用 API 值类型。二者通过 package
区分职责。provider 实现位于格式模块，框架适配器位于集成模块。

| 包 / 类型                                                                                | 职责                                                                                                     |
|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `ProcessEngine`, `ProcessEngineFactory`                                                  | 引擎生命周期与执行入口                                                                                   |
| `ProcessRef`, `ProcessDefinition`                                                        | 已有流程身份与显式定义来源                                                                               |
| `ProcessResult`, `ProcessError`, `ProcessExecution`, `ProcessExecutionOptions`           | 类型化执行结果、受控归因与请求选项                                                                       |
| `ProcessAliasTarget`、`AliasRoutingOptions`、`ProcessTrigger`、`ProcessDataMapper`、`ProcessExecutionException` | 执行辅助值、路由控制、类型映射与失败传播                                                               |
| `ProcessDefinitionDigest`、`ProcessIdentifiers`、`ProcessText` | 稳定摘要与身份/文本校验契约                                                                         |
| `ProcessRuntimeManager`, `ProcessToolingService`                                         | 本地 runtime 生命周期与不执行流程的工具链                                                                |
| `CompileFlowException`, `ErrorCode`, `ProcessModelType`                                  | 根包错误分类与格式身份                                                                                   |
| `config.*`                                                                               | 不可变 `ProcessEngineConfig` 树与校验器                                                                  |
| `preflight.*`                                                                            | `ProcessPreflightOptions`, `ProcessPreflightReport`                                                      |
| `spi.*`                                                                                  | `ProcessEngineProvider`, `ProcessEnginePlugin`, `ProcessEnginePluginContext`, `ProcessComponentResolver` |
| `spi.event.*`                                                                            | 封闭的类型化 `ProcessEvent` 生命周期 record 与 `ProcessEventListener`                                    |
| `spi.execution.*`                                                                        | 重试策略、终态失败处理器、应用上下文传播及有界执行值                                                      |
| `spi.observability.TraceIdProvider`                                                      | Trace id 协作                                                                                            |
| `spi.routing.*`                                                                          | Alias route 权威与 route-bound 具名 targeting 契约                                                            |
| `spi.script.*`                                                                           | `ScriptExecutor`、script program、preparation request 与 provider 无关的脚本失败分类                  |

应用不应依赖 `com.alibaba.compileflow.engine.core`、格式解析器 AST、部署协调器、Spring auto-configuration 内部或 server
controller 类。

`compileflow-core` 默认注册 `java` 脚本语言。Java 脚本是可信进程内代码，只能用于可信定义；executor 实现仍属于
core 内部类型。

## 部署 API 与 SPI（`compileflow-deploy-api`）

受支持的部署契约位于 `com.alibaba.compileflow.deploy.api`：

| 包 / 类型                             | 职责                                                             |
|---------------------------------------|------------------------------------------------------------------|
| `ProcessDeploymentService`            | 不可变发布与带 revision 前置条件的 rollout facade                |
| `command.*`                           | 已校验的发布、rollout、canary、promote、abort 与 rollback 命令   |
| `artifact.*`, `release.*`             | 可执行 artifact 值与控制面 release 审计/元数据值                 |
| `routing.*`, `rollout.*`, `version.*` | 不可变路由与 rollout 状态、约束、查询与分页                      |
| `error.*`                             | 有界部署错误码与结构化异常上下文                                 |
| `protocol.*`                          | 带 schema 版本的 artifact/routing 解析器、payload 与有界 key     |
| `protocol.json.DeploymentProtocolJson` | 部署协议值的规范化有界 JSON 表示                                |
| `observability.*`                     | 实例级部署计数器与稳定、低基数的有界维度                         |
| `spi.ProcessArtifactSource`           | 不可变 artifact 查询边界                                         |
| `sync.DeploymentSyncChannel`          | 线性一致 transport SPI 与订阅契约                                |

应用不得依赖 deploy admin/runtime/integration 实现、repository、Spring 组合根内部或 server adapter。

Maven `tests` classifier 仅包含供 CompileFlow 自身测试套件使用的确定性内存夹具；它们不是生产 API、运行时适配器或降级实现。

Release metadata 只承载描述信息。完整性前置条件使用
`PublishProcessVersionCommand.getExpectedArtifactDigest()`，绝不通过 metadata 偷渡。`compileflow.` metadata key
命名空间只保留给 `ReleaseMetadataKeys` 声明的框架 key；应用使用自己的命名空间。Release metadata 不投影到可执行
artifact payload。

## Durable API、SPI 与交付面（Developer Preview）

`compileflow-durable-api` 下已文档化的以下类型是 `durable-strict@1` 的最终用户 Developer Preview 契约：

| 包 / 类型                    | 职责                                                                                         |
|------------------------------|----------------------------------------------------------------------------------------------|
| `DurableProcessEngine`      | 面向应用的直接 Start、Wait 完成、Cancel、脱敏 Run 查询与显式结果读取门面；不持有 Worker 生命周期 |
| `DurableOperatorService`     | Timeline、Pause/Resume、Effect 裁决与 Outbox 管理                                         |
| `command.*`                  | 带审计的 Pause/Resume、Effect 裁决与 Outbox 裁决 Operator 命令                              |
| `model.*`                    | Run identity/result、typed cursor、控制、Timeline、Wait、Timer、Effect 与 Outbox 生命周期模型 |
| `effect.*`                   | Effect reconciliation outcome                                                                 |
| `error.*`                    | 稳定 Durable 错误分类                                                                        |
| `validation.*`               | 供不可变 Durable API 值对象复用的校验契约                                              |

`compileflow-durable-spi` 是 Provider Preview，不与最终用户 API 共享同一级别的长期兼容承诺。它的 `DurableStore`
契约与数据库无关，但每个受支持 Provider 都必须独立证明完整事务、并发、崩溃、迁移和保留语义。它还公开
`DurableWaitDescriptionProvider` 与 `DurableWaitDescriptionContext`、`DurableOutboxSink`，以及彼此分离的 admission 边界 `DurableAliasStateSource`、
`DurableVersionDefinitionSource`。
`DurableVersionDefinitionSource` 只在 admission 时提供缺失的权威 exact-Version 副本；Run 恢复不会调用它。
可选具名 `ProcessAliasTargetingPolicy` 只能覆盖 route 的 stable/candidate target；CompileFlow 协议固定的百分比分桶不可替换。
PostgreSQL SQL、Schema、索引和事务实现是第一方 Provider 实现细节，不是通用 JDBC 或 Dialect 抽象。
托管 definition source 返回嵌套的极小 `VersionDefinition(ProcessModelType, ProcessDefinition.Inline)`，model type
作为不可变恢复事实被保留。显式 definition 由 Durable 按已配置的 Engine 格式直接通过 Core 加载。
Durable 通过同一语义 backend 接受文档定义的严格 TBBPM/BPMN profile。

受支持的接入入口是：

- Provider-neutral 应用与维护运行时：`compileflow-durable-spring-boot-starter` 加一个完整 `DurableStore`；
- 第一方 PostgreSQL 发行入口：`compileflow-durable-spring-boot-starter-postgres`；
- 应用能力与集成 Provider：`compileflow-durable-spi`。

Embedded Query 公开 typed keyset cursor；authentication、authorization、request dedupe、approval 和 opaque page-token
编码/签名属于 transport/application adapter。可移植流程 payload 只有一种 Kernel 固定编码；不存在应用 codec 或
payload-protector SPI。

`compileflow-durable-runtime` 的全部包、PostgreSQL 实现类、`compileflow-durable-testkit`、Spring 自动配置组合 bean
和 Durable 数据库表都不是应用 API。第一方跨制品装配所需的 JVM `public` 可见性不会形成 Store 扩展契约。公共 Durable API
当前仍是 Developer Preview；Production Ready 必须满足独立发布 Gate。

完整边界与维护规则见 [Durable 架构](10-DURABLE_ARCHITECTURE.zh.md)。

## Spring 配置

| 前缀                             | 归属                           | 说明                                            |
|----------------------------------|--------------------------------|-------------------------------------------------|
| `compileflow.engine.*`           | 引擎实例                       | 严格绑定（`ignoreUnknownFields=false`）         |
| `compileflow.deploy.*`           | 部署控制/数据面                | 拓扑与角色开关均显式声明                        |
| `compileflow.durable.*`          | Durable 应用/维护运行时        | 默认关闭；角色、迁移、Worker 和留存必须显式选择 |
| `compileflow.workbench.server.*` | `compileflow-workbench-server` | 认证、HTTP 限制、持久化异步调用                 |

权威用户指南：[configuration.md](../zh/configuration.md)。生成的 Spring metadata 与 `ConfigurationMetadataTest`
必须与属性类保持一致。

## 部署行为

受支持的集成路径：Spring Boot auto-configuration + `ProcessDeploymentService`。

不变量（见 [Deploy 协议](../../compileflow-deploy/docs/PROTOCOL.md)）：

- 控制面与数据面保持分离
- 已发布的 Deploy `ProcessArtifact` 与 exact Process Version 不可变
- 第一方事实源是 PostgreSQL；`Jdbc*Repository` 不代表支持任意 JDBC 数据库
- H2 只是测试替身，不是 Deploy 生产数据库
- 不允许生产环境静默回退到内存实现
- `DeployRuntime` 通过 Spring 生命周期启动，不通过公开的 bootstrap API
- Deploy 协议代际只支持协调后的同质版本升级；当前 parser 只接受文档指定的精确 schema 版本，不承诺 `N-1` 滚动混部窗口

## Server REST 与 Workbench Operate

| 面                       | 权威                                                                         |
|--------------------------|------------------------------------------------------------------------------|
| Workbench REST wire 形态 | 匹配版本前后端契约，由提交到仓库的 Workbench Server OpenAPI 描述              |
| Server 实现              | `compileflow-workbench-server` controller 与 transport record 生成该描述     |
| Web 契约                 | 生成的 OpenAPI 类型、收窄领域类型、运行时校验与编译期 parity                 |
| 开发网关/Web 环境变量    | `COMPILEFLOW_DEV_GATEWAY_*`（仅开发）与 `VITE_COMPILEFLOW_*`（仅公开构建时） |

浏览器绝不持有共享 API key。具备认证能力的网关只在受保护的上游链路注入私有 Server 服务凭据；Node 开发网关不进入生产拓扑。
官方本地全栈路径：[DEPLOYMENT.md](../../compileflow-workbench/DEPLOYMENT.md)。

Workbench `/api/**` 是官方前端与匹配 Server 版本之间的 companion-backend 契约，不是跨 MINOR 的第三方集成 ABI。
OpenAPI 仍是同版本精确 wire 与 parity 权威。未来第三方集成 API 必须使用显式版本化根路径并单独声明兼容等级。

## 明确不支持的能力

- 进程级可变配置或扩展 registry
- API artifact 中的公开 parser/model AST 类型
- 面向全 classpath 的注解扫描扩展发现
- 运行时插件安装、替换或卸载
- 通过 `ProcessEngine` 配置开关获得 Durable 语义
- 应用直接读写 Durable 数据库表或依赖 Durable runtime 实现包
- 把精确版本 Demand Observation 当作基础设施 Mutation Command
- 把 Workbench companion `/api/**` endpoint 当作跨 MINOR 的公开集成 API

## 相关文档

- [Durable 架构](10-DURABLE_ARCHITECTURE.zh.md)
- [API 参考](../zh/api-reference.md)
- [配置指南](../zh/configuration.md)
- [扩展指南](../zh/extension-guide.md)
- [兼容性策略](../compatibility-policy.md)
