# CompileFlow Compatibility Policy

CompileFlow follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). This policy defines what counts
as a breaking change for each kind of supported surface and how the project communicates changes.

## 1. Versioning scheme

`MAJOR.MINOR.PATCH`

- **MAJOR**: incompatible changes to a supported surface (see §2). Used only for an explicit contract break.
- **MINOR**: new functionality in a backward-compatible manner. Includes new nodes, new actions, new SPIs, and
  new configuration properties with safe defaults.
- **PATCH**: bug fixes and security fixes that preserve supported-surface compatibility.

Pre-release versions use the `-SNAPSHOT` suffix during development and may contain any change. Never depend on a
SNAPSHOT in production.

## 2. Supported surfaces

Compatibility promises apply only to the surfaces listed in
[`docs/architecture/06-SUPPORTED_SURFACES.en.md`](architecture/06-SUPPORTED_SURFACES.en.md). Anything outside those
surfaces may change in any release without notice.

### 2.1 Java API and SPI

Two artifacts currently define the **Supported** Java contract:

- `compileflow-api`: engine execution, configuration, preflight, errors, and extension SPI under
  `com.alibaba.compileflow.engine`
- `compileflow-deploy-api`: immutable publication/rollout commands and views, deployment errors, wire-protocol values,
  and transport SPI under `com.alibaba.compileflow.deploy.api`

`compileflow-api` keeps call-facing API and extension SPI in one artifact because their public types refer to each
other. The package boundary distinguishes their roles; provider implementations remain in separate modules.

Only the packages and types enumerated by the supported-surfaces specification form the Supported contract. Public JVM
visibility in an implementation artifact does not create a compatibility promise.

`compileflow-durable-api` is a **Developer Preview** end-user contract and
`compileflow-durable-spi` is a **Provider Preview** contract. They are intentionally not part of the Supported
binary-compatibility baseline yet:

- a PATCH release must not intentionally break either Preview surface;
- a MINOR Preview release may change a surface only when the release notes identify the break and provide migration
  instructions;
- persisted exact Process identity, portable continuation envelope, committed boundary/fact meaning, and fail-closed
  recovery semantics may not be silently reinterpreted, even during Preview;
- Application capability/integration SPI changes require corresponding runtime/contract-test updates and may not weaken the end-user Durable safety
  contract.

Durable Provider SPI and Kernel versions form one strong contract. A new authoritative persistence transition may
require a new abstract Store method; CompileFlow does not preserve nominal binary compatibility with a default method
that fails later through `UnsupportedOperationException`. A Provider must upgrade explicitly and pass the complete
current `DurableStoreContract` before claiming compatibility. Missing capability must fail at build or startup, not
during a production state transition.

Promotion of Durable to **Supported** requires a designated released baseline, automated binary compatibility
enforcement, documented database/serialized-state upgrade compatibility, and a passing supported-version matrix.
Application implementation, ScriptExecutor Provider, and Runtime-build compatibility are deliberately outside the
Kernel promise. Preview does not mean that Kernel-owned persisted data may be made unreadable without an explicit
migration and recovery plan.

The distinction is ownership based:

- **Application-owned compatibility** includes Action/Spring-bean implementations, third-party ScriptExecutor
  semantics, application libraries, and the Java class shape selected by the current deployment. The Kernel does not
  persist or route on an application Build ID, compatibility group, execution pin, provider identity, or any equivalent
  opaque deployment label. Making such a field nullable does not change its ownership.
- **CompileFlow-owned Process semantic compatibility** includes parsing the stored exact definition, deriving semantic
  resume coordinates, preserving its immutable `ProcessModelType`, decoding Engine-owned envelopes, and interpreting
  already committed Wait/Timer/Effect facts. A
  compatible release in the same MAJOR must recover historical corpus fixtures with equivalent semantics or ship an
  explicit, tested data migration. An internal compiler/generator refactor may not silently turn valid retained Runs
  into permanent capability failures.

The checked-in `durable-compatibility/vN` corpus is the executable authority for this second promise. A new persisted
format adds a new immutable corpus version only when a real incompatible representation exists; released historical
fixtures are never regenerated in place. The fixture directory is test organization, not Runtime routing. The compact
envelope format marker is a decode/fail-closed discriminator only and never Process identity, codec identity, Alias,
Worker routing, or a Provider SPI.

