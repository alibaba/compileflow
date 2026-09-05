# CompileFlow Core

Internal compile-then-execute implementation shared by the TBBPM and BPMN providers. It owns semantic compilation,
runtime loading, execution, caching, and engine lifecycle.

Applications should depend on `compileflow-api` plus a format module or the Spring Boot starter. Do not depend on
`com.alibaba.compileflow.engine.core` types: they are implementation details and may change between releases.

The built-in QL and Java script executors run inside the host JVM. Java scripts require trusted definitions and a full
JDK; they are not a sandbox. See the [Security Guide](../docs/en/security.md).

```bash
./mvnw compile -pl compileflow-core -am
./mvnw test -pl compileflow-core -am \
  -Dtest=ClassName \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Architecture map: [03-MODULE_MAP.en.md](../docs/architecture/03-MODULE_MAP.en.md).
