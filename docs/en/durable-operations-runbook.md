# Durable Operations Runbook

Use this runbook to operate the Durable kernel with a first-party PostgreSQL or MySQL Store. Each Provider owns its
schema layout; the physical layout is an operational restore detail, not a kernel invariant.

## 1. Safety rules

- The selected Durable Store is the only execution authority. Never run PostgreSQL and MySQL as competing authorities
  for the same Run set.
- Stop every worker before Store-level restore, migration repair, or manual state inspection that takes locks.
- Never edit Process, Run, Run-Process, Wait, Effect, Journal, or Outbox rows by hand.
- Use the audited operator API for Pause/Resume, Effect resolution, and Outbox resolution.
- Never log snapshot envelopes, Effect payloads, Wait tokens, transport credentials, or page tokens.
- Alias changes affect only future admissions; never use Alias to “move” an existing Run.

## 2. Minimum health evidence

Alert on:

- oldest eligible `RUNNABLE` age and queue depth;
- `RUNNING` rows past `lease_until`;
- due active Timers that remain unresolved;
- `PENDING/RUNNING/UNKNOWN` Effects, especially review-required age;
- `PENDING/DELIVERING/ABANDONED` Outbox events;
- Store transaction latency, pool exhaustion, deadlocks, and migration validation;
- Durable Health `workerMachinery=degraded` or `leaseRenewal=degraded`, per-lane active-authority and consecutive-fault
  counts, and stale last-success/progress timestamps;
- repeated preparation faults for an exact stored Process ID.

Use sanitized Run/Timeline/Outbox APIs for diagnosis. Raw envelopes are not operator projections.
Worker machinery faults and process-scoped capability readiness are different signals: the former can degrade the
node; the latter identifies only the affected stored Process ID.

Lease renewal runs independently for Run, Effect, and Outbox at `lease-duration / 3`. Size the lease above observed JVM
pause plus tail Store latency, and configure connection acquisition, network, lock, and statement timeouts so a failed
renewal call returns before the next cadence. These are DataSource and database limits, not additional Kernel knobs.

## 3. Store outage

1. Keep application workers running only if they fail closed; they must not create local shadow state.
2. Restore connectivity and verify database time, primary role, migration checksum, and connection-pool health.
3. Let maintenance reclaim expired Run, Effect, and Outbox leases.
4. Confirm stale workers cannot commit with old tokens.
5. Watch backlog age until it returns to the normal envelope.

Do not manually flip `RUNNING` rows to `RUNNABLE`; lease reclaim is the authority-preserving path.

## 4. Coordinated PITR

1. Stop every process capable of Start, Wait completion, cancellation, operator commands, worker claims, Outbox delivery, or maintenance.
2. Record the restore point and discard all local program caches.
3. Restore all seven Durable tables as one unit: Process, Run, Run-Process, Wait, Effect, Journal, and Outbox.
4. Validate the Flyway version/checksum and all schema constraints.
5. Start one runtime, prepare the stored Process IDs required by retained Runs, and inspect diagnostics.
6. Start remaining workers and monitor reclaimed leases/backlog.
7. Reconcile external Effects and Outbox consumers from stable occurrence/event IDs.

Never restore only selected tables. Never run pre-restore and post-restore authorities concurrently.

## 5. UNKNOWN Effect

1. Read the sanitized Effect and Timeline view.
2. Confirm the current deployment has the exact Process prepared and the Action/reconcile target available.
3. Query the external provider using the Effect occurrence ID or mapped business key.
4. Prefer automatic reconciliation when it can prove succeeded/not-executed.
5. If proof requires an operator decision, authenticate and authorize in the outer adapter, then call `resolveEffect`
   with the current review revision, actor, a concrete reason, and optional audit-context ID.
6. Do not treat timeout as failure and do not cancel away unresolved external authority.

If the current deployment lacks a required capability, make that capability available to the configured runtime. Kernel
state does not contain application build routing.

## 6. Outbox failure

1. Identify the stable `eventId`, Run, event type, attempt count, and status.
2. Check the authenticated sink and downstream deduplication ledger.
3. If delivery is safe to retry, use the operator retry command.
4. If the event must never be delivered, abandon it with audited authorization.
5. Consumers must remain idempotent because a timeout may occur after downstream acceptance.

Journal facts are not automatically republished; Journal and Outbox have different semantics.

## 7. Stuck Run or missing capability

- `RUNNABLE` with repeated fault backoff: inspect stored-Process runtime preparation, ScriptExecutor registration, Java/Spring
  target resolution, and generated compilation diagnostics.
- `RUNNING` past lease: verify maintenance, database time, and reclaim indexes.
- `WAITING` Event: verify authenticated `WAIT_COMMITTED` delivery and token handling.
- `WAITING` Timer past due: verify timer sweep and database clock.
- `WAITING` Effect: inspect pending/unknown/review state.

Do not automatically mark a poison Run `FAILED` from an infrastructure retry count. Pause it if operator containment is
needed, repair the current deployment, then resume.

## 8. Pause and cancel

Pause is appropriate for containing Run business execution while retaining Wait completion, Timer, reconciliation, Outbox, and
maintenance progress. A `PAUSE_REQUESTED` Run has in-flight authority; wait for the fenced outcome.

Cancel is cooperative. Confirm whether an Effect has uncertain external authority before declaring the incident closed.

## 9. Capability and transport-secret incident

The Kernel has no request-identity HMAC root and no page-token signing key. Rotate HTTP/RPC credentials and opaque
page-token keys according to the owning adapter's protocol. Wait tokens are random bearer capabilities: if one is
exposed, prevent its delivery, inspect whether it was already committed, and follow the application incident process.
Never create a second Kernel result for the same Wait boundary.

## 10. Graceful shutdown

On graceful shutdown, a worker stops admitting claims and cancels delayed probes before waiting for already executing
Turn, Effect, and Outbox work. Those calls are not interrupted, and lease renewal remains available while the Spring
lifecycle phase drains. Set `spring.lifecycle.timeout-per-shutdown-phase` and the container termination grace period
above the longest supported in-flight call. A forced process death after that outer deadline is recovered through lease
expiry and fencing, so externally visible work still requires idempotency.

A stored Process must be executable by the configured engine and registered application capabilities. If a required
capability is unavailable, the Run remains recoverable but does not advance until that capability is restored.

## 11. Incident completion

An incident is complete only when:

- no unexpected expired leases remain;
- eligible backlog age has recovered;
- every UNKNOWN/review Effect has an explicit owner and next action;
- every abandoned Outbox event has an audited decision;
- stored-Process runtime preparation is healthy on intended workers;
- external Effect and event deduplication ledgers agree with Kernel identities;
- the timeline and change record contain no secrets.

See the [architecture](architecture/durable-architecture.md).
