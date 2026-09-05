# CompileFlow Workbench Server OpenAPI

## English

[`compileflow-workbench-server.openapi.json`](compileflow-workbench-server.openapi.json) is the generated HTTP wire description for the Workbench Server. The Spring
MVC controllers and typed transport records are the implementation source; the committed description is a reproducible
projection and drift gate, not a file to edit manually.

Regenerate it with the scoped `OpenApiContractTest` command documented in the
[Workbench Server README](../../../compileflow-workbench-server/README.md). Then run
`pnpm check:workbench-server-contract` from [`compileflow-workbench`](../../../compileflow-workbench/README.md).
Workbench domain contracts may refine plain wire strings into narrower unions, and
[`serverContractParity.ts`](../../../compileflow-workbench/apps/web/src/shared/contracts/serverContractParity.ts)
verifies their field sets and base types at compile time.

Springdoc is test-scoped. Production Server artifacts expose neither Swagger UI nor the OpenAPI endpoint.

## 中文

[`compileflow-workbench-server.openapi.json`](compileflow-workbench-server.openapi.json) 是 Workbench Server 自动生成的 HTTP wire 描述。Spring MVC controller 与强类型
transport record 是实现来源；提交到仓库的描述是可复现投影和漂移门禁，不应手工修改。

使用 [Workbench Server README](../../../compileflow-workbench-server/README.md) 中限定到
`OpenApiContractTest` 的命令重新生成，然后在
[`compileflow-workbench`](../../../compileflow-workbench/README.md) 运行
`pnpm check:workbench-server-contract`。Workbench 领域 contract 可以把 wire 中的普通字符串收窄为更精确的
联合类型，[`serverContractParity.ts`](../../../compileflow-workbench/apps/web/src/shared/contracts/serverContractParity.ts)
会在编译期验证字段集合和基础类型。

Springdoc 仅存在于测试作用域。生产 Server 产物不会暴露 Swagger UI 或 OpenAPI endpoint。
