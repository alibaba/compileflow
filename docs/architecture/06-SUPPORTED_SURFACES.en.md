# Supported Surfaces

The product contracts listed here are the supported CompileFlow surfaces. Anything not listed is internal, even when its JVM
visibility is `public`.

The English page is canonical. The Chinese translation, user guides, and ADRs must remain aligned with it.

## Stability Tiers

| Tier                    | Meaning                                                                                                                                              |
|-------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| Supported               | Safe for application and integrator code; documented; covered by contract tests                                                                      |
| Supported by deployment | Safe when running the documented Workbench/Server topology                                                                                           |
| Developer Preview       | Available for documented evaluation with an explicit behavior and delivery boundary, but without stable compatibility or Production Ready commitment |
| Provider Preview        | Preview contract for application capability and integration providers; may evolve independently from the end-user API                              |
| Internal                | Implementation detail; do not depend on it from application code                                                                                     |

## Target Platform Baseline

| Boundary                                        | Target baseline                                                                                                          |
|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| Java artifacts and generated flows              | Java 17 source/API/bytecode; all supported LTS JDKs build it, Java 17 runs the full suite, and Java 21/25 run focused concurrency and dynamic-code runtime contracts |
| Spring integration                              | Spring Boot 4.1.1                                                                                                        |
| Engine API/Core/TBBPM/BPMN                      | No CompileFlow-owned deployment database                                                                                 |
| Deploy first-party authority                    | PostgreSQL 16.15, 17.11, and 18.6 contract matrix; JDBC is the access mechanism, not an arbitrary-database support claim |
| Workbench production persistence                | PostgreSQL 16.15, 17.11, and 18.6 contract matrix; the bundled deployment and new-install recommendation are 18.6        |
| Durable PostgreSQL implementation (Developer Preview) | PostgreSQL 16.15, 17.11, and 18.6 contract matrix; H2 is test-scoped and is not a supported deployment database    |
| Workbench build                                 | Node.js 24.18.0 and pnpm 11.11.0                                                                                         |
| Workbench Java runtime                         | Official Temurin `17-jdk-noble` channel; the full release tag and OCI index digest are pinned in `release-baselines.json` |

These values define the 2.0 release target. They become a release claim only after the corresponding same-commit gates
produce non-skipped evidence; listing a version here does not by itself mark an open gate complete. A platform not
listed here may work, but it is not part of the supported matrix. Exact current versions and image digests live in the
root `release-baselines.json`; the scheduled and release-day baseline checker compares it with official upstream facts.
For the Java container, freshness means that the immutable image digest still equals the current digest of the official
Temurin container channel. A newer standalone Adoptium binary does not block a container release before that binary is
published through the official multi-architecture container channel.

## Java API And SPI (`compileflow-api`)

Supported types live under `com.alibaba.compileflow.engine` and its documented subpackages:

API and SPI share one artifact with no runtime dependencies because engine configuration uses SPI types and SPI
callbacks use API value types. Packages separate their roles. Provider implementations are shipped by the format
modules; framework adapters remain in integration modules.

| Package / type                                                                           | Role                                                                                                     |
|------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------|
| `ProcessEngine`, `ProcessEngineFactory`                                                  | Engine lifecycle and execution entry                                                                     |
| `ProcessRef`, `ProcessDefinition`                                                        | Existing process identity and explicit definition sources                                                |
| `ProcessResult`, `ProcessError`, `ProcessExecution`, `ProcessExecutionOptions`           | Typed execution outcomes, controlled attribution, and request options                                    |
| `ProcessAliasTarget`, `AliasRoutingOptions`, `ProcessTrigger`, `ProcessDataMapper`, `ProcessExecutionException` | Supporting execution values, routing controls, typed mapping, and failure propagation                    |
| `ProcessDefinitionDigest`, `ProcessIdentifiers`, `ProcessText` | Stable digest and identity/text validation contracts                                                       |
| `ProcessRuntimeManager`, `ProcessToolingService`                                         | Local runtime lifecycle and non-executing tooling                                                        |
| `CompileFlowException`, `ErrorCode`, `ProcessModelType`                                  | Root error taxonomy and format identity                                                                  |
| `config.*`                                                                               | Immutable `ProcessEngineConfig` tree and validators                                                      |
| `preflight.*`                                                                            | `ProcessPreflightOptions`, `ProcessPreflightReport`                                                      |
| `spi.*`                                                                                  | `ProcessEngineProvider`, `ProcessEnginePlugin`, `ProcessEnginePluginContext`, `ProcessComponentResolver` |
| `spi.event.*`                                                                            | Sealed typed `ProcessEvent` lifecycle records and `ProcessEventListener`                                 |
| `spi.execution.*`                                                                        | Retry policies, terminal failure handlers, application-context propagation, and bounded execution values |
| `spi.observability.TraceIdProvider`                                                      | Trace id collaboration                                                                                   |
| `spi.routing.*`                                                                          | Alias route authority plus route-bound named targeting contracts                                                |
| `spi.script.*`                                                                           | `ScriptExecutor`, script programs, program specifications, and provider-independent script failures     |

