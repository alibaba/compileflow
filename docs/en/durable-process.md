# Durable Process

CompileFlow Durable persists process state in a `DurableStore`, allowing supported processes to resume after an
application restart. The provided distributions support PostgreSQL and MySQL. Durable is a separate execution surface;
it does not change the behavior of `ProcessEngine` invocations.

## Add the starter

```xml
<dependency>
  <groupId>com.alibaba.compileflow</groupId>
  <artifactId>compileflow-tbbpm</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
<dependency>
  <groupId>com.alibaba.compileflow</groupId>
  <artifactId>compileflow-durable-spring-boot-starter-postgresql</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

The Durable starter selects a store provider but no source format. Add exactly the frontend modules used by the
application; replace `compileflow-tbbpm` with `compileflow-bpmn` for a BPMN-only Durable application, or add both.

When building from source, install the selected frontend, starter, and their reactor dependencies:

```bash
./mvnw install -pl compileflow-tbbpm,compileflow-durable/compileflow-durable-spring-boot-starter-postgresql -am -DskipTests
```

```yaml
compileflow:
    durable:
        enabled: true
        database:
            migrate: true
```

For MySQL 8.4, use `compileflow-durable-spring-boot-starter-mysql` with the same database configuration keys. Do not place both
Provider starters on the classpath unless `compileflow.durable.database.provider` explicitly selects one.

Configure a `DataSource` matching the selected Provider. When the application also has a business `DataSource`, expose the Durable authority
connection as the bean named `compileFlowDurableDataSource`; otherwise the unique or primary `DataSource` is used.
Production deployments should normally run the Provider-owned Flyway migrations externally and disable in-process
migration; startup rejects a mismatched database product, validates the schema, and fails closed when migrations are pending.
Use a separate migration identity for DDL. MySQL with binary logging can require elevated privileges to create the
immutability triggers; do not grant those privileges to the runtime account or relax global trust to run application code.
The runtime identity needs schema validation and the Store's DML permissions, not migration authority.

## Configure exact Version acquisition

```java
ProcessRef.Version process = ProcessRef.version("sales", "approval", "v17");
```

`DurableVersionDefinitionSource` provides the authoritative exact definition for each new Version or Alias admission.
Its `VersionDefinition` carries a typed `ProcessDefinition.Inline` and exact `callBindings`.
Bindings prove that each direct ProcessCall in the returned source agrees with the child Version frozen at publication;
they are admission proof, not an independently mutable execution graph. The source is separate from Alias selection
and is not another Artifact type.
Durable encodes the content as exact UTF-8, computes SHA-256, and stores model type, process code, exact bytes, and
digest as immutable Process semantics. The resulting content-addressed `processId` is the recovery and program-cache
identity; namespace and Version remain optional Run attribution. Recovery reads stored semantics and never consults
the Version source. Each Definition owns its model type. Exact Version ProcessCalls may cross frontend boundaries;
their input/output contracts are validated before starting the Run. Classpath calls inherit the caller's frontend.

Durable accepts the documented strict TBBPM and BPMN profiles and rejects every other model type or unsupported
construct before registration. The schema preserves the closed `ProcessModelType` fact so recovery never guesses a
format from XML. Both frontends lower only semantics already proved by the same format-neutral Kernel; general message correlation, user tasks,
boundary events, and event-based gateways remain separate product choices.

## Start an explicit definition

```java
ProcessRun run = durable.start(
    ProcessRunId.random(),
    ProcessDefinition.inline(ProcessModelType.TBBPM, "approval", definitionText),
    Map.of("orderId", "o-42"));
```

The caller supplies a typed definition. Durable uses its independent `DurableProcessEngineConfig` and Core source loader
to freeze exact source bytes and the declared type. There is no engine-level format default.

Plain Java applications construct the node-local lifecycle root through `DurableProcessEngineFactory.create(config)`.
It immediately accepts application operations. Call no-argument `start()` to start configured Workers, `stop()` to drain them,
and `close()` to drain operations and release owned resources. The Store and supplied capabilities remain application-owned.
Spring uses the same factory and only adapts lifecycle timing. A Durable-only starter does not create a `ProcessEngine`.

## Start by Version or Alias

```java
ProcessRun exact = durable.start(
    ProcessRunId.random(),
    process,
    Map.of("orderId", "o-42"));

ProcessRunId knownId = ProcessRunId.random();
ProcessRun addressable = durable.start(
    knownId,
    process,
    Map.of("orderId", "o-44"));

