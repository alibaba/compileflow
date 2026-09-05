# CompileFlow 2.0 Durable Architecture

Status: current architecture for the Durable Developer Preview.

## Core Constraints

CompileFlow persists only process semantics owned by the engine and committed Durable execution facts. The current
deployment supplies application code, `ScriptExecutor` providers, handlers, generated artifacts, and runtime
capabilities. The Durable kernel does not preserve historical compatibility for those deployment-owned components,
and application compatibility metadata never participates in Run identity or Store worker routing.

The two execution planes share Process language semantics but have different continuation owners:

| Plane                | Public reference                          | Continuation owner                     |
| -------------------- | ----------------------------------------- | -------------------------------------- |
| Stateless invocation | `Version`, `Alias`, or direct definition  | Application, if continuation is needed |
| Durable execution    | `definition`, `Version`, or `Alias`       | Durable kernel                         |

`ProcessEngine.execute/trigger` creates one stateless invocation. If an application continues a business process across
calls, it stores `ProcessExecution.getProcessVersion()` and later triggers that exact Version, or retains the direct
definition used by a definition-based invocation.

`DurableProcessEngine.start` admits a new Run. Definition, Version, and Alias are acquisition choices; admission
materializes one exact immutable stored Process and binds the Run to its `processId`. Alias is Deploy control-plane
state: it selects a Version only during admission and never becomes snapshot, wait, effect, lease, or recovery identity.
Every Run stores one exact root `processId`; Version is optional admission attribution.

## Admission and execution layers

The user-facing façade and exact executor are deliberately separate:

```text
ProcessEngine façade
  Version / Alias / definition
  -> normalize or resolve once
  -> exact invocation runtime acquisition
  -> execute

Durable façade
  definition / Version / Alias
  -> load the supplied definition, resolve the Version, or route Alias once
  -> validate and persist exact immutable Process semantics
  -> exact-bind every static Process call
  -> create processId-bound Run

Durable Worker
  Run.processId -> stored Process semantics -> disposable loaded runtime
```

Invocation Alias admission reads the node's local-ready projection because execution is immediate.
Durable Alias admission reads committed Deploy authority because it creates long-lived recovery
authority. Both use the same stable/candidate selection contract. Code paths must perform request or
domain admission checks before resolving Alias once; the Kernel itself does not implement a generic
request-retry protocol.

The stable `ProcessEngine` path remains `DefaultProcessEngine -> ProcessRuntimeResolver ->
cache/compile -> executor`. For a stateless invocation, the provider resolves Alias before exact
cache matching, compilation, and execution. Durable does not reuse that stateful acquisition path:
its runtime-internal admission policy reads committed Deploy state once, and every later Worker step is
`processId`-only. No shared `InvocationService` or universal admission resolver is introduced.

When Deploy and Durable are composed, first-party thin adapters map
`ProcessDeploymentService.getAlias(...)` to `DurableAliasStateSource` and map Deploy's
`ProcessArtifactSource` to `DurableVersionDefinitionSource.VersionDefinition` only after exact
identity and source-digest validation. The adapter preserves `ProcessModelType`; Durable V1 then
accepts the documented strict TBBPM and BPMN profiles and fails closed otherwise. Durable owns UTF-8 encoding,
digest calculation, and its recovery replica. This is a composition boundary: Alias, Rollout, and Deploy revision never
enter Run identity, checkpoints, or recovery authority. Alias Start may retain only admission attribution
in the `RUN_CREATED` audit fact.

## Identity and immutable Process Definition

Inside the Durable Kernel, the recovery identity is:

```text
processId -> exact model type + process code + definition bytes
```

`processId` is deterministically derived from the full definition digest; the Store retains and validates the complete
SHA-256 digest. The immutable stored Process contains `ProcessModelType`, process code, exact definition bytes, and
the digest. Namespace and Version remain optional Run admission attribution and never select recovery semantics.
A conflicting registration is rejected. Recovery never depends on Alias,
Rollout, application build ID, Program ABI, provider identity, custom codec, generated Java source, classes, bytecode, or runtime objects.

Deploy is retained as its own control plane. It owns immutable version publication, Alias CAS,
stable/candidate BPS rollout, promotion, abort, rollback, audit, routing outbox, install-before-route,
and local-ready projections. Durable owns none of those mutable deployment decisions.

