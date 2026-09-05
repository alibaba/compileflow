# API Design Principles

## Purpose

CompileFlow chooses an API shape according to the operation's role. The goal is a small, stable public surface in which
required information is explicit, invalid requests are difficult to construct, and long-lived concepts have one
canonical representation.

## Choose a Shape by Role

| Role | Shape | Examples |
|---|---|---|
| Frequent embedded application call | `verb(required arguments, Options)` | `ProcessEngine.execute`, `DurableProcessEngine.start`, `completeWait` |
| Audited or revision-checked control operation | `verb(Command)` | Deploy rollout commands; Durable pause, resume, and resolution commands |
| Read with several filters or pagination fields | `verb(Query)` | Run, timeline, outbox, version, and rollout queries |

A small application call does not need a command wrapper simply to resemble a control-plane API.

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
- Durable exact admission accepts `ProcessRef.Version`. Published Alias admission accepts `ProcessRef.Alias`, resolves it once,
  and materializes the same exact stored Process semantics. Version may remain admission attribution, but recovery uses
  the stored Process ID and never routes through Version or Alias again.
- Alias is mutable Deploy control-plane state, never Durable recovery identity.
- Deploy domain artifacts live in `deploy.api.artifact`; protocol parsers, payloads, and keys live in
  `deploy.api.protocol`.

## Review Checklist

A change to a Supported API must document the operation's role, invalid-state strategy, canonical identity, time types,
construction strategy, and compatibility tier. Durable remains Developer Preview, but its core vocabulary follows the
same review before promotion.

See [Supported Surfaces](06-SUPPORTED_SURFACES.en.md), [Version Routing](05-VERSION_ROUTING.en.md), and
[Durable Architecture](10-DURABLE_ARCHITECTURE.en.md).
