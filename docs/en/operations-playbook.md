# Deployment and runtime operations

This page is the operational checklist for Deploy and Workbench Server. Command procedures are in
[Hot deployment](hot-deploy.md); persisted process recovery is covered by
[Durable operations](durable-operations-runbook.md).

## Before a change

- Confirm the target namespace, process code, Alias, exact version, and current route revision.
- Confirm the content digest and immutable version identity.
- Confirm database health, migration baseline, outbox state, projection-store access, and runtime capacity.
- Define the observation window, minimum canary sample, and rollback authority before changing traffic.
- Keep API keys, routing keys, payload variables, and lease tokens out of logs and tickets.

## Publish and route

1. Validate the definition and run the relevant preflight and release tests.
2. Publish a new immutable version. Reusing an identity for different content is prohibited.
3. Read the current Alias route and submit its revision as the expected precondition.
4. Create an all-at-once or canary Rollout with a stable idempotency key.
5. Verify control-plane commit, outbox delivery, local-ready state, and a real execution.

In `DISTRIBUTED` topology, a successful command proves the control-plane transaction, not every worker's readiness. A worker executes its last atomically published local-ready revision. If no valid revision exists, Alias resolution fails closed.

Canary weights are integer basis points in `1..9999`. Evaluate technical and business signals over a declared window; the built-in health endpoint is read-only evidence and does not promote or abort a rollout. Treat `412 Precondition Failed` as a stale-concurrency signal and reread state. Treat `409 Conflict` as a semantic conflict requiring a decision.

## Abort and rollback

To abort an active canary, stop further weight changes, submit `AbortRolloutCommand` with the current rollout revision and a reason, then verify that the captured baseline is active.

To roll back a completed deployment, select the completed rollout that still owns the current route revision and has a captured baseline. Submit a new rollback operation with a fresh idempotency key and verify convergence. Previous rollout history is not edited.

## Async invocation health

Use `/api/async-invocations/health` as a queue snapshot:

- ready work waits for capacity; delayed work waits for its retry time;
- local running and dispatched counts are node-local; persisted queue, dead-letter, and expired-lease counts are shared facts;
- `degraded` means a dead letter or expired running lease exists, not that an SLO was violated;
- correlate an invocation with its attempt ledger, trace, effective version, Alias revision, and runtime diagnostics;
- requeue only after repairing the model, artifact, capacity, or dependency cause.

A running invocation is recovered after its lease expires. Fencing rejects a late completion from the previous owner. Do not edit queue rows or shorten leases to force recovery.

## Incident checks

### Publication rejected

Inspect the typed `DeploymentException` code and request id. Check identity, model type, UTF-8 size, actor, and digest assertion. Use preflight for model diagnosis and publish changed content under a new version identity.

### Outbox or projection backlog

Check deployment health and distinguish unavailable state from a valid zero count. Inspect pending, processing, expired claims, retries, and dead letters separately. Verify credentials, write latency, and projection CAS before retrying. Duplicate delivery is expected; conflicting content is not.

### Runtime installation failure

Inspect `DeploymentRuntime.snapshot()` for the exact version and backoff reason. Verify artifact existence, digest, resolver access, compiler resources, and executor capacity. Do not bypass local-ready admission or use an unversioned fallback.

### Restart

Verify schema admission, outbox dispatch, reconciliation, route convergence, expired lease recovery, and one Alias execution after restart. Confirm the effective version and route revision in secured logs or diagnostics; built-in metric labels do not carry these unbounded identities.

## Routine review

Review publication conflicts, route and rollout revisions, outbox backlog, runtime failures, async dead letters, expired leases, and convergence time. Retain versions according to an explicit reference and rollback policy; never delete a version still referenced by a route, retained Run, or required recovery window.
