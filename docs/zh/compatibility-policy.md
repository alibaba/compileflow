# 兼容性策略

CompileFlow 遵循[语义化版本 2.0.0](https://semver.org/lang/zh-CN/)。本页定义 `2.x` 系列的兼容性承诺。只有在[支持面清单](architecture/supported-surfaces.md)中明确标记为“受支持”的界面，才适用这些承诺。

## 版本规则

- **MAJOR**：以不兼容方式删除或修改受支持契约。
- **MINOR**：向后兼容地增加能力、节点、SPI 或安全关闭的配置项。
- **PATCH**：不改变受支持契约的缺陷和安全修复。

## 支持面

### Java API 与 SPI

受支持的 Java 与部署制品包括：

- `compileflow-api`：引擎执行、配置、预检、错误和扩展 SPI；
- `compileflow-deploy-api`：不可变发布、Rollout、路由、视图和错误契约；
- `compileflow-deploy-protocol`：版本化载荷、规范编解码器和投影键；
- `compileflow-durable-api`：流程启动、Wait 完成、取消、查询和运维操作契约。

`compileflow-durable-runtime` 中的 `DurableProcessEngineConfig` 和 `DurableProcessEngineFactory` 也是受支持的
纯 Java 组合入口。其他运行时实现类型不属于应用 API。

只有支持面清单列出的包和类型受兼容性承诺保护。实现模块中的 Java `public` 修饰符不会自动产生兼容性承诺。

`compileflow-deploy-spi`（含 `projection.DeploymentProjectionStore`）属于与版本耦合的 **Provider Preview**，不属于受支持的
应用 API。远程投影实现必须正确处理订阅、重连、历史丢失、并发写入和网络分区。
`compileflow-deploy-jdbc` 是版本耦合的第一方实现支撑层，不是应用 API 或扩展 SPI。

Durable API 属于受支持的应用 API，其存储 SPI 属于与版本耦合的 **Provider Preview**。Durable 制品必须保持精确流程身份、
已提交事实、延续状态和安全失败恢复的语义。

对于受支持界面，删除或重命名类型、方法或字段，修改方法签名或受检异常，改变可赋值关系，向应用必须实现的接口增加抽象方法，或改变部署命令、视图、错误和协议不变量的含义，均属于 MAJOR 变更。

增加兼容的类型或可选能力通常属于 MINOR。调用方必须处理未知错误值，不能依赖枚举顺序。`ProcessEvent` 是密封的生命周期类型层级，在 `2.x` 中保持封闭。

### 流程定义格式

TBBPM 和文档化的 BPMN 2.0 子集由各自规范及[节点支持](node-support.md)页面定义，并属于兼容性界面。

删除或重命名受支持元素或属性，改变有效定义的含义，或增加现有定义必须提供的值，均属于 MAJOR。新增元素如果不会改变既有定义的含义，并且不支持该元素的运行时会在预检阶段拒绝，可以属于 MINOR。符合通用 BPMN 规范不代表 CompileFlow 或 Durable 支持。

### Spring Boot 配置

文档中的 `compileflow.*` 属性名、类型、单位、枚举值、归属、语义和取值范围属于受支持契约。重命名属性、修改类型，或在用户未主动启用时改变语义、安全边界或默认启用状态，属于 MAJOR。默认安全关闭的新属性属于 MINOR。

安全和正确性修复可以在同一 MAJOR 内收紧不安全默认值，但必须记录变更。线程、队列、缓存和轮询默认值属于运维调优，不是延迟保证；可依据有记录的验证结果演进，但不改变属性语义或接受范围。
租约、重试、截止时间和退避策略的变化必须说明对持久化行为的影响。

### Workbench 配套 REST

仓库中的 [OpenAPI 描述](specifications/workbench-server-openapi.md)是 `compileflow-workbench-server` `/api/**` 端点在同一版本内的精确线协议契约。该接口属于 **Supported by deployment**，供匹配版本的 Workbench 前端与 Server 配套使用，不是稳定的第三方集成 API。

控制器记录类、OpenAPI、生成的 TypeScript、运行时校验和契约测试必须同步更新。前端和 Server 必须来自同一个 CompileFlow
版本；此支持面不定义兼容窗口。

### 持久化、线协议与遥测

每个已发布的 Flyway 数据库变更脚本都不可修改。数据库结构变化必须新增脚本，并为所属产品提供启动、重启和恢复验证。

持久化类型标识、错误码、协议字段名、指标名称和标签键都属于兼容性契约。CompileFlow 元数据使用 `compileflow.` 命名空间；Deploy 指标使用 `compileflow.deploy.*` 命名空间。应用元数据应使用自己的命名空间。发布元数据只用于描述，不是路由或投影的权威来源。发布完整性通过命令中的 `expectedArtifactDigest` 字段校验。

### Java 平台与序列化

**Java 17 是最低**构建和字节码基线，编译器发布级别为 `maven.compiler.release=17`。受支持的运行时是 Java 17、Java 21 和 Java 25 LTS；动态准备流程需要标准 `jdk.compiler` 模块。CompileFlow 为所有受支持的 JDK 发布同一组 Java 制品，不提供 JDK 专用分类包。

Java 原生序列化不属于 CompileFlow 的线协议或持久化契约。稳定集成应使用文档化的 Java API、流程格式、OpenAPI 契约或 Durable 持久化契约。运行时线程策略属于内部实现。

## 运行时与持久化边界

Deploy 和 Durable 在同一运行拓扑中必须使用匹配的协议版本。Deploy 采用协调一致的同版本协议升级；Durable 不支持混合版本滚动运行。

Durable 按引用关系保留数据：只要保留的 Run 仍引用某个已存储流程定义，该定义就必须保留。CompileFlow 负责解析已存储定义并恢复已提交的流程语义状态；应用负责保证自身持久化值仍可解码。生成代码可以丢弃，恢复时会根据已存储的流程语义重新生成。
Deploy 的制品保留策略独立于 Durable，不决定 Durable 的恢复依据。线程、队列、缓存和轮询默认值属于运维调优，不是 SLA 承诺。

Deploy 控制面的写入端、投影存储载荷和运行时读取端必须使用同一协议版本。遇到未知协议字段或数据库结构版本时，系统会安全拒绝处理。

## 参考资料

- [支持面清单](architecture/supported-surfaces.md)
- [API 参考](api-reference.md)
- [TBBPM 规范](specifications/tbbpm.md)
- [流程格式参考](specifications/process-formats.md)
