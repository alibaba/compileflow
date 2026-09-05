# CompileFlow Testing Guide

Repository tests use the boundaries, authoring principles, and verification commands below. Dependency versions follow
the root `pom.xml`, the Spring Boot BOM, and `compileflow-workbench/pnpm-lock.yaml`; version numbers are not duplicated
here.

## Core Principles

- Test behavior and public contracts; do not mirror implementation steps.
- Tests must be repeatable, parallel-friendly, and independent of execution order, static residual state, or local
  machine environment.
- Match test scope to change risk. Run targeted unit tests for local logic; widen the verification scope for
  cross-module, persistence, or delivery-shape changes.
- Failures must produce diagnostic evidence. Assert business outcomes, error codes, and persistence state; do not assert
  only "no exception thrown".
- Tests own the cleanup of thread pools, class loaders, database connections, temporary directories, and child
  processes.
- Prefer AssertJ for Java tests so assertions and failure diagnostics stay consistent. Use framework-native assertions
  only when an integration API requires them; do not mix assertion styles within one test.
- Coverage numbers are not a completion gate. Cover key branches, boundary conditions, and past regressions.

## Test Layers

| Layer          | Location                                                | Target                                                                              | External dependencies                                             |
|----------------|---------------------------------------------------------|-------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| Unit           | each module's `src/test`                                | a single type or small collaboration boundary                                       | no network, no real database                                      |
| Component      | each module's `src/test`                                | module boundaries such as parsers, repositories, Spring auto-configuration          | may use in-memory fixtures or a controlled Spring context         |
| Integration    | `compileflow-integration-tests`                         | cross-module contracts across Engine, format modules, Spring, and Deploy            | as declared by each test                                          |
| Server         | `compileflow-workbench-server/src/test`                 | REST, auth, persistence, routing, observability                                     | H2 for tests only; PostgreSQL verifies real persistence contracts |
| Delivery smoke | `scripts/smoke_test_workbench_server_executable_jar.py` | startup, dynamic compilation, and execution of the final Spring Boot executable JAR | standalone Server subprocess with PostgreSQL                      |
| Workbench      | `compileflow-workbench`                                 | TypeScript unit, contract, and Playwright workflows                                 | `pnpm` only                                                       |

A lower layer cannot substitute for a higher layer. An exploded Spring test context passing does not prove that a nested
JAR classpath under `java -jar` works for dynamic compilation.

## Java Tests

### Naming and Structure

Test names should describe observable behavior, for example:

```java
@Test
void staleReconciliationDoesNotReloadCompletedVersion() {
    // Arrange only the state needed to expose the race.
    // Act through the public or owned module boundary.
    // Assert the externally visible invariant.
}
```

- Do not force a verbose `should...When...` template.
- Use `@Nested` and `@DisplayName` only when they genuinely improve organization or reporting.
- Arrange/Act/Assert comments are only for long tests; short tests should express stages through code structure.
- A test may contain multiple assertions that jointly prove the same behavior; "one assertion per test" is not required.

### Assertions

```java
assertThat(result.getOutput()).containsEntry("status", "completed");

assertThatThrownBy(() -> service.publish(invalidRequest))
        .isInstanceOf(DeploymentException.class)
        .hasMessageContaining("version");
```

Add `.as(...)` only when failure messages lack business context. Do not repeat descriptions on self-explanatory
assertions.

Exception tests should preferentially verify stable contracts: exception type, error code, and key context. Do not
assert full mutable text unless the full message is itself a public contract.

### Concurrency Tests

- Use latch, barrier, controlled executor, or test doubles to precisely arrange the race window.
- Do not use `Thread.sleep` to guess that another thread has reached some point.
- Timeouts are only an upper bound to prevent tests from hanging forever; they are not a primary correctness assertion.
- Verify success results, invocation counts, failure isolation, and resource reclamation together.
- Regression tests must deterministically reproduce the original problem before the fix, rather than relying on high
  random-iteration counts.

See
[`RuntimeInstallerReconciliationTest`](../../compileflow-deploy/compileflow-deploy-runtime/src/test/java/com/alibaba/compileflow/deploy/runtime/install/RuntimeInstallerReconciliationTest.java).

### Class Loading and Resource Lifecycle

Dynamic compilation tests must distinguish resource discovery from resource ownership. Tests should verify that the
caller-supplied `ClassLoader` remains usable after compilation, and cover directory classpath, plain JAR, and final
executable JAR.