CompileFlow must not persist implementation versions to compensate for compatibility it can guarantee itself. A Run
therefore has no Machine-semantics, compiler, generator, Runtime or Program-ABI version, and the project does not prebuild
a codec registry, upcaster or dual reader/writer for a hypothetical second format. A new compatibility discriminator is
introduced only when two real valid persisted representations or semantics need to coexist and their concrete migration
evidence identifies the minimum required mechanism. A bug fix restores the documented semantics and adds a regression
fixture; it does not preserve the buggy implementation as a new semantic version.

**Breaking changes** that require a MAJOR bump:

- Removing or renaming a public type, method, or field
- Changing a method signature (parameters, return type, checked exceptions)
- Changing a class hierarchy or interface implementation in a way that breaks `instanceof` or assignment
- Adding a new abstract method to a non-sealed interface that users are expected to implement
- Removing or reinterpreting a deployment command, view, error code, transport operation, or protocol invariant
- Changing a JSON or YAML configuration property name or shape exposed via Spring Boot
  `@ConfigurationProperties`

**Non-breaking changes** that fit a MINOR bump:

- Adding a new method with a default implementation
- Adding a new SPI interface in a new package
- Adding a new configuration property with a safe default
- Adding a bounded error-code enum constant; consumers must retain an unknown/default branch, while existing enum names
  and meanings remain stable

`ProcessEvent` is a sealed lifecycle hierarchy and is closed for the 2.x line. Adding a permitted event subtype is
source-incompatible for exhaustive consumers and therefore requires a MAJOR release or a separate extensible event
surface.

### 2.2 Process definition format (TBBPM)

The TBBPM format is specified in [`docs/specs/tbbpm-specification.en.md`](specs/tbbpm-specification.en.md). The engine
rejects unknown elements and unknown actions at `preflight`, so adding new nodes or handles is
backward-compatible.

**Breaking changes** that require a MAJOR bump:

- Removing or renaming a node type, attribute, or action
- Changing the semantics of an existing node or action in a way that changes execution results for previously
  valid definitions
- Changing required attributes on an existing node

**Non-breaking changes** that fit a MINOR bump:

- Adding a new node type or action (with a default `preflight` rejection until registered)
- Adding a new optional attribute to an existing node
- Loosening validation (accepting a previously rejected definition)

### 2.3 BPMN 2.0 subset

CompileFlow supports a documented subset of BPMN 2.0 (see [`docs/specs/tbbpm-vs-bpmn.md`](specs/tbbpm-vs-bpmn.md)). The
supported node list is part of the compatibility promise.

Baseline BPMN support does not imply that every construct is Durable. Durable supports the documented strict TBBPM
and BPMN profiles whose constructs map to the same format-neutral Kernel semantics; this is not a promise to implement general
message correlation, human-task lifecycle, boundary events, or the full BPMN execution platform.

**Breaking changes** that require a MAJOR bump:

- Removing a supported BPMN node from the supported-subset list
- Changing the semantics of a supported BPMN node

**Non-breaking changes** that fit a MINOR bump:

- Adding a new BPMN node to the supported-subset list

### 2.4 Spring Boot configuration

The `compileflow.*` configuration property namespace exposed via `compileflow-spring-boot-autoconfigure` is part of the
compatibility promise.

Configuration compatibility freezes property names, value types, units, semantic ranges, and the following default
categories; it does not freeze every performance-tuning number forever.

| Default category | Examples | Compatible evolution |
|---|---|---|
| Semantic | feature enablement, fail-open/fail-closed, automatic migration | Preserved within a MAJOR |
| Safety | payload/script/resource limits, security policy | May be tightened for a documented security or correctness fix; release notes and a safe migration/override are required where possible |
| Operational tuning | thread, queue, cache, batch and poll sizes | May change in a PATCH or MINOR when backed by benchmark/operational evidence and called out in release notes |
| SLA-sensitive | lease, retry, deadline and backoff defaults | Must be identified explicitly; changes require at least a MINOR and migration guidance, and become MAJOR if they reinterpret persisted Process outcomes |

**Breaking changes** that require a MAJOR bump:

- Renaming or removing a property
- Changing the type of a property value (e.g., `String` → `Duration`)
- Changing a semantic default or reinterpreting a persisted outcome without an opt-in

