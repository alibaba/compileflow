# CompileFlow API

Public Java contracts for embedding and extending CompileFlow. This is the dependency to use when application code
needs the engine API or one of the documented extension points.

The artifact has no production dependencies. It contains both the call-facing API and the SPI types used by engine
configuration. API and SPI are separated by package; provider implementations remain in `compileflow-tbbpm` and
`compileflow-bpmn`.

## Packages

| Package                                            | Purpose                                                      |
|----------------------------------------------------|--------------------------------------------------------------|
| `com.alibaba.compileflow.engine`                   | Engine lifecycle, execution, results, and shared value types |
| `com.alibaba.compileflow.engine.config`            | Immutable scalar configuration and engine assembly surface   |
| `com.alibaba.compileflow.engine.preflight`         | Validation and dry-run compilation reports                   |
| `com.alibaba.compileflow.engine.spi`               | Engine provider, plugin, and component-resolution contracts  |
| `com.alibaba.compileflow.engine.spi.event`         | Typed lifecycle events and listeners                         |
| `com.alibaba.compileflow.engine.spi.execution`     | Retry and terminal-failure policies                          |
| `com.alibaba.compileflow.engine.spi.observability` | Trace identity collaboration                                 |
| `com.alibaba.compileflow.engine.spi.routing`       | Alias routing collaboration                                  |
| `com.alibaba.compileflow.engine.spi.script`        | Script-language execution contracts                          |

Parser models, compilers, caches, deployment coordinators, and Spring internals do not belong in this artifact.

## Use this module when

- writing application code against `ProcessEngine`, `ProcessDefinition`, or `ProcessRef`;
- implementing a documented engine plugin, listener, component resolver, or script executor;
- publishing a library that must not depend on a format parser or Spring Boot.

Do not use this module as a process-format provider by itself. Add `compileflow-tbbpm`, `compileflow-bpmn`, or the
Spring Boot starter for a runnable engine.

See [Supported Surfaces](../docs/architecture/06-SUPPORTED_SURFACES.en.md), the
[API Reference](../docs/en/api-reference.md), and the [Extension Guide](../docs/en/extension-guide.md).

```bash
./mvnw test -pl compileflow-api
```
