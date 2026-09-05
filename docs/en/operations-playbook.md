# Deployment And Runtime Operations Playbook

This playbook covers immutable version publication, Alias rollouts, transactional outbox delivery, and runtime
convergence, plus Workbench persisted asynchronous invocation. The database is authoritative for control-plane and async
queue state. Local snapshots, delivery channels, and worker memory are rebuildable projections; there is no in-memory
authority or silent fallback.

## 1. Topology Readiness

Confirm the intended process roles before releasing:

| Process             | Required settings                                                                    |
|---------------------|--------------------------------------------------------------------------------------|
| Embedded Server     | `topology=EMBEDDED`, `control-plane-enabled=true`, `runtime-worker-enabled=false`    |
| Distributed control | `topology=DISTRIBUTED`, `control-plane-enabled=true`, `runtime-worker-enabled=false` |
| Distributed worker  | `topology=DISTRIBUTED`, `control-plane-enabled=false`, `runtime-worker-enabled=true` |

For every production topology:

1. Run Flyway and verify the application schema is at the expected baseline.
2. Verify datasource availability, sync-channel credentials when distributed, and artifact resolver access.
3. Confirm the outbox has no unexplained `FAILED` or expired `PROCESSING` records.
4. Confirm clocks, logs, metrics, and alert delivery are working before traffic changes.

## 2. Publish A Version

1. Validate the BPMN or TBBPM definition and execute release-focused tests.
2. Publish with `ProcessDeploymentService.publish(PublishProcessVersionCommand)` or the corresponding Workbench
   endpoint.
3. Verify `(namespace, code, version)`, model type, exact content digest, actor, and release metadata.
4. Confirm the immutable version row exists and complete any release-specific preflight policy before routing it.
5. Reusing the same version identity for different content is prohibited. Changed content requires a new version.

Publication does not move traffic or install node-local runtime state. Do not edit a published row.

## 3. Create A Rollout

Read the current Alias route immediately before mutation and submit its `routeRevision` as
`expectedRouteRevision`. Every create request also needs a stable idempotency key scoped by route and operation. The
Java deployment command names the same compare-and-set value `expectedAliasRevision`; the Workbench HTTP contract uses
`expectedRouteRevision`.

### All At Once

Create an `ALL_AT_ONCE` rollout targeting the published version. The rollout becomes `COMPLETED` in the same transaction
that updates the route, appends history, and inserts the outbox record.

### Canary

Create a `CANARY` rollout with an initial weight from 1 to 9,999 basis points. The current stable version remains the baseline and
the target becomes the candidate. Increase traffic with `updateCanaryWeight(...)` only after the current step is
healthy. Promote with `promoteRollout(...)` to make the candidate the sole stable version.

Treat `412 Precondition Failed` as a concurrency signal: read current route and rollout state, decide again, and submit
a new request. Do not blindly replay with a replaced revision. Treat `409 Conflict` as a semantic conflict requiring an
operator decision.

## 4. Verify Convergence

A successful rollout response means the control-plane transaction committed. Outbox delivery and runtime installation
remain asynchronous.

Verify both planes:

- Control plane: route revision, rollout phase, rollout events, and outbox state.
- Embedded data plane: local route snapshot, effective execution metadata, and a real execution.
- Distributed data plane: channel delivery, `DeployRuntime.snapshot()`, demanded/in-flight/deployed/backed-off versions,
  and route-attributed execution logs.
- Metrics: `compileflow.deploy.operations`, `compileflow.deploy.runtime.install.attempts`,
  `compileflow.deploy.alias.convergence`, `compileflow.deploy.reconciliation.*`, and the aggregate
  desired/local-ready/pending/retained gauges.

Desired state may be newer than a node's local-ready state during convergence. Execution continues against the last
atomically published local-ready revision. When no local-ready revision exists, alias resolution fails closed. The
execution path never observes a desired route whose selected artifacts have not all been retained.

## 5. Evaluate A Canary

Evaluate a step over a declared observation window and minimum sample count. Use both technical and business signals.

The built-in server health endpoint:

- includes only logs matching namespace, flow, and route alias;
- excludes samples older than rollout creation;
- reports baseline and candidate separately;
- supports absolute candidate error-rate and p95 latency thresholds;
- is read-only and never promotes, aborts, or otherwise changes traffic.

Unattributed logs are excluded from the result. Treat the response as evidence for an operator decision, not as a
deployment controller. External SLO, business KPI, relative baseline analysis, and any automatic decision require a
separately designed policy and authority.

## 6. Abort And Roll Back

### Abort An Active Canary

1. Stop further percentage changes.
2. Call `abortRollout(AbortRolloutCommand)` with the latest rollout revision and a concrete reason.
3. Verify the route returned to the baseline captured when the canary was created.
4. Verify a new route revision was delivered and candidate traffic ceased.

