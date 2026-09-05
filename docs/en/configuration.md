# CompileFlow Configuration

CompileFlow parses external configuration once at an application boundary, validates it, and passes typed snapshots to
the components that own it. Core execution does not read environment variables, JVM system properties, or a mutable
global resolver.

The settings below are the supported configuration surface. Defaults and constraints are part of the contract; internal
implementation fields and undocumented aliases are not supported configuration.

## Boundaries And Precedence

| Boundary            | Namespace                        | Lifetime                 | Source of truth                                  |
|---------------------|----------------------------------|--------------------------|--------------------------------------------------|
| Java engine         | `compileflow.engine.*`           | One `ProcessEngine`      | Immutable `ProcessEngineConfig`                  |
| Deployment          | `compileflow.deploy.*`           | Control/data-plane beans | Immutable deployment configuration tree          |
| Durable runtime     | `compileflow.durable.*`          | Durable runtime          | Immutable `CompileFlowDurableProperties`         |
| Workbench Server    | `compileflow.workbench.server.*` | Java application         | Immutable `CompileFlowWorkbenchServerProperties` |
| Development gateway | `COMPILEFLOW_DEV_GATEWAY_*`      | Loopback Node.js mock    | Frozen `DevGatewayConfig`                        |
| Web application     | `VITE_COMPILEFLOW_*`             | Vite build artifact      | Frozen `AppBuildConfig`                          |

Local-evaluation delivery templates accept a few additional interpolation inputs; they are not a seventh application
configuration boundary. Compose translates `COMPILEFLOW_WORKBENCH_DATABASE_*` into PostgreSQL and standard Spring
datasource configuration. The loopback nginx container receives a derived
`COMPILEFLOW_WORKBENCH_LOCAL_GATEWAY_UPSTREAM_API_KEY` only to inject the same Workbench Server key. These values never
enter the browser build or a second Java/Node configuration resolver.

Spring Boot uses its standard property-source precedence and relaxed binding. An absent value uses its documented
default. An explicitly invalid value, an unknown field from an enumerable Spring configuration source inside a supported
prefix, an unknown managed Server/Dev Gateway/Web variable, or an invalid cross-field combination fails startup or the
Web build. Spring excludes raw system-environment and system-property sources from unknown-field enumeration; their
known relaxed-binding names still work, but only the Workbench Server's managed
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` aliases add an explicit environment-variable whitelist. Engine, deploy, and Durable
configuration is not dynamically refreshed; create a new engine or restart the owning component to apply changes.

For AWS Lambda or Alibaba Cloud Function Compute, construct and retain the Java engine during execution-environment
initialization so the validated snapshot and compiled runtimes can be reused by warm invocations. Keep environment
variables small and deployment-specific; both platforms limit the aggregate to 4 KB. Retrieve credentials from the
platform secret service or execution role rather than treating environment variables as a secret store. The deployment
control plane and Workbench Server own schedulers, leases, database coordination, and background dispatch, so deploy
them as long-lived services or containers instead of assuming function handlers can preserve that lifecycle.

## Engine

Minimal configuration:

```yaml
compileflow:
  engine:
    enabled: true
    model-type: TBBPM
```

Every engine `Duration` setting uses whole-millisecond precision and must be representable as a Java `long` number of
milliseconds. Positive durations are at least `1ms`; explicitly non-negative settings may use `0ms`. Values with finer
precision or excessive magnitude fail configuration validation instead of being truncated or surfacing as an arithmetic
error during engine construction.

### Request-scoped execution options

`ProcessExecutionOptions` is invocation data, not external application configuration. Pass a correlation ID, the opaque
deterministic routing key, and only the custom attributes required by the selected policy directly to
`execute(...)` or `trigger(...)`:

```java
ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .invocationId("order-20260716-42")
        .aliasRouting(new AliasRoutingOptions("user-7", Map.of("region", "eu-west")))
        .build();

engine.execute(
        ProcessRef.alias("default", "order.process", "prod"),
        variables,
        options).orElseThrow();
```

The engine takes an immutable defensive snapshot. Routing attributes are strings and remain isolated from process
variables and outputs. The routing key is also excluded from logs, events, results, and persistence. Do not place
request identity, aliases, or routing keys in environment variables or the process-variable map. Names beginning with
`__cf_` are reserved for Engine-owned metadata and cannot be used by application routing attributes or root Process
variables.

Configuration is presented in three levels: **Essential** selects the engine contract, **Operational** controls
production behavior, and **Advanced** exposes concrete executor or diagnostic tuning. Quick starts should use only
Essential settings; production owners normally add Operational settings; Advanced values should be changed only from
measurements.

### General

| Level       | Property                              | Default    | Constraint / purpose                                                                                                                                             |
|-------------|---------------------------------------|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Essential   | `compileflow.engine.enabled`          | `true`     | Enables engine auto-configuration.                                                                                                                               |
| Essential   | `compileflow.engine.model-type`       | `TBBPM`    | `TBBPM` or `BPMN`.                                                                                                                                               |
| Advanced    | `compileflow.engine.runtime-mode`     | `COMPILED` | Selects the disposable runtime realization. `INTERPRETED` is intended for diagnostics and differential verification.                                             |
| Operational | `compileflow.engine.call.max-depth`   | `32`       | Root-inclusive synchronous process-call depth; from `1` through `256`. A deeper nested or recursive call fails before its actions run and reports the call path. |
| Operational | `compileflow.engine.shutdown.timeout` | `15s`      | Total budget shared by operation draining and executor shutdown. The engine reserves an internal portion for forced termination; at least `1ms`.                |

### Advanced Executor Tuning

| Property                                                             | Default                    | Constraint / purpose                                                                                                                                    |
|----------------------------------------------------------------------|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.engine.executor.runtime-load.max-concurrency`                | `min(2, max(1, CPUs / 8))` | Maximum concurrent local runtime loads; positive.                                                                                                       |
| `compileflow.engine.executor.runtime-load.max-pending`            | `4`                        | Maximum additional runtime loads waiting for a slot; non-negative. `0` rejects immediately when all slots are busy.                                      |
| `compileflow.engine.executor.action-timeout.max-concurrency`               | `max(4, CPUs)`             | Maximum concurrent actions requiring timeout enforcement; positive.                                                                                     |
| `compileflow.engine.executor.action-timeout.max-pending`            | `32`                       | Maximum additional timeout-enforced action attempts waiting for a slot; non-negative. Queue time counts toward the action timeout.                       |
| `compileflow.engine.executor.action-timeout.cancellation-grace-period` | `2s`                       | Maximum cooperative drain time after timeout or cancellation. Retry fails closed if the prior attempt does not stop, preventing overlapping attempts.  |
| `compileflow.engine.executor.parallel.cancellation-grace-period`     | `2s`                       | Maximum cooperative drain time after one parallel branch fails; lingering branches are reported as an execution interruption.                           |

