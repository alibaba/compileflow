# CompileFlow 模块说明

本文只记录当前模块边界和定位源码所需的主要入口。详细行为以链接的规范和 API 参考为准。

## 产品边界

| 模块                                    | 职责                                                                             |
| --------------------------------------- | -------------------------------------------------------------------------------- |
| `compileflow-bom`                       | 统一管理 CompileFlow Maven 构件的依赖版本。                                      |
| `compileflow-api`                       | 公共 `ProcessEngine`、`ProcessRef`、`ProcessDefinition`、结果、配置和 SPI 契约。 |
| `compileflow-core`                      | 与格式无关的解析、语义编译、运行时缓存、执行和本地路由。                         |
| `compileflow-tbbpm`                     | TBBPM 解析、校验、语义前端及 `ProcessSemanticCompilerProvider` 实现。            |
| `compileflow-bpmn`                      | BPMN 解析、校验、语义前端及 `ProcessSemanticCompilerProvider` 实现。             |
| `compileflow-spring-boot-autoconfigure` | 为核心引擎和 `compileflow.engine.*` 提供与格式无关的 Spring Boot 自动配置。      |
| `compileflow-spring-boot-starter`       | 与格式无关的 Spring Boot 和 Engine 依赖聚合。                                    |
| `compileflow-spring-boot-starter-tbbpm` | 组合 `ProcessEngine` 与 TBBPM 语义前端。                                         |
| `compileflow-spring-boot-starter-bpmn`  | 组合 `ProcessEngine` 与 BPMN 语义前端。                                          |
| `compileflow-deploy`                    | 提供不可变版本发布、灰度控制、部署协议和节点本地运行时收敛。                     |
| `compileflow-deploy-jdbc`               | 数据库 Provider 共享的版本耦合 JDBC 持久化状态机。                               |
| `compileflow-durable`                   | Durable 父模块，包含持久化执行、有限执行单元、Effect、Outbox 和内置存储实现。    |
| `compileflow-durable-runtime`           | Durable Run 的准备、恢复、租约和 Worker 执行。                                   |
| `compileflow-workbench-server`          | Workbench REST API、持久化、异步调用、部署操作和诊断。                           |
| `compileflow-workbench`                 | 提供浏览器端流程建模、执行预览、学习和运维界面。                                 |
| `compileflow-integration-tests`         | 跨模块行为测试。                                                                 |

Maven Reactor 包含 Java 模块；Workbench 使用独立的 pnpm 工作区。示例和性能测试通过 Maven Profile 按需构建，
不属于运行时依赖。

## 执行入口

`ProcessEngine` 与 `DurableProcessEngine` 是面向应用的两个独立执行入口。`ProcessEngine` 根据显式定义、版本或别名执行一次
流程调用；Durable 创建持久化 Run，并由每个引擎实例管理节点本地资源和可选 Worker。Durable 不是
`ProcessEngine` 的一种隐藏模式。

核心引擎通过 `ProcessRuntimeResolver` 限量读取流程定义、完成校验和编译，并按精确的本地标识安装运行时。
`ProcessRuntimeManager` 负责节点本地预热、精确版本绑定和卸载，不负责版本发布或路由。
Deploy 通过 `ProcessRuntimeOwnership`、`ProcessCallInspector` 和 `ProcessExecutionGraphPreparer` 完成带所有权的安装，
不依赖公共的 `ProcessRuntimeManager` 接口。

`LocalRoutingState` 保存节点本地的服务状态。`AliasAdmission` 接收已发布路由，`DeterministicAliasSelector` 按协议选择稳定版本或候选版本。
部署侧的 `DeploymentRuntime` 将期望状态收敛为本地就绪的运行时，不改变核心引擎契约。

## 所有权规则

- 公共契约位于 `compileflow-api` 或明确命名的产品 API 模块。
- 格式模块负责解析并归一化各自的源格式；核心模块只依赖 `ProcessSemanticCompilerProvider` 接口。
- 执行入口与持久化 Provider 不依赖具体格式实现；Starter 只组合这些相互独立的能力，不为每种格式和执行入口创建组合模块。
- 部署控制面先提交状态，再进行数据面收敛和 runtime 安装。
- Durable 持久化执行独立于 Workbench 的整次调用异步队列。
- 除非产品契约明确要求，内部实现仅在包或模块内可见。
- 模块只依赖表达自身职责所需的最小契约，不使用包罗万象的工具模块。

## 源码定位

| 需求                    | 起点                                                                                  |
| ----------------------- | ------------------------------------------------------------------------------------- |
| 公共执行                | `compileflow-api` 的 `ProcessEngine`                                                  |
| 运行时解析              | `compileflow-core` 的 `ProcessRuntimeResolver`                                        |
| Spring 组合             | `CompileFlowEngineAutoConfiguration`                                                  |
| Alias 路由              | `AliasAdmission`、`DeterministicAliasSelector`                                        |
| 部署数据面              | `compileflow-deploy-runtime` 的 `DeploymentRuntime`                                   |
| Durable runtime         | `DurableProcessRuntimeManager` 和 Durable runtime 模块                                |
| 浏览器草稿存储          | `compileflow-workbench/apps/web/src/authoring/designer/api`                           |
| Workbench Server 持久化 | `compileflow-workbench-server/src/main/java/com/alibaba/compileflow/workbench/server` |
