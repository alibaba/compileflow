# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - Unreleased

CompileFlow 2.0 defines a new, incompatible contract. There is no 1.x compatibility bridge. Applications must adopt
the 2.0 Java API, Spring configuration, and Workbench Server contracts documented in this repository.

### Added

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
- Runnable [Spring Boot examples](examples/README.md), canonical
  [Supported Surfaces](docs/architecture/06-SUPPORTED_SURFACES.en.md), release guidance, SBOMs, checksums, and provenance
  for release assets.

### Changed

- Direct definitions now use only explicit Inline or Classpath sources. Published execution uses an exact Version or a
  mutable Alias that resolves to one exact Version at admission.
- Every Process Call declares an explicit Classpath or exact Version target. Exact-Version and published graphs are
  Version-only; Alias routing is limited to root admission.
- TBBPM and BPMN now share validation and runtime semantics for gateways, loops, trigger entries, Process Calls,
  conditions, type conversion, and generated execution.
- Parallel and inclusive branches use isolated state and deterministic merge. Conflicting root-variable writes and
  unsupported nested concurrent regions fail validation.
- `ProcessEngine.execute` and Durable Start accept a closed, partial map of declared `param` variables. Trigger remains
  the explicit state-seed API for named trigger entries.
- Configuration is bound once into immutable `ProcessEngineConfig` snapshots. Process-call depth now uses
  `ProcessEngineConfig.maxCallDepth` and `compileflow.engine.call.max-depth`.
- Workbench production deployment uses a same-origin authentication gateway. The browser does not carry the Workbench
  Server API key; the Node mock remains a loopback development tool.
- The first-party stateful support matrix is PostgreSQL 16.15, 17.11, and 18.6. Deploy, Workbench, and Durable each run
  their own real-database contract matrix.
- Build and release checks now cover reproducible Maven archives, API compatibility and coverage, dependency review,
  CodeQL, pinned images and CI actions, and scoped release-candidate matrices.

### Removed

- Public `FlowModel`, process-global property resolution, classpath scanning, JVM-global mutable extension registration,
  and the generic public invocation helper.
- The obsolete `compileflow-benchmark` module; maintained JMH benchmarks now live in `compileflow-benchmarks`.
- Obsolete generated reports, duplicate changelogs, unverifiable performance claims, stale planning artifacts, and demo
  identity controls that did not represent authenticated users.

### Fixed

- Publish, deployment creation, and rollback now require `Idempotency-Key`; missing headers return the standard
  `INVALID_REQUEST` Problem Detail.
- The storage-free Spring Boot starter no longer links optional Deploy API classes during unconditional configuration
  binding.
- Documentation links, Workbench shortcut guidance, CI path filters, and tagged-release validation boundaries now match
  the delivered project structure.

## Older Releases

For older versions, refer to [GitHub Releases](https://github.com/alibaba/compileflow/releases).

[2.0.0]: https://github.com/alibaba/compileflow/compare/v1.2.0...HEAD
