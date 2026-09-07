# Java API Reference

The supported Java API lives in `compileflow-api`. Engine implementation classes, parser models, Spring internals, and
Server DTOs are not public Java API unless another document explicitly says so.

## 1. Engine Lifecycle

Create one long-lived engine for each resource/configuration boundary, independent of definition format.
The engine discovers installed TBBPM/BPMN frontends and owns its executors, runtime cache, and generated-class loaders.

```java
try (ProcessEngine engine = ProcessEngineFactory.create()) {
    engine.execute(ProcessDefinition.inline(ProcessModelType.TBBPM, "order", orderXml), input);
    engine.execute(ProcessDefinition.inline(ProcessModelType.BPMN, "payment", paymentXml), input);
}
```

Spring Boot and Workbench use the same single-engine assembly. Add the BPMN frontend module to support BPMN alongside TBBPM.

## 2. Process Identity And Content

`ProcessDefinition` supplies an explicit definition source; `ProcessRef` identifies a published process:

```java
ProcessRef.Version version =
        ProcessRef.version("order.validate", "2026-07-25.1");
ProcessRef.Alias alias =
        ProcessRef.alias("tenant-a", "order.validate", "production");
```

- `Version` selects one exact immutable published version.
- `Alias` selects one authoritative stable/candidate route.

`ProcessRef.DEFAULT_NAMESPACE` is the fixed `"default"` scope. Only overloads without a namespace select it. Factories
and record constructors that receive an explicit namespace reject null, blank, surrounding whitespace, and invalid
identifier characters. Namespace is a logical resource scope, not an authorization boundary by itself. A reference never
carries source content.

`ProcessDefinition` describes how content is supplied or located with an explicit model type, but without namespace, version, or alias:

```java
ProcessDefinition inline = ProcessDefinition.inline(ProcessModelType.TBBPM, "order.validate", xml);
ProcessDefinition classpath =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.validate", "flows/order.bpm");
```

The Definition owns its model type; the engine never guesses it from content or a filename. Inline content is redacted from `toString()`.
Classpath access, UTF-8 decoding, and source-size limits are enforced by the Engine definition loader.

The root API also exposes supported protocol primitives: `ProcessDefinitionDigest` computes the stable exact definition
digest; `ProcessIdentifiers` validates identities without normalization; and `ProcessText` provides explicit
Unicode/text operations. Root failures use `CompileFlowException` and `ErrorCode`. Bounded nested-call rejection uses
`CF_EXEC_013`; its implementation exception type is not a Supported API. These public shapes and documented semantics
are part of the 2.x Java API contract.

## 3. Execution

The canonical execution model is `Map<String, Object>`:

```java
ProcessResult<Map<String, Object>> execute(
        ProcessRef ref,
        Map<String, Object> variables,
        ProcessExecutionOptions options);

ProcessResult<Map<String, Object>> execute(
        ProcessDefinition definition,
        Map<String, Object> variables,
        ProcessExecutionOptions options);
```

ProcessEngine execution input is a closed, partial map of `param` variables declared by the exact Process Definition.
Supplying a `return`, `inner`, or undeclared key returns `CF_VALIDATION_001`; an omitted parameter keeps its model
default, while a present key with a null value is an explicit null. `trigger(...)` is not a Run Start: it
starts a new downstream invocation at a trigger entry, so its Map is a partial state seed over any declared root
variable, while still rejecting undeclared keys. Durable Wait/Event completion does not reuse this state-seed API.

Overloads with default options exist for the two Map forms. Typed overloads are adapters over the same pipeline and use
the configured `ProcessDataMapper`:

```java
<I, O> ProcessResult<O> execute(
        ProcessRef ref,
        I input,
        Class<O> outputType,
        ProcessExecutionOptions options);

<I, O> ProcessResult<O> execute(
        ProcessDefinition definition,
        I input,
        Class<O> outputType,
        ProcessExecutionOptions options);
```

For example:

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.validate", "flows/order.bpm");
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, Map.of("orderId", "A-42"));
```

Published execution uses an exact Version or Alias reference. An inline or classpath definition is an explicit
source, not a hidden "latest version" lookup.

## 4. Request Metadata

`ProcessExecutionOptions` keeps routing controls outside business variables:

```java
ProcessExecutionOptions options = ProcessExecutionOptions.builder()
        .invocationId("request-42")
        .aliasRouting(new AliasRoutingOptions(
                "opaque-cohort-key", Map.of("region", "cn-hangzhou")))
        .build();
