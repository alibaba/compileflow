# CompileFlow Engine Spring Boot Auto-Configuration

Format-neutral Spring Boot auto-configuration for CompileFlow. It binds `compileflow.engine.*` properties and
integrates application beans, engine lifecycle, metrics, and context propagation. It does not select a process format
or enable persistence.

Most applications should use `compileflow-spring-boot-starter-tbbpm` or `compileflow-spring-boot-starter-bpmn`.
Applications that assemble multiple process formats can combine `compileflow-spring-boot-starter` with the required
format modules. The concrete auto-configuration classes and implementation packages are not supported application APIs;
public engine contracts live in `compileflow-api`.

Use this module directly only when assembling a custom Spring Boot integration. Engine properties and activation rules are documented in the
[configuration guide](../docs/en/configuration.md); module relationships are documented in the
[module map](../docs/en/architecture/module-map.md). Deploy and Durable provide separate starters.

```bash
./mvnw test -pl compileflow-spring-boot-autoconfigure -am
./mvnw checkstyle:check -pl compileflow-spring-boot-autoconfigure -am
```
