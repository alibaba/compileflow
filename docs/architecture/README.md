# CompileFlow architecture

This directory contains the current, public architecture of CompileFlow. The documents are bilingual and describe
component boundaries and supported behavior. Product contracts remain authoritative in
[Supported Surfaces](06-SUPPORTED_SURFACES.en.md), the format specifications, and the generated OpenAPI schema.

## Documents

| Subject | English | 中文 |
|---|---|---|
| System overview | [Overview](00-OVERVIEW.en.md) | [概览](00-OVERVIEW.zh.md) |
| Module ownership | [Module Map](03-MODULE_MAP.en.md) | [模块地图](03-MODULE_MAP.zh.md) |
| Runtime preparation and execution | [Execution Flow](04-EXECUTION_FLOW.en.md) | [执行流程](04-EXECUTION_FLOW.zh.md) |
| Immutable versions and Alias routing | [Version Routing](05-VERSION_ROUTING.en.md) | [版本路由](05-VERSION_ROUTING.zh.md) |
| Public and internal surfaces | [Supported Surfaces](06-SUPPORTED_SURFACES.en.md) | [支持面清单](06-SUPPORTED_SURFACES.zh.md) |
| Process model and invocation policy | [Process Model](07-PROCESS_MODEL.en.md) | [流程模型](07-PROCESS_MODEL.zh.md) |
| Persisted execution | [Durable Architecture](10-DURABLE_ARCHITECTURE.en.md) | [Durable 架构](10-DURABLE_ARCHITECTURE.zh.md) |
| Shared vocabulary | [Terminology](11-TERMINOLOGY.en.md) | [术语](11-TERMINOLOGY.zh.md) |
| Public API design rules | [API Design](12-API_DESIGN.en.md) | [API 设计](12-API_DESIGN.zh.md) |

## Reading paths

| Goal | Path |
|---|---|
| Understand the system | Overview → Module Map → Execution Flow → Supported Surfaces |
| Integrate the embedded engine | [Quick Start](../en/quick-start.md) → [Configuration](../en/configuration.md) → Supported Surfaces |
| Integrate Durable | [Durable Process](../en/durable-process.md) → Durable Architecture → [Operations](../en/durable-operations-runbook.md) |
| Extend the engine | [Extension Guide](../en/extension-guide.md) → Module Map → Supported Surfaces |
| Change implementation code | Module Map → [Contributing](../../CONTRIBUTING.md) → [Testing](../en/testing.md) |

## Quick lookup

| Concept | Document |
|---|---|
| Compile-then-execute and interpreted execution | [Overview](00-OVERVIEW.en.md#core-execution) |
| Process engine and module ownership | [Module Map](03-MODULE_MAP.en.md) |
| Runtime cache and single-flight preparation | [Execution Flow](04-EXECUTION_FLOW.en.md) |
| Java API, SPI, configuration, and compatibility | [Supported Surfaces](06-SUPPORTED_SURFACES.en.md) |
| Immutable publication and Alias routing | [Version Routing](05-VERSION_ROUTING.en.md) |
| Process model and action policy | [Process Model](07-PROCESS_MODEL.en.md) |
| Deploy projection protocol | [Deploy Protocol](../../compileflow-deploy/docs/PROTOCOL.md) |
| Durable identity, recovery, and state machines | [Durable Architecture](10-DURABLE_ARCHITECTURE.en.md) |
| API ownership and naming | [API Design](12-API_DESIGN.en.md) |
| TBBPM and BPMN semantics | [Specifications](../specs/) |
| Workbench product boundary | [Workbench Product Surfaces](../../compileflow-workbench/docs/PRODUCT_SURFACES.md) |

Architecture documents explain the current implementation shape. They do not expand the compatibility commitments
listed in Supported Surfaces.

Documentation follows the same [Apache License 2.0](../../LICENSE) as the project.
