# Compatibility policy

CompileFlow follows [Semantic Versioning 2.0.0](https://semver.org/). This page defines the compatibility promises for the `2.x` line. A promise applies only to a surface explicitly marked Supported in [Supported surfaces](architecture/supported-surfaces.md).

## Version numbers

- **MAJOR** removes or changes a Supported contract incompatibly.
- **MINOR** adds a backward-compatible capability, node, SPI, or safely disabled configuration.
- **PATCH** fixes a defect or security issue without changing a Supported contract.

## Supported surfaces

### Java API and SPI

The Supported Java and deployment artifacts are:

- `compileflow-api`, for Engine execution, configuration, preflight, errors, and extension SPI;
- `compileflow-deploy-api`, for immutable publication, rollout, route, view, and error contracts;
- `compileflow-deploy-protocol`, for versioned payloads, canonical codecs, and projection keys;
- `compileflow-durable-api`, for process start, Wait completion, cancellation, queries, and operator contracts.

`DurableProcessEngineConfig` and `DurableProcessEngineFactory` in `compileflow-durable-runtime` are also Supported as
the plain Java composition entry points. Other runtime implementation types are not application APIs.

Only packages and types listed by Supported surfaces are covered. A Java `public` modifier in an implementation module does not create a compatibility promise.

`compileflow-deploy-spi`, including `projection.DeploymentProjectionStore`, is **Provider Preview** and version-coupled;
it is excluded from the Supported application API baseline. A real remote projection provider must prove subscription,
reconnect, history-loss, concurrent-writer and partition behavior before that transport contract can be promoted.
`compileflow-deploy-jdbc` is version-coupled first-party implementation support, not an application API or extension SPI.

The Durable API is part of the Supported application API baseline. Its Store SPI is **Provider Preview** and
version-coupled. Durable artifacts must preserve the meaning of exact process identity, committed facts, continuation,
and fail-closed recovery.

The following are MAJOR changes for a Supported surface: removing or renaming a type, method, or field; changing a method signature or checked exception; changing an assignability relationship; adding an abstract method that an application must implement; or changing the meaning of a deployment command, view, error, or protocol invariant.

Adding a compatible type or optional capability is normally MINOR. Consumers must handle unknown error values and must not depend on enum ordering. `ProcessEvent` is a sealed lifecycle hierarchy and is closed for the 2.x line.

### Process definition formats

TBBPM and the documented BPMN 2.0 subset are compatibility surfaces defined by their specifications and the [node support](node-support.md) page.

MAJOR changes include deleting or renaming a supported element or attribute, changing the meaning of a valid definition, or making an existing required value mandatory in a new way. Adding an element that old runtimes reject during preflight is MINOR when existing definitions retain their meaning. General BPMN validity does not imply CompileFlow or Durable support.

### Spring Boot configuration

The names, types, units, enum values, ownership, semantics and accepted bounds of documented `compileflow.*` properties are Supported. Renaming a property, changing its type, or changing a semantic, security or activation default without opt-in is MAJOR. A new property with a safe disabled default is MINOR.

Security and correctness fixes may tighten an unsafe default within the same MAJOR when the change is documented. Thread,
queue, cache, and polling defaults are operational tuning rather than a latency guarantee; they may evolve with documented validation evidence without changing the property's meaning or accepted bounds. Lease, retry, deadline, and
backoff changes must state their effect on persisted behavior.

### Workbench companion REST

The committed [OpenAPI description](specifications/workbench-server-openapi.md) is the exact same-release wire contract for `compileflow-workbench-server` `/api/**` endpoints. It supports the matching Workbench frontend and Server deployment (**Supported by deployment**); it is not a stable third-party integration API.

Controller records, OpenAPI, generated TypeScript, runtime validation, and contract tests must change together. The frontend
and Server must come from the same CompileFlow version; this surface does not define a compatibility window.

### Persisted, wire, and telemetry facts

The V1 Deploy and Workbench migrations establish their initial schema baselines. Every published Flyway migration is immutable;
later schema changes add migrations and include startup, restart, and recovery coverage for the owning product.

Persisted discriminator strings, error codes, protocol field names, metric names, and metric tag keys are compatibility facts. CompileFlow metadata uses the `compileflow.` namespace; Deploy uses the `compileflow.deploy.*` metric namespace. Application metadata uses an application-owned namespace. Release metadata is descriptive and is not a routing or projection authority. Publication integrity uses the explicit `expectedArtifactDigest` command field.

### Java platform and serialization

**Java 17 is the minimum** build and bytecode baseline; `maven.compiler.release=17`. Supported runtime lines are Java 17, Java 21, and Java 25 LTS, with the standard `jdk.compiler` module available for dynamic preparation. CompileFlow publishes one set of Java artifacts and does not publish JDK-specific classifiers.

Java native serialization is not a CompileFlow wire or persistence contract. Stable integrations must use the documented Java API, process formats, OpenAPI contract, or Durable persistence contract. Runtime thread strategies remain internal.

## Runtime and persistence boundaries

Deploy and Durable persistence products use one matching protocol generation for each running topology. Deploy uses
coordinated homogeneous protocol upgrades. Durable does not support mixed-version rolling operation.

Durable retention is reference-based: a stored process definition remains while a retained Run references it. CompileFlow-owned Process semantic compatibility covers parsing stored definitions and restoring committed semantic state; application
implementations own the decodability of their persisted values. Generated code is disposable; recovery rebuilds from stored
process semantics. Deploy artifact retention is a separate publication concern and does not become Durable recovery
authority. Operational tuning includes thread, queue, cache, and polling defaults and is not an SLA promise.

Deploy control-plane writers, projection-store payloads, and runtime readers use one exact protocol generation. Unknown
protocol fields and schema generations fail closed.

## References

- [Supported surfaces](architecture/supported-surfaces.md)
- [API reference](api-reference.md)
- [TBBPM specification](specifications/tbbpm.md)
- [Process format reference](specifications/process-formats.md)
