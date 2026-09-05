# CompileFlow BPMN Provider

BPMN format provider for CompileFlow. This module owns parsing, validation, writing, Java code generation, and engine
composition for the documented executable BPMN 2.0 subset.

Applications that use the supported BPMN subset directly depend on this artifact; it brings
`compileflow-api` and the core implementation transitively. Spring Boot applications also select
`compileflow.engine.model-type=BPMN`.

The exact supported node surface is documented in the
[node support list](../docs/en/node-support.md). The
[BPMN extension specification](../docs/specs/bpmn-extension-specification.en.md)
defines the `cf:` vocabulary, and
[`CompileFlowBpmnExtensions.xsd`](src/main/resources/CompileFlowBpmnExtensions.xsd)
provides its schema contract. Types under the module's parser, definition, and generator packages are implementation
details, not supported application APIs.

Use this module with `compileflow-api` for a standalone engine, or set
`compileflow.engine.model-type=BPMN` when using the Spring Boot starter. CompileFlow does not claim full BPMN 2.0
execution coverage; consult the [node support list](../docs/en/node-support.md) before importing a model.

```bash
./mvnw test -pl compileflow-bpmn -am
./mvnw checkstyle:check -pl compileflow-bpmn -am
```
