# Changelog

All notable changes to this project are documented in this file.

CompileFlow uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2.0.0

The initial product surface includes the following capabilities.

### Included

- Format-neutral `ProcessEngine`, `ProcessDefinition`, `ProcessRef`, typed result, preflight, tooling, and runtime
  lifecycle APIs.
- Durable process execution with recoverable Runs, Wait and Timer boundaries, governed Effects, same-Run Process Calls,
  structured concurrent gateways, loops, and bounded parallel collection processing.
- `compileflow-deploy` for immutable Version publication, revision-checked Alias rollout, deterministic canary routing,
  promotion, abort, rollback, and runtime installation.
- `compileflow-workbench-server` for draft management, publication, execution, monitoring, execution logs, and persisted
  asynchronous invocation.
- Workbench Learn, Build, and Operate surfaces, plus split Web/Server artifacts and an optional all-in-one executable
  JAR.
- Runnable [Spring Boot examples](examples/README.md), the canonical
  [Supported Surfaces](docs/en/architecture/supported-surfaces.md), SBOMs, checksums, and provenance for release
  assets.

### Contracts

- Direct definitions use only explicit Inline or Classpath sources. Published execution uses an exact Version or a
  mutable Alias that resolves to one exact Version at admission.
- Every Process Call declares an explicit Classpath or exact Version target. Exact-Version and published graphs are
  Version-only; Alias routing is limited to root admission.
- TBBPM and BPMN share validation and runtime semantics for gateways, loops, trigger entries, Process Calls,
  conditions, type conversion, and generated execution.
- Parallel and inclusive branches use isolated state and deterministic merge. Conflicting root-variable writes and
  unsupported nested concurrent regions fail validation.
- `ProcessEngine.execute` and Durable Start accept a closed, partial map of declared `param` variables. Trigger remains
  the explicit state-seed API for named trigger entries.
- Configuration is bound once into immutable `ProcessEngineConfig` snapshots. Process-call depth uses
  `ProcessEngineConfig.maxCallDepth` and `compileflow.engine.call.max-depth`.
- Workbench production deployment uses a same-origin authentication gateway. The browser does not carry the Workbench
  Server API key; the Node mock remains a loopback development tool.
- The first-party stateful support matrix is PostgreSQL 16.15, 17.11, and 18.6 plus MySQL 8.4.7. Deploy, Workbench, and
  Durable own independent Provider implementations, migrations, and real-database contract evidence.

### Operational guarantees

- Publish, deployment creation, and rollback require `Idempotency-Key`; missing headers return the standard
  `INVALID_REQUEST` Problem Detail.
- Deploy, Workbench, and Durable use independent Provider implementations and migrations for PostgreSQL and MySQL.
