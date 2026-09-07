# Supported surfaces

This page defines CompileFlow's supported product contracts. Anything not listed is internal, even when its JVM
visibility is `public`.

## Stability Tiers

| Tier                    | Meaning                                                                                                               |
| ----------------------- | --------------------------------------------------------------------------------------------------------------------- |
| Supported               | Safe for application and integrator code; documented; covered by contract tests                                       |
| Supported by deployment | Safe when running the documented Workbench/Server topology                                                            |
| Provider Preview        | Preview contract for application capability and integration providers; may evolve independently from the end-user API |
| Internal                | Implementation detail; do not depend on it from application code                                                      |

## Target Platform Baseline

| Boundary                            | Target baseline                                                                                                                                                      |
| ----------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Java artifacts and generated flows  | Java 17 source/API/bytecode; all supported LTS JDKs build it, Java 17 runs the full suite, and Java 21/25 run focused concurrency and dynamic-code runtime contracts |
| Spring integration                  | Spring Boot 4.1.1                                                                                                                                                    |
| Engine API/Core/TBBPM/BPMN          | No CompileFlow-owned deployment database                                                                                                                             |
| Deploy first-party authorities      | PostgreSQL 16.15/17.11/18.6 and MySQL 8.4.7 contract matrices; Providers share the version-coupled JDBC state machine and own their dialect selection and migrations |
| Workbench production persistence    | The same executable supports PostgreSQL 16.15/17.11/18.6 and MySQL 8.4.7; Compose recommends PostgreSQL 18.6                                                         |
| Durable first-party implementations | PostgreSQL 16.15/17.11/18.6 and MySQL 8.4.7 contract matrices; H2 remains test-scoped                                                                                |
| Workbench build                     | Node.js 24 LTS and pnpm 11.11.0; the exact Node patch is pinned in `release-baselines.json`                                                                          |
| Workbench Java runtime              | Pinned Java 17 JDK container image; the full image tag and OCI index digest are recorded in `release-baselines.json`                                                 |

This table is the supported platform baseline. A platform not listed here may work, but it is outside the supported
matrix. The root `release-baselines.json` records the exact build-tool versions and immutable container-image digests.

## Java API And SPI (`compileflow-api`)

Supported types live under `com.alibaba.compileflow.engine` and its documented subpackages:

API and SPI share one artifact with no runtime dependencies because engine configuration uses SPI types and SPI
callbacks use API value types. Packages separate their roles. Core ships the engine provider and implementation;
format modules ship semantic-compiler providers, and framework adapters remain in integration modules.

| Package / type                                                                                                  | Role                                                                                                     |
| --------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `ProcessEngine`, `ProcessEngineFactory`                                                                         | Engine lifecycle and execution entry                                                                     |
| `ProcessRef`, `ProcessDefinition`                                                                               | Existing process identity and explicit definition sources                                                |
| `ProcessResult`, `ProcessError`, `ProcessExecution`, `ProcessExecutionOptions`                                  | Typed execution outcomes, controlled attribution, and request options                                    |
| `ProcessAliasTarget`, `AliasRoutingOptions`, `ProcessTrigger`, `ProcessDataMapper`, `ProcessExecutionException` | Supporting execution values, routing controls, typed mapping, and failure propagation                    |
| `ProcessDefinitionDigest`, `ProcessIdentifiers`, `ProcessText`                                                  | Stable digest and identity/text validation contracts                                                     |
| `ProcessRuntimeManager`, `ProcessToolingService`                                                                | Local runtime lifecycle and non-executing tooling                                                        |
| `CompileFlowException`, `ErrorCode`, `ProcessModelType`                                                         | Root error taxonomy and format identity                                                                  |
| `config.*`                                                                                                      | Immutable `ProcessEngineConfig` tree and validators                                                      |
| `preflight.*`                                                                                                   | `ProcessPreflightOptions`, `ProcessPreflightReport`                                                      |
| `spi.*`                                                                                                         | `ProcessEnginePlugin`, `ProcessEnginePluginContext`, `ProcessComponentResolver`                          |
| `spi.event.*`                                                                                                   | Sealed typed `ProcessEvent` lifecycle records and `ProcessEventListener`                                 |
| `spi.execution.*`                                                                                               | Retry policies, terminal failure handlers, application-context propagation, and bounded execution values |
| `spi.observability.TraceIdProvider`                                                                             | Trace id collaboration                                                                                   |
| `spi.routing.*`                                                                                                 | Alias route authority plus route-bound named targeting contracts                                         |
| `spi.script.*`                                                                                                  | `ScriptExecutor`, script programs, program specifications, and provider-independent script failures      |

`ProcessEngineProvider` is a version-coupled API/Core bootstrap contract, not a Supported application SPI.
Frontend modules register semantic compilers; they do not create separate format-bound engines.

Applications must not depend on `com.alibaba.compileflow.engine.core`, format parser ASTs, deploy coordinators, Spring
auto-configuration internals, or server controller classes.