Aborting changes the active canary to `ABORTED`; it does not rewrite earlier events.

### Roll Back A Completed Deployment

1. Select the completed rollout that still owns the current route revision and has a captured baseline.
2. Call `rollbackRollout(RollbackRolloutCommand)` with a stable idempotency key, that route revision, and the
   authenticated actor.
3. Verify the new `ROLLBACK` rollout targets the source rollout's captured baseline and then verify convergence.

The original completed rollout remains unchanged. A superseded or aborted rollout is rejected as an ambiguous rollback
source.

## 7. Operate Persisted Async Invocations

`POST /api/processes/{code}/async-invocations` returns `202` only after the request and its route selector are persisted. An
exact version remains exact. An Alias request selects the stable target after a worker owns the request, then persists
that version and route revision before process code starts. Every later attempt uses the persisted exact root version;
Alias movement cannot retarget an admitted request.

Use `/api/async-invocations/health` as a queue snapshot:

1. Compare `readyQueuedCount` with `delayedQueuedCount`. Ready work is waiting for capacity; delayed work is waiting for
   its declared retry time.
2. Treat `localRunningCount`, `dispatchedCount`, and `workerId` as node-local facts. The persisted queued, running,
   succeeded, dead-letter, and expired-lease counts are shared database facts.
3. Treat `degraded` as a hard anomaly signal: at least one dead letter or expired running lease exists. `healthy` does
   not assert a latency SLO; alert separately on acceptable queue depth and age for the workload.
4. Start correlation with the invocation record and
   `/api/async-invocations/{invocationId}/attempts`. The attempt ledger is complete even when a process dies before the
   engine can emit a trace. For attempts with a `traceId`, continue through the execution log, effective version, Alias
   revision, and runtime diagnostics. Do not log routing keys, payload variables, or lease tokens.

For a dead letter, identify and repair the model, artifact, capacity, or dependency failure before requeueing. A single
record and the bounded bulk endpoint both use a compare-and-set transition from `dead_letter` to `queued`; concurrent or
repeated operator requests cannot reset an already requeued invocation. Requeue preserves the invocation identity and
pinned version, resets the row's attempt counter to grant a fresh retry budget, and leaves prior execution attempts in
the immutable attempt ledger. Only attempts that reached the engine have an execution log and `traceId`.

After a process stops, queued records remain durable. A running record is recovered only after its lease expires. The
next owner receives a new token, and fencing rejects a late completion from the previous process. Do not edit queue rows
or shorten leases to force recovery; verify database time, scheduler activity, and lease settings first.

## 8. Incident Runbooks

### Publication Rejected

1. Inspect the `DeploymentException` code and request id.
2. Verify reference/content identity, model type, UTF-8 size, actor, and any caller digest assertion.
3. Use preflight for model-specific diagnosis. Changed content uses a new version identity.

### Digest Mismatch

1. Stop the release and preserve the payload for audit.
2. Compare the stored digest with one computed from the trusted source.
3. Publish a new immutable version from a trusted artifact source.

### Outbox Backlog

1. Check `/api/deployment-control/health`.
   `outboxStateAvailable=false` means the snapshot is `DOWN`; zero counts are not trustworthy substitutes for an
   unavailable repository.
2. Inspect `PENDING`, `PROCESSING`, expired claims, retry attempts, and `FAILED` records separately.
3. In distributed mode, verify channel availability, credentials, and write latency.
4. Fix the cause before requeueing dead letters. Duplicate delivery is expected and safe because route revisions are
   idempotent. A failed requeue request is an operation failure, not a successful `requeued=0` response.

### Runtime Installation Failure

1. Inspect `DeployRuntime.snapshot()` for the exact backed-off version and reason.
2. Verify the published artifact exists, has a matching digest, and can be resolved by that worker.
3. Verify compiler resources and executor capacity.
4. Do not bypass the local-deployment check or enable a previous-version fallback.

### Server Or Worker Restart

1. Confirm the deployment outbox dispatcher and reconciliation cycle restarted.
2. Confirm desired routes converge to local-ready snapshots before testing Alias execution.
3. Confirm expired async leases are recovered once and queued work resumes without changing its effective version.
4. Execute one Alias request and one persisted async request, then verify their route attribution in execution logs.

## 9. Routine Practice

- Weekly: review publication conflicts, rollout conflicts, outbox backlog, dead letters, and runtime installation
  failures; also review ready async backlog, expired leases, and async dead letters.
- Before peak traffic: rehearse publish, canary, promote, abort, completed-rollout rollback, and node restart recovery
  in staging.
- After incidents: record command identities, route and rollout revisions, outbox sequence, sample scope, and
  convergence time.
- Retain immutable versions according to an explicit policy; never delete a version still referenced by a route or
  retained rollout history.