The reusable Spring starter creates one engine selected by `model-type`. Workbench Server always hosts one TBBPM engine
and one BPMN engine; `model-type` selects only the conventional primary
`ProcessEngine`, `ProcessRuntimeManager`, and `ProcessToolingService` beans. The remaining engine settings are applied
independently to both engines, so executor and cache limits are per engine, not process-wide totals.
`COMPILED` remains the default. `INTERPRETED` is a reference realization, not a compiler-free host profile: the supported
2.0 host still includes `jdk.compiler` for definition validation and preparation.

Parallel branches use one thread per orchestration task. Java 21 and newer use virtual threads. Java 17 uses a finite,
unqueued platform-thread executor with the internal CPU-adaptive ceiling `max(8, min(64, CPUs * 4))`; at its ceiling,
the submitting flow thread runs the branch, providing backpressure without queuing nested work behind a waiting parent.
This compatibility mechanism is intentionally not a public concurrency control. Actions without a timeout run on the
invoking flow thread; actions with a timeout use the bounded action-timeout pool. Compilation, action-timeout, and event pools reject overload and never execute
rejected work on the submitting thread. Preflight uses a separate fixed bounded coordinator pool with an internal limit of
two workers and four pending tasks; queue wait counts toward its end-to-end timeout, and overload is rejected rather than
executed by the caller. Caller execution is limited to nested branch orchestration, where it
preserves progress without invalidating timeout measurement.

### Cache

| Property                                                         | Default | Constraint / purpose                                                          |
|------------------------------------------------------------------|---------|-------------------------------------------------------------------------------|
| `compileflow.engine.max-resident-runtimes`                       | `2048`  | Hard limit on cached and owner-retained runtimes in this engine; positive.     |

Java Code is represented as `Script(language="java", source)` and is supported by `compileflow-core` by default.
Its typed wrapper is compiled during process runtime load and retained by that exact runtime for its lifetime. The exact
source remains Process-version truth; there is no persisted bytecode
cache or execution-time compiler setting.

### Scripts

Core registers QLExpress 4 and the trusted Java Code executor by default. QL uses a fixed language profile: isolated
host access, a fixed safe function set, a one-second deadline, and a maximum array dimension of 10,000. These are
language guarantees rather than application tuning knobs. The bundled `qlexpress` and `java` names cannot be silently
replaced; custom QL dialects, Groovy, MVEL, and other languages use distinct `ScriptExecutor` names whose owners must
define their own security, timeout, cache, ClassLoader, and lifecycle policies.

### Process Definition Loading

| Property                                 | Default | Constraint / purpose                                                                                                  |
|------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------------------|
| `compileflow.engine.definition.max-size` | `4MB`   | Maximum UTF-8/binary size of one inline or classpath definition; from `1B` through the hard `100MB` safety ceiling.  |

The loader reads at most `max-size + 1` bytes and freezes one immutable byte snapshot before schema validation. Schema
validation and model parsing therefore see exactly the same content. Classpath locations that resolve to HTTP, HTTPS,
FTP, or FTPS are rejected; remote artifact retrieval belongs to an authenticated application or
deployment resolver.

### Runtime Loading And Java Diagnostics

| Property                                                        | Default     | Constraint / purpose                                                          |
|-----------------------------------------------------------------|-------------|-------------------------------------------------------------------------------|
| `compileflow.engine.runtime-load-timeout`                       | `10s`       | Maximum synchronous caller wait for a runtime load; at least `1ms`.           |
| `compileflow.engine.java-diagnostics.debug.symbols`            | `LINES`     | `NONE`, `LINES`, or `FULL`; never writes files by itself.                     |
| `compileflow.engine.java-diagnostics.debug.output-directory`   | unset       | Enables stable `source/` and `metadata/` trees keyed by generated class name. |
| `compileflow.engine.java-diagnostics.debug.bytecode-enabled`   | `false`     | Also exports `.class` files; requires `output-directory`.                     |

Runtime loading is in-memory by default. Debug output can contain generated source, scripts, expressions, constants, and
class files. Treat the directory as sensitive, secure the configured root with OS permissions or ACLs, restrict
retention, and do not place it under a served web root. Generated source is written to a stable package-relative path
under `source/`; optional bytecode uses the matching path under `classes/`, and sidecar properties are stored under
`metadata/`. Recompiling the same generated class atomically replaces these files, so IDE breakpoints remain attached to
one file URL. In IntelliJ IDEA, mark `<output-directory>/source` as a Sources Root and place breakpoints on executable
lines. Durable class names include the normalized full process code and a program-digest prefix, while the source header
retains the original process code for direct identification.

Each single-flight runtime load attempts Java compilation once. Parsing, code generation, class resolution, and Java diagnostics
are deterministic for the resolved source snapshot, so the engine does not blindly retry them. A later caller may start
a fresh attempt after the definition or runtime environment is corrected. Deployment convergence and Workbench persisted
asynchronous invocation own their separate, bounded retry policies.

`runtime-load-timeout` bounds only a synchronous caller's wait. One waiter timing out does not cancel a shared runtime
load; an admitted task continues and enters the node-local cache if it succeeds, so a later caller can reuse it. The JDK
compiler has no reliable hard-termination contract. Definition size, runtime-load limits, and the bounded pending admission provide
the resource limits.