See
[`GeneratedCodeCompilerTest`](../../compileflow-core/src/test/java/com/alibaba/compileflow/engine/core/java/compiler/GeneratedCodeCompilerTest.java).

### Time and Performance

Millisecond thresholds on a normal CI runner are jittery and cannot serve as micro-benchmarks. Unit tests may verify:

- bounded queue, concurrency permit, and cache cap configurations take effect;
- algorithms do not retry unboundedly or reload repeatedly;
- timeouts, cancellation, and backoff use a controllable clock/scheduler;
- complexity regressions can be proved by operation counts or input-size relationships.

When comparing throughput, latency, or allocation rate, use an independent, warmup-capable, reproducible benchmark, and
record JDK, hardware, parameters, and sample distribution in the report.

## Persistence Tests

- Flyway migrations are the single source of truth for schema.
- H2 is only for fast tests; it cannot prove PostgreSQL dialect, locks, or transaction semantics. The `dev` profile uses
  PostgreSQL.
- SQL, concurrent claim, `FOR UPDATE SKIP LOCKED`, constraints, and migrations must be verified on a supported
  PostgreSQL version.
- Each test creates or cleans its own state independently; it must not depend on test execution order.
- Silent in-memory fallback after a persistence failure is not allowed.

CI verifies Deploy and Workbench migrations, concurrency, persistence, and external-schema admission against pinned
PostgreSQL 16, 17, and 18 images. Java 17 runs the Server target suite and Java 25 runs the executable-JAR runtime
smoke against PostgreSQL 18; these bounded checks do not represent every JVM and database combination.

## Spring Tests

- For property binding and conditional wiring, prefer `ApplicationContextRunner` and load only the relevant
  auto-configuration.
- For web contracts, use MVC tests or a small Spring context; only cross-layer behavior launches the full application.
- Test default values, explicit values, illegal values, unknown fields, and missing required dependencies together.
- Contexts must be closed by the test framework; do not place Spring beans in cross-test static holders.

[`EmbeddedDeploymentExecutionIntegrationTest`](../../compileflow-workbench-server/src/test/java/com/alibaba/compileflow/workbench/server/deployment/EmbeddedDeploymentExecutionIntegrationTest.java)
verifies the deployment execution chain in an exploded Spring application; it complements (and cannot replace) the
executable JAR smoke test.

## Targeted Verification Commands

Running `./mvnw test` from the repository root without a scope is forbidden. Locally, run the minimal sufficient scope
first:

```bash
# A single test class in a single module
./mvnw test -pl compileflow-core -Dtest=GeneratedCodeCompilerTest

# When the change also touches upstream modules, build dependencies from the reactor
./mvnw test -pl compileflow-core -am \
  -Dtest=GeneratedCodeCompilerTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Server-related tests
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=EmbeddedDeploymentExecutionIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Static quality gates
./mvnw checkstyle:check -pl compileflow-workbench-server -am
./mvnw install -pl compileflow-workbench-server -am -DskipTests
./mvnw spotbugs:check -pl compileflow-workbench-server -am
python3 scripts/verify_spotbugs_reports.py \
  compileflow-api \
  compileflow-core \
  compileflow-tbbpm \
  compileflow-bpmn \
  compileflow-deploy/compileflow-deploy-api \
  compileflow-deploy/compileflow-deploy-control-plane \
  compileflow-deploy/compileflow-deploy-runtime \
  compileflow-spring-boot-autoconfigure \
  compileflow-workbench-server
python3 scripts/check_internal_links.py
```

`-am` makes Maven also traverse upstream modules. When using `-Dtest`, set
`-Dsurefire.failIfNoSpecifiedTests=false` so upstream modules without that test class do not falsely fail. The `install`
before SpotBugs is not optional: the analysis plugin needs the dependency JARs of the current reactor; afterwards the
XML reports must be verified so the plugin does not return success on missing classes.

## Executable JAR Smoke Test

First build the final delivery, then run the smoke test:

```bash
./mvnw package -pl compileflow-workbench-server -am -DskipTests
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/compileflow_smoke
export SPRING_DATASOURCE_USERNAME=compileflow
export SPRING_DATASOURCE_PASSWORD=replace-with-a-local-test-password
python3 scripts/smoke_test_workbench_server_executable_jar.py
```

