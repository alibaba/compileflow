# CompileFlow API

Public Java contracts for embedding and extending CompileFlow. Applications and libraries use this artifact for the
engine API and documented extension points.

The artifact has no production dependencies. Application APIs and extension SPIs are separated by package.
`compileflow-core` provides the engine implementation, while `compileflow-tbbpm` and `compileflow-bpmn` provide the
process-format frontends.

## Packages

| Package                                            | Purpose                                                      |
| -------------------------------------------------- | ------------------------------------------------------------ |
| `com.alibaba.compileflow.engine`                   | Engine lifecycle, execution, results, and shared value types |
| `com.alibaba.compileflow.engine.config`            | Engine configuration and assembly                            |
| `com.alibaba.compileflow.engine.preflight`         | Validation and dry-run compilation reports                   |
| `com.alibaba.compileflow.engine.spi`               | Version-coupled bootstrap, plugin, and component contracts   |
| `com.alibaba.compileflow.engine.spi.event`         | Typed lifecycle events and listeners                         |
| `com.alibaba.compileflow.engine.spi.execution`     | Retry and terminal-failure policies                          |
| `com.alibaba.compileflow.engine.spi.observability` | Trace identity propagation                                   |
| `com.alibaba.compileflow.engine.spi.routing`       | Alias routing and targeting                                  |
| `com.alibaba.compileflow.engine.spi.script`        | Script-language execution contracts                          |

Parser models, compilers, caches, deployment coordinators, and Spring internals do not belong in this artifact.

## Use this module when

- writing application code against `ProcessEngine`, `ProcessDefinition`, or `ProcessRef`;
- implementing a documented engine plugin, listener, component resolver, or script executor;
- publishing a library that must not depend on a format parser or Spring Boot.

This module alone is not a runnable engine. Add `compileflow-tbbpm` or `compileflow-bpmn` for standalone use, or select
the corresponding Spring Boot starter.

See [Supported Surfaces](../docs/en/architecture/supported-surfaces.md), the
[API Reference](../docs/en/api-reference.md), and the [Extension Guide](../docs/en/extension-guide.md).

```bash
./mvnw test -pl compileflow-api
```