ProcessRun started = durable.start(
    ProcessRunId.random(),
    ProcessRef.alias("sales", "approval", "prod"),
    Map.of("orderId", "o-43"),
    new AliasRoutingOptions("customer-43", Map.of("region", "cn")));
```

The public API uses type-safe Version and Alias overloads. Code is therefore not a callable Durable
reference, and exact Version has no overload that accepts routing inputs. Alias Start reads committed Alias state,
applies its optional named `ProcessAliasTargetingPolicy`, then falls through to the protocol-defined percentage split,
and retains the selected Version as Run attribution. Routing keys and attributes are admission-only and are not
persisted with the Run.
Admission materializes exact stored Process semantics; Workers and recovery use `processId` and never read Alias.

Start input is a closed, partial map of `param` variables from the selected exact Process Definition. A `return`,
`inner`, or undeclared key fails before the Run is committed. Missing parameters receive definition defaults on the
first Machine Turn, while a present key with a null value remains an explicit null. Later checkpoints contain complete
Process state, but only as Kernel-committed continuation; callers cannot inject that state again.

Every Start accepts a caller-allocated RunId so admission is addressable before the request. RunId is not a generic idempotency key: a duplicate
returns `RUN_ALREADY_EXISTS`, and an ambiguous response is resolved with `getRun(runId)`. Alias duplicate
detection happens before mutable Alias resolution. A caller-allocated value is permanently bound to one Run
occurrence and must never be reused, even after retention. If an HTTP request, message, or business operation must
start only once, the outer application/admission inbox still owns request equivalence and its identity-to-RunId
mapping. The Kernel has no generic idempotency key or command receipt.

Before a replayable Action, Effect input, or Wait-description callback crosses into application
code, the exact Process serializer creates a typed detached graph. This includes mutable POJOs inside
otherwise read-only containers. Mutating the callback input therefore cannot mutate Segment state;
state changes must be returned through the declared Action output mapping or a committed typed
boundary result.

## Persisted state types

Exact stored Process semantics pin the Process source, control semantics, variable declarations, Action declarations, and
semantic recovery coordinates. It does **not** pin Spring beans, Java Action bytecode, third-party libraries,
ScriptExecutor implementations, or application POJO class shape. Every declared Java type reachable from a persisted
variable, scope frame, Effect input/output, or Wait result is therefore an application-managed persistence schema.

Application-owned implementations must keep those values decodable for as long as the corresponding Runs are retained.
CompileFlow does not persist an ApplicationBuildId or route recovery by application build.

CompileFlow does own compatibility of its parser, semantic compiler, resume coordinates, and Engine envelope format.
The persisted envelope has a compact internal version header used only for fail-closed decode and migration; it is not
Process/codec identity. The checked-in compatibility corpus verifies recovery from frozen Definition, continuation,
Wait, Timer, and Effect fixtures.

Persisted Runs require matching application, Kernel, and Store protocol versions. Mixed-version rolling operation is
not a Supported contract.

## Wait and complete

`WAIT_COMMITTED` contains the raw 256-bit Wait token for delivery to the authorized caller. The Wait authority row stores
only its SHA-256 digest; an active Outbox record temporarily retains the raw token until crash-safe delivery succeeds or
the authority is otherwise disposed. Protect the Outbox as credential storage and do not log the raw token.

```java
durable.completeWait(
    new WaitToken(rawToken),
    Map.of("approved", true));
```

The scalar token is globally unique and lets the Kernel discover its owning Run before the Run-first
authority transaction. The committed Wait fact already owns the element/event recovery coordinates; the caller neither
repeats nor overrides them during completion. The token is an opaque, one-shot continuation capability, not a domain identity. A controlled
RPC/MQ can carry it unchanged; an integration can protect an `externalJobId -> WaitToken` mapping
when an external provider returns only its own job ID. Order/payment/inventory systems should keep
using their domain identities and fact stores. No token table is required by the Kernel. Never put a
raw token in a metric label, browser-visible URL, third-party metadata, or Workbench view. CompileFlow does
not provide message subscriptions, buffering, TTL, or early-arrival correlation.

The payload is a typed partial update of variables declared by the Run's exact Process and is
validated before the Wait fact is committed. Completing the same token with the same canonical typed result is a
no-op; a different result conflicts. A cancelled or unrelated occurrence does not accept the token.

## Effect actions

External observations use `execution="effect"` on an Action:

```xml
<action type="spring-bean" execution="effect" bean="payment"
        class="com.example.PaymentService" method="charge">
  <input source="requestId" target="requestId" dataType="java.lang.String"/>
  <effectPolicy recovery="reconcile"
                maxAttempts="3"
                maxReconcileAttempts="10"
                recoveryDelay="PT5S"
                maxRecoveryDuration="PT30M">
    <reconcileAction type="spring-bean" bean="payment"
                     class="com.example.PaymentService" method="queryCharge">
      <input source="requestId" target="requestId" dataType="java.lang.String"/>
      <input source="__cf_effect_id" target="effectId" dataType="java.lang.String"/>
    </reconcileAction>
  </effectPolicy>