`compileflow-core` registers `java` as a built-in script language. Java scripts are trusted in-process code and must only
be used with trusted definitions; the executor implementation remains a core internal.

## Deployment API And SPI (`compileflow-deploy-api`)

Supported deployment contracts live under `com.alibaba.compileflow.deploy.api`:

| Package / type                        | Role                                                                       |
| ------------------------------------- | -------------------------------------------------------------------------- |
| `ProcessDeploymentService`            | Immutable publication and revision-checked rollout facade                  |
| `command.*`                           | Validated publish, rollout, canary, promote, abort, and rollback commands  |
| `artifact.*`, `release.*`             | Executable artifact values and Control Plane release audit/metadata values |
| `routing.*`, `rollout.*`, `version.*` | Immutable routing and rollout state, constraints, queries, and pages       |
| `error.*`                             | Bounded deployment error codes and structured exception context            |
| `observability.*`                     | Instance-scoped deployment counters with stable, bounded dimensions        |
| `spi.ProcessArtifactSource`           | Immutable artifact lookup boundary                                         |

`compileflow-deploy-spi` is the version-coupled Provider contract. Its `DeployStore` describes the atomic publication,
rollout, route, and routing-outbox transitions required by the control plane without exposing JDBC or SQL. The first-party
PostgreSQL and MySQL Providers share one version-coupled JDBC implementation of that state machine while retaining their
own dialect selection, migrations, and real-database contract evidence; this does not make arbitrary JDBC databases supported.
Its `projection.DeploymentProjectionStore` is also **Provider Preview**, not a Supported application API. A conforming
remote provider must preserve per-key linearizable CAS and eventual subscription convergence, including reconnect,
history-loss, concurrent-writer, and partition behavior. The in-memory testkit alone is not sufficient evidence.

## Deployment Wire Protocol (`compileflow-deploy-protocol`)

Supported wire-contract helpers live under `com.alibaba.compileflow.deploy.protocol`. They own schema-versioned
artifact/routing payloads, parsers, canonical bounded JSON, and projection keys. The protocol module depends on the
deployment domain API; the domain API never depends on wire representations.

Applications must not depend on deploy admin/runtime/integration implementations, repositories, Spring composition
internals, or server adapters.

The Maven `tests` classifiers contain deterministic in-memory fixtures for CompileFlow's own test suite. They are not
production APIs, runtime adapters, or fallback implementations.

Release metadata is descriptive only. Integrity preconditions use
`PublishProcessVersionCommand.getExpectedArtifactDigest()` and are never smuggled through metadata. The
`compileflow.` metadata-key namespace is reserved for keys declared by `ReleaseMetadataKeys`; applications use their
own namespace. Release metadata is not projected into executable artifact payloads.

## Durable API, SPI, And Delivery Surface

The following documented types under `compileflow-durable-api` are the Supported end-user contract of
`durable-strict@1`:

| Package / type           | Role                                                                                                                  |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------- |
| `DurableProcessEngine`   | Application execution entry point and node-local lifecycle owner for optional Workers, runtime cache, and compilation |
| `DurableOperatorService` | Timeline, Pause/Resume, Effect resolution, and Outbox administration                                                  |
| `command.*`              | Audited Pause/Resume, Effect-resolution, and Outbox-resolution operator commands                                      |
| `model.*`                | Run identity/result, typed cursors, control, Timeline, Wait, Timer, Effect, and Outbox lifecycle models               |
| `effect.*`               | Effect reconciliation outcome                                                                                         |
| `error.*`                | Stable Durable error classification                                                                                   |
| `validation.*`           | Shared validation contracts used by immutable Durable API values                                                      |

`compileflow-durable-spi` is a Provider Preview and does not carry the same long-term compatibility tier as the end-user
API. Its `DurableStore` contract is database-neutral, but every supported Provider must independently prove the complete
transaction, concurrency, crash, migration, and retention semantics. It also exposes `DurableWaitDescriptionProvider`
with `DurableWaitDescriptionContext`, `DurableOutboxSink`, plus the separate admission boundaries
`DurableAliasStateSource` and `DurableVersionDefinitionSource`.
`DurableVersionDefinitionSource` supplies a missing authoritative exact-Version replica during admission; Run recovery
never calls it. Optional named `ProcessAliasTargetingPolicy` values may override only the route's stable/candidate
target; CompileFlow's protocol-defined percentage selector is not replaceable. PostgreSQL and MySQL SQL, schema,
indexes, and transaction realizations remain first-party Provider implementation details; they are not a generic JDBC
or dialect abstraction.
The published definition source returns the nested minimal
`VersionDefinition` with a typed inline definition and exact `callBindings`. Bindings prove source-to-child-Version
agreement at admission, not a second mutable execution graph. Definition owns model type; exact Version calls may cross
frontend boundaries, while classpath calls inherit the caller's frontend. Recovery
uses stored `processId` semantics, not the admission source. Durable accepts the documented strict TBBPM and BPMN
profiles through one semantic backend.

