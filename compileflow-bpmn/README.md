# CompileFlow BPMN Frontend

BPMN semantic frontend for CompileFlow. This module owns parsing, validation, writing, and semantic lowering
for the documented executable BPMN 2.0 subset. Engine composition, Java generation, and execution belong to
`compileflow-core`.

Applications that use the supported BPMN subset directly depend on this artifact; it brings
`compileflow-api` and the core implementation transitively. Each `ProcessDefinition` explicitly declares
`ProcessModelType.BPMN`; the same engine can execute TBBPM definitions when that frontend is installed.

The exact supported node surface is documented in the
[node support list](../docs/en/node-support.md). The
[BPMN extension specification](../docs/en/specifications/bpmn-extensions.md)
defines the `cf:` vocabulary, and
[`CompileFlowBpmnExtensions.xsd`](src/main/resources/CompileFlowBpmnExtensions.xsd)
provides its schema contract. Types under the module's model, parser, validation, writer, and semantic packages are
implementation details, not supported application APIs.

Use this module directly for a standalone engine, or use `compileflow-spring-boot-starter-bpmn` in a Spring Boot
application. CompileFlow does not claim full BPMN 2.0 execution coverage; consult the
[node support list](../docs/en/node-support.md) before importing a model.

```bash
./mvnw test -pl compileflow-bpmn -am
./mvnw checkstyle:check -pl compileflow-bpmn -am
```
