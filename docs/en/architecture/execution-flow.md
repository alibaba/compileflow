# CompileFlow execution flow

One invocation passes from the public API to a typed result through request identity, exact source resolution, local
runtime identity, routing, compilation, and execution attribution.

Sections 1–14 describe `ProcessEngine`. Durable start, wait completion, timer and Effect resumption, and Worker
advancement use the separate persisted lifecycle described in section 15.

## 1. Public Entry

The canonical Map entry points accept either an existing `ProcessRef` or an explicit
`ProcessDefinition`, variables, and `ProcessExecutionOptions`.

```java
ProcessResult<Map<String, Object>> result =
    engine.execute(ref, variables, options);
```

Typed input is converted by `ProcessDataMapper` before this path. Typed output is converted after a successful Map
execution. Input mapping failure returns `CF_EXEC_010` before process execution; output mapping failure returns
`CF_EXEC_009` after process completion and does not imply rollback of side effects already performed by process actions.

`ProcessExecutionOptions` contains request-only invocation and routing metadata. The routing key and routing attributes
are never inserted into variables.

## 2. Request Normalization

`DefaultProcessEngine` converts the public input into one internal `ProcessRuntimeRequest`:

| Public input         | Internal request                                 |
| -------------------- | ------------------------------------------------ |
| `ProcessDefinition`  | default namespace/code plus explicit source      |
| `ProcessRef.Version` | namespace/code/exact version                     |
| `ProcessRef.Alias`   | namespace/code/Alias pending route selection     |
| admin exact load     | exact version reference plus explicit definition |

The definition code and version-reference code must match. Direct definitions carry an explicit model type and use
the documented default namespace. They do not acquire an implicit Version or Alias.

## 3. Execution Scope

Before runtime resolution, the Engine opens an operation scope and an `EngineExecutionContext`. The context owns:

- invocation ID;
- routing key and immutable routing attributes;
- requested process reference;
- effective route attribution as it becomes known;
- start and completion timestamps.

`EngineExecutionContextHolder` is a scoped `ThreadLocal` bridge for internal components. The Engine always clears it in
`finally`; it is not global business state.

Lifecycle events are a sealed set of typed `ProcessEvent` records. Completion and failure events carry the controlled
`ProcessExecution` returned to the caller and a separate `ProcessEvent.ExecutionAttribution`; failure events add a typed
`ProcessError`. Start events contain namespace, process code and invocation ID; trigger-start also carries the trigger.
Trace ID and event time are common fields. Events cannot carry routing keys, process
variables, source content, arbitrary metadata, or raw exceptions.

The public `ProcessExecution` value is a closed type containing trace and invocation IDs, namespace, process code, an
optional exact published Version, and start/completion timestamps. Operational event attribution contains parent
invocation and call depth, model type, source digest, and admitted Alias facts. Neither exposes a generic metadata map.

## 4. Version Resolution

`ProcessRuntimeResolver` resolves the effective version:

1. `ProcessRef.Version` is already exact and bypasses Alias selection.
2. `ProcessRef.Alias` reads one route from the configured `ProcessAliasRouteSource`, applies its optional named
   `ProcessAliasTargetingPolicy`, then uses the protocol-defined percentage selector when targeting falls through.
3. Every `ProcessDefinition` variant is unversioned.

Published Alias selection has one bounded handoff protocol:

- the selected target must map to stable or candidate in the observed Alias revision;
- if exact runtime acquisition misses during a route handoff, Core re-reads and repeats admission once;
- the engine retains the selected exact root runtime before execution, regardless of its frontend;
- after a successful handoff, a newer Alias revision affects only later invocations.

Selecting a target outside the observed Alias authority, or naming an unavailable targeting policy, fails closed.
Re-selection is not a previous-version or
latest-version fallback: it evaluates one newer complete serving route through the same configured policy.
There is no active-version lookup or random request-local fallback.

On a deployment node, an exact version must be present in `InstalledVersionState`. A version-only request without locally
ready content cannot trigger an untrusted remote read on the execution path.

## 5. Binding Key And Exact Identity

The cache has two related keys:

- a binding key, `namespace#code` or `namespace#code#version` (often shortened to `code#version`
  in diagnostics);
- an exact `ProcessRuntimeIdentity`.

The binding key answers "which exact runtime currently owns this process coordinate?" It is not sufficient to identify
compiled code.

