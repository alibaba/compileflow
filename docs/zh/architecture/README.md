# 架构

本组文档介绍 CompileFlow 的组件边界、执行模型和支持范围。具体契约以[支持范围与兼容性](supported-surfaces.md)、
[格式规范](../specifications/README.md)和生成的 OpenAPI Schema 为准。

## 文档

| 主题                 | 文档                                    |
| -------------------- | --------------------------------------- |
| 系统模型             | [架构概览](overview.md)                 |
| 模块职责             | [模块地图](module-map.md)               |
| 运行时准备与执行     | [执行流程](execution-flow.md)           |
| 不可变版本与别名路由 | [版本路由](version-routing.md)          |
| 公共与内部契约       | [支持范围与兼容性](supported-surfaces.md)     |
| 流程模型与调用策略   | [流程模型](process-model.md)            |
| 持久化执行           | [Durable 架构](durable-architecture.md) |
| 统一词汇             | [术语](terminology.md)                  |
| 公共 API 约定        | [API 设计](api-design.md)               |

## 建议阅读路径

- **了解系统：** 架构概览 → 模块地图 → 执行流程 → 支持范围与兼容性。
- **嵌入引擎：** [快速开始](../quick-start.md) → [配置指南](../configuration.md) → 支持范围与兼容性。
- **接入 Durable：** [Durable 使用指南](../durable-process.md) → Durable 架构 →
  [值班与恢复手册](../durable-operations-runbook.md)。
- **扩展引擎：** [扩展指南](../extension-guide.md) → 模块地图 → 支持范围与兼容性。
- **修改实现：** 模块地图 → [参与贡献（英文）](../../../CONTRIBUTING.md) → [测试指南](../testing.md)。

架构文档用于说明实现，不扩展[支持范围与兼容性](supported-surfaces.md)中的兼容性承诺。本文档与项目均采用
[Apache License 2.0](../../../LICENSE) 许可证。
