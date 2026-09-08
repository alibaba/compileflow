# Hot Deployment

CompileFlow hot deployment publishes immutable process versions and changes Alias routes without restarting the
application. This guide covers release and rollout tasks for application owners and operators. For distributed platform
embedding, transport requirements, and recovery wiring, use the
[integration guide](hot-deploy-integration.md). Version publication, route mutation, rollout history, outbox delivery,
and node-local installation have separate ownership.

## Local Definition Prewarming

A development tool can prepare an exact updated definition in one engine's bounded runtime cache before executing that
same definition:

```java
ProcessDefinition updated =
        ProcessDefinition.inline(ProcessModelType.TBBPM, "bpm.order.process", updatedXml);

engine.runtime().warmUp(updated);
engine.execute(updated, variables).orElseThrow();
```

`warmUp` does not create a code or version binding. A later call through an explicit `ProcessDefinition` still resolves its
configured definition source; callers must execute the same explicit content or load it under an exact
`ProcessRef.Version`. Prewarming changes only that engine's cache. It is not a production release or multi-node routing
mechanism.

## Choose A Topology

| Use case                                | Topology      | Control plane | Runtime worker | Delivery                                                   |
| --------------------------------------- | ------------- | ------------- | -------------- | ---------------------------------------------------------- |
| CompileFlow Workbench Server or one JVM | `EMBEDDED`    | true          | false          | Post-commit local-ready activation; outbox-backed recovery |
| Release API process                     | `DISTRIBUTED` | true          | false          | Outbox delivery to the projection store                    |
| Execution worker                        | `DISTRIBUTED` | false         | true           | Projection store subscription drives runtime installation  |
| Combined node                           | `DISTRIBUTED` | true          | true           | Publishes and subscribes through the projection store      |

The starter defaults `compileflow.deploy.enabled` to `false`. Enabling deploy with no process role is invalid.

### Embedded

```yaml
compileflow:
    deploy:
        enabled: true
        topology: EMBEDDED
        control-plane-enabled: true
        runtime-worker-enabled: false
        artifact:
            mode: SOURCE
```

The database stores immutable versions, alias routes, rollout history, and outbox state. After a route transaction
commits, the command converges the latest authoritative Alias through artifact installation and publishes it to the
local-ready snapshot before returning. The outbox replays the same revision as a durable recovery path. Memory is a
cache; JDBC remains authoritative after restart or eviction.

### Distributed Control Plane

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: true
        runtime-worker-enabled: false
        artifact:
            mode: SOURCE
```

The control plane requires a Provider Preview `DeploymentProjectionStore` from `compileflow-deploy-spi`, dispatches only committed outbox records, and reconciles current
route authority to the projection store. The application must provide one audited projection store bean; missing mandatory infrastructure
fails startup.

### Distributed Deployment Runtime

```yaml
compileflow:
    deploy:
        enabled: true
        topology: DISTRIBUTED
        control-plane-enabled: false
        runtime-worker-enabled: true
        runtime:
            failure-backoff: 5m
            installation-concurrency: 1
        artifact:
            mode: SOURCE
        routing:
            namespaces: [default]
            codes: [order.rule]
            aliases: [production]
            operation-timeout: 5s
```

Configure a non-empty `routing.namespaces × routing.codes × routing.aliases` subscription set, bounded to 10,000 keys.
There is no separate explicit-key property. Database artifact mode requires read access to the
version repository; projection store mode resolves immutable artifacts from the configured sync transport.

## Publish An Immutable Version

```java
PublishedProcessVersion published = deploymentService.publish(
        new PublishProcessVersionCommand(
                ProcessRef.version("default", "order.rule", "2026-07-15-001"),
                ProcessDefinition.inline(ProcessModelType.TBBPM, "order.rule", flowXml),
                "alice",
                metadata));
```

Publishing validates immutable identity, size, digest assertions, and actor, then stores the exact source. It has no
mutable readiness status, performs no runtime compilation, and never changes a route. Data-plane installation and local
readiness remain separate fail-closed steps.

## Move Traffic With Rollouts

Create a canary against the Alias revision just read from authority:

```java
ProcessRollout rollout = deploymentService.createRollout(CreateRolloutCommand.canary(
        "order-v2-release",
        ProcessRef.alias("default", "order.rule", "production"),
        ProcessRef.version("default", "order.rule", "2026-07-15-001"),
        currentAliasRevision,
        1_000,
        "alice",
        "ticket-4821"));

rollout = deploymentService.updateCanaryWeight(new UpdateCanaryWeightCommand(
        rollout.getId(), 5_000, rollout.getRevision(), "alice"));

rollout = deploymentService.promoteRollout(new PromoteRolloutCommand(
        rollout.getId(), rollout.getRevision(), "alice"));
```

An immediate release uses `RolloutStrategy.ALL_AT_ONCE` and no canary weight. Abort a running canary with
`AbortRolloutCommand`; this restores the baseline captured at creation. Roll back a completed release by creating a new
all-at-once rollout targeting the earlier immutable version. Completed history is never edited.

Every rollout creation needs a stable retry key scoped by namespace, process, Alias, and operation kind, plus the
expected Alias revision. Reusing the same scoped key with different request fields or actor is a conflict. Canary
mutations require the current rollout revision and verify the Alias precondition. Stale requests fail instead of
overwriting concurrent changes.

## Delivery Contract

- Route, rollout, audit event, and outbox row commit atomically.
- Post-commit activation always reads the current authoritative Alias rather than reconstructing state from a possibly
  superseded rollout.
- In `EMBEDDED` topology, a successful rollout command means the affected Alias is local-ready in that process. If
  installation fails after commit, the command reports convergence failure; an idempotent retry activates the latest
  committed revision, while the outbox preserves recovery work.
- In `DISTRIBUTED` topology, a successful command means the control-plane transaction committed. Routing activation
  requests immediate outbox dispatch, but runtime-node readiness remains observable asynchronous convergence.
- `RoutingOutboxDispatcher` remains the durable replay path in both topologies and the only control-plane writer to a
  distributed projection store.
- The outbox coalesces identical delivery work by a database-unique key; rollout events remain the append-only audit
  log.
- Alias state payloads carry one positive `revision`; duplicates and older messages are ignored.
- A malformed initial state fails startup; a malformed live update retains the last valid local-ready route.
- Runtime installation verifies the artifact digest and fails closed when the selected version is unavailable.
- Stop the control-plane role as a whole when delivery must stop; command admission and delivery share one lifecycle.

Alias route keys:

```text
compileflow.deployment.alias.{identityDigest}
```

Projection store artifact keys:

```text
compileflow.process.version.{identityDigest}
```

See the [distributed integration guide](hot-deploy-integration.md) for key encoding and validation. In
`PROJECTION_STORE` mode, the artifact payload must fit the selected backend's documented per-item capacity.

See [configuration](configuration.md), [distributed integration](hot-deploy-integration.md), and
[operations](operations-playbook.md).