`ProcessRuntimeIdentity` combines model type, process code, exact source digest, an opaque engine-local compilation fingerprint,
and class-loader identity. Equality uses object identity for the compilation fingerprint and class loader. The
diagnostic string contains only a digest prefix and identity descriptions.

## 6. Exact Source Resolution

If a binding cache hit is available for a reference-only request, the runtime can be returned without loading source
again. Otherwise, the request must carry a `ProcessDefinition` with an explicit source.

`DefaultProcessDefinitionLoader` performs one bounded read:

- inline content is encoded as UTF-8 and checked by byte size;
- classpath lookup rejects network-backed URLs;
- every stream reads at most `maxBytes + 1`;
- malformed UTF-8 fails before parser invocation.

The output is one immutable `ProcessDefinitionSnapshot` containing exact bytes, decoded content, safe source
description, and SHA-256 digest. Parsing, Runtime identity, and compilation all reuse this same snapshot. A path or
resource locator never substitutes for content identity.

## 7. Cache And Single-Flight

`DefaultProcessRuntimeLoader` first checks the exact `ProcessRuntimeIdentity` cache. A miss enters
`InflightRuntimeLoadRegistry`:

```text
putIfAbsent(ProcessRuntimeIdentity, proposed future)
  -> existing future: join it
  -> proposed future: submit exactly one compilation
```

Every waiter sees the same success or failure. The entry is removed after completion so a later request can retry a
failed compilation.

ProcessRuntimeLoader does not hold a lifecycle lock on its public call path:

- an `AtomicBoolean` rejects calls after close starts;
- the short `InflightRuntimeLoadRegistry` critical section strictly orders task registration and close, which cancels
  incomplete futures;
- a separate short critical section orders compiled-result publication against close, so output is not installed after
  the service closes.

## 8. Compilation

The runtime loader compiles source semantics before invoking the selected runtime realization:

```text
DefaultProcessDefinitionLoader.load
  -> ProcessDefinitionSnapshot
  -> parser and model conversion
  -> structural and semantic validation
  -> ProcessSemanticCompilation
  -> ProcessRuntimeFactory (COMPILED or INTERPRETED)
  -> ProcessRuntime
```

Both runtime factories consume the same semantic compilation value; neither owns a parser or a semantic compiler.
The compiled factory generates and compiles Java process code. The interpreted factory prepares actions and
expressions for interpretation. The loader scopes both semantic compilation and runtime realization to the requested
ClassLoader and restores the worker's previous context ClassLoader on success or failure.
Preflight also scopes source resolution and linting to that ClassLoader, including lint-only requests.

One compilation attempt is never retried internally. Parser, generator, javac, timeout, and configuration failures
retain their typed error boundary and are shared by every waiter on that single-flight attempt. The in-flight entry is
removed after completion, so a later request may start a new attempt after the definition or environment has been
corrected.

Optional debug export runs only when explicitly configured. Exported source/classes are diagnostic artifacts and use
stable package-relative paths under `source/`, `metadata/`, and optionally `classes/` in the configured output directory.
They do not change Runtime identity; an export failure still fails preparation instead of silently dropping diagnostics.

## 9. Conditional Installation

After runtime preparation, `runtimeCache.install` performs a conditional binding update:

- immutable version binding to different exact content is a conflict;
- an unversioned binding may change only if the expected wrapper still owns it;
- a concurrent newer binding is not overwritten;
- the exact runtime remains shareable by other bindings and owners.

The cache is bounded and retain-aware. Eviction can remove only entries without ownership claims.

Every execution prepares and retains its complete static process-call graph before process code starts. Published calls
use the exact called-process versions declared at their call sites; Direct calls bind exact Classpath or Version targets. Route convergence
may release obsolete deployment ownership immediately; the resolved graph keeps every runtime needed by the admitted
invocation strongly reachable until execution finishes.
Direct `execute`, Alias admission, nested calls, and `trigger` use this same Engine boundary. Its operation gate
holds admission across route selection and execution. Shutdown stops new admission and drains admitted calls;
resource cleanup shares the remaining shutdown deadline instead of restarting the grace period at each step.

Preflight uses `runtimeCheckSync` after validation when dry-run compilation is requested. It can reuse or create an
exact runtime but does not publish a durable version or route.

## 10. Runtime Execution

