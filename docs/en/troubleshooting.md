# Troubleshooting

Start with the stable error code, the requested process identity, and the relevant Engine or deploy diagnostics. Do not
enable broad debug logging or log process variables/source content as a first response.

## 1. Startup Fails

### Unknown Or Invalid Configuration

CompileFlow Spring properties use strict binding. Unknown fields in enumerable configuration sources under
`compileflow.engine.*`, `compileflow.deploy.*`, `compileflow.durable.*`, or `compileflow.workbench.server.*`
fail startup. This is not an arbitrary OS-environment typo detector: Workbench's explicit `CONFIG_*` aliases have
a whitelist, while other environment names follow Spring relaxed binding. See the exact source boundary in Configuration.

1. Read the binding failure and property path.
2. Compare it with [Configuration](configuration.md).
3. Remove obsolete aliases instead of duplicating the same setting under another prefix.
4. Keep datasource, HTTP server, Actuator, and logging settings under Spring's standard prefixes.

`CF_CONFIG_001` indicates an invalid value. `CF_CONFIG_005` usually means no matching semantic-compiler provider or more
than one provider for the same `ProcessModelType`; ensure the intended `compileflow-tbbpm` or `compileflow-bpmn`
frontend module is present exactly once.

### Runtime Compiler Missing

CompileFlow requires the standard `jdk.compiler` module at runtime. Use a full JDK or include that module in a custom
runtime image. A JRE-only or over-trimmed image cannot compile generated flows.

## 2. Definition Cannot Be Loaded

| Code              | Meaning                        | Checks                                         |
| ----------------- | ------------------------------ | ---------------------------------------------- |
| `CF_RESOURCE_001` | definition not found           | Check process code and classpath resource name |
| `CF_RESOURCE_002` | read or UTF-8 decoding failed  | Check readability and strict UTF-8             |
| `CF_RESOURCE_003` | resource policy rejected input | Check size limit and local-only classpath URL  |

For an explicit packaged definition:

```java
ProcessDefinition definition =
        ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.bpm");
```

Use an explicit resource path that matches the definition's explicit model type, for example
`ProcessDefinition.classpath(ProcessModelType.TBBPM, "order.process", "flows/order.process.bpm")` for TBBPM or
`ProcessDefinition.classpath(ProcessModelType.BPMN, "order.process", "flows/order.process.bpmn")` for BPMN.

Network-backed classpath URLs are rejected. Remote artifacts must be fetched and verified outside the Engine.

## 3. Compilation Fails Or Runtime Does Not Become Ready

`CF_COMPILE_001` and `CF_COMPILE_002` identify process compilation failure and generated-Java
compilation failure. `CF_RUNTIME_001` means runtime loading or warm-up did not become ready because it timed out, was
interrupted, or was cancelled.

1. Run strict preflight on the exact definition:

```java
ProcessPreflightReport report = engine.tooling()
        .preflight(definition, ProcessPreflightOptions.strict());
```

2. Inspect structured compiler diagnostics and the failing process code.
3. Verify action classes and methods are visible through the configured class loader.
4. Increase `compileflow.engine.runtime-load-timeout` only after measuring a legitimate cold runtime load that
   exceeds the caller's wait budget.

The engine does not automatically retry a failed runtime load. A single-flight attempt uses one immutable source snapshot
and class-loader scope, so repeating parser, generator, or javac failures inside the same request only consumes the
bounded runtime-load executor. Correct the definition or environment; the next request can start a fresh attempt.

A runtime-load timeout does not cancel shared single-flight work. An admitted load may succeed and populate the node-local
cache after the caller returns. Check runtime-load diagnostics and logs before treating a timeout as a definitive failure.

For local diagnosis, configure
`compileflow.engine.java-diagnostics.debug.output-directory`. Add
`debug.bytecode-enabled=true` only when class output is necessary. Treat exported code as sensitive and remove it after
diagnosis.

## 4. Execution Returns A Failure

Expected execution failures are values:

```java
ProcessResult<Map<String, Object>> result =
        engine.execute(definition, variables);

if (result.isFailure()) {
    ProcessError error = result.getError();
    log.warn("Flow failed: code={}", error.getCode());
}
```

Use `getError().getCode()` for machine handling. Do not infer failure from `getOutput()`, because successful data may be
`null`. `orElseThrow()` converts a failed value to
`ProcessExecutionException` when an exception boundary is required.

Common codes:

| Code          | Meaning                                                                 |
| ------------- | ----------------------------------------------------------------------- |
| `CF_EXEC_001` | process action/execution failure                                        |
| `CF_EXEC_003` | script failure                                                          |
| `CF_EXEC_004` | action or process timeout                                               |
| `CF_EXEC_005` | local bounded execution capacity was exhausted                          |
| `CF_EXEC_007` | operation interrupted                                                   |
| `CF_EXEC_008` | execution validation failure                                            |
| `CF_EXEC_009` | typed output mapping failed after process completion                    |
| `CF_EXEC_010` | typed input mapping failed before process start                         |
| `CF_EXEC_011` | Alias route unavailable or unable to stabilize during bounded selection |
| `CF_EXEC_012` | selected runtime unavailable before action execution                    |
| `CF_EXEC_013` | nested process call exceeded the configured depth                       |
| `CF_EXEC_014` | resolved process-call graph is invalid before action execution          |

