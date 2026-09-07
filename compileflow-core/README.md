# CompileFlow Core

Format-neutral engine implementation shared by the TBBPM and BPMN frontends. It provides engine bootstrap, Java source
generation, compilation, class loading, interpreted and compiled execution, caching, and lifecycle management.

Standalone applications should depend on a format module, which brings the API and core implementation transitively.
Spring Boot applications should use a format-specific starter or compose the base starter with their frontend modules.
Do not depend on `com.alibaba.compileflow.engine.core` types: they are implementation details and may change between
releases.

The built-in QL and Java script executors run inside the host JVM. Java scripts require trusted definitions and a full
JDK; they are not a sandbox. See the [Security Guide](../docs/en/security.md).

```bash
./mvnw compile -pl compileflow-core -am
./mvnw test -pl compileflow-core -am \
  -Dtest=ClassName \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Architecture map: [module-map.md](../docs/en/architecture/module-map.md).
