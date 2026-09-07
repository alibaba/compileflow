# Workbench Server OpenAPI

[`compileflow-workbench-server.openapi.json`](../../specs/openapi/compileflow-workbench-server.openapi.json) is the
generated HTTP wire description for Workbench Server. Spring MVC controllers and typed transport records are the
implementation source; the committed document is a reproducible projection and drift gate, not a file to edit manually.

Regenerate it with the scoped `OpenApiContractTest` command in the
[Workbench Server README](../../../compileflow-workbench-server/README.md), then run
`pnpm check:workbench-server-contract` from the [Workbench workspace](../../../compileflow-workbench/README.md).
Workbench domain contracts may narrow plain wire strings into unions;
[`serverContractParity.ts`](../../../compileflow-workbench/apps/web/src/shared/contracts/serverContractParity.ts)
checks field sets and base types at compile time.

Springdoc is test-scoped. Production Server artifacts expose neither Swagger UI nor an OpenAPI endpoint.
