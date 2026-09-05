# CompileFlow documentation

These documents describe the unreleased `2.0.0-SNAPSHOT` line. Durable is a Developer Preview; check the
[supported surfaces](architecture/06-SUPPORTED_SURFACES.en.md) before choosing a deployment model. Documentation for a
released version belongs to its source tag.

## Choose a language

- [English documentation](en/README.md)
- [中文文档](zh/README.md)

## Start here

| Task | English | 中文 |
|---|---|---|
| Get started | [Quick Start](en/quick-start.md) | [快速开始](zh/quick-start.md) |
| Configure the engine | [Configuration](en/configuration.md) | [配置指南](zh/configuration.md) |
| Use persisted execution | [Durable Process](en/durable-process.md) | [Durable Process](zh/durable-process.md) |
| Operate Durable | [Operations runbook](en/durable-operations-runbook.md) | [值班与恢复手册](zh/durable-operations-runbook.md) |
| Check the API | [API Reference](en/api-reference.md) | [API 参考](zh/api-reference.md) |
| Extend the engine | [Extension Guide](en/extension-guide.md) | [扩展指南](zh/extension-guide.md) |
| Deploy process versions | [Hot Deployment](en/hot-deploy.md) | [热部署](zh/hot-deploy.md) |
| Operate a deployment | [Operations Playbook](en/operations-playbook.md) | [运维手册](zh/operations-playbook.md) |
| Resolve a problem | [Troubleshooting](en/troubleshooting.md) | [故障排查](zh/troubleshooting.md) |

## Contracts and architecture

| Subject | Document |
|---|---|
| Supported products, APIs, SPIs, and configuration | [Supported Surfaces](architecture/06-SUPPORTED_SURFACES.en.md) / [中文](architecture/06-SUPPORTED_SURFACES.zh.md) |
| Version compatibility | [Compatibility Policy](compatibility-policy.md) |
| Product selection | [When to use CompileFlow](when-to-use.md) |
| Architecture | [Architecture index](architecture/README.md) |
| TBBPM XML and execution semantics | [English](specs/tbbpm-specification.en.md) / [中文](specs/tbbpm-specification.zh.md) |
| BPMN extensions and execution semantics | [English](specs/bpmn-extension-specification.en.md) / [中文](specs/bpmn-extension-specification.zh.md) |
| TBBPM and BPMN feature mapping | [TBBPM vs BPMN](specs/tbbpm-vs-bpmn.md) |
| Workbench Server HTTP contract | [OpenAPI](specs/openapi/README.md) |

Supported-surface and specification pages define compatibility commitments. Tests establish the behavior of the current
commit. A disagreement between documentation and implementation is a defect.

## Modules and examples

| Area | Guide |
|---|---|
| Engine | [API](../compileflow-api/README.md) · [Core](../compileflow-core/README.md) |
| Process formats | [TBBPM](../compileflow-tbbpm/README.md) · [BPMN](../compileflow-bpmn/README.md) |
| Deployment | [Deploy](../compileflow-deploy/README.md) |
| Persisted execution | [Durable](../compileflow-durable/README.md) |
| Spring Boot | [Auto-configuration](../compileflow-spring-boot-autoconfigure/README.md) · [Starter](../compileflow-spring-boot-starter/README.md) |
| Workbench | [Web application](../compileflow-workbench/README.md) · [Server](../compileflow-workbench-server/README.md) |
| Verification | [Integration tests](../compileflow-integration-tests/README.md) · [Benchmarks](../compileflow-benchmarks/README.md) |
| Runnable applications | [Examples](../examples/README.md) |

## Contributing and support

- [Contributing](../CONTRIBUTING.md)
- [Testing](en/testing.md) / [测试](zh/testing.md)
- [Support](../SUPPORT.md)
- [Security](../SECURITY.md)
- [Maintainers](../MAINTAINERS.md)
