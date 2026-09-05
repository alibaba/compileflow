# CompileFlow TBBPM Provider

TBBPM format provider for CompileFlow. This module owns the TBBPM XML model, schema, parser, validator, writer, Java
code generator, and engine provider.

Applications that use the CompileFlow TBBPM format directly depend on this artifact; it brings
`compileflow-api` and the core implementation transitively. The Spring Boot starter includes it as the default format
provider.

The supported XML contract is documented in the
[TBBPM specification](../docs/specs/tbbpm-specification.en.md). Types under the module's parser, definition, and
generator packages are implementation details, not supported application APIs.

```bash
./mvnw test -pl compileflow-tbbpm -am
./mvnw checkstyle:check -pl compileflow-tbbpm -am
```