</action>
```

Reconcile input sources are persisted fields from the original Effect request, plus the defined
Effect metadata fields; they are not Process expressions. The adapter has no defaults, outputs,
execution mode, or invocation policy. A confirmed result is written through the original Effect
Action's output mapping.

The policy is optional and defaults to manual recovery. Retry/reconcile Actions may map
`__cf_effect_id` when the business invocation needs the stable ID reused for every attempt; that mapping is not itself
an idempotency proof. Structural preparation does not
resolve or construct application components. Spring resolution, Java construction, script evaluation,
and method invocation cross the possible-dispatch boundary; any unprovable result after that boundary
causes UNKNOWN. The uncertainty episode keeps one `unknownSince` across Reconcile claims, and
`maxRecoveryDuration` is measured from that first uncertainty. Business rejection is a normal typed
return and Decision path.

Operator resolution requires the expected `reviewRevision` and is limited to confirmed success,
confirmed not executed plus retry, or fail Run. Repeating a confirmed-success resolution is a no-op
only when its canonical typed result matches the committed result. Repeating a fail-Run resolution is
equivalent only when that Effect resolution actually caused the failed terminal state, not when Run
cancellation merely cancelled the Effect.

## Pause, Resume, Cancel, and Outbox

Pause/Resume are control-state transitions fenced by `expectedControlRevision`. In-flight authority
converges through `PAUSE_REQUESTED`. Application cancellation is `cancel(runId)` and is
first-intent-wins. Authentication, principal attribution, and request audit belong to the boundary
that exposes the operation. Outbox operator changes use `eventId + expectedRevision`.

Outbox exposes only:

- `WAIT_COMMITTED`;
- `EFFECT_REVIEW_REQUIRED`;
- `RUN_SUCCEEDED`;
- `RUN_FAILED`;
- `RUN_CANCELLED`.

Provide `DurableOutboxSink` only as the generic external-delivery adapter. Delivery is at least once and every retry
contains the same event ID, type, and logical payload. A successful return means the destination boundary durably
accepted the event, not that a process-local buffer accepted it. The sink must durably deduplicate by event ID or
propagate it unchanged downstream; arbitrary external side effects are never claimed as exactly once. This SPI is not
the contract for a CompileFlow-owned Run-to-Run protocol.

## Queries

Embedded query objects and pages carry typed keyset cursors (`ProcessRunCursor`,
`ProcessTimelineCursor`, `OutboxEventCursor`). A web adapter may encode and sign those cursors as an
opaque page token, but token security is not a Kernel concern.

`getRun(runId)` and `listRuns(query)` return payload-blind Run data. `getRunResult(runId)` is the explicit
payload-bearing query and returns a closed `ProcessRunResult`: `NotFound`, `NotCompleted`,
`Succeeded`, `Failed`, or `Cancelled`. This avoids conflating a missing Run with an unfinished one.

## Operational rules

- The selected Store's database time owns availability and leases.
- Lock order is Run first and exact occurrence second.
- Claim/reclaim generates a new random lease token; renew keeps it; completion must match it.
- Continuation/Effect/Wait payloads and raw tokens must not be logged.
- Back up and restore all seven Durable tables together. Stop all runtimes during PITR and rebuild
  disposable program caches afterwards.
- Retain every exact Process Definition referenced by a recoverable Run, backup, or WAL/PITR point. The
  optional bounded unused-Process policy deletes only Definitions with no Run reference; the Run-Process foreign keys and
  Start key-share lock make concurrent admission/GC referentially safe.
- Authentication, authorization, approval policy, request dedupe, rate limiting, TLS, and database
  access control belong to the transport/application boundary.
