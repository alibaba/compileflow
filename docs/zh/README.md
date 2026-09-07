# CompileFlow 文档

本目录包含 CompileFlow 的中文文档。使用前请阅读[支持面清单](architecture/supported-surfaces.md)，了解各项能力的支持范围。

CompileFlow 是一款轻量、高性能、可集成、可扩展的 Java 流程引擎，支持 TBBPM 和文档列出的 BPMN 2.0 子集。
CompileFlow Process 引擎采用纯内存、无状态的执行方式，支持编译执行和解释执行两种模式。
CompileFlow 已应用于阿里业务中台、淘宝、阿里云、国际化等业务的多个核心系统。

对于需要跨应用重启保存流程状态的场景，可以使用 CompileFlow Durable，支持流程等待、定时触发和外部操作的可靠处理。

开发人员可以通过流程编辑器设计业务流程，将复杂的业务逻辑可视化，在业务设计人员与开发工程师之间建立清晰、高效的协作方式。
需要版本化部署或可视化建模时，可以分别使用 CompileFlow Deploy 和 Workbench。

## 核心能力

- **高性能执行** —— 将流程文件转换为 Java 代码并编译执行，同时提供解释执行模式。
- **TBBPM 与 BPMN** —— 使用统一的引擎 API 处理 TBBPM 和文档列出的 BPMN 2.0 子集。
- **Java 与 Spring Boot 集成** —— 提供线程安全的引擎、流程预检、类型化结果和稳定错误码。
- **版本化部署** —— 使用 CompileFlow Deploy 发布不可变版本，通过修订号检查更新别名，并进行确定性灰度路由。
- **持久化执行** —— 保存流程等待、定时任务和外部操作状态，并在应用重启后恢复执行。
- **可视化工作台** —— 在浏览器中建模和校验流程，并通过 Workbench Server 发布、监控和查看执行情况。

## 开始

- [何时使用 CompileFlow](when-to-use.md)
- [快速开始](quick-start.md)
- [示例](../../examples/README.md)

## 操作指南

- [高级特性](advanced-features.md)
- [扩展指南](extension-guide.md)
- [热部署](hot-deploy.md)
- [热部署集成](hot-deploy-integration.md)
- [Durable 流程](durable-process.md)
- [性能调优](performance-tuning.md)
- [故障排查](troubleshooting.md)

## 参考

- [配置指南](configuration.md)
- [API 参考](api-reference.md)
- [流程数据类型](type-system.md)
- [节点支持](node-support.md)
- [资源管理](resource-management.md)
- [错误模型](error-model.md)
- [格式与协议规范](specifications/README.md)
- [Workbench Server OpenAPI](specifications/workbench-server-openapi.md)
- [兼容性策略](compatibility-policy.md)
- [术语表](glossary.md)

## 架构

- [架构概览](architecture/overview.md)
- [流程模型](architecture/process-model.md)
- [执行流程](architecture/execution-flow.md)
- [版本路由](architecture/version-routing.md)
- [模块说明](architecture/module-map.md)
- [Durable 架构](architecture/durable-architecture.md)

## 运维与安全

- [Durable 密钥轮换](durable-key-rotation.md)
- [Durable 值班与恢复手册](durable-operations-runbook.md)
- [Durable 存储测试](durable-testing.md)
- [监控与可观测性](monitoring.md)
- [运维手册](operations-playbook.md)
- [安全指南](security.md)
- [威胁模型](threat-model.md)

## 贡献

- [测试指南](testing.md)
- [架构与 API 设计](architecture/api-design.md)
- [参与贡献（英文）](../../CONTRIBUTING.md)
- [Workbench 贡献指南（英文）](../../compileflow-workbench/CONTRIBUTING.md)

## 项目参考

- [架构索引](architecture/README.md)
- [支持面清单](architecture/supported-surfaces.md)
- [流程图示例](examples/flow-diagrams.md)
- [文档语言索引](../README.md)
- [支持政策](../../SUPPORT.md)
- [安全报告](../../SECURITY.md)

[English](../en/README.md)