Durable accepts the documented strict TBBPM and BPMN profiles through one format-neutral semantic backend. This adds
no public compiler SPI or generic BPMN message/task/event platform.

## Run admission and RunId

The public Durable Start API follows the direct-argument style of `ProcessEngine`:

```java
ProcessRun start(ProcessRunId runId, ProcessDefinition definition, Map<String, ?> input);
ProcessRun start(ProcessRunId runId, ProcessRef.Version version, Map<String, ?> input);
ProcessRun start(ProcessRunId runId, ProcessRef.Alias alias, Map<String, ?> input);
ProcessRun start(
    ProcessRunId runId,
    ProcessRef.Alias alias,
    Map<String, ?> input,
    AliasRoutingOptions options);
```

Only explicit `ProcessDefinition`, exact `Version`, and selected `Alias` admission exist in the callable Start surface.
The explicit definition source supplies its authoritative format with the resolved inline snapshot;
an explicit child inherits and must match its caller's format. `AliasRoutingOptions` exists only on the Alias overload.
Every Start requires the caller to allocate the Run occurrence identity before admission. RunId is an address and recovery
handle, not a generic idempotency key. Starting an existing RunId fails with `RUN_ALREADY_EXISTS`, because
the Kernel does not retain a complete Start request digest and therefore cannot assert that two requests
are equivalent. It is permanently bound to one Run occurrence: callers must never reuse a value, including
after retention removes the original Run. After an ambiguous response the caller reads `getRun(runId)`. For Alias admission, duplicate
RunId detection happens before consulting mutable Alias state.

There is no default idempotency key or business key. An HTTP, MQ, or application boundary that needs one
logical admission still owns the mapping from its natural request, message, or domain identity to RunId.

Alias Start writes requested Alias, authoritative revision, and selected target to the `RUN_CREATED` audit fact.
The selected Version may remain on the Run as admission attribution, but checkpoints and recovery use only exact
stored `processId` values.

A Wait completion result is a typed partial update of variables declared by the exact Process. The
facade validates and canonically encodes it before the Store transition; Turn recovery only
decodes and applies that committed update and never invokes an application payload mapper.

## Recovery authority

The next deterministic Turn is reconstructed from:

```text
exact immutable Process Definition
+ opaque continuation inside the existing fail-closed CFD envelope
+ committed Wait / Timer / Effect occurrences
+ Run lifecycle, control, cancellation, availability, and lease facts
```

The Store persists and fences the continuation as bytes; it never interprets an element ID, scope,
frontend token, branch, or frontier representation.

No `machine_semantics_version`, compiler/generator/runtime version, Program ABI, codec registry, upcaster, or dual
reader/writer framework is added. `DurableStore.Envelope` already recognizes `CFD + formatVersion` and rejects unknown
stored bytes. A new discriminator is justified only when two real valid persisted representations must coexist.
CompileFlow-owned compatibility is proved by historical Definition + continuation + committed-fact fixtures and release
gates; it is never converted into a Run field or Worker-routing condition.

The final 2.0 target is the **Durable Machine**: one format-neutral semantic frontend with target-specific lowering; a
disposable Durable Machine Plan; and compiled and interpreted production realizations. Each
`advance` executes one bounded Turn. One continuation writer may maintain multiple deterministic frontiers and, in one
fenced commit, consume exact resolved results, write the next continuation, and issue zero or more Wait/Timer/Effect
occurrences. Turn-budget exhaustion produces checkpoint-only Yield and returns the Run to `RUNNABLE`; it is never a
Turn fault. State is serialized while external work may run concurrently, and completion order never determines
branch merge.

### Compilation and preparation

Durable prepares one exact immutable Process semantic program and reuses it for many Runs. Stored `processId` is the
node-local program-cache and single-flight key. `DurableVersionDefinitionSource` is consulted only when Version or
Alias admission must acquire definition semantics; recovery reads the already stored definition by `processId`.
Start admission validates and persists the static graph, then builds the current deployment's disposable program on
demand. Repeated preparation is a cache hit.

The configured runtime mode prepares every exact Process as either generated Java plus javac or an interpreter over the
same Machine Plan. Mode selection is a Host policy, never a Start option or persisted Run fact.

