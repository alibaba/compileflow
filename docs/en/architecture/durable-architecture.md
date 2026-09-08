# Durable architecture

Durable is the optional execution product for processes that must survive application restarts. It is a separate execution product, not a persistence switch for `ProcessEngine`.

## Core invariant

The Store is authoritative for a Run. A Worker loads committed semantic state, performs one bounded turn, and commits the next continuation and all facts produced by that turn atomically. A crash before commit is retried; a crash after commit is resumed from the committed boundary.

The Kernel does not persist generated Java source, classes, bytecode, live object instances, executor state, or an in-memory route. Declared application values are serialized as typed state. Compilation is disposable preparation. Recovery rebuilds executable state from the exact stored process definition and the committed semantic checkpoint.

## Admission and execution

Start admission materializes one exact immutable stored Process and binds the Run to its `processId`. It resolves an exact `ProcessRef.Version` or a published Alias to an exact version. Alias is Deploy control-plane state, not recovery authority. The Engine rejects a reused Run ID before resolving Alias once. Alias movement affects later admissions; it does not retarget an admitted Run. `DurableProcessEngine` and `ProcessEngine` are sibling execution surfaces.

Durable supports the documented TBBPM and BPMN profiles only. An Action declares `execution=replayable|effect`; this is distinct from the synchronous retry policy. The Durable API exposes Run, Effect, and operator contracts. Definition registration is start-admission preparation, not an application lifecycle API; Deploy is not required to recover a Run and its route state is not recovery authority.

## Worker lifecycle

`DurableWorkerCoordinator` in `compileflow-durable-runtime` owns polling, bounded execution slots,
maintenance scheduling, and worker health. It has no Spring dependency. `DurableWorkerLifecycle`
only adapts Spring startup and shutdown timing through the owning `DurableProcessEngine`; disabling workers creates
neither the worker graph nor its lifecycle adapter.

Stopping prevents new scheduling and drains admitted work without interrupting application code.
The completion callback runs only after that work exits. The composing host must retain Store,
lease-renewal and runtime resources until draining completes; stopping is not proof that an
external Effect was canceled. Application and operator APIs remain separate capabilities.

## Process identity

A stored process definition has a content-addressed `processId` and immutable process code, model type, exact bytes, and digest. Namespace and optional Version belong to Run admission attribution, not stored semantic identity. Direct definitions with the same code may produce different `processId` values without a Version. Published Version content remains immutable. Every Run stores one exact root `processId`; retention must preserve every definition referenced by a recoverable or retained Run.

## Run and invocation boundaries

Each Start creates one Run with a stable Run ID. A Process Call creates an invocation frame in that same Run; it does not create another Run. Invocation frames carry the semantic input, output, status, and continuation needed by the owning turn.

Root input is a closed, partial map of declared `param` variables. `return` and `inner` remain Process-owned. Undeclared keys fail before application code runs. Completing a Wait commits a typed result for that existing Run; it does not create a new invocation or Run.

## Recovery authority

Recovery uses the committed semantic checkpoint, exact process identity, typed invocation state, pending requests, Wait/Timer facts, Effect facts, and bounded ownership lease. Database time and fencing tokens protect ownership. A late Worker completion is rejected after ownership changes.

Preparation may compile a disposable runtime. It must complete before execution uses it, and a failed preparation cannot advance the Run. A Worker renews its lease while it owns a turn and releases ownership on completion or failure.

A missing application class, component, script executor, or serializer is an application/runtime capability problem.
Restore that capability in the deployment without rewriting stored process semantics.

## Action and Effect

An Action is deterministic process logic or a governed external boundary. A replayable Action may run again from the same semantic inputs. An external Effect request is committed with a stable occurrence identity before dispatch, and its outcome is resolved separately. That identity enables application-owned deduplication but does not prove it; the Kernel does not pretend that an interrupted external call is automatically reversible.

Durable rejects non-default `invocationPolicy`; synchronous retry and timeout policy belongs to `ProcessEngine` invocations. Durable Effect recovery uses the committed `effectPolicy` and fenced Store transitions. A failed or interrupted external call can leave an unknown outcome and must not be treated as proof that nothing happened.

## Wait completion and queries

A `WaitToken` is an opaque, one-shot bearer capability for one materialized Wait occurrence. The Store retains only its digest as authority and discovers the owning Run from that digest; the caller cannot supply recovery coordinates. This does not require an application token table, although an integration may retain a mapping when an external system exposes only its own job ID. Raw tokens are credentials and must not enter logs, URLs, metrics, or operator views.

Run, timeline, and Outbox queries use typed exact filters and keyset cursors. Durable does not provide fuzzy search; adapters may build separate non-authoritative search projections.

## State, transactions, and stores

Run state, invocation state, requests, leases, and Effect/Outbox facts use explicit state transitions. A Store Provider must preserve transition atomicity, lock ordering, compare-and-set behavior, database-time semantics, and token fencing. PostgreSQL and MySQL are independent first-party Providers with their own migrations; H2 is test-only.

The physical table layout is Provider-owned. The Kernel contract is the transaction and recovery semantics, not a fixed
table count. The provider-neutral testkit checks the common Store contract; support also requires the database-specific
transaction, concurrency, crash-recovery, and migration evidence listed in [Supported surfaces](supported-surfaces.md).

## Retention and observability

Process definitions are deletable only after no retained Run references them and the applicable backup and rollback window permits deletion. Generated runtimes may be released earlier. Retention is reference-based; keeping only the latest N versions is insufficient.

Run views and metrics expose controlled state and outcomes. They must not expose payload variables, credentials, routing keys, lease tokens, or other secrets. See [Durable Process](../durable-process.md), [Durable operations](../durable-operations-runbook.md), and [Supported surfaces](supported-surfaces.md).
