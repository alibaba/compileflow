# Testing guide

Tests define the documented behavior. Choose the smallest layer that proves a rule, then add a higher-level test when the
rule crosses a module or persistence boundary.

## Test layers

| Layer                    | Scope                                       | Typical use                                                               |
| ------------------------ | ------------------------------------------- | ------------------------------------------------------------------------- |
| Unit                     | One class or pure rule                      | Parsing, validation, routing, state transitions, and error mapping.       |
| Module integration       | One Maven module with its dependencies      | Spring wiring, generated code, resource lifecycle, and provider behavior. |
| Store contract           | A real first-party database Provider        | Transaction, migration, locking, lease, and recovery semantics.           |
| Cross-module integration | Engine, Deploy, Durable, or Server together | Public workflows and the exact effective version.                         |
| Workbench                | pnpm workspace                              | TypeScript, contract, UI, and browser behavior.                           |

Tests must be deterministic. Do not depend on execution order, a developer home directory, the default timezone, network availability, or static state left by an earlier test.

## Java tests

Keep tests next to the production module and use the public contract when the behavior is public. Name each test after
the rule it proves, and use the smallest input that demonstrates the behavior.

For concurrency and lifecycle code:

- use latches, barriers, or a controllable clock instead of arbitrary sleeps;
- assert ownership, cancellation, fencing, and shutdown explicitly;
- close executors, class loaders, database connections, and child processes;
- assert both the success path and the fail-closed path.

Generated Java tests should verify semantic output and execution behavior, not a brittle formatting detail. A source snapshot is useful only when the emitted structure is itself a contract.

## Persistence tests

Migration and transaction behavior must be verified against the supported first-party databases used by the feature. PostgreSQL 16, 17, and 18 and MySQL 8.4 are supported where the module declares them. H2 is a test implementation and is not evidence of production database compatibility.

For a persistence change, cover:

1. clean schema creation and migration admission;
2. commit and rollback behavior;
3. concurrent claims or compare-and-set transitions;
4. restart, lease expiry, and recovery behavior when applicable;
5. exact error or dead-letter handling.

Provider implementations must pass their provider-neutral Store testkit. Do not modify a published migration in place.

## Spring and Server tests

Use a focused Spring context. Assert that required infrastructure is present, that an incomplete Provider selection fails startup, and that disabled optional products do not create database dependencies.

Server endpoint tests should cover request validation, authentication mode, problem responses, OpenAPI shape, persistence effects, and idempotency. Workbench Server `/api/**` is a same-release companion contract, so a change must update controller types, the OpenAPI snapshot, generated frontend types, runtime validation, and tests together.

## Targeted commands

Run one test class:

```bash
./mvnw -pl <module> -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false test
```

Build dependent modules from the reactor:

```bash
./mvnw -pl <module> -am -DskipTests compile
```

Run a focused integration test:

```bash
./mvnw -pl compileflow-integration-tests -am -Dtest=<TestClass> -Dsurefire.failIfNoSpecifiedTests=false test
```

Run repository documentation and architecture gates:

```bash
python3 scripts/check_internal_links.py
python3 scripts/check_bilingual_parity.py
python3 scripts/check_architecture_boundaries.py
python3 scripts/check_spec_impl_parity.py
git diff --check
```

Run the Workbench checks from `compileflow-workbench` using the scripts in its `package.json`, normally:

```bash
pnpm type-check
pnpm test
pnpm build
```

Use the relevant Playwright command only when the change affects a browser workflow. External Server and browser targets must be explicitly configured; unavailable targets must fail rather than silently skip applicable coverage.

## Executable Server smoke test

When a change affects packaging, configuration, migrations, authentication, or cross-module wiring, run the packaged Server as an executable JAR with a supported database. Verify startup, schema admission, one authenticated request, one process execution, one deployment operation when enabled, and graceful shutdown. A unit test or H2 run alone is insufficient evidence for these changes.

## Failure diagnosis

Treat a flaky test as a defect signal. Preserve the seed, input, thread state, database logs, and relevant configuration; determine whether the problem is a product race, resource leak, environment dependency, or test nondeterminism. Fix the cause and add a deterministic regression test. Do not hide it with wider sleeps, weaker assertions, or unlimited retries.

## Submission checklist

- New behavior has a test; a fixed defect has a regression test.
- Tests prove the public contract at the narrowest appropriate layer.
- Concurrency and time behavior use deterministic coordination.
- Persistence changes are verified on each relevant supported Provider.
- API, configuration, REST, schema, wire, and generated-client changes cover their corresponding contract layers.
- Targeted tests and repository gates have been run.
- Build artifacts are not left in the working tree.