Preparation produces one disposable `DurableProcessRuntime` for one stored `processId`. Normal Start advancement, Turn,
retry, resume, and boundary completion never trigger Kernel Program compilation. Missing or failed preparation is
current-deployment evidence; it does not become a business failure.

Generated Java plus javac is the default Durable realization and the supported V1 host profile includes `jdk.compiler`.
The interpreter is also a supported production realization over the same `DurableMachinePlan` and serves as the
differential oracle. Configuration selects one explicitly; preparation never silently falls back between modes.
V1 adds no AOT Program source, public compiler SPI, persisted bytecode/class registry,
distributed compiler, disk-class cache, ECJ backend, or compiler module. AOT or ECJ is considered only after a real
product consumer and measured package/startup evidence justify its end-to-end toolchain and differential tests.

Decision, While, Timer, and guard keep the existing source/expression and generated-Java model. CompileFlow adds no new
expression parser, typed AST, or DSL. Durable static validation runs in javac preparation.

Agent prompt, conversation, model, current tools, ToolCalls/results, workspace state, and dynamic parallel selection are
state/capability or occurrences. Only the stable Agent control algorithm is Process semantics. A conformant Agent
lowering uses a generic parameterized tool Effect and never generates one Process node/Version per tool inventory,
request, plan, step, or turn.

TBBPM `foreach execution="parallel"` and BPMN parallel multi-instance implement structured collection semantics without
adding a Kernel `Map` node. They freeze one declared Process `List`, give every input index an isolated iteration frame,
use a bounded rolling issue window, and optionally collect output in input order rather than completion order. The
current Runtime owns the window width; it is not persisted as Process identity. The continuation
stores the parent state once and iteration deltas only. ProcessEngine execution rejects this Durable-only profile; both
source formats lower to the same algebra. Agent-specific safety labels do not enter Kernel scheduling
semantics. A parallel collection may be owned by a sequential `foreach` or `while` scope, including a collection read
from its enclosing lexical frame. A second parallel collection under any concurrent ancestor, and Process calls inside
a concurrent region, remain fail-closed until their state-write and deterministic merge laws are defined and proved.

The same occurrence algebra also supports an EVENT Wait with one optional PostgreSQL-time deadline. It resolves once to
a typed triggered or expired result; it is not a generic `anyOf` or event inbox. TBBPM `bpmCall` and BPMN
`callActivity` are same-Run Process calls. Initial Run admission prepares the complete static graph and persists each
`callSiteId -> exact process target` edge. At execution the continuation pushes a `ProcessInvocation` frame, then returns its typed
result to the caller frame. No routing or source lookup occurs after admission, and recovery never reads Alias, current
classpath, or latest state. The call shares the Run's lifecycle, cancellation, lease, fencing, and operations boundary;
there is no Child Run or Child table.

Only an explicit typed Process failure emitted by the compiled Machine may terminalize a Run as `FAILED`. An arbitrary
Action, ScriptExecutor, reflection, compiler, Runtime or Store exception remains bounded operational retry evidence;
CompileFlow does not persist current application compatibility as a business outcome through a Throwable classifier.

Journal is audit and diagnostics, not Event Sourcing. Outbox is reliable integration delivery, not a
copy of Journal. Missing application capabilities keep work eligible with bounded operational
backoff; they are an application/runtime capability problem and never mutate a Run into a different
Process identity or automatic business failure. Active-work projections expose only a closed sanitized capability
code and the next eligibility time; neither value participates in recovery authority.

## Action and Effect semantics

Action has a closed Durable execution semantic:

```xml
<action execution="replayable">...</action>
<action execution="effect">...</action>
```

Effect is not a node type. Decision, While, Timer expressions and Action source remain Process model
source. Script languages are selected by Action type/provider; the Kernel does not invent or freeze a
new universal expression language.

For Java control expressions, the Kernel uses javac type analysis and an AST/symbol allowlist to reject
known unsafe syntax. Side-effect freedom of application value accessors is a model-author contract; the
Kernel does not claim that it can statically prove the purity of arbitrary application methods.

Before any application Action or Wait-description callback runs, the Runtime uses the exact
Process-typed serializer to create an independent input/state/scope graph. Read-only containers are
not treated as sufficient isolation because they may contain mutable POJOs. Application code can
mutate only its detached copy; Process state changes enter the Turn solely through declared
Action output mappings or a committed typed boundary result.

