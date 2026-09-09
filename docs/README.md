# CompileFlow documentation

Documentation is organized by language. English and Simplified Chinese contain the same user-facing document set.

| Language | Documentation           |
| -------- | ----------------------- |
| English  | [docs/en](en/README.md) |
| 简体中文 | [docs/zh](zh/README.md) |

## Shared contract artifacts

Machine-readable contracts shared by both language editions are stored under [`docs/specs`](specs/):

- [Workbench Server OpenAPI](specs/openapi/compileflow-workbench-server.openapi.json)
- [Durable PostgreSQL production-drill schema](specs/compileflow-durable-production-drill-evidence-v1.schema.json)
- [Engine–Workbench protocol fixture](specs/fixtures/engine-workbench-protocol-golden-v1.json)

Architecture, specifications, guides, and policies live under the English or Simplified Chinese directory. Contract
artifacts are generated or verified by tests and are not translated.

## Repository documentation

| Area                  | Guide                                                                                                                                                                                                                                                                             |
| --------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Dependency management | [Maven BOM](../compileflow-bom/README.md)                                                                                                                                                                                                                                         |
| Engine                | [API](../compileflow-api/README.md) · [Core](../compileflow-core/README.md)                                                                                                                                                                                                       |
| Process formats       | [TBBPM](../compileflow-tbbpm/README.md) · [BPMN](../compileflow-bpmn/README.md)                                                                                                                                                                                                   |
| Deployment            | [Deploy](../compileflow-deploy/README.md)                                                                                                                                                                                                                                         |
| Persisted execution   | [Durable](../compileflow-durable/README.md)                                                                                                                                                                                                                                       |
| Spring Boot           | [Auto-configuration](../compileflow-spring-boot-autoconfigure/README.md) · [Base starter](../compileflow-spring-boot-starter/README.md) · [TBBPM starter](../compileflow-spring-boot-starter-tbbpm/README.md) · [BPMN starter](../compileflow-spring-boot-starter-bpmn/README.md) |
| Workbench             | [Web application](../compileflow-workbench/README.md) · [Server](../compileflow-workbench-server/README.md)                                                                                                                                                                       |
| Verification          | [Integration tests](../compileflow-integration-tests/README.md) · [Benchmarks](../compileflow-benchmarks/README.md)                                                                                                                                                               |
| Runnable applications | [Examples](../examples/README.md)                                                                                                                                                                                                                                                 |

Project policies: [Contributing](../CONTRIBUTING.md) · [Support](../SUPPORT.md) · [Security](../SECURITY.md) ·
[Maintainers](../MAINTAINERS.md)

Product boundaries: [English supported surfaces](en/architecture/supported-surfaces.md) ·
[中文支持范围与兼容性](zh/architecture/supported-surfaces.md)
