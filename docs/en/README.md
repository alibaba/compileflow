# CompileFlow documentation

These pages cover CompileFlow's public APIs, integrations, and deployment boundaries. See
[Supported surfaces](architecture/supported-surfaces.md) for the complete support matrix.

CompileFlow is a lightweight, high-performance, embeddable, and extensible process engine for Java. It supports TBBPM
and the documented BPMN 2.0 subset. `ProcessEngine` uses stateless, in-memory execution and supports both compiled and
interpreted modes. CompileFlow has been adopted by core systems across Alibaba business platforms, Taobao,
Alibaba Cloud, and international businesses.

For processes that must retain state across application restarts, CompileFlow Durable provides persisted waits, timers,
and reliable handling of external operations.

Developers can use the visual process editor to design workflows and express complex business logic clearly, helping
business designers and software engineers work together more effectively. CompileFlow Deploy and Workbench provide
versioned deployment and visual modeling when required.

## Key capabilities

- **Compile or interpret** — Run processes in compiled or interpreted mode.
- **TBBPM and BPMN** — Use one engine API and runtime model for TBBPM and the documented BPMN 2.0 subset.
- **Typed Java integration** — Embed a thread-safe engine directly or through Spring Boot, with declared variables,
  preflight validation, typed results, and stable errors.
- **Versioned deployment** — Publish immutable Versions, update Aliases with revision checks, and route deterministic
  canary traffic with CompileFlow Deploy.
- **Durable execution** — Persist waits, timers, and external-operation state, then resume execution after an
  application restart.
- **Visual Workbench** — Model and validate processes in the browser, then publish, monitor, and inspect execution
  through the Workbench Server.

## Start

- [When to use CompileFlow](when-to-use.md)
- [Quick start](quick-start.md)
- [Examples](../../examples/README.md)

## How-to guides

- [Advanced features](advanced-features.md)
- [Extension guide](extension-guide.md)
- [Hot deployment](hot-deploy.md)
- [Hot-deploy integration](hot-deploy-integration.md)
- [Durable process](durable-process.md)
- [Performance tuning](performance-tuning.md)
- [Troubleshooting](troubleshooting.md)

## Reference

- [Configuration](configuration.md)
- [API reference](api-reference.md)
- [Process data types](type-system.md)
- [Node support](node-support.md)
- [Resource management](resource-management.md)
- [Error model](error-model.md)
- [Specifications](specifications/README.md)
- [Workbench Server OpenAPI](specifications/workbench-server-openapi.md)
- [Compatibility policy](compatibility-policy.md)
- [Glossary](glossary.md)

## Architecture

- [Overview](architecture/overview.md)
- [Process model](architecture/process-model.md)
- [Execution flow](architecture/execution-flow.md)
- [Version routing](architecture/version-routing.md)
- [Module map](architecture/module-map.md)
- [Durable architecture](architecture/durable-architecture.md)

## Operations and security

- [Durable key rotation](durable-key-rotation.md)
- [Durable operations runbook](durable-operations-runbook.md)
- [Durable Provider testing](durable-testing.md)
- [Monitoring](monitoring.md)
- [Operations playbook](operations-playbook.md)
- [Security](security.md)
- [Threat model](threat-model.md)

## Contribute

- [Testing](testing.md)
- [Architecture and API design](architecture/api-design.md)
- [Contributing](../../CONTRIBUTING.md)
- [Workbench contributor guide](../../compileflow-workbench/CONTRIBUTING.md)

## Project reference

- [Architecture index](architecture/README.md)
- [Supported surfaces](architecture/supported-surfaces.md)
- [Flow diagram examples](examples/flow-diagrams.md)
- [Documentation language index](../README.md)
- [Support](../../SUPPORT.md)
- [Security reports](../../SECURITY.md)

[简体中文](../zh/README.md)

Guides describe supported procedures, specifications define process and HTTP contracts, and architecture pages explain
component boundaries.