**Non-breaking changes** that fit a MINOR bump:

- Adding a new property with a default that preserves existing behavior
- Adding a new auto-configuration class guarded by `@ConditionalOnProperty(matchIfMissing = false)`
- Tuning a documented operational default without changing its name, type, unit, semantic range, or safety boundary

### 2.5 Workbench companion REST (`compileflow-workbench-server`)

The committed
[`compileflow-workbench-server.openapi.json`](specs/openapi/compileflow-workbench-server.openapi.json)
is the exact same-release wire-contract authority for endpoints exposed under `/api/**`.
[`API_SPEC.md`](../compileflow-workbench/apps/web/src/operate/API_SPEC.md)
documents their behavioral semantics.

These endpoints form the companion-backend contract between the official Workbench frontend and its matching Server
release. They are **Supported by deployment**, not a public third-party integration ABI preserved across every 2.x
MINOR. Wire changes must update controllers, OpenAPI, generated TypeScript, runtime validation, tests, and release notes
atomically. A deployed frontend and Server must come from the same CompileFlow release unless a specific skew window is
documented and tested.

A future public integration API must use an explicitly versioned root such as `/api/v1`, define authentication and
error semantics independently, and be added to the Supported-surface list before it receives a SemVer compatibility
promise. The current `/api/**` path must not be inferred to have that status.

> `compileflow-workbench-server` is the Workbench companion backend. See
> its [README](../compileflow-workbench-server/README.md). For embedding the engine in your own service, depend on
> `compileflow-spring-boot-starter` instead.

### 2.6 Workbench contracts

Generated TypeScript in
`compileflow-workbench/apps/web/src/shared/contracts/generated` mirrors the OpenAPI wire contract. Handwritten contracts
refine those types for UI domain semantics, while `operate/api/*.ts` owns mock/real adapter behavior. Compile-time
parity must preserve property names, types, and requiredness; these layers may not define a competing HTTP contract.

### 2.7 Persisted, wire, and telemetry facts

Once released, a Flyway migration is immutable: its filename, order, and checksum-producing contents are never edited
in place. Schema evolution uses a new migration and includes forward, restart, and rollback/recovery evidence appropriate
to the owning bounded context.

Persisted enum names, discriminator strings, error codes, protocol field names, metric names, and metric tag keys are
compatibility facts. Their spelling and meaning remain stable within a MAJOR unless the owning surface is explicitly
Preview. Deploy telemetry uses the `compileflow.deploy.*` namespace and bounded dotted tag keys such as `error.code`.

Release metadata is descriptive data, not a command channel. Publication integrity preconditions use the explicit
`expectedArtifactDigest` command field. The `compileflow.` metadata-key namespace is reserved for keys declared by
CompileFlow; application metadata uses an application-owned namespace.

### 2.8 Java platform

Java 17 is the minimum build and runtime baseline. Project source, published bytecode, and generated-flow bytecode use
`maven.compiler.release=17`. Supported runtimes are patched Java 17, Java 21, and Java 25 LTS releases, and every
runtime must include the standard `jdk.compiler` module used for dynamic preparation.

CompileFlow publishes one set of Java artifacts for all supported JDKs and does not publish JDK-specific classifiers.
Runtime-specific thread strategies remain internal and do not change the public API or configuration contract.

### 2.9 Java native serialization

Supported Java API compatibility does not make `ObjectOutputStream` bytes a wire, persistence, or cross-version
contract. `Serializable` and `serialVersionUID` on defensive values or exceptions support standard JVM/library use
only. Stable integration uses documented Java values, OpenAPI, Process definitions, or the Durable persisted contract;
it never stores a serialized exception as authoritative state.

## 3. Versioning lifecycle

### 3.1 2.x line

CompileFlow 2.0 establishes the supported surfaces above. The 2.x line preserves compatibility for those surfaces. The
maintainer-operated Maven Central path publishes supported library artifacts for patch and minor releases through the
`central-publishing-maven-plugin` (see `pom.xml`). The tag workflow publishes GitHub Release assets and provenance but
does not receive Central credentials or signing keys.

### 3.2 Durable upgrade and artifact-retention contract

