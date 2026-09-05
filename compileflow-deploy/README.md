# CompileFlow Deploy

`compileflow-deploy` provides immutable process publication, revision-checked Alias rollout, and runtime convergence.
It is an embeddable control/data plane, not a standalone service. Applications that execute only direct Inline or
Classpath definitions do not need it.

PostgreSQL is the first-party authority for deployment state; H2 is test-only. Publishing a Version does not install
code or change traffic. Read the [hot-deployment guide](../docs/en/hot-deploy.md) and
[Supported Surfaces](../docs/architecture/06-SUPPORTED_SURFACES.en.md) before deployment.

## Modules

| Module | Responsibility |
|---|---|
| `compileflow-deploy-api` | Supported commands, views, errors, wire values, and transport SPI |
| `compileflow-deploy-control-plane` | PostgreSQL publication, Alias, rollout, audit, outbox, and reconciliation authority |
| `compileflow-deploy-runtime` | Exact artifact verification, runtime installation, and desired-to-local-ready convergence |

Depend on `compileflow-deploy-api` from application code. Control-plane and runtime packages are implementation
modules composed by the host or Spring Boot integration.

## Publication and Rollout

Publication binds one exact `(namespace, code, version)` identity to immutable content:

```java
PublishedProcessVersion published = deploymentService.publish(
    new PublishProcessVersionCommand(
        ProcessRef.version("default", "order.rule", "2026-09-05-001"),
        ProcessModelType.TBBPM,
        ProcessDefinition.inline("order.rule", flowXml),
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
`DISTRIBUTED` commits authority first and distributes desired Alias state and immutable artifacts through explicit
transport implementations. PostgreSQL remains authoritative in both modes; channels and local-ready state are
rebuildable projections.

Hosts must deliberately apply or validate the packaged migration location
`db/compileflow-deploy/migration` before exposing commands. The embeddable library does not run Flyway or create an
in-memory production fallback implicitly.

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
python3 scripts/check_architecture_boundaries.py
```