```

The routing key is used only during target selection. It is not copied into variables, results, events, logs, metrics,
or HTTP responses. Routing attributes are immutable; fixed routing fields and the Engine-owned `__cf_` prefix are
reserved and cannot be overridden. Routing input is bounded to a 512-character key, at most 32 attributes,
128-character names, 2,048-character values, and 32 KiB total UTF-8 data. An invocation
ID is optional; when present it is validated unchanged, rejects surrounding whitespace, is limited to 128 characters,
starts with an ASCII letter or digit, and
contains only ASCII letters, digits,
`.`, `_`, `:`, `@`, or `-`.

## 5. Results And Errors

`ProcessResult<T>` is an immutable success-or-failure value with controlled execution attribution:

```java
boolean isSuccess();
boolean isFailure();
T getOutput();
ProcessError getError();
ProcessExecution getExecution();
<U> ProcessResult<U> map(Function<? super T, ? extends U> mapper);
T orElse(T fallback);
T orElseGet(Supplier<? extends T> fallback);
T orElseThrow();
<X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X;
```

A success has output and no error. A failure has a `ProcessError` and no output. Successful output may itself be `null`,
so test `isSuccess()` instead of inferring the outcome from `getOutput()`. `ProcessError` codes are limited to 128
characters and sanitized messages to 4,096 characters.

`map()` transforms only successful output and carries the same failure and execution attribution otherwise. `orElse()`
and `orElseGet()` discard failure information, so use them only when a fallback is part of the application contract.
`orElseThrow()` raises `ProcessExecutionException`; its supplier overload adapts a failure at an application boundary:

```java
Map<String, Object> output = result.orElseThrow(() ->
        new IllegalStateException(
                result.getError().getCode() + ": " + result.getError().getMessage()));
```

`ProcessExecution` exposes only trace ID, invocation ID, logical namespace, process code, optional exact published
version, and start/completion times. Root selectors, Alias route details, routing inputs, model/source diagnostics,
variables, and exception objects remain at their owning boundaries.

## 6. Triggered Entry Points

`ProcessTrigger` selects a trigger entry by its globally unique ID and an optional event:

```java
ProcessTrigger trigger = ProcessTrigger.on("paymentReceived", "approved");
ProcessResult<Map<String, Object>> result = engine.trigger(
        ProcessRef.alias("default", "order.process", "production"),
        trigger,
        Map.of("orderId", "A-42"),
        ProcessExecutionOptions.defaults());
```

Trigger starts a new in-memory execution through the same source, routing, runtime, and result pipeline. It does not
resume a durable workflow instance and does not imply message persistence, correlation, or state recovery.
Both the node ID and optional event selector are bounded to 512 characters.

## 7. Local Administration And Tooling

`engine.runtime()` returns the engine's stable local-runtime administration view:

```java
void warmUp(ProcessDefinition... definitions);
void load(ProcessRef.Version ref, ProcessDefinition definition);
void unload(ProcessRef.Version... refs);
```

`warmUp` compiles exact definitions into the node-local cache without creating or rebinding a public process identity.
Versioned `load` installs an immutable version binding. Neither operation publishes durable state or mutates an Alias.
`unload` releases only explicit version ownership; Alias lifecycle belongs to the control plane.

`engine.tooling()` exposes non-executing preflight and source generation:

```java
ProcessPreflightReport preflight(
        ProcessDefinition definition, ProcessPreflightOptions options);
String generateJavaCode(ProcessDefinition definition);

static ProcessPreflightOptions strict();
static ProcessPreflightOptions fast();
Duration getTimeout();

ProcessPreflightReport.OverallStatus getOverallStatus();
List<ProcessPreflightReport.Item> getItems();
```

`fast()` runs structural validation; `strict()` also dry-run compiles generated Java. Both use a one-minute default
deadline, which a builder can replace with a positive, millisecond-representable
`Duration`. Preflight returns one report for one definition. It never installs a runtime or executes process logic.

Generated source can contain business logic and must be handled as sensitive diagnostic output.

## 8. Configuration And Data Mapping

Configurations are immutable snapshots:

```java
ProcessEngineConfig.Builder builder();

ProcessEngineConfig config = ProcessEngineConfig.builder()
        .dataMapper(customMapper)
        .build();
