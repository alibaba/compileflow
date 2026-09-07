# CompileFlow Spring Boot Starter

Format-neutral dependency-only Spring Boot starter for embedding CompileFlow. It includes the auto-configuration
module, Spring Boot infrastructure, and validation, but selects no process format.

Use `compileflow-spring-boot-starter-tbbpm` or `compileflow-spring-boot-starter-bpmn` for a single-format application.
Applications supporting both formats may depend on this base starter plus `compileflow-tbbpm` and `compileflow-bpmn`;
each `ProcessDefinition` declares its own model type. The starter creates one application-scoped, lifecycle-managed
`ProcessEngine`, independent of how many frontends are present.

The starter defines no independent Java API. Use the contracts from `compileflow-api`, and see
the [quick start](../docs/en/quick-start.md) and
[configuration guide](../docs/en/configuration.md).

The starter is not a remote service. It does not enable Durable or Deploy persistence by itself; those capabilities
require their documented modules and configuration.

```bash
./mvnw test -pl compileflow-spring-boot-starter -am
./mvnw checkstyle:check -pl compileflow-spring-boot-starter -am
```
