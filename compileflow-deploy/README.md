# CompileFlow Deploy

`compileflow-deploy` provides immutable process publication, revision-checked Alias rollout, and runtime convergence.
It is an embeddable control plane/runtime, not a standalone service. Applications that execute only direct Inline or
Classpath definitions do not need it.

PostgreSQL 16.15, 17.11, and 18.6 and MySQL 8.4.7 are built-in Deploy Store providers; H2 is test-only. Publishing a
Version does not install code or change traffic. Read the [hot-deployment guide](../docs/en/hot-deploy.md) and
[Supported Surfaces](../docs/en/architecture/supported-surfaces.md) before deployment.

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
        Map.of("releaseNote", "new fraud rule")));
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
        "initial production route"));
```

The exact command signatures are owned by `compileflow-deploy-api`; see the
[Java API reference](../docs/en/api-reference.md#10-deployment-api) for the public entry points.

## Contract

- A Version is immutable. Reusing its identity with different content is a conflict.
- Publication validates and stores content; it does not install a runtime or mutate an Alias.
- An Alias contains one stable Version and at most one candidate. Canary weight is `1..9999` basis points.
- Alias revision is the only routing order. Stale mutations return a concurrency failure instead of overwriting newer
  intent.
- Rollback creates a new rollout from a captured baseline; it never rewrites history.
- Runtime nodes retain every referenced exact runtime before publishing local-ready state.
- Missing or unverifiable artifacts fail closed. There is no hidden previous-Version fallback.
- Outbox delivery is at least once and separate from append-only rollout audit history.

Detailed route, rollout, cohort, outbox, and projection semantics are defined in
[PROTOCOL.md](docs/PROTOCOL.md). The user-facing publication workflow is documented in
[Hot Deployment](../docs/en/hot-deploy.md).

## Topology and Configuration

`EMBEDDED` waits for process-local convergence before a successful route-changing command returns.
`DISTRIBUTED` commits authority first and distributes desired Alias state through a `DeploymentProjectionStore`.
Immutable artifacts are read from the version repository in `SOURCE` mode or projected through the same store in
`PROJECTION_STORE` mode. The selected `DeployStore` remains authoritative in both topologies; remote and local-ready
state are rebuildable projections.

In `EMBEDDED`, `compileflow.deploy.runtime.convergence-timeout` bounds the caller's wait for alias lookup,
artifact resolution, runtime loading, and local-ready publication. The runtime owns a bounded background executor;
`runtime.installation-concurrency` limits admitted convergence operations, including work whose callers timed out.
Admission fails immediately with `CONVERGENCE_FAILED` when capacity is exhausted. Timeout and interruption do not
cancel shared convergence, which may still publish local-ready state later. This is a caller wait budget, not a
deadline that forcibly stops provider I/O or compilation. A stuck provider retains capacity until it returns;
configure provider I/O timeouts separately. Shutdown stops new admission and lets submitted work finish.

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