The current Durable Developer Preview supports a coordinated upgrade only:
stop admission and Workers as documented, take and verify a recoverable backup, apply the reviewed schema/application
change, and start a homogeneous version. It does not yet promise a rolling mixed-version control-plane/Worker upgrade.
Promotion to Supported requires either a tested `N-1` skew window or a narrower explicit skew contract, plus
forward/rollback schema and persisted-envelope tests.

Durable retention is **reference based**, not merely time based or “keep the last N versions.” Every retained Run keeps
its exact stored Process Definition set through Run–Process foreign keys. A stored Process may be deleted only by the
bounded unused-Process sweep after all Run references are gone. Terminal Run deletion is separately gated by its age
and required Outbox state. Generated programs are disposable; Run recovery rebuilds them from stored Process semantics,
not from Deploy routing or a Version lookup.

Deploy Version and Artifact retention is a separate control-plane policy for future admission, rollback, and audit. It
does not become recovery authority for an existing Durable Run. A calendar retention floor may lengthen either policy
but may never bypass Run–Process references or a retained database backup/WAL recovery point. Production operations
must join live database references with declared rollback and backup-recovery windows before deleting an exact stored
Process Definition or publication artifact. Keeping a compatible application implementation is an application
deployment decision, not a persisted Process identity or a CompileFlow Kernel compatibility promise.

### 3.3 Deploy protocol upgrade contract

Deploy 2.x supports coordinated homogeneous protocol upgrades. Control-plane writers, distributed channel contents,
and runtime readers must use the same documented protocol generation. Current routing and artifact parsers accept only
their exact schema versions and reject unknown fields; no rolling `N/N-1` mixed-version window is claimed.

Before a future release may claim mixed-version rolling upgrades, it must provide real adjacent-version readers or
writers, immutable `N-1` fixtures, both rollout directions, and rollback behavior. A generic codec registry is not added
before two concrete valid representations need to coexist.

## 4. API compatibility checking

The first stable release establishes baselines for `compileflow-api` and
`compileflow-deploy-api`. For each subsequent tag, the release workflow resolves the latest earlier stable tag reachable
from the release commit in the same MAJOR line and invokes the `api-compat` profile automatically. A later stable
release with no reachable same-MAJOR baseline fails closed, which detects shallow or incomplete release history.

The deployment comparison covers the Supported `com.alibaba.compileflow.deploy.api` packages. Repository-scoped
collaboration code outside that namespace is excluded from the binary-compatibility comparison and published API
Javadocs. Exact shape tests keep every public API-artifact package and top-level type allowlisted, so a new public type
cannot silently enter the Supported contract.

Maintainers can run the same comparison before tagging:

```bash
./mvnw verify \
  -pl compileflow-api,compileflow-deploy/compileflow-deploy-api \
  -am \
  -Papi-compat \
  -Dcompileflow.api.baseline.version=<previous-2.x-version>
```

The first stable `X.0.0` release deliberately establishes the new MAJOR baseline and therefore has no same-MAJOR
predecessor. Pre-releases compare against the latest reachable stable release when one exists.

A diff in the Supported API surface fails the tag build and requires either:

- a MINOR or PATCH release with a backward-compatible API surface, or
- a MAJOR release with an explicit upgrade guide.

Durable is added to this automatic baseline only when it is promoted from Preview to Supported. Until then, its
Javadocs, contract tests, persisted-format checks, and documented Preview change rules remain mandatory but must not be
presented as a stable binary-compatibility promise. The release workflow therefore excludes Durable Preview artifacts
from the Supported `api-compat` step; the first release that promotes Durable to Supported establishes its
Durable-specific baseline rather than inheriting compatibility debt from pre-release snapshots.

## 5. Reporting breakage

If you discover a breaking change in a non-MAJOR release, please file a GitHub issue with:

1. The CompileFlow version you upgraded from and to
2. The supported surface affected (API, format, configuration, REST)
3. A minimal reproducer
4. The error message or observable behavior change

The maintainers treat silent breakage as a bug and will issue a PATCH release to restore compatibility.

## 6. References

- [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html)
- [Supported Surfaces](architecture/06-SUPPORTED_SURFACES.en.md)
- [Java API Reference](en/api-reference.md)
- [TBBPM Specification](specs/tbbpm-specification.en.md)
- [TBBPM vs BPMN](specs/tbbpm-vs-bpmn.md)