```yaml
compileflow:
  engine:
    runtime-load-timeout: 20s
    executor:
      runtime-load:
        max-concurrency: 2
        max-pending: 4
    java-diagnostics:
      debug:
        symbols: FULL
        output-directory: /var/lib/compileflow/debug-runtime
        bytecode-enabled: true
```

### Observability And Plugins

| Level       | Property                                                        | Default | Constraint / purpose                                                                                   |
|-------------|-----------------------------------------------------------------|---------|--------------------------------------------------------------------------------------------------------|
| Operational | `compileflow.engine.observability.events.async`                 | `true`  | Uses bounded, best-effort asynchronous lifecycle-event delivery.                                       |
| Operational | `compileflow.engine.observability.events.max-concurrency`           | `2`     | Maximum concurrent lifecycle-event deliveries; positive.                                               |
| Operational | `compileflow.engine.observability.events.max-pending`        | `16`    | Maximum additional asynchronous event deliveries waiting for a slot; non-negative. `0` rejects saturated delivery. |
| Operational | `compileflow.engine.observability.mdc-propagation-enabled`      | `false` | Copies MDC across engine executor boundaries.                                                          |
| Advanced    | `compileflow.engine.plugins.discovery-enabled`                  | `false` | Discovers `ProcessEnginePlugin` implementations via `ServiceLoader` only when explicitly enabled.      |
| Operational | `compileflow.engine.components.allowed-beans`                   | `[]`    | Exact Spring bean names exposed to process definitions; empty denies automatic bean access.            |

Micrometer binders are registered automatically when a `MeterRegistry` exists. Use Spring Boot's standard
`management.metrics.enable.*` meter filters instead of a duplicate CompileFlow switch. Programmatic
`ProcessObservabilityConfig` contains only event-dispatch and MDC-propagation behavior.

Event listeners are construction-time collaborators, not string configuration. Standalone applications register them
with `ProcessEngineConfig.Builder.eventListener(...)`; the Spring starter collects ordered
`ProcessEventListener` beans into the immutable engine configuration snapshot.

Workbench Server overrides `events.async` to `false` for its terminal execution-log listener. This makes each request
attempt the database observation before returning and avoids event-queue sampling bias. Listener failures are still
isolated from process outcomes, so this is not a durable audit guarantee.

Trace capture is also a construction-time collaborator. Standalone applications may set one `TraceIdProvider` with
`ProcessEngineConfig.Builder.traceIdProvider(...)`. Spring uses exactly one application `TraceIdProvider` bean when
present, otherwise reads the standard `traceId` MDC entry. Only identifiers from 1 through 128 characters with no
surrounding whitespace are accepted. Blank, oversized, whitespace-padded, or failing provider/MDC values fall through
without normalization or truncation to a local 32-character
hexadecimal identifier, so invalid observability input cannot change process execution outcome.

Standalone applications expose components with one explicit `ProcessComponentResolver`. Spring applications may instead
list exact bean names in `compileflow.engine.components.allowed-beans`. The default empty list denies all automatic bean
access; factory dereference names beginning with `&`, blanks, duplicates, and whitespace-normalized aliases are
rejected. A custom resolver and a non-empty allowlist are mutually exclusive so no policy is silently ignored. Resolution
uses both the declared component name and required Java type; absence or incompatibility fails without constructing the
type declared by the flow.

Standalone applications may set one `ProcessContextPropagator` through
`ProcessEngineConfig.Builder.contextPropagator(...)`. It captures application ambient context for ProcessEngine-owned
thread handoffs and restores the worker's previous context afterward; it never persists or restores Durable context.
When Micrometer Context Propagation is present, Spring supplies an adapter automatically and a custom
`ProcessContextPropagator` bean replaces it.

Other typed extension points (`ScriptExecutor`, `RetryPolicy`, `FailureHandler`, `ProcessEnginePlugin`) remain
construction-time collaborators. See the [extension guide](extension-guide.md) for
registration, ordering, ownership, and failure semantics.

## Deployment

The reusable starter keeps deployment off until an application opts in. CompileFlow Workbench Server explicitly enables
the complete embedded topology in its own `application.yml`.

### Topology And Process Roles

| Property                                    | Default    | Constraint / purpose                                                                                                                                                                  |
|---------------------------------------------|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.enabled`                | `false`    | Master opt-in for deployment repositories, routing, control, runtime, and metrics.                                                                                                    |
| `compileflow.deploy.topology`               | `EMBEDDED` | `EMBEDDED` activates committed Aliases locally before command return and retains outbox recovery; `DISTRIBUTED` uses a sync channel.                                                  |
| `compileflow.deploy.control-plane-enabled`  | `true`     | Hosts release commands, outbox dispatch, and distributed reconciliation.                                                                                                              |
| `compileflow.deploy.runtime-worker-enabled` | `false`    | In `DISTRIBUTED` topology, hosts channel subscription, demand planning, artifact resolution, and installation. Embedded local convergence is selected by `topology`, not this switch. |

An enabled deployment must select at least one process role. `EMBEDDED` requires
`control-plane-enabled=true` and `runtime-worker-enabled=false`. `DISTRIBUTED` supports control-only, worker-only, and
deliberate combined processes. A database-backed worker-only worker should set `control-plane-enabled=false`; having a
`DataSource` then does not start release or outbox workers.

JDBC repositories are auto-configured only when deployment is enabled and a `DataSource` exists. There is no in-memory
fallback. Alias state and its routing event always commit on the same JDBC connection; mandatory topology infrastructure
fails startup instead of selecting a non-transactional or disconnected path. The resulting topology and consistency
requirements are part of the supported configuration contract.

### Publication, Artifacts, And Runtime

| Property                                         | Default                | Constraint / purpose                                                                                                                                                                                                          |
|--------------------------------------------------|------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.artifact.mode`               | `DATABASE`             | `DATABASE` or `CHANNEL`; shared by publisher and runtime resolver.                                                                                                                                                            |
| `compileflow.deploy.artifact.key-prefix`         | `compileflow.process.` | Portable channel prefix of at most 128 ASCII letters, digits, `.`, `_`, `:`, or `-`.                                                                                                                                          |
| `compileflow.deploy.artifact.operation-timeout`  | `5s`                   | Deadline for each channel artifact read or atomic compare-and-set; at least `1ms`.                                                                                                                                            |
| `compileflow.deploy.runtime.failure-backoff`     | `5m`                   | Positive whole-millisecond delay before retrying a failed installation in either topology; it must also fit a Java `long` nanosecond interval because the runtime uses a monotonic deadline.                                  |
| `compileflow.deploy.runtime.convergence-timeout` | `30s`                  | Maximum request wait for embedded on-demand preparation; convergence continues after a timeout and distributed runtime nodes do not use this value.                                                                           |
| `compileflow.deploy.runtime.concurrency`         | `1`                    | Maximum concurrent distributed artifact installations; `1..256`. Embedded delivery is synchronous and does not consume this value.                                                                                            |
| `compileflow.deploy.runtime.queue-capacity`      | `256`                  | Distributed installation queue; `1..10000`. Together with `concurrency`, this bounds admitted installation records in distributed runtime processes. Embedded admission derives from the engine runtime-load capacity.        |

