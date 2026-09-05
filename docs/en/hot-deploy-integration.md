# Hot Deployment Integration Guide

This implementer guide defines the process, storage, transport, and recovery contracts for release platforms that call
CompileFlow's versioned control plane and operate separate execution workers. Application owners performing ordinary
publication and rollout tasks should use [Hot Deployment](hot-deploy.md); its `EMBEDDED` example covers a one-process
Server deployment.

## Process Contract

Configure each distributed process by responsibility; do not rely on missing beans to infer its role.

| Process       | `control-plane-enabled` | `runtime-worker-enabled` | Required infrastructure                                                            |
|---------------|-------------------------|--------------------------|------------------------------------------------------------------------------------|
| Control plane | true                    | false                    | JDBC repositories, `DeploymentSyncChannel`                                         |
| Data plane    | false                   | true                     | `ProcessRuntimeManager`, `DeploymentSyncChannel`, subscriptions, artifact resolver |
| Combined      | true                    | true                     | Union of both sets; use only when intentional                                      |

All rows require `compileflow.deploy.enabled=true` and `compileflow.deploy.topology=DISTRIBUTED`.

## Control Plane

Use `ProcessDeploymentService` as the supported command API:

- `publish(PublishProcessVersionCommand)`
- `createRollout(CreateRolloutCommand)`
- `updateCanaryWeight(UpdateCanaryWeightCommand)`
- `promoteRollout(PromoteRolloutCommand)`
- `abortRollout(AbortRolloutCommand)`
- `rollbackRollout(RollbackRolloutCommand)`

The control plane stores version, alias route, rollout history, and outbox state in JDBC repositories. Route state and
its outbox event commit on one connection. `RoutingOutboxDispatcher` publishes only committed rows to the channel;
failed delivery is retried and remains visible as backlog or dead-letter state.

CompileFlow supports PostgreSQL as its production database. Applications embedding the deploy modules outside
`compileflow-workbench-server` must include the packaged Flyway location
`classpath:db/compileflow-deploy/migration`. Its complete PostgreSQL V1 owns both the Deploy schema and physical
immutability enforcement. It is the sole DDL owner for deploy tables; Server composes the location and does not copy
its SQL. H2 is only a test implementation and is not a supported deployment database.

The Deploy control-plane artifact is a library, so it deliberately does not select a migration policy or start Flyway on
its own. Before registering command ingress or background dispatch, the product host must apply the exact packaged
migrations or validate their checksums and reject pending migrations. Disabling application-owned DDL does not permit
disabling this fail-closed schema admission. Workbench Server implements the first-party composition; another host must
provide the equivalent startup gate.

```yaml
compileflow:
  deploy:
    enabled: true
    topology: DISTRIBUTED
    control-plane-enabled: true
    runtime-worker-enabled: false
    artifact:
      mode: DATABASE
```

The platform must declare one audited `DeploymentSyncChannel` bean. CompileFlow does not bundle a remote implementation;
the rest of the control and data planes remain transport-independent.

## Data Plane

Each worker hosts one Spring-managed `DeployRuntime`. Spring starts channel subscription before control-plane background
delivery in a combined process and closes it after publishers stop. Application code must not construct or start the
pipeline manually.

```yaml
compileflow:
  deploy:
    enabled: true
    topology: DISTRIBUTED
    control-plane-enabled: false
    runtime-worker-enabled: true
    artifact:
      mode: DATABASE
    routing:
      namespaces: [default]
      codes: [order.rule]
      aliases: [production]
```

Routing subscriptions are explicit capacity and ownership declarations. CompileFlow derives its internal transport
keys from the Cartesian product of canonical namespaces, codes, and aliases; the wire-key encoding is not public
configuration.

## Artifact Transport

| Mode       | Publication and resolution                                                                                  | Required trust boundary                                                                             |
|------------|-------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| `DATABASE` | Control plane stores content in the version repository; workers resolve it through `ProcessArtifactSource`. | Workers need read-only database access to version content.                                          |
| `CHANNEL`  | Control plane publishes an immutable artifact payload; workers resolve it from `DeploymentSyncChannel`.     | Channel size, retention, access control, and payload durability must satisfy artifact requirements. |

Both modes carry the mandatory digest as a first-class artifact field and verify it before runtime installation. An
optional caller digest assertion that differs from published content rejects publication before any routing change. For
`CHANNEL`, the UTF-8 definition, metadata, and JSON envelope must fit the selected backend's documented item capacity.
The adapter must reject oversized content before starting remote I/O.

If a channel projection fails after the immutable database record is stored, publish returns a typed failure and an
exact retry safely resumes projection. Create and rollback also confirm the persisted target projection before their
route transaction, so a newly referenced version is never committed only because its database row exists.

## Routing Protocol

Routing state is small intent, not the process artifact:

- `compileflow.deployment.alias.{identityDigest}`

`CHANNEL` artifact keys are:

- `compileflow.process.version.{identityDigest}`

`identityDigest` is lowercase SHA-256 over the ordered, length-prefixed UTF-8 identity tuple. Every payload carries its
complete identity, and consumers recompute the digest before accepting it. Every alias payload also carries complete
stable/candidate state and one monotonic `revision`. The control plane advances each channel key with atomic
exact-content CAS, so a late lower revision cannot replace the current projection. Consumers still reject duplicate and
lower revisions because notifications can arrive out of order.

A custom `DeploymentSyncChannel` must provide server-side atomic CAS, including atomic create-if-absent. Client-side
read followed by write is invalid; for example, use one Redis Lua script that compares, stores, and publishes
atomically, or an etcd transaction with value/version comparisons.

## Startup And Recovery

- A distributed control plane without a channel fails startup.
- A data plane without subscriptions, a channel, or its selected artifact source fails startup.
- Runtime install failure never falls back to unversioned source or process-local state.
- Channel recovery replays routing state. Reconciliation repairs repository-to-channel routing drift and, in
  `CHANNEL` artifact mode, detects missing stable/candidate artifact projections for authoritative Aliases and recreates
  them when `reconciliation.mode=REPAIR`.
- Routing repair coalesces the same exact outbox delivery. Artifact repair uses immutable create-if-absent CAS; an
  existing conflicting or corrupt artifact fails closed and is reported instead of overwritten.
- Operators should monitor pending, processing, expired-claim, and dead-letter outbox counts, runtime installation
  failures, and convergence time. The claim lease must exceed the delivery transport's worst-case timeout.

See the [operations playbook](operations-playbook.md).