Supported composition entry points are:

- Plain Java node-local lifecycle: `DurableProcessEngineConfig` and `DurableProcessEngineFactory` in `compileflow-durable-runtime`;
- Provider-neutral application and maintenance runtime: `compileflow-durable-spring-boot-starter` plus one complete `DurableStore`;
- first-party PostgreSQL distribution: `compileflow-durable-spring-boot-starter-postgresql`;
- first-party MySQL distribution: `compileflow-durable-spring-boot-starter-mysql`;
- application capability and integration providers: `compileflow-durable-spi`.

Embedded queries expose typed keyset cursors. Authentication, authorization, request dedupe, approval, and opaque
page-token encoding/signing belong to transport/application adapters. Portable process payloads have one fixed Kernel
encoding; there is no application codec or payload-protector SPI.

Except for the Config and Factory composition entry points above, packages in `compileflow-durable-runtime`,
PostgreSQL/MySQL implementation classes, `compileflow-durable-testkit`, Spring
auto-configuration composition beans, and Durable database tables are not application APIs. Public JVM visibility used for
first-party cross-artifact composition does not create a Store extension contract. The public Durable API is Supported;
production deployments must meet the documented database, migration, and operational requirements.

See the [Durable Architecture](durable-architecture.md) for the complete boundary and maintenance rules.

## Spring Configuration

| Prefix                           | Owner                                   | Notes                                                            |
| -------------------------------- | --------------------------------------- | ---------------------------------------------------------------- |
| `compileflow.engine.*`           | Engine instance                         | Strict binding (`ignoreUnknownFields=false`)                     |
| `compileflow.deploy.*`           | Deploy control/runtime                  | Topology and role flags are explicit                             |
| `compileflow.durable.*`          | Durable application/maintenance runtime | Default-off; role, migration, Worker, and retention are explicit |
| `compileflow.workbench.server.*` | `compileflow-workbench-server`          | Auth, HTTP limits, async invocation                              |

Authoritative user guide: [configuration.md](../configuration.md). Generated Spring metadata and
`ConfigurationMetadataTest` must stay aligned with the property classes.

## Deploy Behavior

Supported integration path: Spring Boot auto-configuration + `ProcessDeploymentService`.

Invariants (see the [Deploy protocol](../../../compileflow-deploy/docs/PROTOCOL.md)):

- Control plane and runtime stay separated
- A published Deploy `ProcessArtifact` and its exact Process Version are immutable
- PostgreSQL and MySQL are distinct first-party authorities selected once during application composition; their shared
  JDBC state machine does not merge schemas, migrations, runtime selection, or database-specific evidence
- `compileflow-deploy-spring-boot-autoconfigure` owns only database-independent Deploy composition
- `compileflow-deploy-spring-boot-starter` is the format- and Provider-neutral entry point for a host-supplied complete `DeployStore`
- `compileflow-deploy-spring-boot-starter-postgresql` and `compileflow-deploy-spring-boot-starter-mysql` select only a Store Provider; applications add their source frontends explicitly
- `DeployStore` is a semantic Provider contract; SQL, migrations, schemas, and physical transaction strategies are not public APIs
- H2 is a test double and is not a Deploy production database
- No silent in-memory production fallback
- `DeploymentRuntime` is started through Spring lifecycle, not a public bootstrap API
- Deploy protocol generations are exact; parsers accept only their documented schema version and fail closed on
  unknown generations

## Server REST And Workbench Operate

| Surface                   | Authority                                                                                             |
| ------------------------- | ----------------------------------------------------------------------------------------------------- |
| Workbench REST wire shape | Same-release frontend/server contract described by the committed Workbench Server OpenAPI description |
| Server implementation     | `compileflow-workbench-server` controllers and transport records generate that description            |
| Web contracts             | Generated OpenAPI types plus refined contracts, runtime validation, and compile-time parity           |
| Dev Gateway/Web env       | `COMPILEFLOW_DEV_GATEWAY_*` (development only) and `VITE_COMPILEFLOW_*` (public build-time only)      |

Browsers never hold shared API keys. An authentication-capable gateway injects the private Server service credential on
its protected upstream hop. The Node development gateway is not deployed in production. Official local full-stack
path: [DEPLOYMENT.md](../../../compileflow-workbench/DEPLOYMENT.md).

Workbench `/api/**` is a companion-backend contract for the matching official frontend release, not a public
cross-minor integration ABI. OpenAPI remains the exact same-release wire and parity authority. Third-party integration
APIs are outside this surface and require an explicitly versioned root with a separately declared compatibility tier.

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

- [Durable Architecture](durable-architecture.md)
- [API reference](../api-reference.md)
- [Configuration](../configuration.md)
- [Extension guide](../extension-guide.md)
- [Compatibility policy](../compatibility-policy.md)
