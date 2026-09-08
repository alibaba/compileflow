# CompileFlow Deployment Lifecycle Example

This Spring Boot service demonstrates the complete deployment lifecycle for a process stored in PostgreSQL. A single
request publishes three immutable versions, creates a `production` alias, runs a deterministic canary rollout, promotes
v2, aborts a simulated v3 regression, rolls back to v1, and executes v2 by its exact version.

The server listens only on `127.0.0.1` and does not implement authentication or authorization. Do not expose it on a
shared network. Applications that accept deployment commands must enforce authentication, authorization, approval,
and audit controls.

## What it demonstrates

| Capability               | Behavior                                                                                            |
| ------------------------ | --------------------------------------------------------------------------------------------------- |
| Immutable publication    | Publishes v1, v2, and v3 under exact version identities                                             |
| PostgreSQL `DeployStore` | Stores process versions, aliases, rollouts, audit events, and routing Outbox events                 |
| Local readiness          | Makes an alias change visible only after the selected process is executable in the embedded runtime |
| Deterministic canary     | Routes stable and candidate cohorts by persistent routing keys                                      |
| Revision checks          | Supplies the current revision when widening, promoting, aborting, or rolling back a rollout         |
| Promotion and abort      | Widens v2 from 10% to 50%, promotes it, then aborts a simulated v3 regression                       |
| Rollback                 | Restores the original v1 target through an audited rollout                                          |
| Exact-version execution  | Loads and executes v2 directly while the `production` alias points to v1                            |
| Audit events             | Returns the audit-event count for each rollout operation                                            |

## Prerequisites

- JDK 17 or later
- A PostgreSQL database created specifically for this example
- `curl` to call the example endpoint

The application applies the CompileFlow deployment schema and writes process and rollout data. Never point it at a
shared or production database. Supply credentials through environment variables rather than committing them to source
control.

## Run

Create the database, then install and start the example from the repository root. The following values match the local
defaults in `application.yml`; change them for your PostgreSQL installation.

```bash
./mvnw install -pl examples/spring-boot-deployment -Pexamples -am -DskipTests
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/compileflow \
SPRING_DATASOURCE_USERNAME=compileflow \
SPRING_DATASOURCE_PASSWORD=compileflow \
./mvnw spring-boot:run -pl examples/spring-boot-deployment -Pexamples
```

In another terminal, run the lifecycle:

```bash
curl -sS -X POST http://localhost:8080/api/release-lifecycle
```

The JSON response identifies the generated process, the version selected at each stage, the canary routing keys, the
rollout states, and the audit-event counts. The expected final state is:

- the `production` alias points to v1 after rollback;
- the exact v2 execution still returns the v2 marker;
- the v2 canary and rollback finish with `COMPLETED`, while the v3 rollout finishes with `ABORTED`.

Each request uses a new process code, so it can run repeatedly without overwriting earlier results.

## Verify

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/compileflow \
SPRING_DATASOURCE_USERNAME=compileflow \
SPRING_DATASOURCE_PASSWORD=compileflow \
./mvnw test -pl examples/spring-boot-deployment -Pexamples -am \
  -Dtest=DeploymentExampleApplicationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The test runs only when `SPRING_DATASOURCE_URL` begins with `jdbc:postgresql:`. It executes the lifecycle and verifies
publication, deterministic routing, promotion, abort, rollback, exact-version execution, revision changes, and audit
events.
