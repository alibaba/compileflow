# Workbench Server OpenAPI

[`compileflow-workbench-server.openapi.json`](../../specs/openapi/compileflow-workbench-server.openapi.json) 定义
Workbench Web 与 Workbench Server 之间的 HTTP 契约，包括路径、方法、数据结构、必填字段和枚举值。两个组件必须使用同一个
CompileFlow 版本。该接口只服务于 Workbench 内部集成，不作为第三方引擎接入 API。

OpenAPI 无法完整表达的信任边界、幂等、并发控制、路由与重试语义，见
[Operate API 契约](../../../compileflow-workbench/apps/web/src/operate/API_SPEC.md)。

生产环境的 Server 构件不提供 Swagger UI 或 OpenAPI 端点。
