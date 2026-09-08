# CompileFlow Deploy

`compileflow-deploy` provides immutable process publication, revision-checked Alias rollout, and runtime installation.
It is embedded in an application rather than deployed as a standalone service. Applications that execute only Inline
or Classpath definitions do not need it.

PostgreSQL and MySQL are built-in Deploy Store providers; H2 is test-only. Publishing a Version stores immutable
content but does not install it or change traffic. Supported database versions and deployment requirements are listed
in [Supported Surfaces](../docs/en/architecture/supported-surfaces.md) and the
[hot-deployment guide](../docs/en/hot-deploy.md).

## Modules

| Module                                                            | Responsibility                                                                               |
| ----------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `compileflow-deploy-api`                                          | Supported commands, views, and errors                                                        |
| `compileflow-deploy-protocol`                                     | Narrow wire codecs and projection keys                                                       |
| `compileflow-deploy-spi`                                          | Version-coupled Store and projection transport contracts                                     |
| `compileflow-deploy-testkit`                                      | Executable Store transaction contract                                                        |
| `compileflow-deploy-control-plane`                                | Publication, Alias, rollout, audit, outbox, and reconciliation application logic             |
| `compileflow-deploy-runtime`                                      | Exact artifact verification, runtime installation, and desired-to-local-ready convergence    |
| `compileflow-deploy-jdbc`                                         | Shared, version-coupled JDBC persistence state machine                                       |
| `compileflow-deploy-postgresql`                                   | PostgreSQL dialect entry point and migrations                                                |
| `compileflow-deploy-mysql`                                        | MySQL dialect entry point and migrations                                                     |
| `compileflow-deploy-spring-boot-autoconfigure`                    | Database-independent Deploy control-plane/runtime composition                                |
| `compileflow-deploy-spring-boot-autoconfigure-{postgresql,mysql}` | Provider-specific Store and schema composition                                               |
| `compileflow-deploy-spring-boot-starter`                          | Format- and Provider-neutral entry point for hosts supplying a complete custom `DeployStore` |
| `compileflow-deploy-spring-boot-starter-{postgresql,mysql}`       | Format-neutral first-party Provider distributions                                            |

Depend on `compileflow-deploy-api` for deployment domain contracts and add `compileflow-deploy-protocol` only when
implementing or integrating a projection-store adapter. Control-plane and runtime packages are implementation modules
composed by the host or Spring Boot integration.

Deploy starters do not select a process format. Add `compileflow-tbbpm` and/or `compileflow-bpmn` explicitly. Spring
Boot applications that only need engine execution can use the corresponding format-specific engine starter.

## Publication and Rollout

Publication binds one exact `(namespace, code, version)` identity to immutable content:

```java
PublishedProcessVersion published = deploymentService.publish(
    new PublishProcessVersionCommand(
        ProcessRef.version("default", "order.rule", "2026-09-05-001"),
        ProcessDefinition.inline(ProcessModelType.TBBPM, "order.rule", flowXml),
        "alice",
        Map.of("releaseNote", "fraud screening")));
```

The first Alias route uses expected revision `0`. Every mutation uses the latest Alias or rollout revision and a stable
idempotency key:

```java
ProcessRollout rollout = deploymentService.createRollout(
    CreateRolloutCommand.allAtOnce(
        "01J-idempotency-key",
        ProcessRef.alias("default", "order.rule", "production"),
        published.getRef(),
        0L,
        "alice",
        "production route"));
```

The exact command signatures are owned by `compileflow-deploy-api`; see the
[Java API reference](../docs/en/api-reference.md#10-deployment-api) for the public entry points.

## Contract

- A Version is immutable. Reusing its identity with different content is a conflict.
- Publication validates and stores content; it does not install a runtime or mutate an Alias.
- An Alias contains one stable Version and at most one candidate. Canary weight is `1..9999` basis points.
- Alias revision is the only routing order. Stale mutations return a concurrency failure instead of overwriting newer
  intent.
- Rollback creates a new rollout from a captured baseline and leaves earlier rollout records unchanged.
- Runtime nodes retain every referenced exact runtime before publishing local-ready state.
- Missing or unverifiable artifacts fail closed; execution does not fall back to another Version.
- Outbox delivery is at least once and is recorded separately from the append-only rollout audit log.

Detailed route, rollout, cohort, outbox, and projection semantics are defined in
[PROTOCOL.md](docs/PROTOCOL.md). The user-facing publication workflow is documented in
[Hot Deployment](../docs/en/hot-deploy.md).

## Topology and Configuration

`EMBEDDED` waits until the local runtime is ready before a route-changing command succeeds. `DISTRIBUTED` first commits
the authoritative change, then distributes the desired Alias state through a `DeploymentProjectionStore`. Immutable
artifacts come from the version repository in `SOURCE` mode or from the projection store in `PROJECTION_STORE` mode.
The selected `DeployStore` remains authoritative in both topologies.

In `EMBEDDED`, `compileflow.deploy.runtime.convergence-timeout` limits how long the caller waits for Alias lookup,
artifact resolution, runtime loading, and ready-state publication. `runtime.installation-concurrency` limits concurrent
installation work. Requests fail with `CONVERGENCE_FAILED` when capacity is exhausted. A caller timeout does not cancel
shared installation work or provider I/O, so configure provider timeouts separately.

Choose exactly one first-party starter, or provide one complete custom `DeployStore`. PostgreSQL migrations live at
`db/compileflow-deploy/postgres/migration`; MySQL migrations live at
`db/compileflow-deploy/mysql/migration`. Provider starters apply or validate only their owned location and use the
independent `cf_deploy_schema_history`. The embeddable application modules never create an in-memory production fallback.

Configuration, required infrastructure, and startup checks are documented in:

- [Hot Deployment](../docs/en/hot-deploy.md)
- [Integration Topologies](../docs/en/hot-deploy-integration.md)
- [Configuration](../docs/en/configuration.md#deployment)
- [Operations Playbook](../docs/en/operations-playbook.md)

## Verification

From the repository root:

```bash
./mvnw test -pl compileflow-deploy/compileflow-deploy-control-plane -am
./mvnw test -pl compileflow-deploy/compileflow-deploy-runtime -am
./mvnw test \
  -pl compileflow-deploy/compileflow-deploy-spring-boot-starter-postgresql,compileflow-deploy/compileflow-deploy-spring-boot-starter-mysql \
  -am
python3 scripts/check_architecture_boundaries.py
```

The database starter command also builds and tests its provider and auto-configuration dependencies. PostgreSQL
contract tests require `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD`. The MySQL contract tests require `COMPILEFLOW_MYSQL_JDBC_URL` and
`COMPILEFLOW_MYSQL_JDBC_USERNAME`; the password is optional. Tests for an unconfigured database are skipped.
