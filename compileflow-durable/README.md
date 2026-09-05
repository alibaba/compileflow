# CompileFlow Durable

CompileFlow Durable is the opt-in persisted execution product for recoverable process Runs. It stores exact process
semantics, continuation state, and committed Wait, Timer, Effect, audit, and Outbox facts. It does not turn the ordinary
`ProcessEngine` into a persistent engine through configuration.

> **Maturity:** Developer Preview. API, SPI, persisted format, and operating procedures may change before promotion to
> Supported. PostgreSQL is the only first-party Store provider; see
> [Supported Surfaces](../docs/architecture/06-SUPPORTED_SURFACES.en.md).

## Modules

| Module | Responsibility |
|---|---|
| `compileflow-durable-api` | Application start, Wait completion, cancellation, query, and operator contracts |
| `compileflow-durable-spi` | Provider-neutral runtime and Store ports |
| `compileflow-durable-testkit` | JUnit Store transaction contract for first-party provider verification |
| `compileflow-durable-runtime` | Admission, compilation, Machine execution, workers, recovery, and maintenance |
| `compileflow-durable-postgres` | First-party PostgreSQL Store and Flyway migrations |
| `compileflow-durable-spring-boot-autoconfigure` | Provider-neutral composition and optional first-party adapters |
| `compileflow-durable-spring-boot-starter` | Starter for applications supplying a `DurableStore` |
| `compileflow-durable-spring-boot-starter-postgres` | First-party PostgreSQL distribution |

Applications using the first-party Store normally depend on the PostgreSQL starter. The aggregate POM is the definitive
module list.

## Start and Query a Run

Every Start requires a caller-allocated `ProcessRunId`. It is a permanent occurrence identity and recovery handle, not
a reusable name or generic idempotency key.

```java
ProcessRunId runId = ProcessRunId.random();

ProcessRun started = durable.start(
    runId,
    ProcessRef.alias("sales", "approval", "production"),
    Map.of("orderId", "o-42"),
    new AliasRoutingOptions("customer-42", Map.of("region", "cn")));

Optional<ProcessRun> current = durable.getRun(runId);
ProcessRunResult result = durable.getRunResult(runId);
```

Start is asynchronous. Independently managed workers advance runnable Runs. `getRun` and `listRuns` are payload-blind;
`getRunResult` distinguishes missing, active, succeeded, failed, and cancelled outcomes.

An ambiguous Start response is resolved with `getRun(runId)`. Starting an existing ID fails with
`RUN_ALREADY_EXISTS`, and the caller must never assign that ID to another Run, including after retention.

## Complete External Work

```java
ProcessRun updated = durable.completeWait(
    waitToken,
    Map.of("approved", true));
```

`WaitToken` is an opaque one-shot capability for one materialized Wait occurrence. Completion validates and commits a
typed partial state update but does not synchronously advance the Run. The integration must protect the raw token as a
credential and keep it out of logs, metric labels, browser URLs, and third-party metadata.

External side effects use Action `execution="effect"`. Recovery is explicit: manual review, bounded retry, or a
declared reconcile action. An uncertain dispatch remains UNKNOWN until policy or an authorized operator resolves it; it
is never reported as known success or failure.

## Identity and Recovery

- Start accepts an explicit `ProcessDefinition`, an exact `ProcessRef.Version`, or a `ProcessRef.Alias`.
- Alias routing happens once at admission. The selected Version is attribution, not recovery authority.
- Admission stores exact model type, process code, UTF-8 definition bytes, digest, and static Process Call bindings.
- Direct Process Calls target Classpath or exact Version. Exact-Version graphs are Version-only.
- A Process Call pushes another `ProcessInvocation` frame in the same Run; it does not create a Child Run.
- Recovery uses stored process semantics, continuation, and committed facts. It does not re-resolve Alias, Version, or
  source locators.
- Generated source and bytecode are disposable. Application implementation and provider identity are deployment
  responsibilities, not Run identity.

The full state machines, transaction boundaries, concurrency rules, retention model, and security ownership are
defined once in the [Durable Architecture](../docs/architecture/10-DURABLE_ARCHITECTURE.en.md).

## Operations

Durable uses a first-party PostgreSQL Store whose migrations and tables are independent from Deploy and Workbench
storage. Back up and restore the complete Durable Store as one unit, with workers stopped for point-in-time restore.

Workers claim bounded work with database-time leases and fencing tokens. Outbox delivery is at least once and unordered;
consumers deduplicate by event ID. Pause, cancellation, retention, UNKNOWN Effect resolution, and dead-letter handling
remain explicit operator workflows.

Use these guides rather than duplicating operating details here:

- [Durable Process Guide](../docs/en/durable-process.md)
- [Configuration](../docs/en/configuration.md#durable-strict-profile)
- [Operations Runbook](../docs/en/durable-operations-runbook.md)
- [Provider Testing](../docs/en/durable-testing.md)
- [Compatibility Policy](../docs/compatibility-policy.md)

## Verification

From the repository root:

```bash
./mvnw test -pl compileflow-durable/compileflow-durable-runtime -am
python3 scripts/check_durable_delivery.py
```
