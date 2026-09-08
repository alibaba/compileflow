# CompileFlow Operate API Contract

> Scope: Workbench Operate real mode and `compileflow-workbench-server`
>
> Wire contract: [`compileflow-workbench-server.openapi.json`](../../../../../docs/specs/openapi/compileflow-workbench-server.openapi.json)

The OpenAPI document defines endpoint paths, methods, request and response schemas, required fields, and wire-level
enums. This document covers behavior that is difficult to express in OpenAPI: trust boundaries, identity, optimistic
concurrency, idempotency, routing, retries, and data-exposure rules. Endpoint and schema inventories remain in OpenAPI.

The generated TypeScript projection is
[`workbenchServerOpenApi.ts`](../shared/contracts/generated/workbenchServerOpenApi.ts). Domain contracts may narrow wire
strings into safer unions; [`serverContractParity.ts`](../shared/contracts/serverContractParity.ts) verifies their field
sets and base types.

## Request and Trust Boundary

Workbench sends relative, same-origin requests under `/api`. Vite selects a loopback development target. Production
routes the Web assets and `/api/**` through an authentication-capable gateway, even when one executable image contains
both the SPA and Workbench Server.

The browser never stores or sends the shared Workbench Server API key. The production gateway authenticates and
authorizes the end user, removes client-supplied internal credential and forwarding headers, and adds its private key
only on the protected upstream hop. That key identifies the gateway service principal, not the end user.

## Common Rules

- Page-number pagination is 1-based. Version and deployment list cursors are opaque and must not be parsed or
  constructed by clients. Attempt records use the numeric `afterSequence` cursor described below.
- JSON fields documented as integers must be JSON integers; the Server rejects fractional values instead of truncating
  them.
- Process resources use `code`; deployment, execution, log, and metric references use `processCode`.
- An Alias is an opaque routing identity. `dev`, `staging`, and `production` are UI suggestions, not protocol enums.
- Mutable resources use explicit revision preconditions. A stale revision returns `412 Precondition Failed`; a semantic
  state conflict returns `409 Conflict`.
- Request and transport failures use RFC 9457 Problem Details with media type `application/problem+json` and a stable,
  uppercase `code` extension.

An accepted execution request can still produce a process-domain failure. In that case the endpoint returns `200` with
`success=false`, routing attribution, the engine's stable `errorCode`, and a safe message. Clients must not treat this
domain result as an HTTP Problem Detail.

Problem Details and domain failures never expose stack traces, source content, secrets, arbitrary exception messages,
process variables, result payloads, routing keys, or internal lease tokens.

## Process Drafts and Versions

Process resources are editable drafts. Immutable published versions and Alias routes belong to the deployment control
plane and are not duplicated as draft status fields.

- List entries omit the potentially large `xml` definition. Allowed `sortBy` values: `name`, `createdAt`, `updatedAt`.
  `sortOrder` is `asc` or `desc`.
- Every draft and summary carries a non-negative `revision`. Update replaces all mutable fields and requires
  `expectedRevision`; omitting the optional description clears it. Delete also requires the expected revision.
- Tags are stored as a JSON string array. Blank tags are removed and duplicates are collapsed in encounter order;
  commas inside a tag are preserved.
- Import accepts one TBBPM `<bpm>` document or one BPMN 2.0 `<definitions>` document containing exactly one top-level
  process. The process code and display name come from the XML, not the filename. DTD processing and external resource
  resolution are disabled; malformed XML, ambiguous definitions, and unsupported root formats are rejected.
  Import creates a draft; executable validation and compilation preflight occur on publication.
- Publish freezes one draft revision, runs strict lint and compilation preflight, and then creates an immutable Version.
  It requires `Idempotency-Key`. Retrying the same key and content returns the same Version; reusing the key for
  different content is a conflict. Publish does not change an Alias route or install a runtime.
- A Version is inherently published. Version responses therefore do not carry a redundant lifecycle status.

## Deployment and Alias Routing

Deployment commands select an exact process Version and mutate one Alias route. A route contains a stable Version, an
optional candidate Version and candidate weight, plus the revision used for compare-and-set updates.

- `expectedRouteRevision=0` means the route must not exist. Any other value must equal the current revision.
- Publish, deployment creation, and rollback require an `Idempotency-Key` header.
- `all_at_once` selects only the new stable Version. `canary` requires an existing stable route and an integer candidate
  weight from 1 through 9,999 basis points.
