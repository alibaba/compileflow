# CompileFlow 2.0 Durable Terminology

The terms below apply to the Durable public API and architecture. The general engine and deployment
vocabulary remains in the [project glossary](../en/glossary.md). Java type names, enum values, configuration keys, and
wire fields keep their source spelling when they appear in prose.

A process definition or its XML language is a **process model**, not a “process protocol.” The word *protocol* is
reserved for wire, database, and synchronization contracts.

| Term | Meaning |
|---|---|
| Process definition | Model content supplied by the caller. `ProcessDefinition.Inline` contains the process code and text; it does not carry a version, alias, model type, or deployment metadata. |
| Process artifact | Deploy-owned executable projection: an exact version, its verified definition, digest, and direct call bindings. Release metadata remains in the Control Plane publication record. Durable does not expose a second artifact wrapper. |
| Process version | Immutable long-term identity `(namespace, code, version)`. A version is never reused. |
| Alias | Mutable Deploy pointer used when admitting a new invocation or run. Recovery never resolves an alias again. |
| Process invocation | One stateless `ProcessEngine.execute` or `trigger` call. The application owns any continuation across calls. |
| Durable run | Recoverable continuation owned by the Durable kernel and permanently bound to one exact stored Process identity. A Version is optional admission attribution, not recovery authority. |
| Wait occurrence | One materialized external wait. Its `WaitToken` identifies that occurrence, not a worker attempt, lease, delivery, or domain operation. |
| Complete Wait | `DurableProcessEngine.completeWait` commits the typed result of one wait occurrence. It does not synchronously advance or complete the owning run; a Durable Worker performs subsequent progress. |
| Trigger | Selects an entry for a local `ProcessEngine` invocation. It is not the Durable wait-completion operation. |
| Effect | Durable execution semantics for an action that may observe an external system. It is not a separate business-node family. |
| Semantic checkpoint | Process-level recovery position: a resume point plus scope and control frames. It contains no process variables. |
| Continuation snapshot | Persisted state required to continue: a semantic checkpoint plus typed process variables. Generated code and current application capabilities are excluded. |
| Frontier | One independently runnable or waiting Process continuation held inside a continuation snapshot. Frontier order is persisted deterministic scheduling state. |
| Machine turn | One bounded advancement of exactly one selected frontier, followed by one fenced Store commit. A turn may yield, wait, issue an occurrence, or reach a terminal outcome. It is not an application transaction or a worker polling attempt. |
| Journal | Append-only audit and diagnostic facts. It is neither the recovery log nor a one-to-one copy of the outbox. |
| Outbox | At-least-once delivery for the closed integration-event set. Consumers deduplicate by event ID. |
| Run view | Payload-free, sanitized lifecycle projection returned by `getRun` and `listRuns`. |
| Run result | Explicit payload-bearing result returned by `getRunResult`: missing, active, succeeded, failed, or cancelled. |
| Command | Structured mutation request for a privileged or control-plane operation whose revision, actor, reason, or audit context must travel together. Frequent application calls use direct semantic arguments. |
| Query | Immutable filters, ordering, bounds, and cursor for a read operation. |

In general prose, use normal sentence capitalization for common nouns such as “run,” “worker,” and “effect.” Capitalize
an exact Java type, enum value, or named product surface only when the spelling carries technical meaning.
