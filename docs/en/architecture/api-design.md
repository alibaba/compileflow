# API design principles

## Purpose

CompileFlow chooses an API shape according to the operation's role. The goal is a small, stable public surface in which
required information is explicit, invalid requests are difficult to construct, and long-lived concepts have one
canonical representation.

## Choose a Shape by Role

| Role                                           | Shape                               | Examples                                                                |
| ---------------------------------------------- | ----------------------------------- | ----------------------------------------------------------------------- |
| Frequent embedded application call             | `verb(required arguments, Options)` | `ProcessEngine.execute`, `DurableProcessEngine.start`, `completeWait`   |
| Audited or revision-checked control operation  | `verb(Command)`                     | Deploy rollout commands; Durable pause, resume, and resolution commands |
| Read with several filters or pagination fields | `verb(Query)`                       | Run, timeline, outbox, version, and rollout queries                     |

A small application call needs a command wrapper only when the wrapper adds a meaningful contract.

## Principles

1. Choose the method shape from the work it authorizes, not from visual uniformity with unrelated APIs.
2. Use distinct types or overloads when they prevent an invalid request at low cost. Exact-version and Alias admission,
   for example, must not accept a generic `ProcessRef` and reject it later.
3. Put required domain information in method arguments. Put optional per-call behavior in `Options`; do not use an
   options type as a miscellaneous domain DTO.
4. Reserve `Command` for audited, privileged, or revision-checked mutations. Do not wrap frequent embedded calls such
   as execute, start, completeWait, or cancel without a specific semantic need.
5. Use immutable `Query` values when a read needs several filters, ordering rules, bounds, or a continuation cursor.
6. Carry canonical identity types end to end. Stable APIs use `ProcessRef.Version` and `ProcessRef.Alias` instead of
   repeatedly decomposing them into strings.
7. Keep capabilities and cursors opaque. Applications may transport their scalar value, but must not depend on storage
   keys, internal run or element identities, or ordering details.
8. Represent wall-clock time with `Instant`. Represent elapsed time with `Duration`, or include the unit in the field
   name.
9. Keep complex public views evolvable. Prefer a builder or another role-appropriate construction boundary to a long
   positional constructor.
10. Treat the Java API, wire protocol, and database schema as separate contracts. A JSON string or SQL `bigint` does not
    determine the public Java domain type.
11. Do not infer product support from JVM `public` visibility. Implementation metrics, repository records, adapters,
    and composition plumbing are outside the Supported surface unless the supported-surfaces specification lists them.
12. Add a public type only when it exposes a necessary capability or prevents a meaningful class of invalid requests.

## Stable Identity and Admission Rules

- `ProcessDefinition` has explicit `Inline` and `Classpath` sources; `ProcessRef.Version` and
  `ProcessRef.Alias` are the two published-reference variants.
- `ProcessExecution` reports the process identity and optional exact published `ProcessRef.Version` that ran.
- Immediate execution accepts the reference forms supported by `ProcessEngine` routing.
- Durable starts from either `ProcessRef.Version` or `ProcessRef.Alias`, resolving an alias once. Both paths store the
  resolved process definition. The version may be recorded as the start source, but recovery uses the stored process ID
  and never routes through a version or alias again.
- Alias is mutable Deploy control-plane state, never Durable recovery identity.
- Deploy domain artifacts live in `deploy.api.artifact`; protocol parsers, payloads, and keys live in
  `deploy.protocol`.

## Identity and Ownership

Identity categories distinguish responsibilities; they do not require a separate public type for every category.

| Category    | Fact and owner                                                                                                   | Must not substitute for                                                   |
| ----------- | ---------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| Semantic    | `ProcessDefinition` declares model type and code; its resolved snapshot fixes exact source bytes                 | Engine configuration or a mutable resource path as exact content identity |
| Admission   | `ProcessRef.Version` or `ProcessRef.Alias` identifies the requested published target; admission resolves routing | Durable recovery identity                                                 |
| Runtime     | Engine-local executable realization includes semantic content, construction pipeline and ClassLoader scope       | A portable database key                                                   |
| Recovery    | Durable Store retains exact Processes, continuation and frozen call-site targets for a Run                       | Current Alias routes, version sources or local caches                     |
| Operational | Persisted control state, revisions and lease authority govern operator and worker mutations                      | Process code, invocation attribution or diagnostic worker names alone     |

An engine manages its construction configuration and runtime resources. Internal components receive only the dependencies
and values they need. Framework adapters bind properties and coordinate startup and shutdown without duplicating resource
management already handled by the engine.

Database columns, artifact projections, and diagnostics may hold copies of the same data. Specify which copy is
authoritative and how other copies are validated and invalidated.

Admission and recovery need different dependencies, not duplicate admission implementations. All sources admitted under
the same policy pass the same bounds before semantic compilation. Recovery uses stored exact semantics without consulting
admission sources or applying a new admission-size policy to an existing Run.

## Complexity and ownership

Each abstraction represents a domain fact, capability, or resource lifetime with one clear owner. Configuration,
factories, providers, runtime state, database constraints, and framework adapters must preserve that ownership rather
than create independently configurable copies of the same fact.

Coordination remains explicit where it enforces concurrency, authority, recovery, or resource-lifetime guarantees.
Implementation limits stay internal unless they define a documented product contract.

## Contract summary

Supported APIs expose the operation's role, invalid-state strategy, canonical identity, time types, construction strategy,
and support tier. Durable follows the same API design principles.

See [Supported Surfaces](supported-surfaces.md), [Version Routing](version-routing.md), and
[Durable Architecture](durable-architecture.md).