Every deployment `Duration` property is consumed with millisecond precision and must therefore be an exact
whole-millisecond value representable as a Java `long`. `runtime.failure-backoff` additionally has to fit a `long`
nanosecond interval for its monotonic deadline. Positive values are at least `1ms`; only properties explicitly
documented as non-negative, such as outbox `retention`, accept `0ms`. Finer or excessive values fail binding instead of
being truncated or reaching runtime arithmetic.

The embedded topology requires `DATABASE` artifact mode and recovers alias, version, and source content from the shared
repository after memory loss. A distributed runtime requires a `DeploymentSyncChannel`, an artifact repository for
`DATABASE` mode, and at least one explicit or derived routing subscription. Missing dependencies fail startup.

Publication inserts one immutable `(namespace, code, version)` fact containing exact UTF-8 source, model type, SHA-256
digest, metadata, actor, and database timestamp. Row existence is the publication fact; there is no mutable publication
status or preparation lease. Repeating the same identity and content returns the original row without rewriting its
audit fields, while different content is a conflict. Before the first repository write, publication must parse the exact
source and pass its format schema and model-structure checks. This validation does not generate, cache, or install a
node-local runtime. Editors and release pipelines may additionally call
`ProcessToolingService.preflight(...)` with strict options when they need generated-code compilation diagnostics.
Neither control-plane check proves readiness on runtime nodes with different application class paths or components.
Publishing never mutates a route.

The digest is a mandatory first-class version and artifact field, not optional metadata or a feature flag. Artifact
payload parsing, database resolution, and runtime loading all verify identity and integrity. Runtime installation
performs the real engine deployment at the data-plane trust boundary; a failure prevents local readiness and route
activation on that node.

Spring starts the deploy runtime, outbox dispatcher, and reconciler through `SmartLifecycle` after context refresh. The
data plane starts before the control-plane background tasks; shutdown runs in reverse so publishing stops before
subscriptions and installers are closed. Bean construction never starts worker threads.

### Routing, Outbox, And Reconciliation