Applications must not depend on `com.alibaba.compileflow.engine.core`, format parser ASTs, deploy coordinators, Spring
auto-configuration internals, or server controller classes.

`compileflow-core` registers `java` as a built-in script language. Java scripts are trusted in-process code and must only
be used with trusted definitions; the executor implementation remains a core internal.

## Deployment API And SPI (`compileflow-deploy-api`)

Supported deployment contracts live under `com.alibaba.compileflow.deploy.api`:

| Package / type                        | Role                                                                          |
|---------------------------------------|-------------------------------------------------------------------------------|
| `ProcessDeploymentService`            | Immutable publication and revision-checked rollout facade                     |
| `command.*`                           | Validated publish, rollout, canary, promote, abort, and rollback commands     |
| `artifact.*`, `release.*`             | Executable artifact values and Control Plane release audit/metadata values     |
| `routing.*`, `rollout.*`, `version.*` | Immutable routing and rollout state, constraints, queries, and pages          |
| `error.*`                             | Bounded deployment error codes and structured exception context               |
| `protocol.*`                          | Schema-versioned artifact/routing parsers, payloads, and bounded keys         |
| `protocol.json.DeploymentProtocolJson` | Canonical bounded JSON representation for deployment protocol values          |
| `observability.*`                     | Instance-scoped deployment counters with stable, bounded dimensions           |
| `spi.ProcessArtifactSource`           | Immutable artifact lookup boundary                                            |
| `sync.DeploymentSyncChannel`          | Linearizable transport SPI and subscription contract                          |

Applications must not depend on deploy admin/runtime/integration implementations, repositories, Spring composition
internals, or server adapters.

The Maven `tests` classifiers contain deterministic in-memory fixtures for CompileFlow's own test suite. They are not
production APIs, runtime adapters, or fallback implementations.

Release metadata is descriptive only. Integrity preconditions use
`PublishProcessVersionCommand.getExpectedArtifactDigest()` and are never smuggled through metadata. The
`compileflow.` metadata-key namespace is reserved for keys declared by `ReleaseMetadataKeys`; applications use their
own namespace. Release metadata is not projected into executable artifact payloads.

## Durable API, SPI, And Delivery Surface (Developer Preview)

The following documented types under `compileflow-durable-api` are the end-user Developer Preview contract of
`durable-strict@1`:

| Package / type               | Role                                                                                                                     |
|------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `DurableProcessEngine`      | Direct application facade for Start, Wait completion, Cancel, sanitized Run queries, and explicit result lookup; it does not own Worker lifecycle |
| `DurableOperatorService`     | Timeline, Pause/Resume, Effect resolution, and Outbox administration                                                     |
| `command.*`                  | Audited Pause/Resume, Effect-resolution, and Outbox-resolution operator commands                                          |
| `model.*`                    | Run identity/result, typed cursors, control, Timeline, Wait, Timer, Effect, and Outbox lifecycle models               |
| `effect.*`                   | Effect reconciliation outcome                                                                                            |
| `error.*`                    | Stable Durable error classification                                                                                      |
| `validation.*`               | Shared validation contracts used by immutable Durable API values                                                       |

`compileflow-durable-spi` is a Provider Preview and does not carry the same long-term compatibility tier as the end-user
API. Its `DurableStore` contract is database-neutral, but every supported Provider must independently prove the complete
transaction, concurrency, crash, migration, and retention semantics. It also exposes `DurableWaitDescriptionProvider`
with `DurableWaitDescriptionContext`, `DurableOutboxSink`, plus the separate admission boundaries
`DurableAliasStateSource` and `DurableVersionDefinitionSource`.
`DurableVersionDefinitionSource` supplies a missing authoritative exact-Version replica during admission; Run recovery
never calls it. Optional named `ProcessAliasTargetingPolicy` values may override only the route's stable/candidate
target; CompileFlow's protocol-defined percentage selector is not replaceable. PostgreSQL SQL, schema, indexes, and transaction realization remain first-party Provider
implementation details; they are not a generic JDBC or dialect abstraction.
The published definition source returns the nested minimal
`VersionDefinition(ProcessModelType, ProcessDefinition.Inline)`; model type is preserved as an immutable recovery fact.
Explicit definitions are loaded directly through Core using Durable's configured engine format. The
first-party implementation is bound to one configured engine format. Durable accepts the documented strict TBBPM and
BPMN profiles through one semantic backend.

