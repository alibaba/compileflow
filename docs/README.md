# CompileFlow documentation

Documentation is organized by language. English and Simplified Chinese contain the same user-facing document set.

| Language | Documentation           |
| -------- | ----------------------- |
| English  | [docs/en](en/README.md) |
| 简体中文 | [docs/zh](zh/README.md) |

## Shared contract artifacts

Language-neutral, machine-validated artifacts remain under [`docs/specs`](specs/):

- generated [Workbench Server OpenAPI](specs/openapi/compileflow-workbench-server.openapi.json);
- Durable PostgreSQL production-drill JSON Schema;
- Engine/Workbench protocol golden fixture.

Human-readable architecture, specifications, guides, and policies live only under a locale root. Files in
`docs/specs` are generated or test-owned contract inputs and must not be translated or edited as prose.

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
[中文支持面清单](zh/architecture/supported-surfaces.md)
