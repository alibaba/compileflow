# CompileFlow Durable

CompileFlow Durable adds persistent, recoverable process execution. It stores the process definition, continuation
state, waits, timers, effects, audit records, and outbox events so a run can continue after an application restart.
Durable has its own runtime and API; enabling it does not change the in-memory semantics of `ProcessEngine`.

PostgreSQL and MySQL are first-party Store providers. See
[Supported Surfaces](../docs/en/architecture/supported-surfaces.md) for supported database versions and the complete
support boundary.

## Modules

| Module                                                     | Responsibility                                                                  |
| ---------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `compileflow-durable-api`                                  | Application start, Wait completion, cancellation, query, and operator contracts |
| `compileflow-durable-spi`                                  | Provider-neutral runtime and Store ports                                        |
| `compileflow-durable-testkit`                              | JUnit Store transaction contract for first-party provider verification          |
| `compileflow-durable-runtime`                              | Admission, compilation, Machine execution, workers, recovery, and maintenance   |
| `compileflow-durable-postgresql`                           | First-party PostgreSQL Store and Flyway migrations                              |
| `compileflow-durable-mysql`                                | First-party MySQL Store and Flyway migrations                                   |
| `compileflow-durable-spring-boot-autoconfigure`            | Provider-neutral runtime composition                                            |
| `compileflow-durable-spring-boot-autoconfigure-postgresql` | PostgreSQL Store and schema composition                                         |
| `compileflow-durable-spring-boot-autoconfigure-mysql`      | MySQL Store and schema composition                                              |
| `compileflow-durable-spring-boot-starter`                  | Starter for applications supplying a `DurableStore`                             |
| `compileflow-durable-spring-boot-starter-postgresql`       | First-party PostgreSQL distribution                                             |
| `compileflow-durable-spring-boot-starter-mysql`            | First-party MySQL distribution                                                  |

Applications choose exactly one database starter. If both provider compositions are present,
set `compileflow.durable.database.provider` explicitly; otherwise startup fails closed. A complete custom `DurableStore`
overrides first-party auto-configuration. The aggregate POM is the definitive module list.

## Start and Query a Run

Every start requires a caller-generated `ProcessRunId`. This ID identifies one run for its entire lifetime and is also
used to resolve an ambiguous start response. Do not reuse it as a general idempotency key.

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

Start is asynchronous, and workers advance runnable runs in the background. `getRun` and `listRuns` do not return result
payloads; `getRunResult` distinguishes missing, active, succeeded, failed, and cancelled outcomes.

Resolve an ambiguous start response with `getRun(runId)`. Starting an existing ID fails with `RUN_ALREADY_EXISTS`.
Never assign the same ID to another run, including after retention.

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
- A Process Call pushes another `ProcessInvocation` frame in the same Run; it does not create a second Run.
- Recovery uses stored process semantics, continuation, and committed facts. It does not re-resolve Alias, Version, or
  source locators.
- Generated source and bytecode are disposable. Application implementation and provider identity are deployment
  responsibilities, not Run identity.

The full state machines, transaction boundaries, concurrency rules, retention model, and security ownership are
defined once in the [Durable Architecture](../docs/en/architecture/durable-architecture.md).

## Operations

With a first-party PostgreSQL or MySQL Store, Durable migrations and tables are independent from Deploy and Workbench
storage. Back up and restore the configured Durable Store as one unit, with workers stopped for point-in-time restore.

Workers claim bounded work with database-time leases and fencing tokens. Outbox delivery is at least once and unordered;
consumers deduplicate by event ID. Pause, cancellation, retention, UNKNOWN Effect resolution, and dead-letter handling
remain explicit operator workflows.

Use these guides rather than duplicating operating details here:

- [Durable Process Guide](../docs/en/durable-process.md)
- [Configuration](../docs/en/configuration.md#durable-strict-profile)
- [Operations Runbook](../docs/en/durable-operations-runbook.md)
- [Provider Testing](../docs/en/durable-testing.md)
- [Compatibility Policy](../docs/en/compatibility-policy.md)

## Verification

From the repository root:

```bash
./mvnw test \
  -pl compileflow-durable/compileflow-durable-runtime,compileflow-durable/compileflow-durable-spring-boot-starter-postgresql,compileflow-durable/compileflow-durable-spring-boot-starter-mysql \
  -am
python3 scripts/check_durable_delivery.py
```
