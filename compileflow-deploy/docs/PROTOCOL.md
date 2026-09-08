# CompileFlow Deploy Protocol

CompileFlow projects routing intent and immutable process artifacts from the control plane
(`compileflow-deploy-control-plane`) to the runtime (`compileflow-deploy-runtime`) as two independent streams:

```text
artifact stream: immutable source for one published version
alias stream: mutable complete desired state for one alias
```

Publishing an artifact never changes traffic. Changing an alias never retransmits process content. This separation is
part of the protocol contract.

## Protocol Generation

Control-plane writers, projection payloads, and runtime readers in one deployment must use the same protocol generation.
Deploy supports coordinated homogeneous upgrades only; it does not support mixed-version rolling operation.
Alias-state and artifact parsers accept exactly schema `1` and reject unknown fields. The protocol uses explicit
versioned payload codecs rather than a generic codec registry.

In artifact mode `PROJECTION_STORE`, publication stores the immutable publication record before projecting it. A failed
projection makes the publish command fail but does not discard or rewrite that record; an exact retry is idempotent.
Before create or rollback can introduce the version into an Alias, the control plane reloads the persisted artifact and
confirms the projection again. A projection failure therefore cannot commit a route that newly references an
unavailable artifact.

## 1. Transport And Keys

Distributed topology uses `com.alibaba.compileflow.deploy.spi.projection.DeploymentProjectionStore` from
`compileflow-deploy-spi`. This version-coupled provider transport is not part of the supported application API:

```java
String read(String key, Duration timeout) throws Exception;
boolean compareAndSet(
    String key,
    String expectedContent,
    String content,
    String contentType,
    Duration timeout) throws Exception;
Subscription subscribe(
    String key,
    UpdateCallback callback,
    Duration timeout) throws Exception;
```

CAS compares exact content atomically; a null expected value means create-if-absent. The contract has no unconditional
write because a delayed routing publisher must not replace a newer revision and concurrent artifact publishers must not
rebind an immutable version key. Adapters must use a server-side atomic primitive, never a client-side read/write pair.
Each timeout is a positive deadline for the entire remote operation or subscription setup; an adapter must fail the call
when that deadline expires rather than leave a worker blocked indefinitely. Notifications are convergence hints and may
be duplicated, delayed, or coalesced. Once `subscribe` returns, a successful later update must eventually be reflected
by a callback. Runtime subscribes before its initial read and validates every configured initial value before emitting
any of them, closing the startup race without partially applying a corrupt key set.

`InMemoryDeploymentProjectionStore` is a deterministic test implementation. CompileFlow does not bundle a production remote
adapter. A distributed deployment must provide an implementation that proves all of these properties against the real
backing service:

1. `read` returns the current server value or fails; it never substitutes a local failover or stale snapshot.
2. CAS is atomic for both existing values and `expectedContent == null` create-if-absent.
3. A successful update after subscription establishment is eventually observable by the subscriber.
4. Integration tests exercise competing creates, competing updates, delayed writers, subscription-establishment races,
   reconnects, missed updates after backend compaction, and backend failures or partitions.

Stores whose publish operation is create-or-update, whose absent-key condition is not an atomic create-if-absent, or
whose reads may return local failover or snapshot content do not satisfy this contract. A client-side read/write
sequence cannot replace server-side CAS.

The two streams have separate key spaces:

| Stream           | Default key shape                               | Example                                  |
| ---------------- | ----------------------------------------------- | ---------------------------------------- |
| Alias state      | `compileflow.deployment.alias.{identityDigest}` | `compileflow.deployment.alias.5ec738...` |
| Process artifact | `compileflow.process.version.{identityDigest}`  | `compileflow.process.version.14546f...`  |

`identityDigest` is lowercase SHA-256 over the ordered identity tuple. Each UTF-8 segment is prefixed by its unsigned
four-byte big-endian length before hashing, so tuples such as `(a, b.c)` and `(a.b, c)` cannot collide through delimiter
ambiguity. Alias keys hash `(namespace, code, alias)` and artifact keys hash `(namespace, code, version)`. The payload
retains the full identity, and consumers recompute the digest before accepting it. The bounded ASCII key format remains
portable across common remote stores even when identifiers are at their maximum supported length.