| Property                                           | Default                   | Constraint / purpose                                                                                                                                                                                                                                                                                           |
|----------------------------------------------------|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.deploy.routing.key-prefix`            | `compileflow.deployment.` | Portable alias-route prefix of at most 128 ASCII letters, digits, `.`, `_`, `:`, or `-`.                                                                                                                                                                                                                       |
| `compileflow.deploy.routing.namespaces`            | `[default]`               | Non-empty unique canonical namespace list used to derive keys.                                                                                                                                                                                                                                                 |
| `compileflow.deploy.routing.codes`                 | `[]`                      | Unique canonical process codes used to derive keys.                                                                                                                                                                                                                                                            |
| `compileflow.deploy.routing.aliases`               | `[production]`            | Unique canonical aliases used to derive route keys; an explicit empty list disables alias subscriptions.                                                                                                                                                                                                       |
| `compileflow.deploy.routing.operation-timeout`     | `5s`                      | Deadline for each routing-state read, atomic compare-and-set, or subscription setup; at least `1ms`.                                                                                                                                                                                                           |
| `compileflow.deploy.outbox.dispatch-interval`      | `1s`                      | At least `1ms`.                                                                                                                                                                                                                                                                                                |
| `compileflow.deploy.outbox.dispatch-batch-size`             | `50`                      | Maximum records in one bounded dispatch slice, `1..1000`; saturated slices continue automatically after a short yield, so this is not a sustained-throughput ceiling.                                                                                                                                           |
| `compileflow.deploy.outbox.retention`              | `24h`                     | Non-negative; `0ms` disables bounded cleanup.                                                                                                                                                                                                                                                                  |
| `compileflow.deploy.outbox.lease-duration`         | `1m`                      | Authority TTL for one claimed delivery attempt; at least `1ms`, longer than `runtime.convergence-timeout` in `EMBEDDED`, and longer than `routing.operation-timeout` in `DISTRIBUTED`. Distributed delivery remains idempotent and lease-token fenced; size this lease above observed end-to-end channel latency. |
| `compileflow.deploy.outbox.retry.initial-delay`    | `5s`                      | Positive first-retry ceiling. Delivery uses capped exponential backoff with runtime-owned full jitter.                                                                                                                                                                                                      |
| `compileflow.deploy.outbox.retry.max-delay`        | `5m`                      | Positive jitter-window cap; at least the initial delay.                                                                                                                                                                                                                                                      |
| `compileflow.deploy.outbox.retry.max-attempts`     | `10`                      | Positive failed-delivery attempts before dead-lettering.                                                                                                                                                                                                                                                    |
| `compileflow.deploy.reconciliation.mode`           | `REPAIR`                  | `DISABLED`, `DETECT`, or `REPAIR`; only derived routing/artifact projections are reconciled.                                                                                                                                                                                                                    |
| `compileflow.deploy.reconciliation.interval`       | `1m`                      | At least `1ms`.                                                                                                                                                                                                                                                                                                |

Outbox dispatch has no static enable switch. Version or alias state and its event are one transaction, so accepting
commands while disabling delivery would create a stale execution view. Suspend the control-plane process as a whole for
planned or incident maintenance so command admission stops with delivery. In `EMBEDDED`, dispatch applies committed
events to local `LocalRoutingState`; in `DISTRIBUTED`, it advances `DeploymentSyncChannel` projections with atomic
exact-content CAS. A late lower revision and a duplicate are successful no-ops. Consumers also filter monotonic
revisions because notifications can arrive out of order. Custom channel adapters must provide a server-side CAS
primitive and must not emulate it with read followed by write. Dispatchers claim rows through persisted `PROCESSING`
leases and fencing tokens; no database lock is held during network delivery. Health reports pending, processing,
expired-claim, and dead-letter counts separately.

### Distributed Transport

CompileFlow does not select a remote transport through `compileflow.deploy.*`. A distributed process must provide
exactly one `DeploymentSyncChannel` bean; provider-specific connection, authentication, namespace, and capacity settings
belong to that adapter's own configuration prefix.

An adapter is valid only when its real backend provides exact current reads, atomic exact-content CAS including
create-if-absent, and eventual update notifications. The starter does not bundle a Nacos adapter because Nacos publish
is create-or-update when the CAS digest is absent and normal client reads may return local failover or snapshot content.
The strict deployment root rejects removed `compileflow.deploy.sync.nacos.*` properties instead of silently ignoring a
transport that is no longer active.

## Durable Strict Profile

Durable runtime composition is opt-in through `compileflow-durable-spring-boot-starter` and requires a `DurableStore`.
The first-party PostgreSQL distribution is `compileflow-durable-spring-boot-starter-postgres`. Explicit
definitions are loaded through Core using the configured `ProcessEngineConfig`. During Version or Alias admission, a
`DurableVersionDefinitionSource` supplies the authoritative exact model type and inline definition when the Version has no
stored Process replica yet; Run recovery does not call this source. Version and Alias starts remain
separate from explicit definition starts; Alias admission additionally requires a `DurableAliasStateSource` backed by
committed Deploy state.
CompileFlow applies the route-bound named `ProcessAliasTargetingPolicy`, when present, before its fixed percentage
selector; admission policy is not a separate Durable SPI. Application capability and provider/runtime
compatibility remain outside Durable identity. Provide a `DurableOutboxSink` only when closed Kernel Integration Events
must be delivered externally. A Wait completion payload is a typed partial update of variables declared by the exact
Process and is validated before commit. `DurableWaitDescriptionProvider` may only derive deterministic read-only
description attributes for `WAIT_COMMITTED` from `DurableWaitDescriptionContext`, which contains the process code,
semantic digest, Wait node, detached state, and lexical bindings; the default adds none.

| Property                                             |      Default | Contract                                                                                                                                      |
|------------------------------------------------------|-------------:|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.durable.enabled`                          |      `false` | Explicitly enables the Durable composition                                                                                                    |
| `compileflow.durable.runtime-mode`                     |   `COMPILED` | Selects `COMPILED` or `INTERPRETED` disposable runtime realization of the same Durable Machine; persisted behavior and identity are unchanged     |
| `compileflow.durable-postgres.migrate`                 |      `false` | Applies the PostgreSQL baseline when true; otherwise validates the externally migrated schema                                                  |
| `compileflow.durable.worker.enabled`                   |       `true` | Starts adaptive Turn, Effect, maintenance, and optional Outbox work                                                                         |
| `compileflow.durable.worker.id`                        |    generated | Optional runtime identity shared by every worker kind, at most 96 characters                                                                   |
| `compileflow.durable.worker.lease-duration`            |        `30s` | Runtime-wide positive whole-millisecond token-lease duration, at most 1 hour; Run, Effect, and Outbox use independent bounded batch-renewal lanes at a derived one-third cadence, so there is no separate renewal-interval knob |
| `compileflow.durable.worker.idle-poll-delay`        |      `100ms` | Initial idle acquisition delay, at most 1 minute; runtime applies exponential backoff and jitter                                                |
| `compileflow.durable.worker.turn-fault-backoff`             |         `1s` | Delay before retrying an unexpected Turn fault, at most 1 hour                                                                              |
| `compileflow.durable.worker.turn-max-steps`            |      `10000` | Maximum Process-node advances in one bounded Machine Turn, range `1..1000000`; exhaustion durably yields instead of failing the Run             |
| `compileflow.durable.worker.max-active-iterations`     |         `32` | Maximum active iterations per Durable parallel collection scope, range `1..64`; current Runtime policy, not persisted Process identity           |
| `compileflow.durable.worker.turn-concurrency`       |          `2` | Maximum concurrent Turn executions, range `1..256`; not a database poller count                                                             |
| `compileflow.durable.worker.effect-concurrency`        |          `8` | Maximum concurrent Effect executions, range `1..256`; not a database poller count                                                              |
| `compileflow.durable.outbox.concurrency`               |          `2` | Maximum concurrent Outbox deliveries, range `1..256`; not a database poller count                                                              |
| `compileflow.durable.outbox.retry.initial-delay`       |         `1s` | First retry-window ceiling; delivery uses capped exponential full jitter                                                                       |
| `compileflow.durable.outbox.retry.max-delay`           |         `1m` | Retry-window cap; at least the initial delay and at most 1 hour                                                                                 |
| `compileflow.durable.outbox.retry.max-attempts`        |        `100` | Range `1..10000`; required `WAIT_COMMITTED` authority delivery is never automatically abandoned                                                 |
| `compileflow.durable.maintenance.interval`             |         `1s` | Positive bounded maintenance/scheduler sweep interval, at most 1 hour; initial phase is staggered per runtime                                   |
| `compileflow.durable.maintenance.batch-size`           |        `100` | Range `1..10000`                                                                                                                               |
| `compileflow.durable.retention.terminal-run`           |     disabled | Optional positive terminal-Run retention, at most 3650 days; active Runs and Runs with required pending Outbox delivery are never selected     |
| `compileflow.durable.retention.unused-process`         |     disabled | Optional positive retention for exact Process Definitions with no Run reference, at most 3650 days; Start protects the complete recovery set transactionally |
| `compileflow.durable.retention.consumed-occurrence`    |     disabled | Optional positive retention for consumed Wait/Effect rows, at most 3650 days; unresolved work and occurrences with pending Outbox are preserved |
| `compileflow.durable.retention.interval`               |         `1h` | Positive interval between bounded retention sweeps, at most 1 day                                                                              |
| `compileflow.durable.cache.runtime-max-size`           |        `256` | Bounded node-local disposable Durable runtime cache, range `1..10000`                                                                          |