Supported composition entry points are:

- Provider-neutral application and maintenance runtime: `compileflow-durable-spring-boot-starter` plus one complete `DurableStore`;
- first-party PostgreSQL distribution: `compileflow-durable-spring-boot-starter-postgres`;
- application capability and integration providers: `compileflow-durable-spi`.

Embedded queries expose typed keyset cursors. Authentication, authorization, request dedupe, approval, and opaque
page-token encoding/signing belong to transport/application adapters. Portable process payloads have one fixed Kernel
encoding; there is no application codec or payload-protector SPI.

Every package in `compileflow-durable-runtime`, PostgreSQL implementation classes, `compileflow-durable-testkit`, Spring
auto-configuration composition beans, and Durable database tables are not application APIs. Public JVM visibility used for
first-party cross-artifact composition does not create a Store extension contract. The public Durable API remains
Developer Preview; Production Ready requires separate release-gate evidence.

See the [Durable Architecture](10-DURABLE_ARCHITECTURE.en.md) for the complete boundary and maintenance rules.

## Spring Configuration

| Prefix                           | Owner                                   | Notes                                                            |
|----------------------------------|-----------------------------------------|------------------------------------------------------------------|
| `compileflow.engine.*`           | Engine instance                         | Strict binding (`ignoreUnknownFields=false`)                     |
| `compileflow.deploy.*`           | Deploy control/data plane               | Topology and role flags are explicit                             |
| `compileflow.durable.*`          | Durable application/maintenance runtime | Default-off; role, migration, Worker, and retention are explicit |
| `compileflow.workbench.server.*` | `compileflow-workbench-server`          | Auth, HTTP limits, async invocation                              |

Authoritative user guide: [configuration.md](../en/configuration.md). Generated Spring metadata and
`ConfigurationMetadataTest` must stay aligned with the property classes.

## Deploy Behavior

Supported integration path: Spring Boot auto-configuration + `ProcessDeploymentService`.

Invariants (see the [Deploy protocol](../../compileflow-deploy/docs/PROTOCOL.md)):

- Control plane and data plane stay separated
- A published Deploy `ProcessArtifact` and its exact Process Version are immutable
- The first-party authority is PostgreSQL; `Jdbc*Repository` does not imply arbitrary JDBC database support
- H2 is a test double and is not a Deploy production database
- No silent in-memory production fallback
- `DeployRuntime` is started through Spring lifecycle, not a public bootstrap API
- Deploy protocol generations require a coordinated homogeneous upgrade; current parsers accept only their exact
  documented schema version and do not claim an `N-1` rolling-skew window

## Server REST And Workbench Operate

| Surface                   | Authority                                                                                        |
|---------------------------|--------------------------------------------------------------------------------------------------|
| Workbench REST wire shape | Same-release frontend/server contract described by the committed Workbench Server OpenAPI description |
| Server implementation     | `compileflow-workbench-server` controllers and transport records generate that description       |
| Web contracts             | Generated OpenAPI types plus refined contracts, runtime validation, and compile-time parity      |
| Dev Gateway/Web env       | `COMPILEFLOW_DEV_GATEWAY_*` (development only) and `VITE_COMPILEFLOW_*` (public build-time only) |

Browsers never hold shared API keys. An authentication-capable gateway injects the private Server service credential on
its protected upstream hop. The Node development gateway is not deployed in production. Official local full-stack
path: [DEPLOYMENT.md](../../compileflow-workbench/DEPLOYMENT.md).

Workbench `/api/**` is a companion-backend contract for the matching official frontend release, not a public
cross-minor integration ABI. OpenAPI remains the exact same-release wire and parity authority. A future third-party
integration API must use an explicitly versioned root and declare its own compatibility tier.

## Unsupported Surfaces

- Process-global mutable configuration or extension registries
- Public parser/model AST types in the API artifact
- Full-classpath annotation scanning for extension discovery
- Runtime plugin installation, replacement, or unloading
- Obtaining Durable semantics through a `ProcessEngine` configuration switch
- Application table-level access to Durable state or dependencies on Durable runtime implementation packages
- Treating exact-version demand observations as infrastructure mutation commands
- Treating Workbench companion `/api/**` endpoints as a public cross-minor integration API

## Related Docs

- [Durable Architecture](10-DURABLE_ARCHITECTURE.en.md)
- [API reference](../en/api-reference.md)
- [Configuration](../en/configuration.md)
- [Extension guide](../en/extension-guide.md)
- [Compatibility policy](../compatibility-policy.md)