An input mapping failure means the process did not start. An output mapping failure does not roll back side effects
already performed by the process.

## 5. Exact Version Or Alias Is Not Ready

Published execution is fail-closed:

- an exact version must be installed on the node;
- an Alias reads only local-ready state;
- a router may choose only the current stable or candidate;
- there is no previous-version fallback.

Check:

1. the control-plane Alias revision and stable/candidate versions;
2. outbox status and dead letters;
3. `DeploymentRuntime.snapshot()` desired, in-flight, deployed, and backed-off versions;
4. artifact identity, model type, and digest;
5. local-ready revision and the bounded installation failure reason.

Control-plane `COMPLETED` means the route transaction committed; it does not mean every node has converged.

`CF_EXEC_011` identifies Alias route absence or a concurrent route revision change.
`CF_EXEC_012` identifies a selected version that is not installed or ready on this node. Both occur before process
actions, but retries must remain bounded because an invalid Alias or unavailable artifact will not recover merely by
waiting.

`CF_EXEC_013` means `compileflow.engine.call.max-depth` is too small for the requested call graph; increase it rather
than retrying. `CF_EXEC_014` means the process-call graph is invalid: for example, it contains a cycle, an exact-Version
or published graph declares a `classpath` target, or a target's process code does not match the call declaration. A
Direct graph may target either `classpath` or an exact `version`. Fix the definition or publish/install the correct exact
graph; execution never guesses an Alias, latest version, or caller-relative classpath.

## 6. Repeated Compilation Or High Memory

Do not create an Engine per request. Reuse one Engine for each resource/configuration boundary and close it with the
application lifecycle.

If compilation repeats:

1. verify the definition bytes are stable;
2. verify requests use the same effective class-loader scope;
3. check runtime ownership is not being released immediately;
4. inspect `compileflow.engine.max-resident-runtimes`;
5. preflight and `engine.runtime().warmUp(definition)` known definitions at startup.

Do not create random version identifiers for repeated calls. Published version identity is immutable, and changed
content requires a new explicit version.

## 7. Executor Rejection

`CF_EXEC_005` and `RejectedExecutionException` indicate bounded overload, not a signal to make every queue unbounded.

- Runtime-load capacity:
  `compileflow.engine.executor.runtime-load.max-concurrency` and `max-pending`.
- Timeout-enforced action capacity:
  `compileflow.engine.executor.action-timeout.max-concurrency` and `max-pending`.
- Event capacity:
  `compileflow.engine.observability.events.max-concurrency` and `max-pending`.
- Deploy installation capacity:
  `compileflow.deploy.runtime.installation-concurrency` controls admission in both distributed and embedded topologies.
  Compilation and runtime loading are also bounded by the Engine runtime-load capacity.

Measure queue wait, service time, rejection rate, and upstream concurrency before increasing limits. Apply backpressure
at the caller when offered load exceeds sustainable throughput.

## 8. Persisted Async Invocations

For a failed or dead-lettered async request:

1. query the async invocation record and attempt count;
2. use its invocation ID to inspect execution logs;
3. verify the caller supplied exactly one Alias route selector: version or Alias;
4. check that a retry remains pinned to the previously selected exact version;
5. inspect deploy-runtime diagnostics for root-artifact resolution, integrity, or installation failures;
6. requeue a dead letter only after fixing the root cause.

Workbench evaluates Alias routing before it persists an async request. It stores the selected exact version and bounded
routing attribution, but never the routing key or attributes. Without an explicit key, the persisted invocation ID is
the cohort key. Invocation responses and execution logs expose the controlled effective version, Alias, route revision,
and target for retry correlation; routing keys and lease tokens remain internal.

Each attempt materializes its persisted exact root version before process code starts.
This is expected even when the Alias has moved and the old runtime was reclaimed locally. Repeated
`Async invocation runtime could not be loaded` failures therefore point to artifact availability, integrity, compile
capacity, or node-local installation rather than a request being silently rerouted to the current Alias.

## 9. Safe Diagnostic Data

`ProcessExecution` supplies trace and invocation IDs, process namespace/code, an optional exact published Version, and
timing. Terminal `ProcessEvent.ExecutionAttribution` may additionally supply parent invocation and call depth, model
type, source digest, and admitted Alias revision/target. Bounded error codes are also safe for diagnostics.

Never log routing keys, source content, full local paths, credentials, raw variables, arbitrary application objects, or
unredacted compiler output in shared production logs.

## Getting Help

Include:

- CompileFlow commit/version and Java runtime;
- model type;
- stable error code and redacted message;
- minimal definition if it contains no confidential logic;
- exact configuration keys relevant to the failure;
- deterministic reproduction steps.

See [Testing](testing.md), [Monitoring](monitoring.md), and
[Operations Playbook](operations-playbook.md).
