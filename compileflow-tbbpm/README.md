# CompileFlow TBBPM Frontend

TBBPM semantic frontend for CompileFlow. This module owns the TBBPM XML model, schema, parser, validator, writer, and
semantic lowering. Engine bootstrap, format-neutral Java generation, and execution belong to `compileflow-core`.

Applications that use the CompileFlow TBBPM format directly depend on this artifact; it brings
`compileflow-api` and the core implementation transitively. Spring Boot applications can use the dedicated
`compileflow-spring-boot-starter-tbbpm`; the format-neutral base starter does not select a process format.

The supported XML contract is documented in the
[TBBPM specification](../docs/en/specifications/tbbpm.md). Types under the module's model, parser, validation, writer,
and semantic packages are implementation details, not supported application APIs.

```bash
./mvnw test -pl compileflow-tbbpm -am
./mvnw checkstyle:check -pl compileflow-tbbpm -am
```