`DefaultProcessEngine` invokes the acquired `ProcessRuntime`. Sequential nodes execute in graph order. Parallel branches use
the Engine execution strategy and join before downstream continuation. Timeout cancellation is cooperative; action code
must honor interruption when applicable.

The Engine converts known execution failures to `ProcessError` and returns
`ProcessResult.failure(...)`. JVM fatal `Error` values are never normalized into a `ProcessResult`. Success returns the
output variable map with the same execution details.

Nested process calls preserve the outer execution scope while returning their own result. Generated code identifies the
call site; the execution context performs one exact lookup in the resolved graph. It never derives a classpath from the
child code or routes a child through Alias/latest. A nested `ProcessExecutionException` is translated without discarding
its typed error.

## 11. Trigger Flow

`trigger(...)` uses the same request normalization, routing, runtime resolution, execution context, error, and result
pipeline.

After obtaining the runtime, the Engine requires a `TriggerableProcess` and invokes the trigger-entry node ID plus
optional event. Every call creates a new in-memory instance from the supplied context. It does not provide durable
process-instance persistence, resume, external message correlation, or crash recovery.

## 12. Local Admin Flow

`ProcessRuntimeManager` is an Engine-local capability view:

- `warmUp(ProcessDefinition...)` prepares exact definitions without creating a public binding;
- `load(ProcessRef.Version, ProcessDefinition)` creates one immutable local version binding;
- `unload(ProcessRef.Version...)` releases local ownership of exact-version bindings.

`ProcessToolingService.preflight(...)` performs non-executing validation and optional dry-run compilation. Warm-up uses
the engine's bounded runtime-load capacity; there is no separate warm-up parallelism switch. Admin load and warm-up are not
`ProcessDeploymentService.publish`: they neither write durable publication state nor change an Alias.

## 13. Sensitive Data Boundary

May be recorded:

- invocation ID;
- trace ID;
- namespace and process code;
- requested reference;
- effective version/Alias;
- Alias revision and stable/candidate target;
- model type and source digest;
- bounded error code and safe message;
- timing.

Must not be recorded:

- routing key;
- source content;
- full local path;
- credentials;
- raw variables;
- arbitrary application objects;
- raw exception context in public results.

## 14. Source Navigation

- [`DefaultProcessEngine`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/DefaultProcessEngine.java)
- [`ProcessRuntimeRequest`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/ProcessRuntimeRequest.java)
- [`ProcessRuntimeResolver`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/resolution/ProcessRuntimeResolver.java)
- [`DefaultProcessDefinitionLoader`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/source/loader/DefaultProcessDefinitionLoader.java)
- [`ProcessDefinitionSnapshot`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/source/ProcessDefinitionSnapshot.java)
- [`ProcessRuntimeIdentity`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/ProcessRuntimeIdentity.java)
- [`DefaultProcessRuntimeLoader`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/loading/DefaultProcessRuntimeLoader.java)
- [`InflightRuntimeLoadRegistry`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/loading/InflightRuntimeLoadRegistry.java)
- [`EngineExecutionContext`](../../../compileflow-core/src/main/java/com/alibaba/compileflow/engine/core/runtime/context/EngineExecutionContext.java)

## 15. Durable Execution Flow

Durable uses a separate API and transactional state machine:

```text
start(explicit Definition | exact Version | Alias)
  -> load exact definition or resolve Version/Alias once
  -> persist immutable Process semantics and exact static-call bindings
  -> configured runtime loads disposable code and application capabilities
  -> commit a processId-bound RUNNABLE Run
  -> Turn Worker claims a revisioned random-token lease
  -> execute to a Wait / Timer / Effect Action / terminal boundary
  -> Store atomically commits Run + boundary + Journal + selected Integration Event
  -> Wait completion / Timer / Effect result creates one resume envelope
  -> the next Turn resumes from the portable committed checkpoint
```

It does not call `ProcessEngine.trigger(...)` to resume a process and does not persist a Java stack or arbitrary object.
Alias is accepted only at admission and resolved once to an exact Version; recovery uses stored Process IDs and never
resolves Alias, Version, or definition locators. See
the [Durable Architecture](durable-architecture.md) for full sequences, state machines, failure recovery, and data
ownership.

## Related documents

- [Version Routing](version-routing.md)
- [Durable Architecture](durable-architecture.md)
- [Advanced Features](../advanced-features.md)
- [Resource Management](../resource-management.md)