Execution capacity and PostgreSQL acquisition cost are independent. A work kind with a backlog expands to its
configured concurrency; an idle kind converges to one adaptive, jittered probe. Capability-not-ready suppression is an
internal runtime policy rather than a public tuning knob. Outbox retries persist a positive full-jitter delay inside an
exponentially growing bounded window, so a sink outage cannot create synchronized fixed-interval retry waves.

Size the lease above observed JVM pause plus tail Store latency. Connection acquisition, network, lock, and statement
timeouts should make a failed renewal return within the derived one-third cadence, preserving another attempt before
expiry. Graceful shutdown stops claims and delayed probes first, then drains executing work without interrupting it;
`spring.lifecycle.timeout-per-shutdown-phase` and the container termination grace period own the outer deadline.

When Micrometer is available, auto-configuration registers `compileflow.durable.operations` with
only bounded `operation`/`outcome` tags and `compileflow.durable.loaded.runtimes` as a node-local
gauge. When Spring Boot Health is available, the Durable contributor probes PostgreSQL authority
time. Three consecutive unexpected machinery faults in an enabled Worker lane report `DEGRADED`. An active
lease-renewal lane reports `DEGRADED` after two failed cycles or two derived intervals without a successful cycle;
Health includes bounded sanitized counters, timestamps, staleness, and failure types. Worker faults are logged with rate
limiting; renewal faults remain visible through metrics and Health. Missing process-scoped Action/Effect capabilities
and an unconfigured optional Outbox sink are reported as details rather than making the entire Engine unhealthy.

Unknown fields under `compileflow.durable` are rejected. Duration values must have whole-millisecond precision. Missing
PostgreSQL schema fails startup. Embedded queries use typed keyset cursors and require no Kernel page-token keyring.
Wait tokens are random capabilities and only their digests are persisted. Process snapshots use the Kernel's fixed
portable-value envelope and intentionally have no application codec or payload-encryption Provider SPI. Transport
authentication, authorization, request dedupe, and opaque page-token protection are configured by the outer adapter.

## Workbench Server

The readable `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` variables below form an explicit, prefix-owned API. The dedicated
`CONFIG_` segment avoids collisions with Kubernetes service-link variables such as
`COMPILEFLOW_WORKBENCH_SERVER_SERVICE_HOST`. An early startup adapter rejects unknown names inside the owned prefix and
translates known names at OS-environment precedence. Spring Boot excludes the raw system environment from unknown-field
enumeration, while the adapter's canonical alias source remains subject to strict binding. It defines no defaults; the
immutable
`CompileFlowWorkbenchServerProperties` schema remains the single source of defaults and validation.