The database must already exist and the configured user must own it. The smoke test uses the production persistence
engine because H2 is a test-scoped dependency and is not packaged in the executable JAR.

The smoke test automatically:

1. Starts the packaged Spring Boot JAR;
2. Waits for the health endpoint to become ready;
3. Creates and publishes a TBBPM flow that calls Commons Lang from a nested dependency;
4. Deploys it through the control-plane/data-plane path;
5. Executes the converged `dev` alias and asserts the result and routing attribution;
6. Re-checks service health;
7. Terminates the subprocess, and on failure prints the full service log.

Server CI runs this gate on JDK 17, 21, and 25. It simultaneously verifies `jdk.compiler`, nested JAR classpath, caller
classloader lifecycle, REST deployment, and runtime execution.

## Workbench

Run repository scripts from `compileflow-workbench/`, using only `pnpm`:

```bash
pnpm type-check
pnpm check:workbench-server-contract
pnpm test
pnpm test:e2e:smoke
pnpm test:e2e:integration
```

By default, the integration command starts and stops the bundled Workbench Server JAR and a loopback-only test edge.
Build that artifact with
`pnpm verify:delivery --assembly-only` and provide
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and
`SPRING_DATASOURCE_PASSWORD` for an existing PostgreSQL database. Also set a 32..256 character URL-safe
`COMPILEFLOW_E2E_SERVER_API_KEY`; the managed Server runs with the `prod` profile and uses that key for fail-closed
authentication. The test edge strips browser-supplied credentials and forwarding headers before injecting the key on the
private upstream hop. It models that trust boundary; it is not a production authentication gateway.

The managed suite includes the complete deployment lifecycle through public HTTP and browser surfaces: immutable
publication and replay, baseline deploy, canary creation, stable/candidate execution, health evaluation, promotion,
rollback, and final effective-version attribution. Canary cohorts are selected with the runtime's exact routing hash;
the test does not rely on repeated random requests. Delivery verification also compares every bundled static asset with
the current production Web build so stale incremental-build files fail the gate.

Set `COMPILEFLOW_E2E_SERVER_URL` to test an already running Server API, or set
`COMPILEFLOW_E2E_BROWSER_URL` to exercise an already running browser-facing gateway and its UI. Playwright will not
manage external processes. Set
`COMPILEFLOW_E2E_SERVER_API_KEY` when a directly selected Server requires API-key authentication. Unavailable targets
fail instead of silently skipping their applicable tests.

The exact available commands follow that directory's `package.json` and
[Workbench contribution guide](../../compileflow-workbench/CONTRIBUTING.md). Frontend-backend contract changes must
regenerate the Server OpenAPI snapshot, check generated Workbench types, and pass the shared-contract parity assertions.

## CI and Failure Handling

CI covers at least:

- a JDK 17 source build and targeted tests that prove the minimum build and runtime floor;
- JDK 17 Checkstyle, SpotBugs, public API Javadoc, packaging, and delivery gates over Java 17 artifacts;
- full integration and Server behavior on JDK 17, with focused JDK 25 runtime compatibility smoke;
- PostgreSQL 16/17/18 Deploy and Workbench migration, concurrency, and persistence contracts;
- JDK 17 and JDK 25 executable JAR smoke;
- Workbench typecheck, unit tests, Playwright smoke, and authenticated browser/HTTP tests against the PostgreSQL-backed
  bundled Server artifact;
- repository hygiene, internal link, and supply-chain gates.

A flaky test is not "just rerun it". First save the seed, input, thread state, and logs; determine whether it is a
product race, a resource leak, an environment dependency, or test-side nondeterminism. After the fix, add a
deterministic regression test; do not widen sleeps, loosen assertions, or retry indefinitely to hide the problem.

## Pre-Submission Checklist

- New behavior has a corresponding test; fixed defects have a regression test.
- Tests do not depend on order, network, user home directory, default timezone, or static residual state.
- Concurrency tests use deterministic synchronization points; all executors and child processes are closed.
- Persistence semantics are verified on PostgreSQL; H2 is not treated as a production substitute.
- Public API, configuration, REST, or delivery-shape changes cover the corresponding contract layer.
- Targeted tests and static gates have been run for the affected modules.
- Build artifacts do not enter the working tree; `python3 scripts/check_internal_links.py` passes.
