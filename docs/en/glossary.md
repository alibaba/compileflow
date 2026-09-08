# CompileFlow glossary

Use these terms consistently throughout the English documentation. Java type names, enum values, property keys, and
HTTP fields retain their source spelling.

## Engine And Compilation

| Term                 | Meaning                                                                                                      |
| -------------------- | ------------------------------------------------------------------------------------------------------------ |
| Process engine       | Long-lived, multi-frontend `ProcessEngine`                                                                   |
| Process reference    | Reference to an exact published Version or a published Alias                                                 |
| Process definition   | Explicit inline or classpath content                                                                         |
| Preflight            | Parse, validate, and optionally prepare a cached exact runtime, without Version binding or Alias publication |
| Warm-up              | Prepare an explicit definition in the engine cache                                                           |
| Compile-then-execute | Generate and compile Java before running a process                                                           |
| Generated runtime    | Engine-owned compiled process class and runtime wrapper                                                      |
| Runtime identity     | Engine-local exact compilation identity (`ProcessRuntimeIdentity`)                                           |
| Runtime cache        | Bounded cache of prepared runtimes                                                                           |
| Invocation ID        | Stable identifier for one accepted invocation                                                                |
| Process execution    | Execution details returned with an outcome                                                                   |

## Routing And Deployment

| Term              | Meaning                                                     |
| ----------------- | ----------------------------------------------------------- |
| Version           | Immutable published process identity                        |
| Alias             | Named stable/candidate route for an environment             |
| Stable version    | Default version selected by an Alias                        |
| Candidate version | Version receiving canary traffic                            |
| Rollout           | Revision-checked operation that changes Alias traffic       |
| Canary            | Weighted traffic to a candidate version                     |
| Promote           | Make the candidate the sole stable version                  |
| Abort             | Restore the stable route captured by an active canary       |
| Rollback          | New rollout to a baseline captured by a completed rollout   |
| Routing key       | Request metadata used for deterministic cohort selection    |
| Route revision    | Monotonic concurrency token for one Alias route             |
| Control plane     | Authoritative publication, route, rollout, and outbox state |
| Runtime           | Runtime installation and published execution                |

## Convergence And Reliability

| Term                 | Meaning                                                                       |
| -------------------- | ----------------------------------------------------------------------------- |
| Desired state        | Newest authoritative Alias state observed by a runtime                        |
| Local-ready state    | Alias state whose required runtimes are installed locally                     |
| Runtime installation | Resolve, verify, compile, and retain an exact version                         |
| Transactional outbox | Delivery record committed with authoritative state                            |
| Reconciliation       | Repair a projection from authoritative state                                  |
| Tombstone            | Revisioned removal that prevents stale state resurrection                     |
| Dead letter          | Delivery or execution exhausted after bounded attempts                        |
| Fencing token        | Lease-specific token that must match at commit time and rejects stale workers |
| At-least-once        | Retry model that can repeat external side effects                             |

## Durable Execution

For Run, Wait, Effect, Journal, Outbox, checkpoint, and result terminology, see [Durable terminology](architecture/terminology.md).

## Process Structure

| Term              | Meaning                                                              |
| ----------------- | -------------------------------------------------------------------- |
| Split gateway     | Gateway with one incoming and multiple outgoing transitions          |
| Join gateway      | Gateway with multiple incoming and one outgoing transition           |
| Branch frame      | Isolated generated-process state for one concurrent branch           |
| Continuation      | Shared path generated once after branch convergence                  |
| Invocation policy | Retry, timeout, and failure handling for one synchronous action call |
| Trigger entry     | Named entry for a new trigger invocation, not a Durable checkpoint   |
| Process variable  | Typed value declared by the process definition                       |
| Routing attribute | Policy input kept separate from process variables                    |
