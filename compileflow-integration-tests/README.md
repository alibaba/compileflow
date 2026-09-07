# CompileFlow Integration Tests

This module verifies behavior that crosses engine, parser, deployment, and Spring composition boundaries. It is test
infrastructure only and is not published to Maven Central. Application code should not depend on its fixtures or test
helpers.

## Scope

Tests are organized by responsibility:

- `core`: BPMN, TBBPM, Java action, and trigger-entry execution behavior
- `feature`: deployment, routing, invocation policy, Spring, and tooling integration
- `quality`: boundary conditions, concurrency, and resource ownership
- `system`: multi-component and restart scenarios
- `support`: fixtures and test-only extensions

Database-specific repository contracts are tested in their provider modules. System-level deployment tests in this
module use the in-memory testkit stores and do not replace the PostgreSQL or MySQL contract tests.

## Run the Tests

Run commands from the repository root and keep the Maven scope explicit.

```bash
# One integration test class and its reactor dependencies
./mvnw test -pl compileflow-integration-tests -am \
  -Dtest=ProcessDataMapperIntegrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

# Fast smoke tag
./mvnw test -pl compileflow-integration-tests -am -Dgroups=smoke

# Pull-request integration suite without slow scenarios
./mvnw verify -pl compileflow-integration-tests -am \
  -DexcludedGroups=slow

# Nightly-equivalent suite, including slow functional scenarios
./mvnw verify -pl compileflow-integration-tests -am
```

`verify` creates the cross-module JaCoCo report at:

```text
compileflow-integration-tests/target/site/jacoco-aggregate/index.html
```

The module runs tests sequentially by default. Concurrency tests control ordering with latches, barriers, or controlled
executors; do not add
`Thread.sleep`-based timing assumptions.

## Add a Test

- Put the test under the package matching the behavior it proves.
- Prefer public API calls; use implementation types only when the test owns an internal module contract.
- Keep fixtures minimal and deterministic.
- Assert externally visible state and failure types, not incidental log text.
- Use `@Tag("smoke")` only for a small, representative happy path.
- Use `@Tag("slow")` for long-running functional scenarios.
- Require every deterministic execution to succeed; do not mask failures with percentage tolerances.
- Do not use wall-clock thresholds as integration assertions. Performance measurements belong in
  the [JMH benchmark module](../compileflow-benchmarks/README.md).
- Pull request CI excludes the slow tag; the nightly workflow includes it.

See the root [contribution guide](../CONTRIBUTING.md) and
[testing guide](../docs/en/testing.md) for repository-wide requirements.
