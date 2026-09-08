# Monitoring and Observability

CompileFlow exposes separate controls for engine events, JVM metrics, and server health. Enable and configure each
surface independently.

| Surface                       | Owner                          | Configuration                                                                |
| ----------------------------- | ------------------------------ | ---------------------------------------------------------------------------- |
| Engine lifecycle events       | Each `ProcessEngine`           | `ProcessEngineConfig` capabilities and `ProcessObservabilityConfig` behavior |
| JVM metrics export            | Spring application             | Micrometer `MeterRegistry` and standard meter filters                        |
| Server health and diagnostics | `compileflow-workbench-server` | Spring Actuator and server endpoints                                         |

## Engine Events

Event types and `ProcessEventListener` live in `compileflow-api`. Standalone applications register listeners on the
engine builder; Spring applications expose listener beans. See the [extension guide](extension-guide.md) for complete
examples.

```yaml
compileflow:
    engine:
        observability:
            events:
                async: true
            mdc-propagation-enabled: false
```

- `events.async` dispatches all lifecycle events on the bounded engine event executor.
- `mdc-propagation-enabled` copies MDC only across engine-owned executor boundaries. It does not make arbitrary
  application executors context-aware.
- Synchronous listeners run on the execution caller path. Use them only for short, bounded operations.
- Listener failures are logged and isolated from engine behavior and other listeners.
- Async delivery is best-effort: different events may overlap or arrive out of order, and an event is dropped with a
  warning when the bounded executor rejects it. Listener order within one event remains deterministic.

Lifecycle events are never a correctness, audit, billing, or reliable-integration authority. Use an application-owned
transaction/outbox for ProcessEngine execution, or the Durable Journal/Outbox for Durable execution.

`ProcessEvent` is a sealed set of immutable records. A ProcessEngine execution-start event contains namespace, process code and invocation
ID; a trigger-start event also contains the requested `ProcessTrigger`. Completion and failure events carry the same
controlled `ProcessExecution` returned by the engine plus separate operational `ExecutionAttribution`; failure events
carry a typed `ProcessError`. The public hierarchy contains only execution and trigger lifecycle events.
Trace ID and event time are common fields. No event can carry routing keys, process variables, source content, arbitrary metadata, or raw exception
objects.

`TraceIdProvider` is a lightweight correlation hook, not a CompileFlow tracing, span, or context-propagation
abstraction. A host tracing integration may adapt the application's current span context without adding a tracing SDK
dependency to Core. It is an engine construction collaborator. Standalone applications configure it on
`ProcessEngineConfig.Builder`; Spring applications may expose exactly one provider bean. Without an application
provider, Spring reads MDC `traceId`. Values longer than 128 characters are treated as unavailable, never truncated.
The engine generates a local 32-character hexadecimal ID when no valid upstream ID is
available, and provider failure never interrupts execution.

## Micrometer

With the standard Spring starter, `CompileFlowEngineMetricsAutoConfiguration` creates its binder only when all of the following are
true:

1. Micrometer is on the classpath.
2. A `MeterRegistry` bean exists.
3. A `ProcessEngine` bean exists.

Capacity gauges read the configuration owned by the actual default engine, including a user-created engine.
For a separately constructed engine, provide a binder for that engine's configuration; the built-in binder does not
infer its capacity from an unrelated `ProcessEngineConfig` bean. The JVM-wide dropped-event counter remains available.

When Durable is enabled and a Durable engine exists, its starter registers a separate binder under the same two Micrometer conditions.

Spring Boot's `management.metrics.enable.*` settings are standard meter filters. They may deny individual meters when
the binder registers them; they do not control whether the binder bean exists.

The built-in binders export bounded counters and aggregate node gauges. In the executor metrics below, `<pool>` is one
of `runtime.load`, `action.timeout`, or `event.delivery`.

Metric names use Micrometer's dotted namespace. They are not Spring configuration keys: for example,
`compileflow.engine.executor.runtime.load.max.concurrency` reports the value configured by
`compileflow.engine.executor.runtime-load.max-concurrency`, and `action.timeout` meters correspond to the
`action-timeout` property group. Keep the kebab-case property spelling in application configuration and the dotted spelling
in metric filters.

