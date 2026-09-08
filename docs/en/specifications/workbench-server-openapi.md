# Workbench Server OpenAPI

[`compileflow-workbench-server.openapi.json`](../../specs/openapi/compileflow-workbench-server.openapi.json) defines the
HTTP wire contract between Workbench Web and Workbench Server, including paths, methods, schemas, required fields, and
wire-level enum values. Both components must come from the same CompileFlow release. This companion contract is not a
stable API for third-party engine integrations.

Behavior not represented fully in OpenAPI—such as trust boundaries, idempotency, optimistic concurrency, routing, and
retry semantics—is documented in the [Operate API contract](../../../compileflow-workbench/apps/web/src/operate/API_SPEC.md).

Production Server artifacts expose neither Swagger UI nor an OpenAPI endpoint.