ProcessEngine engine = ProcessEngineFactory.create(config);
```

Use `ProcessEngineConfig.builder()`. The builder owns executor, cache, script, compilation,
definition-loading, observability, class-loader, mapper, component resolver, routing, and extension contributions.
External configuration is parsed once by the Spring boundary into the same immutable model.

`ProcessDataMapper` defines typed adapter behavior:

```java
Map<String, Object> toVariables(Object input);
<T> T fromVariables(Map<String, Object> variables, Class<T> outputType);
```

Mapper failures are typed engine failures. Output mapping happens after process execution and does not roll back side
effects already performed by process actions.

## 9. Extension SPI

Supported extension contracts live under `com.alibaba.compileflow.engine.spi`. Register them directly on
`ProcessEngineConfig.Builder`, as Spring beans, or through a `ProcessEnginePlugin`. The resulting capability set is
validated and frozen when the configuration is built. `ProcessAliasRouteSource` is the exception: it is one explicit
serving authority configured with `aliasRouteSource(...)` or one Spring bean and is never plugin-contributed.

See the [Extension Guide](extension-guide.md) for the extension points, precedence rules, ServiceLoader setup,
lifecycle, and thread-safety requirements.

## 10. Deployment API

Process publication and routing are a separate product boundary in `compileflow-deploy-api`.
`ProcessDeploymentService` publishes immutable versions and executes revision-checked rollout commands. Publication
never installs a runtime or changes traffic. Rollback creates a new rollout; it does not rewrite history.

Applications should depend on `compileflow-deploy-api` for deployment commands and domain contracts, not on control-plane
repositories or runtime implementation packages.

Wire payloads, parsers, canonical JSON codecs, and projection keys are published separately by
`compileflow-deploy-protocol`; the domain API does not depend on that representation module.

## 11. Durable Process API

Durable execution is a separate product boundary in `compileflow-durable-api`. It does not extend the synchronous
`ProcessEngine`, and adding the Durable starter does not persist ProcessEngine
`execute(...)` calls. The storage-independent application facade is:

```java
public interface DurableProcessEngine {
    ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input);
    ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias,
                         Map<String, ?> input, AliasRoutingOptions options);
    ProcessRun completeWait(WaitToken waitToken, Map<String, ?> result);
    ProcessRun cancel(ProcessRunId runId);
    Optional<ProcessRun> getRun(ProcessRunId runId);
    ProcessRunPage listRuns(ProcessRunQuery query);
    ProcessRunResult getRunResult(ProcessRunId runId);
}
```

Every Start requires a caller-allocated `ProcessRunId`. It is a permanent Run address and recovery handle, not a
generic idempotency key. Duplicate Start fails with `RUN_ALREADY_EXISTS`; resolve an ambiguous response with
`getRun(runId)`. Request equivalence remains the responsibility of the HTTP, MQ, or application adapter.

Admission follows these rules:

- An explicit definition is loaded and frozen as an immutable snapshot.
- An exact Version is acquired without falling back to another source.
- An Alias is resolved once and permanently binds the Run to the selected exact stored Process.
- `AliasRoutingOptions` applies only to Alias admission.
- Recovery reads the stored Process by `processId`; it never re-resolves Alias or reloads current Classpath content.

`completeWait` accepts an opaque one-shot `WaitToken` and a typed partial result. Repeating a committed completion with
the same token and canonical result is a no-op; a different result conflicts. Treat raw tokens as credentials: keep them
out of logs, metric labels, browser-visible URLs, third-party metadata, and Workbench views.

`getRun` and `listRuns` are payload-blind. `getRunResult` returns `ProcessRunResult.NotFound`, `NotCompleted`,
`Succeeded`, `Failed`, or `Cancelled`.

The public Run lifecycle is:

| Status                             | Meaning                                            |
| ---------------------------------- | -------------------------------------------------- |
| `RUNNABLE`                         | Committed and awaiting a compatible Worker.        |
| `RUNNING`                          | A Turn owns the current lease.                     |
| `WAITING`                          | Waiting for external completion, Timer, or Effect. |
| `SUCCEEDED`, `FAILED`, `CANCELLED` | Terminal.                                          |

Cancellation intent and `ProcessRunControl` are orthogonal to lifecycle. `ACTIVE` admits execution,
`PAUSE_REQUESTED` records cooperative convergence, and `PAUSED` blocks new business Turn/Effect admission while
Timer, Wait completion, Outbox, reconciliation, cancellation, and maintenance work continue.

`DurableOperatorService` is a separate least-privilege facade for Run Timeline, Pause/Resume, Outbox resolution, and
UNKNOWN Effect review. Do not expose it directly over HTTP or RPC; the transport adapter must add authentication,
authorization, approval where required, rate limiting, and audit.

A `bpmCall` or `callActivity` uses same-Run `ProcessInvocation` frames and creates no independently queryable Child
Run. Outbox delivery is at least once and unordered; sinks deduplicate by stable `eventId`. Embedded queries use typed
keyset cursors, while transport adapters own opaque page-token protocols.

Catch `DurableProcessException` and branch on `DurableErrorCode`, never message text. Action
`execution="replayable|effect"` selects Durable execution semantics. An `UNKNOWN` post-invocation Effect follows
its configured automatic recovery policy; authenticated operator resolution is required when `reviewRequired()` is true. See the [Durable Process guide](durable-process.md) for admission,
Wait-token handling, Process Calls, operations, and Effect recovery.

## 12. Failure Boundary

Expected execution failures are represented by `ProcessResult`. Invalid configuration, malformed API input, unavailable
providers, lifecycle misuse, and other failures before an execution result can be formed use the typed
`CompileFlowException` hierarchy.

Its optional diagnostic context is non-authoritative and bounded to 32 entries, 128-character keys,
2,048-character text values, and 32 collection elements. Mutable collections are snapshotted; unsupported or oversized
values are replaced by a fixed omission marker. Diagnostic enrichment must never carry process variables, payloads,
source, credentials, or change execution outcome.

Do not log raw variables, source content, routing keys, credentials, full local paths, or arbitrary application objects
when translating either failure form.

## Related Documents

- [Quick Start](quick-start.md)
- [Configuration](configuration.md)
- [Extension Guide](extension-guide.md)
- [Hot Deployment](hot-deploy.md)
- [Durable Process](durable-process.md)
- [Supported Surfaces](architecture/supported-surfaces.md)
