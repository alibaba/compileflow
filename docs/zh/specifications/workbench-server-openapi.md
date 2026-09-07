# Workbench Server OpenAPI

[`compileflow-workbench-server.openapi.json`](../../specs/openapi/compileflow-workbench-server.openapi.json) 是
Workbench Server 自动生成的 HTTP 接口描述。Spring MVC 控制器和强类型传输对象是接口定义的实现来源；仓库中的 OpenAPI 文档
用于检查接口是否与实现一致，不应手工修改。

使用 [Workbench Server README](../../../compileflow-workbench-server/README.md) 中限定到 `OpenApiContractTest`
的命令重新生成，然后在 [Workbench workspace](../../../compileflow-workbench/README.md) 运行
`pnpm check:workbench-server-contract`。Workbench 领域模型可以把接口中的普通字符串收窄为联合类型；
[`serverContractParity.ts`](../../../compileflow-workbench/apps/web/src/shared/contracts/serverContractParity.ts)
会在编译期检查字段集合和基础类型。

Springdoc 仅用于测试。生产环境的 Server 构件不提供 Swagger UI 或 OpenAPI 端点。
