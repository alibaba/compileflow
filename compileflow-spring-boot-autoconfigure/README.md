# CompileFlow Spring Boot Auto-Configuration

Spring Boot composition for the CompileFlow engine and optional deployment capabilities. This module strictly binds
`compileflow.engine.*` and
`compileflow.deploy.*`, converts them into immutable runtime configuration, and integrates application beans, lifecycle,
health, metrics, JDBC, and deployment roles.

Most applications should depend on `compileflow-spring-boot-starter` instead of using this artifact directly. The
concrete auto-configuration classes and implementation packages are not supported application APIs; public Java
contracts live in `compileflow-api` and `compileflow-deploy-api`.

Use this module directly only when assembling a custom Spring Boot composition. For the standard engine integration,
use the starter and follow the [configuration guide](../docs/en/configuration.md).

Configuration and conditional activation are documented in the
[configuration guide](../docs/en/configuration.md). The current composition map is in
the [module map](../docs/architecture/03-MODULE_MAP.en.md).

```bash
./mvnw test -pl compileflow-spring-boot-autoconfigure -am
./mvnw checkstyle:check -pl compileflow-spring-boot-autoconfigure -am
```
