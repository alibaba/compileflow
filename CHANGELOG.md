# Changelog

Notable changes to CompileFlow are documented here. Releases follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## 2.0.0

Highlights:

- a lightweight, embeddable Java process engine with compiled and interpreted execution;
- TBBPM and the documented subset of BPMN 2.0 through one `ProcessEngine` API;
- Spring Boot starters, preflight validation, typed results, stable errors, and observability hooks;
- CompileFlow Deploy for immutable process versions, aliases, canary rollout, rollback, and runtime installation;
- CompileFlow Durable for persisted waits, timers, external operations, and recovery after application restarts;
- CompileFlow Workbench for learning, visual authoring, publication, monitoring, and execution inspection;
- PostgreSQL and MySQL persistence for Deploy, Durable, and Workbench;
- runnable examples, reference documentation, database migrations, SBOMs, checksums, and build provenance.

Supported APIs, process nodes, databases, and operational boundaries are listed in
[Supported Surfaces](docs/en/architecture/supported-surfaces.md).
