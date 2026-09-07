# Hot-deploy integration

This page is for hosts that embed Deploy with separate control-plane and execution processes. Use [Hot deployment](hot-deploy.md) for publishing, canary, promotion, abort, and rollback workflows.

## Process roles

Every distributed process declares its role explicitly. Missing infrastructure is an error; it does not select a fallback mode.

| Role           | Control plane | Runtime worker | Required infrastructure                                       |
| -------------- | ------------- | -------------- | ------------------------------------------------------------- |
| Control plane  | `true`        | `false`        | `DeployStore`, `DeploymentProjectionStore`                    |
| Runtime worker | `false`       | `true`         | `DeploymentProjectionStore`, subscriptions, artifact resolver |
| Combined       | `true`        | `true`         | Both sets; configure explicitly                               |

All roles require `compileflow.deploy.enabled=true` and `topology=DISTRIBUTED`.

## Control plane

Use `ProcessDeploymentService` for publication and rollout commands. The control plane stores immutable versions, Alias routes, rollout history, and outbox state through one semantic `DeployStore`. A route mutation and its outbox record commit in one Provider transaction.

`RoutingOutboxDispatcher` delivers only committed state to the projection store. Delivery is retryable and observable as pending, processing, expired, or dead-letter work. The control plane must have one complete database Provider and one audited `DeploymentProjectionStore`; CompileFlow does not bundle a remote projection implementation.

PostgreSQL and MySQL 8.4 are supported Deploy databases. Use exactly one matching Provider starter. H2 is test-only. The neutral starter is for hosts that supply a complete custom `DeployStore`; it does not supply a driver, Store, or migration.

Apply the exact packaged migration before enabling command ingress or background delivery. Provider starters default to
`compileflow.deploy.database.migrate=false`: they validate externally applied migrations and reject pending or inconsistent
schemas. Explicit `true` applies migrations using the configured DataSource identity. For MySQL with binary logging,
the V1 triggers require a DDL administrator with the server-required trigger-creation privileges; schema-scoped DDL
grants alone may be insufficient. Run migrations separately, then use a DML-only application identity with migration
disabled. Do not enable global `log_bin_trust_function_creators` to bypass that ownership boundary.

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: true
        runtime-worker-enabled: false
```

## Runtime worker

Each worker hosts one Spring-managed `DeploymentRuntime`. Spring owns subscription, reconciliation, installation, and shutdown. Application code must not construct or start this pipeline manually.

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: false
        runtime-worker-enabled: true
        artifact:
            mode: SOURCE
        routing:
            namespaces: [default]
            codes: [order.rule]
            aliases: [production]
```

Subscriptions are explicit capacity and ownership declarations. A worker installs only the exact versions required by its desired Alias routes and publishes a local-ready snapshot after digest verification. It executes the last valid local-ready revision while convergence is in progress and fails closed when no valid revision exists.

## Artifact modes

| Mode               | Resolution                                                                                                     | Required access                                                                     |
| ------------------ | -------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------- |
| `SOURCE`           | Workers read immutable content from the version repository through `ProcessArtifactSource`.                    | Read access to the version store.                                                   |
| `PROJECTION_STORE` | The control plane projects immutable artifact payloads and workers read them from `DeploymentProjectionStore`. | Projection-store durability, capacity, access control, and atomic create-if-absent. |

Both modes carry and verify the content digest. Projection mode must reject an oversized payload before remote I/O. A conflicting or corrupt existing artifact fails closed; it is never overwritten.

## Projection contract

The control plane publishes committed state only. A projection implementation must provide server-side atomic compare-and-set, including create-if-absent. A client-side read followed by write is not sufficient.

Routing keys are transport details derived from a complete identity tuple:

```text
compileflow.deployment.alias.{identityDigest}
compileflow.process.version.{identityDigest}
```

`identityDigest` is lowercase SHA-256 over the ordered, length-prefixed UTF-8 identity tuple. Payloads carry the full identity and consumers verify it against the key. Alias payloads carry one monotonic revision; duplicates and older revisions are ignored.

## Startup and recovery

- A control plane without a projection store fails startup.
- A runtime without subscriptions, a projection store, or its selected artifact source fails startup.
- Installation never falls back to unversioned source or process-local desired state.
- Reconciliation repairs repository-to-projection drift using the same idempotent delivery contract.
- Operators monitor outbox backlog, runtime installation failures, and convergence time.

For operational response, use [Operations playbook](operations-playbook.md).