Separate prefixes allow independent ACL, retention, size, and transport policies. Prefixes are configuration, while the
digest suffix is protocol. Prefixes may contain ASCII letters, digits, `.`, `_`, `:`, and `-`, and may not exceed 128
UTF-8 bytes. Embedded topology delivers the same alias payload through the same parser and local-ready convergence
coordinator without a remote projection store. A remote adapter may impose a stricter key or payload limit, but must validate it
before I/O and document the operational capacity requirement.

## 2. Alias State

The alias stream carries a complete route for one `(namespace, code, alias)`. A route has one stable version and at most
one candidate:

```json
{
    "schemaVersion": 1,
    "kind": "aliasState",
    "namespace": "default",
    "code": "order.process",
    "alias": "production",
    "stableVersion": "1",
    "candidateVersion": "2",
    "candidateWeightBps": 1000,
    "targetingPolicy": "regional-cohort",
    "targetingParameters": {
        "region": "cn"
    },
    "revision": 44,
    "actor": "alice",
    "updatedAt": 1760000002000
}
```

| Field                 | Type    | Required       | Contract                                                        |
| --------------------- | ------- | -------------- | --------------------------------------------------------------- |
| `schemaVersion`       | integer | yes            | Exactly `1`; strings and floating-point values are invalid      |
| `kind`                | string  | yes            | Exactly `aliasState`                                            |
| `namespace`           | string  | yes            | Explicit, validated namespace                                   |
| `code`                | string  | yes            | Validated process code                                          |
| `alias`               | string  | yes            | Validated alias name                                            |
| `stableVersion`       | string  | unless deleted | Stable published version                                        |
| `candidateVersion`    | string  | no             | Candidate published version, different from stable              |
| `candidateWeightBps`  | integer | with candidate | Candidate weight in `1..9999` basis points                      |
| `targetingPolicy`     | string  | no             | Named targeting policy; allowed only with a candidate           |
| `targetingParameters` | object  | no             | String parameters for `targetingPolicy`                         |
| `deleted`             | boolean | no             | Exactly `true` for a tombstone; omitted for a live route        |
| `revision`            | integer | yes            | Positive authoritative per-alias ordering value                 |
| `actor`               | string  | yes            | Human or service principal that caused the route mutation       |
| `updatedAt`           | integer | yes            | Positive mutation timestamp used only for audit and diagnostics |

The stable share is the residual `10000 - candidateWeightBps`. A stable-only route omits `candidateVersion`,
`candidateWeightBps`, `targetingPolicy`, and `targetingParameters`. `targetingParameters` is allowed only with
`targetingPolicy`; the named policy must be registered on every runtime node before the route becomes locally ready.
Unknown fields are rejected.

Deletion uses the same resource kind:

```json
{
    "schemaVersion": 1,
    "kind": "aliasState",
    "namespace": "default",
    "code": "order.process",
    "alias": "production",
    "deleted": true,
    "revision": 45,
    "actor": "alice",
    "updatedAt": 1760000003000
}
```

A tombstone contains no stable or candidate fields. Its revision high-watermark prevents delayed updates from
resurrecting the alias.

### Ordering And Failure Semantics

- `revision` is the only ordering source. Consumers ignore duplicate and lower revisions.
- `updatedAt` never participates in ordering. It may trigger a throttled fresh read for diagnostics.
- The callback key must equal the subscribed key, and the payload identity must match that key.
- A blank projection store value means that no route state is present.
- A malformed or identity-mismatched initial value fails subscriber startup.
- A malformed or identity-mismatched live update is ignored while the last valid route remains active.
- Runtime prepares every demanded artifact before atomically publishing local-ready alias state.
- A failed preparation keeps the last valid local-ready state; it never falls back to another version.

## 3. Process Artifact

The artifact stream carries the exact immutable source for one `(namespace, code, version)`:

```json
{
    "schemaVersion": 1,
    "namespace": "default",
    "code": "order.process",
    "version": "2",
    "modelType": "TBBPM",
    "content": "<bpm code=\"order.process\">...</bpm>",
    "artifactDigest": "f74b220b3c08f51d8790800aa8f79a916bec6e60285b074613779928b9f35d18",
    "callBindings": [
        {
            "callSiteId": "charge",
            "code": "payment",
            "namespace": "default",
            "version": "3"
        }
    ]
}
```

