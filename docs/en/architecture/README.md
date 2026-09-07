# Architecture

These documents describe CompileFlow's component boundaries, execution model, and supported behavior. Product
contracts remain authoritative in [Supported surfaces](supported-surfaces.md), the
[format specifications](../specifications/README.md), and the generated OpenAPI schema.

## Documents

| Subject                              | Document                                        |
| ------------------------------------ | ----------------------------------------------- |
| System model                         | [Overview](overview.md)                         |
| Module ownership                     | [Module map](module-map.md)                     |
| Runtime preparation and execution    | [Execution flow](execution-flow.md)             |
| Immutable versions and Alias routing | [Version routing](version-routing.md)           |
| Public and internal contracts        | [Supported surfaces](supported-surfaces.md)     |
| Process model and invocation policy  | [Process model](process-model.md)               |
| Persisted execution                  | [Durable architecture](durable-architecture.md) |
| Shared vocabulary                    | [Terminology](terminology.md)                   |
| Public API conventions               | [API design](api-design.md)                     |

## Suggested reading paths

- **Understand the system:** Overview → Module map → Execution flow → Supported surfaces.
- **Embed the engine:** [Quick start](../quick-start.md) → [Configuration](../configuration.md) → Supported surfaces.
- **Adopt Durable:** [Durable process](../durable-process.md) → Durable architecture →
  [Operations runbook](../durable-operations-runbook.md).
- **Extend the engine:** [Extension guide](../extension-guide.md) → Module map → Supported surfaces.
- **Change implementation code:** Module map → [Contributing](../../../CONTRIBUTING.md) → [Testing](../testing.md).

Architecture documents explain the implementation. They do not expand the compatibility commitments in
[Supported surfaces](supported-surfaces.md). The documents use the same [Apache License 2.0](../../../LICENSE) as the
project.