| Property                                                                | Default                    | Environment variable                                                                                                                                                   |
|-------------------------------------------------------------------------|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `compileflow.workbench.server.authentication.mode`                      | `API_KEY`                  | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE`; `API_KEY` or `DISABLED`                                                                                     |
| `compileflow.workbench.server.authentication.api-key`                   | required in `API_KEY` mode | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`; 32..256 URL-safe ASCII characters (`A-Z`, `a-z`, `0-9`, `.`, `_`, `~`, `-`) when set                     |
| `compileflow.workbench.server.authentication.service-principal`         | required                   | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL`; stable 1..128 character service actor represented by the credential                            |
| `compileflow.workbench.server.http.max-request-size`                    | `10MB`                     | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_HTTP_MAX_REQUEST_SIZE`; valid range `1B..100MB`                                                                                   |
| `compileflow.workbench.server.database.migrate`                         | `false`                    | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE`; explicitly grant this process authority to apply Deploy V1 and Workbench V2 migrations                         |
| `compileflow.workbench.server.preview-execution.enabled`                | `false`                    | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED`; exposes trusted draft execution when `true`                                                           |
| `compileflow.workbench.server.execution-log.max-query-rows`             | `10000`                    | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS`; valid range `1..100000`                                                                            |
| `compileflow.workbench.server.execution-log.purge-batch-size`           | `1000`                     | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_PURGE_BATCH_SIZE`; stable-order rows removed by one purge request, range `1..10000`                                  |
| `compileflow.workbench.server.async-invocation.concurrency`             | `4`                        | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY`                                                                                                     |
| `compileflow.workbench.server.async-invocation.queue-capacity`          | `256`                      | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_QUEUE_CAPACITY`                                                                                                  |
| `compileflow.workbench.server.async-invocation.dispatch-batch-size`     | `50`                       | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_BATCH_SIZE`; maximum records in one bounded dispatch slice, with automatic catch-up while backlog remains.             |
| `compileflow.workbench.server.async-invocation.dispatch-interval`       | `1s`                       | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_INTERVAL`                                                                                               |
| `compileflow.workbench.server.async-invocation.lease-duration`          | `30s`                      | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_DURATION`; renewal runs at a derived one-third cadence.                                                     |
| `compileflow.workbench.server.async-invocation.lease-recovery-interval` | `5s`                       | `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_LEASE_RECOVERY_INTERVAL`                                                                                         |

Workbench 2.0 owns one product namespace, `default`. Published execution requests therefore accept a version or Alias but
no caller-selected namespace; responses and logs retain the effective namespace as attribution. Supporting multiple
namespaces requires namespace-aware draft identity, URLs, authorization, persistence, and UI selection together, not a
standalone configuration switch.

Draft preview executes submitted definitions as trusted server-side code. It is not a sandbox or validation endpoint,
and remains unavailable unless `preview-execution.enabled=true`. The `dev` profile enables it explicitly for local
development; production deployments should leave it disabled unless their gateway grants this capability only to trusted
flow authors and the Server runs with appropriately restricted host, network, bean, and database privileges.

Worker threads are limited to `1..256`, the local queue to `1..10000`, and each dispatch batch to `1..1000`; a batch
cannot exceed the configured worker plus queue capacity. Dispatch, lease renewal, and lease recovery use three
independent scheduler threads so a delayed scan cannot starve lease renewal. The deploy data plane, control plane,
reconciler, async scheduler, and worker start in dependency order inside Spring Boot's web lifecycle window. Scheduled
cycles remain gated until the worker is running. On `ContextClosedEvent`, the worker immediately stops dispatch and
expired-lease recovery, but the scheduler deliberately remains alive to renew already-owned attempts. Spring Boot then
closes HTTP admission and drains the worker under the standard `spring.lifecycle.timeout-per-shutdown-phase` budget;
bean destruction closes the now-idle scheduler after the worker stops. This separation prevents both new claims during
drain and replay caused by an early scheduler shutdown.

The async queue is a database-authoritative state machine. Dispatch considers only rows whose durable `available_at`
deadline has arrived. Every running attempt has a unique fencing token and renewable lease; completion requires the
current token and an unexpired lease, so a recovered task cannot be overwritten by a late former worker. Retry delay is
persisted and never implemented by sleeping a worker thread. Queue timestamps use the database clock across Server
nodes. Request-level `maxAttempts` is limited to `1..100`, and `retryDelayMs` to `0..604800000` milliseconds.
`maxAttempts` defaults to `1`; retries are opt-in because a timeout or lost response does not prove that process side
effects were absent.

Configuring more than one attempt provides at-least-once execution, not exactly-once external effects. Side-effecting
actions must be idempotent using a stable business key in process parameters. The HTTP fields and bounds are defined by
the [Workbench Server OpenAPI contract](../specs/openapi/README.md).

Authentication fails closed: the default `API_KEY` mode requires a valid key. `DISABLED` is accepted only when an
explicit
`dev` or `test` profile is active. The `prod` profile cannot be combined with either local profile, regardless of
authentication mode. Development and production both use PostgreSQL; the dev profile supplies local URL and username
defaults but requires a non-empty effective
`spring.datasource.password`. Startup validates that property before creating the connection pool. It can come from any
standard Spring property source; `SPRING_DATASOURCE_PASSWORD` is the recommended environment-variable form. H2 is
test-scoped and is not packaged in the executable Server JAR, so the unprofiled application has no embedded database
fallback.

All async invocation durations are positive whole-millisecond values representable as a Java `long`; the lease-renewal
interval must be at most half of the lease duration. Workbench does not expose a Server CORS configuration. Production
uses one browser origin through an authentication-capable gateway; direct cross-origin browser access is not a supported
topology. Production database settings have no password default and use Spring's standard `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` variables. Where the platform supports mounted secrets,
prefer a secret manager plus Spring Boot's
`spring.config.import=configtree:/run/secrets/` over long-lived credentials in environment variables. Files named
`spring.datasource.password` and `compileflow.workbench.server.authentication.api-key` bind directly to the canonical
properties. Environment variables remain suitable for local evaluation and platforms that inject them securely.

The Server rejects request bodies larger than `compileflow.workbench.server.http.max-request-size` before message
conversion. The same canonical limit configures the Servlet multipart parser for both the complete request and each
uploaded file, so XML import does not have a second independent size policy. The limit applies both to requests with
`Content-Length` and to chunked or otherwise unknown-length bodies; oversized requests receive HTTP 413. Tomcat's
`maxPostSize` is not used for this contract because it limits form parameter parsing, not arbitrary JSON request bodies.

CSV export and canary health evaluation require a complete in-memory execution-log sample. Each query loads at most
`compileflow.workbench.server.execution-log.max-query-rows`; when another matching row exists, the request fails with an
RFC 9457 HTTP 422 Problem Detail whose code is `EXECUTION_LOG_QUERY_LIMIT_EXCEEDED`, instead of returning a truncated or
misleading result. Narrow the time range or filters before raising the bound. The hard maximum prevents one synchronous
request from turning the Server into an unbounded in-memory analysis or export job. Workbench monitoring aggregates are
computed by bounded database grouping over an explicit time range and do not use this row-materialization limit.

Execution-log retention purge is also bounded. One `POST /api/execution-logs/purge` request removes at most
`compileflow.workbench.server.execution-log.purge-batch-size` oldest matching rows in stable
`(logged_at, id)` order. Repeat the request while it reports `hasMore=true`; monitor WAL, dead tuples, locks, and autovacuum
instead of issuing an unbounded catch-up delete.

Flyway is the only schema migration authority for the server application. The default
`compileflow.workbench.server.database.migrate=false` keeps DDL outside the runtime identity. Production applies the
committed Deploy V1 and Workbench V2 migrations through a separately authorized deployment identity. Local development
and the bundled Compose topology explicitly opt in to `database.migrate=true`. The default mode still validates Flyway checksums and rejects every pending
migration before the application becomes ready; `spring.flyway.enabled` must remain enabled. The runtime DML role
therefore needs read access to `flyway_schema_history`, but no schema-creation or migration DDL privileges. Flyway's
destructive `clean` operation is disabled, and Hibernate uses `ddl-auto=validate` to fail startup on schema drift. The
default service configuration also enables graceful shutdown with a 30-second shutdown phase and exposes only Actuator
`health`. Exactly `/actuator/health`, `/actuator/health/liveness`, and `/actuator/health/readiness` are anonymous;
health components, details, and any other health group remain hidden or authenticated. Database pool, HTTP server,
Actuator, and logging tuning continue to use standard `spring.datasource.hikari.*`, `server.*`,
`management.*`, and `logging.*` properties rather than duplicate `compileflow.workbench.server.*` aliases. Container and
pod termination grace periods must exceed the effective Spring lifecycle and engine-executor shutdown budgets; the local
Compose topologies reserve 75 seconds for the provided defaults.

## Workbench

The Node development gateway is a loopback-only preview mock. It parses its configuration once, rejects unknown owned
variables, always binds
`127.0.0.1`, and refuses `NODE_ENV=production`. It has no upstream URL, authentication, proxy, queue, or production
image.

| Variable                                    | Default    | Purpose                                                                                                     |
|---------------------------------------------|------------|-------------------------------------------------------------------------------------------------------------|
| `COMPILEFLOW_DEV_GATEWAY_PORT`              | `3001`     | Loopback TCP port, `1..65535`.                                                                              |
| `COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL`         | `info`     | Structured log threshold: `error`, `warn`, `info`, or `debug`.                                              |
| `COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES` | `10485760` | Preview JSON body limit, `1..10485760` bytes; the default matches Workbench Server's default request limit. |

When the port is overridden, pass the same `COMPILEFLOW_DEV_GATEWAY_PORT` value to both the development-gateway and Vite
processes. Vite consumes it only in its Node.js development-proxy configuration; the value is not exposed to browser
code.

Web `VITE_COMPILEFLOW_*` values are public **build-time** inputs embedded in the JavaScript bundle. They cannot hold
secrets, unknown variables in that application-owned namespace fail the build, and changing a value requires rebuilding
the image. Other tools remain free to own unrelated `VITE_*` names. Browser requests always use same-origin `/api` and
`/health` paths; endpoint routing is a gateway, local nginx, or Vite development-proxy responsibility, not Web config.

| Variable                                 | Default                                  | Purpose                                                                                                                                                                                  |
|------------------------------------------|------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `VITE_COMPILEFLOW_OPERATE_MODE`          | `mock` in development, otherwise `real`  | `mock` or `real`; production builds require `real`.                                                                                                                                      |
| `VITE_COMPILEFLOW_DEBUG`                 | `false`                                  | Strict boolean; enables sanitized diagnostic logging.                                                                                                                                    |
| `VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES` | `true` in `mock` mode, otherwise `false` | Strict boolean selecting the Web-bundled Learn catalog instead of the Server catalog. It must be `true` in `mock` mode because the development gateway does not host the Server catalog. |

The displayed Workbench version is injected from `apps/web/package.json` during the Vite build. It is artifact identity,
not a deployment setting, and therefore has no `VITE_*` override.

Web build modes are `development`, `production`, and `test`. Every deployable environment, including staging, runs the
same production build with the real API, same-origin-only `connect-src`, production-strength script policy, and no
emitted source maps. Development and test modes retain local proxy and diagnostic allowances.
`staging` is a deployment-domain value, not a Web build mode.

## Delivery Toolchain

Build and delivery inputs follow the same single-source and fail-fast rules as runtime configuration:

- Java source, release bytecode, and generated-flow bytecode use Java 17. The same artifact is tested on patched Java
  17, 21, and 25 LTS JDK releases. Maven `release=17` and the runtime compiler's `--release 17` boundary prevent
  accidental use of newer platform APIs. Java 21+ uses virtual threads for orchestration; Java 17 uses an unqueued
  platform-thread fallback. The Server image uses the Java 17 product baseline, and every runtime image must include
  the standard `jdk.compiler` module. The Maven Wrapper pins Maven 3.9.16 and verifies both the distribution and wrapper
  JAR checksums.
- `.node-version` pins Node.js 24.18.0. The root `packageManager` field pins pnpm 11.11.0, and pnpm's `nodeVersion` checks
  new dependencies against the same Node target. Package `engines`, CI, and Docker builds are checked against those
  sources.
- pnpm enforces the engine range, does not auto-install peers, and fails on unresolved or incompatible required peers
  without a broad `ignoreMissing` list. It sets `minimumReleaseAge=1440` to quarantine new dependency releases for
  one day and fails closed when registry metadata omits publication time. Required peers are direct dependencies;
  optional peers are reviewed explicitly.
- Dependency lifecycle scripts are denied unless `allowBuilds` contains a reviewed decision. Only the `esbuild` binary
  verification script is currently allowed.
- Compose defaults to PostgreSQL 18.6 and requires database and API-key secrets at interpolation time instead of
  providing source defaults. CI additionally verifies Deploy and Workbench contracts on PostgreSQL 16.15 and 17.11.

These are repository contracts, not user-overridable application settings. Update their authoritative source and the
automated consistency checks together.

## Programmatic Engine Configuration

```java
JavaDiagnosticsConfig diagnostics = JavaDiagnosticsConfig.builder()
        .debugSymbols(JavaDiagnosticsConfig.DebugSymbols.LINES)
        .build();