| Micrometer name                                              | Meaning                                                                                                                                                                 |
| ------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `compileflow.engine.executor.runtime.load.max.concurrency`   | Configured runtime-load concurrency limit                                                                                                                               |
| `compileflow.engine.executor.runtime.load.max.pending`       | Maximum additional pending runtime loads                                                                                                                                |
| `compileflow.engine.executor.action.timeout.max.concurrency` | Configured timeout-enforced action concurrency limit                                                                                                                    |
| `compileflow.engine.executor.action.timeout.max.pending`     | Maximum additional pending timeout-enforced action attempts                                                                                                             |
| `compileflow.engine.executor.event.delivery.max.concurrency` | Configured event-delivery concurrency limit                                                                                                                             |
| `compileflow.engine.executor.event.delivery.max.pending`     | Maximum additional pending event deliveries                                                                                                                             |
| `compileflow.engine.executor.<pool>.active`                  | Work currently executing in the selected engine-owned pool                                                                                                              |
| `compileflow.engine.executor.<pool>.pending`                 | Work waiting for admission or execution in the selected engine-owned pool                                                                                               |
| `compileflow.engine.executor.<pool>.rejected`                | Cumulative rejected submissions for the selected pool, including saturation and shutdown                                                                                |
| `compileflow.engine.events.dropped`                          | JVM-process-wide cumulative count of best-effort asynchronous lifecycle events rejected across all engine instances                                                     |
| `compileflow.deploy.runtime.install.attempts`                | Runtime installation attempts, tagged by terminal `outcome` and bounded `reason`                                                                                        |
| `compileflow.deploy.alias.convergence`                       | Node-local alias convergence attempts, tagged by terminal `outcome` and bounded `reason`                                                                                |
| `compileflow.deploy.alias.desired.count`                     | Active desired aliases known to this node                                                                                                                               |
| `compileflow.deploy.alias.local_ready.count`                 | Active aliases currently executable on this node                                                                                                                        |
| `compileflow.deploy.alias.pending.count`                     | Desired aliases still converging on this node                                                                                                                           |
| `compileflow.deploy.runtime.retained.count`                  | Exact runtimes retained by the managed deployment runtime                                                                                                               |
| `compileflow.deploy.errors`                                  | Deployment errors, tagged by stable `error.code`                                                                                                                        |
| `compileflow.deploy.operations`                              | Control-plane mutations; `operation` is one of `publish`, `create_rollout`, `rollback`, `update_canary`, `promote`, or `abort`, and `outcome` is `success` or `failure` |
| `compileflow.deploy.reconciliation.runs`                     | Completed control-plane projection-reconciliation cycles                                                                                                                |
| `compileflow.deploy.reconciliation.routing.mismatches`       | Authoritative Alias states that differed from routing projections                                                                                                       |
| `compileflow.deploy.reconciliation.routing.repairs`          | Routing projection corrections enqueued through the outbox                                                                                                              |
| `compileflow.deploy.reconciliation.artifact.checks`          | Active stable/candidate artifact projections checked in `PROJECTION_STORE` mode                                                                                         |
| `compileflow.deploy.reconciliation.artifact.missing`         | Missing active artifact projections observed                                                                                                                            |
| `compileflow.deploy.reconciliation.artifact.repairs`         | Missing active artifact projections recreated by immutable CAS                                                                                                          |
| `compileflow.deploy.reconciliation.artifact.conflicts`       | Conflicting or corrupt active artifact projections that were not overwritten                                                                                            |
| `compileflow.deploy.reconciliation.artifact.failures`        | Total active artifact projection reconciliation failures, including conflicts                                                                                           |
| `compileflow.durable.operations`                             | Durable runtime operations, tagged by bounded `operation` and `outcome` values                                                                                          |
| `compileflow.durable.loaded.runtimes`                        | Node-local disposable Durable process runtimes currently loaded                                                                                                         |

Registry backends may transform names according to their conventions. Do not document backend-specific names as
universal Java meter names.

Default deployment meters never tag `namespace`, process code, alias, version, digest, routing key, invocation ID, or
node ID. Use the deployment runtime diagnostics endpoint, structured logs, or traces for those dimensions.

Execution latency, outcome, and business-level metrics require an application listener because their tags and retention
policy are application decisions. Keep tags bounded: model type and a curated process family are reasonable; invocation
IDs, user IDs, URLs, and raw process variables are not.

## Health and Diagnostics

The starter does not publish a synthetic engine health indicator: the mere existence of an engine bean cannot prove that
user code, capacity, or external dependencies are healthy. When deployment is enabled, it contributes a
deployment-pipeline indicator backed by durable outbox state.

`compileflow-workbench-server` enables liveness/readiness probes and hides health components and details by default.
Expose only the Actuator endpoints required by the deployment platform. Runtime deployment
diagnostics are operational state, not configuration, and should be queried through the diagnostics APIs rather than
encoded as properties.

`/api/deployment-control/health` reports shared outbox counts together with local dispatcher state. `UP` means the
repository is readable, the dispatcher is running, and no failed or expired claim is present. `DEGRADED` identifies a
failed delivery or expired claim; `DOWN` identifies unavailable outbox state or a stopped dispatcher. Pending depth is
reported as a fact, not interpreted against a workload-specific threshold.

`/api/async-invocations/health` separates shared persisted queue counts from `workerId`, `localRunningCount`, and
`dispatchedCount`, which belong only to the responding Server process. `degraded` means a dead letter or expired running
lease exists. `healthy` does not assert queue-latency or throughput objectives: alerting should evaluate ready queue
depth and age against the application's own SLO. A failed repository query is an endpoint failure, never a synthetic
zero-count snapshot.

Workbench Operate execution dashboards query the shared persisted execution log. Metrics, trends, top flows, grouped
errors, and effective-version distribution all use one explicit `1h`, `6h`, `24h`, `7d`, or `30d` window; `24h` is the
default. Instances sharing the same database therefore report the same retained execution facts, while log retention and
explicit deletion bound the available history. Workbench Server uses synchronous terminal-event delivery so a saturated
event queue cannot silently skew these samples. Persistence failures remain isolated from process outcomes; these
operational records are not an exactly-once audit ledger.

The deploy-runtime diagnostics endpoint has a different scope: it reports desired/local-ready convergence, retained
runtimes, retries, and capacity for the current Server node. Do not aggregate those node-local values with the
database-backed execution dashboard or present either scope as the other.

## Production Rules

- Send logs and metrics through application-owned sinks; do not perform unbounded I/O on the execution path.
- Make alert thresholds deployment-specific. The library cannot choose an acceptable latency or failure rate for a
  business process.
- Correlate logs with trace/invocation IDs, but never use those high-cardinality values as metric tags.
- Test listener ordering, failure isolation, and asynchronous behavior when observability is part of the application contract.
- Keep health details and debug compilation artifacts disabled or access-controlled in production.