The Process-owned recovery policy is `manual | retry | reconcile` with optional `maxAttempts`,
`maxReconcileAttempts`, `recoveryDelay`, and `maxRecoveryDuration`. Manual is the default. Retry and
reconcile reuse the Effect ID token. Business rejection is a normal typed Action return. Structural
preparation performs only type/signature/provider checks and never resolves or constructs an
application component. Spring bean resolution, Java construction, script evaluation, and method
invocation are beyond the possible-dispatch boundary. Any outcome that cannot be proved after crossing
that boundary produces UNKNOWN. `maxRecoveryDuration` is measured from the first uncertainty of that
dispatch episode; `unknownSince` remains stable across Reconcile claims until a conclusive result,
operator decision, or confirmed-not-executed redispatch ends the episode.

Operator Effect decisions are only:

- `CONFIRM_SUCCEEDED(result)`;
- `CONFIRM_NOT_EXECUTED_RETRY`;
- `FAIL_RUN(reason)`.

Every review epoch increments `cf_durable_effect.review_revision`; stale operator decisions conflict. A
confirmed-success retry is current-equivalent only when both that revision and the canonical typed
result match the committed Effect fact; a different result conflicts. A `FAIL_RUN` retry is
current-equivalent only when that Effect resolution actually failed the Run; an Effect cancelled by
Run cancellation is not equivalent.

## State machines and authority

Run status is `RUNNABLE | RUNNING | WAITING | SUCCEEDED | FAILED | CANCELLED`. Control state is
orthogonal: `ACTIVE | PAUSE_REQUESTED | PAUSED`. Cancellation is a first-intent-wins monotonic fact,
not another lifecycle status.

Complete uses one exact 256-bit Wait token; the Wait authority row stores only its SHA-256 digest. For
crash-safe delivery, the raw bearer token is temporarily retained only in the `WAIT_COMMITTED` Outbox
payload while that authority is live, then scrubbed after successful delivery, Wait completion,
or authority revocation. While the Wait is ACTIVE, this delivery obligation cannot be auto-abandoned;
after fulfillment or revocation it is settled and the capability is scrubbed. This is fixed Kernel
semantics, not a configurable delivery-policy DSL. Before the locked
transition, the payload is validated and canonically encoded as a typed partial update of variables
declared by the exact Process:

```text
ACTIVE + canonical result -> RESOLVED and wake once
RESOLVED + same canonical result -> current equivalent, zero write
RESOLVED + different result -> conflict
CANCELLED -> authority lost
```

Pause/Resume use `control_revision`, Effect review uses `review_revision`, and Outbox operator
resolution uses `revision`. Worker completion uses the occurrence ID and a random lease token. No
generic Run revision, command receipt, HMAC request root, or numeric fence is required.

A WaitToken is an opaque, one-shot bearer capability for exactly one committed external Wait
occurrence. The Kernel does not require an application token table; the integration only has to
preserve the handle until completion can return it. Controlled RPC/MQ protocols can carry it
opaquely, an integration can protect an `externalJobId -> WaitToken` mapping when a provider returns
only its own job ID, and mature order/payment/inventory domains keep their own identities and fact
stores. Raw tokens must not enter logs, metric labels, browser-visible URLs, third-party metadata, or
Workbench views; long-lived storage protects them as credentials. The Kernel does not provide fuzzy
correlation, message subscriptions/buffers, TTL, or early-arrival event lookup.

The only mutation outcomes are applied, current equivalent, and conflict. The transaction lock order
is Run first and exact occurrence second. PostgreSQL database time owns leases and scheduling.
Claim/reclaim generates a new random token; renewal keeps it; completion must match it.
One runtime owns one lease-duration policy. Its node-local coordinator renews Run, Effect, and Outbox
authorities in three independently scheduled lanes at one third of that duration, with a jittered
initial phase and batches bounded to 256 authorities. A successful batch returns the exact surviving
subset; rejected authorities stop being renewed, while a Store exception is retried because it is not
proof of authority loss. This batching is only an operational optimization: expiry and every outcome
commit remain database-time and token fenced, and a process pause, network partition, or database
outage longer than the lease still loses authority.
The combined DataSource acquisition, connection, socket, statement, and lock-wait timeout budget should remain below
one derived renewal interval. Operators size the lease from observed PostgreSQL tail latency and JVM pause time; these
are deployment-level DataSource controls, not additional Durable lease-policy knobs.
The Effect Worker alternates its preferred Dispatch and Reconcile lanes and falls back only when the
preferred lane is empty. Store claims use `FOR UPDATE OF run SKIP LOCKED` so sustained Dispatch demand
cannot starve UNKNOWN recovery and concurrent workers select different Runs.