| Field            | Type    | Required | Contract                                                                                                                          |
| ---------------- | ------- | -------- | --------------------------------------------------------------------------------------------------------------------------------- |
| `schemaVersion`  | integer | yes      | Exactly `1`                                                                                                                       |
| `namespace`      | string  | yes      | Explicit, validated namespace                                                                                                     |
| `code`           | string  | yes      | Validated process code                                                                                                            |
| `version`        | string  | yes      | Validated immutable version identifier                                                                                            |
| `modelType`      | string  | yes      | Exact supported model type                                                                                                        |
| `content`        | string  | yes      | Non-blank exact process-definition content; never normalized in transit                                                           |
| `artifactDigest` | string  | yes      | Lowercase SHA-256 computed by `ProcessArtifactDigest` over the domain-separated definition digest and sorted direct call bindings |
| `callBindings`   | array   | yes      | Source-derived direct `callSiteId -> exact Version` bindings; call IDs are unique                                                 |

Each call binding contains `callSiteId`, `code`, `namespace`, and `version`. Its namespace must equal the parent artifact
namespace, and its code must equal the exact target code. Bindings freeze publication-time direct linkage; transitive
closure is obtained by recursively reading the referenced immutable artifacts and is never duplicated in one payload.

Each exact Version owns its immutable `modelType`. Different Versions of the same `(namespace, code)` may use
different process formats, including stable and candidate Versions on one Alias. Exact Version ProcessCalls may cross
format boundaries when their shared semantic input/output contracts agree. The wire field remains on every
Version artifact so each payload is self-describing at the Runtime boundary.

The producer refuses to serialize a mismatched digest. The consumer recomputes the artifact digest, checks payload
identity against the requested version, reparses the definition to verify that its declared calls exactly match the
persisted bindings, and rejects unknown fields. SHA-256 detects corruption and identity mismatch; it is not an
authenticity signature.

Release metadata remains in the Control Plane publication record and is absent from this executable
Runtime payload. The publish command's optional `expectedArtifactDigest` field owns compare-before-publish integrity
semantics.

Protocol version 1 has no artifact tombstone. Published version identity is immutable, and a missing artifact fails as
`VERSION_NOT_FOUND`.

## 4. Construction APIs

```java
RoutingStateKeys.aliasState(prefix, namespace, code, alias)
    -> prefix + "alias." + digestIdentity(namespace, code, alias)

ProcessArtifactKeys.versioned(prefix, namespace, code, version)
    -> prefix + "version." + digestIdentity(namespace, code, version)
```

Defaults:

- Routing prefix: `compileflow.deployment.`
- Artifact prefix: `compileflow.process.`

Every key-construction call requires an explicit canonical alias. Spring routing configuration separately defaults its
alias list to `production`; that composition default is not a wire canonicalization rule.

## 5. Delivery And Convergence

Route, rollout, audit event, and routing-outbox records commit in one authoritative `DeployStore` transaction. Built-in
providers implement this contract with JDBC. Alias delivery is at least once:

1. The dispatcher publishes only committed outbox rows.
2. Distributed delivery advances projection store state with exact-content CAS; a late lower revision is a successful no-op.
3. Consumers independently discard duplicate and lower route revisions because notification order is not authoritative.
4. Reconciliation compares the complete repository route with the projection store projection.
5. A missing or lower-revision projection can be replayed with the original route revision, actor, and timestamp.
6. Exact delivery work is coalesced by a store-unique SHA-256 key. Repeated or concurrent reconciliation leaves one
   active row; a delivered row can be reactivated if the same projection drifts again, while a dead-letter requires
   explicit operator requeue.
7. Same-revision or newer conflicting content is reported but not rewritten automatically. Repair requires a new
   authoritative route mutation and revision.
8. In `PROJECTION_STORE` artifact mode, the same reconciliation cycle deduplicates the stable/candidate versions of all
   authoritative Aliases and detects missing immutable artifact projections. With repair enabled, it recreates them with
   create-if-absent CAS.
9. An existing conflicting or corrupt artifact projection is reported and never overwritten. One artifact failure does
   not prevent routing repair or checks for other active versions.
10. Runtime resolves and retains every demanded immutable artifact before publishing local-ready alias state.
11. Superseded runtimes are released only after the local route swap succeeds.

Protocol errors use `DeploymentException` with a stable `DeploymentErrorCode` and bounded context fields. Payload
content, routing keys from requests, and secrets are not included in routine diagnostics.
