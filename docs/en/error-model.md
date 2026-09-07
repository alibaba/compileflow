# Execution Failure Model

CompileFlow separates process outcomes, boundary failures, and HTTP transport failures. They are not interchangeable:
a caller must know whether the process never started, failed while running, or completed successfully before result
conversion failed.

## Engine API

`execute(...)` and `trigger(...)` return `ProcessResult<T>` for failures that occur inside the process execution
pipeline. Every result contains controlled `ProcessExecution` attribution and exactly one outcome:

- success: `output` and no error;
- failure: a `ProcessError` with a stable `code` and a safe human-readable `message`.

`ProcessExecution` exposes only trace and invocation IDs, namespace, process code, an optional exact published Version,
and start/completion timestamps. Terminal `ProcessEvent` values carry separate `ExecutionAttribution` for parent
invocation and call depth, model type, source digest, and admitted Alias facts. Neither exposes routing keys, process
variables, source content, arbitrary metadata, or raw exceptions.

Callers branch on `isSuccess()` or `getError().getCode()`, never on message text. The no-argument `orElseThrow()` raises
`ProcessExecutionException` and preserves the same `ProcessError` and in-memory `ProcessExecution`.

Invalid Java API arguments are rejected by throwing before an invocation is accepted. Typed adapter failures are
represented as results because callers need their retry semantics:

- malformed process definitions, including XSD violations, are authoring errors classified as `CF_VALIDATION_002`.
  Execution reports pipeline failures in `ProcessResult`; `tooling().preflight(...)` returns a failed report with
  stage diagnostics instead of throwing the definition-validation failure. Invalid API arguments, lifecycle misuse,
  and infrastructure interruption/rejection can still throw;
- input mapping returns `CF_EXEC_010` before the process starts;
- output mapping returns `CF_EXEC_009` after the process completes and warns that side effects may already have
  occurred;
- missing or concurrently changed Alias routing returns `CF_EXEC_011` before any process action runs;
- an unavailable selected runtime returns `CF_EXEC_012` before any process action runs.
- a nested process call that exceeds the configured depth returns `CF_EXEC_013` before the child action runs;
- an invalid resolved process-call graph returns `CF_EXEC_014` before any child action runs.

Neither result exposes the mapper exception or the original application object.

## Execution HTTP Contract

Workbench Server and the local development gateway use a discriminated execution response. A process outcome is returned
with HTTP 200:

```json
{
    "success": false,
    "message": "Flow execution failed",
    "errorCode": "CF_EXEC_004",
    "error": "Process execution failed",
    "traceId": "4a04d9f8d8bb4d6fb2ae1e577d99dfe2",
    "invocationId": "inv-...",
    "modelType": "TBBPM",
    "sourceDigest": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
    "durationMs": 12,
    "routing": {
        "namespace": "default",
        "requestedAlias": "production",
        "effectiveVersion": "2026.07.1",
        "alias": "production",
        "routeRevision": 8,
        "target": "STABLE"
    }
}
```

Successful responses use `success: true` and `result`; failed responses require non-empty `errorCode` and `error`.
Workbench validates this distinction at runtime as well as in TypeScript.

Malformed requests, authentication failures, capacity rejection, upstream unavailability, and other transport failures
use non-2xx status codes and RFC 9457 Problem Details:

```json
{
    "type": "urn:compileflow:problem:invalid-request",
    "title": "Invalid request",
    "status": 400,
    "detail": "request body is required",
    "instance": "/api/executions/preview",
    "code": "INVALID_REQUEST"
}
```

Workbench Server and the development mock use the same
`application/problem+json` shape for their shared preview boundary. A production edge may generate its own transport
failures, which remain non-2xx and must not masquerade as an Engine result. The uppercase `code` extension is the stable
programmatic discriminator; `detail` is safe occurrence-specific text.

Deployment command failures use non-2xx Problem Details with a stable
`DeploymentErrorCode` in `code`, a fixed caller-safe detail, and only validated process context. The HTTP adapter never
copies a
`DeploymentException` message, cause, SQL diagnostic, transport detail, or credential into the response. Clients branch
on `code`; detailed diagnostics remain in secured server logs.

## Retry Rules

- The browser never automatically replays a synchronous execution POST.
- A caller that controls replay may retry `CF_EXEC_011` or `CF_EXEC_012` with a bounded convergence deadline because
  neither code is emitted after a process action starts. Exhausted retries require route/runtime diagnosis, not an
  unbounded retry loop.
- `CF_EXEC_013` requires correcting the call graph or depth budget. Waiting and
  replaying the same request cannot resolve them.
- `CF_EXEC_014` requires correcting the resolved call target, authority, or cycle. Waiting and replaying the same
  request cannot resolve it.
- Workbench persisted asynchronous invocation defaults to one attempt.
- `maxAttempts > 1` is explicit opt-in to at-least-once delivery.
- A stable `invocationId` provides correlation and async submission deduplication. The same request returns the existing
  invocation; different request data with the same ID is a conflict. This does not make arbitrary process side effects
  exactly once.
- Every side-effecting action that may be retried must implement idempotency with a stable business key.
- Workbench evaluates Alias routing before persisting an asynchronous invocation, then stores the selected exact
  version and bounded attribution. Routing keys and attributes are never persisted; when no routing key is supplied,
  the persisted invocation ID is the cohort key.
- Source constructs that are format-valid but outside the shared CompileFlow Process semantics fail with
  `CF_VALIDATION_006`; target-specific capability rejection remains `CF_VALIDATION_005`.

Unexpected worker or boundary exceptions are normalized to stable diagnostics before persistence or HTTP exposure. Raw
exception messages remain available only to explicitly secured diagnostic tooling, never as the default public contract.
