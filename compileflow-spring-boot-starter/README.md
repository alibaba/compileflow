# CompileFlow Spring Boot Starter

Dependency-only Spring Boot starter for embedding CompileFlow. It includes the auto-configuration module, Spring Boot
infrastructure, validation, and the TBBPM format provider.

The starter creates one application-scoped, lifecycle-managed `ProcessEngine`
by default. To use BPMN, add `compileflow-bpmn` and set:

```yaml
compileflow:
  engine:
    model-type: BPMN
```

The starter defines no independent Java API. Use the contracts from `compileflow-api`, and see
the [quick start](../docs/en/quick-start.md) and
[configuration guide](../docs/en/configuration.md).

The starter is not a remote service. It does not enable Durable or Deploy persistence by itself; those capabilities
require their documented modules and configuration.

```bash
./mvnw test -pl compileflow-spring-boot-starter -am
./mvnw checkstyle:check -pl compileflow-spring-boot-starter -am
```