ProcessEngineConfig config = ProcessEngineConfig.tbbpmBuilder()
        .maxResidentRuntimes(4096)
        .runtimeLoadTimeout(Duration.ofSeconds(20))
        .javaDiagnostics(diagnostics)
        .build();

try (ProcessEngine engine = ProcessEngineFactory.create(config)) {
    // Deploy and execute flows with this engine-owned immutable snapshot.
}
```

Use one long-lived engine per configuration and close it during application shutdown. Multiple engines in the same JVM
may use different snapshots without sharing mutable configuration.

## Supported Spring Bean Seams

Only the following application-facing API and provider SPI bean types are Supported Spring composition seams:

| Area | Supported bean types |
| --- | --- |
| Process execution | `ProcessDataMapper`, `ProcessEngineConfig`, `ProcessEngine`, `ProcessEventListener`, `TraceIdProvider`, `ProcessComponentResolver`, `ProcessContextPropagator`, `ProcessAliasRouteSource`, `ProcessAliasTargetingPolicy`, `ScriptExecutor`, `RetryPolicy`, `FailureHandler`, `ProcessEnginePlugin` |
| Deploy | `ProcessDeploymentService`, `DeploymentSyncChannel`, `ProcessArtifactSource` |
| Durable execution | `DurableProcessEngine`, `DurableOperatorService`, `DurableStore`, `DurableWaitDescriptionProvider`, `DurableVersionDefinitionSource`, `DurableAliasStateSource`, `DurableOutboxSink` |

Defining one of these beans replaces or supplies that exact composition role. Other
`@ConditionalOnMissingBean` checks, bean method names, implementation classes, executors, schedulers, coordinators,
repositories, and lifecycle adapters are auto-configuration implementation details and are not compatibility seams.
Use the documented API/SPI type, not an implementation package, when customizing composition.