- Promotion makes the candidate the sole stable Version. Abort restores the baseline captured when the active canary
  began. Rollback creates a new all-at-once deployment from a completed deployment's baseline without altering earlier
  deployment records.
- Canary evaluation is read-only. Its response identifies `metricsScope=workbench_server` and
  `metricsSource=execution_logs`; these samples come from the shared Workbench execution-log database, including other
  Server instances writing to that database. They do not include external engine executions that never enter those logs.
- Deployment events form an ordered, append-only control-plane event stream. They are not application logs.
- Deployment dead-letter requeue accepts no request fields. A non-empty body is invalid.

## Synchronous and Persisted Execution

Synchronous execution completes within the request. Persisted asynchronous execution returns a stored acceptance record
and is processed by leased workers.

Both forms require exactly one of `version` or `alias`. Alias routing may also carry `routingKey` and a limited set of targeting
attributes.
Responses include the requested reference, exact effective Version, Alias revision, and selected target, but never echo
the routing key or targeting attributes.

Persisted asynchronous routing excludes `routingKey` and targeting attributes from its stored envelope, not from the
admission request. The Server evaluates Alias routing before persistence, stores the selected exact Version and bounded
attribution, and uses the invocation ID as the cohort key when the caller did not supply a key. Retries remain pinned to
that Version even after the Alias moves.

Async invocation list pagination defaults to `page=1` and `pageSize=20`. Attempt records use a `sequence` that increases
throughout the invocation lifetime and an `afterSequence` cursor. The first request omits the cursor; subsequent requests
use the previous response's `nextAfterSequence`.

An accepted `invocationId` is permanently reserved. Repeating the same ID with the same process, parameters, original
route, and retry policy returns the existing invocation; using it for a different request returns `409 Conflict`.
Invocation and attempt records are retained; the API does not provide a compaction endpoint.

Workers persist retry deadlines and never sleep between attempts. Claim, completion, retry, dead-letter transition, and
expired-lease recovery update the logical invocation and physical attempt record transactionally. Completion and lease
renewal require the current fencing token and an unexpired lease; late results from former owners are discarded.

Malformed persisted `paramsJson` or `routingJson` is treated as permanent invocation corruption and moves the request to
`dead_letter` without consuming transient retry budget. Read and list responses remain available when persisted routing
or result JSON is unreadable; `payloadErrors` identifies the affected fields without exposing their contents.

Operator requeue starts a new redrive generation and resets only the current retry budget. `totalAttemptCount`,
`redriveCount`, and prior attempt records remain monotonic. External effects remain at-least-once, so side-effecting
actions must deduplicate with stable business keys from process parameters.

## Monitoring and Execution Logs

Execution aggregates use one of `1h`, `6h`, `24h`, `7d`, or `30d`; the default is `24h`. They include only published
execution routed by exact Version or Alias. Preview execution is excluded. The response scope is
`workbench_server`: aggregates cover retained records in the shared execution-log database, so log retention and purge
operations determine the available reporting window. Nodes that do not write to this database are outside the metrics
scope.

Deploy runtime diagnostics returns `available=false` when neither an embedded local-ready pipeline nor a distributed
runtime is configured. When available, diagnostics describe the current Server node, not an aggregate cluster view.
They expose Alias convergence state and safe failure reasons, never internal runtime owner keys.

Log list and export filters accept `processCode`, `status`, `keyword`, `startTime`, `endTime`, `invocationId`,
`parentInvocationId`, `traceId`, `callDepth`, `namespace`, `requestedVersion`, `effectiveVersion`, `routingSource`,
`routeAlias`, and `routeRevision`. Only list accepts `page` and `pageSize`; export rejects pagination fields.
Execution-log status is `success`, `failed`, or `all`. Queue lifecycle state belongs to the async-invocation API and is
not synthesized as an execution-log status.

CSV export is UTF-8 and applies RFC 4180 quoting. String cells that spreadsheet software could interpret as formulas
are prefixed with an apostrophe. Purge requires an ISO-8601 `before` timestamp, deletes one size-limited batch, and returns
`hasMore`; callers repeat it while more eligible rows remain.