## Physical closure

PostgreSQL V1 currently maps the six Durable authority categories to seven tables. The table count is a
first-party layout, not a Kernel invariant:

1. `cf_durable_process` — immutable stored Process semantics keyed by `process_id`;
2. `cf_durable_run` — root `process_id`, optional admission attribution, opaque continuation, lifecycle/control/cancel/lease;
3. `cf_durable_run_recovery_process` — the exact stored Process set retained for each Run's recovery;
4. `cf_durable_wait` — EVENT/TIMER occurrence lifecycle;
5. `cf_durable_effect` — Effect outcome, attempts, UNKNOWN and review revision;
6. `cf_durable_journal` — append-only audit/diagnostic facts;
7. `cf_durable_outbox` — closed Integration Events and delivery revision/lease.

The Outbox event set is exactly `WAIT_COMMITTED`, `EFFECT_REVIEW_REQUIRED`, `RUN_SUCCEEDED`,
`RUN_FAILED`, and `RUN_CANCELLED`. Every event carries event ID, Run ID, process code, and optional Version attribution.
Wait and Effect events also carry the exact occurrence ID; Run-terminal events do not. Event ID remains
the sink deduplication identity, while occurrence ID scopes authority cleanup and correlation so resolving
one Wait or Effect can never settle a sibling event.
Delivery is at least once; retries preserve the same event ID, type, and logical payload so the sink
can durably deduplicate or propagate the ID to a downstream deduplicator. Ordering is not guaranteed,
including within one Run, and arbitrary external side effects are not claimed as exactly once. This is
notification/authority delivery, not an Event Sourcing change log or a universal internal messaging protocol.

Deploy separately retains five tables for immutable Version, Alias, Rollout, rollout audit, and routing outbox state.
PITR restores all Durable tables as one coordinated unit with every Runtime stopped; program caches
are discarded and rebuilt.

## Retention

Terminal-Run retention is explicit and disabled by default. A bounded sweep selects only terminal
Runs older than the configured duration in `(completed_at, run_id)` order with `SKIP LOCKED`. A Run
with a `PENDING` or `DELIVERING` required Outbox event is ineligible. The same transaction deletes
Journal, Outbox, Wait, and Effect rows before deleting the Run. Active Runs are never selected.
Every Process Definition referenced by a Run is protected by a Run-Process foreign key. A separately configured,
bounded unused-Process sweep may delete only old Definitions with no Run reference; concurrent Start
locks the complete recovery set and therefore cannot race into a dangling reference. Wait-token authority ends when its Wait is fulfilled or
revoked; obsolete `WAIT_COMMITTED` delivery must be terminal and cannot block retention.

## Optional observability

Runtime counters are dependency-free and use only the bounded `operation` and `outcome` dimensions.
When Micrometer is present, Spring registers `compileflow.durable.operations` and
`compileflow.durable.loaded.runtimes`. When Spring Boot Health is present, the Durable contributor
probes PostgreSQL authority time and reports Worker/cache/capability/sink composition as details. Unexpected Worker
machinery faults produce an immediate sanitized warning, repeated warnings are rate-limited, and recovery is recorded.
Three consecutive faults in one enabled lane report `DEGRADED`, with bounded per-lane last-success, last-progress,
last-fault, and failure-class details; the failure message and process data are never exposed.
Missing process-scoped Action/Effect capability or an absent optional Outbox sink does not make the
entire Engine unhealthy; a failed database-authority probe does.

## Product and transport boundary

The embedded Java API returns typed keyset cursors. HTTP/Workbench adapters own page-token encoding
and signing. Transport/application layers also own authentication, authorization, four-eyes policy,
rate limiting, HTTP/MQ request dedupe, TLS, and database/backup access control. Privileged Operator audit inputs are
actor, reason, and an optional outer audit-context ID only.
