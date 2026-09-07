# CompileFlow Durable PostgreSQL Example

This test-backed example runs a Durable TBBPM process with PostgreSQL. It covers the following sequence:

1. Start Spring Boot with the Durable starter.
2. Apply the Durable schema to an isolated PostgreSQL database.
3. Verify and prepare an immutable TBBPM process version.
4. Start a run and persist a Timer.
5. Invoke the `DemoChargeAction.charge` Effect.
6. Publish a Wait token through an in-memory Outbox sink that deduplicates by event ID.
7. Complete the Wait and verify that the run succeeds.
8. Start another application context with migrations and workers disabled to validate the existing schema.

The Effect uses the default manual recovery policy. Reconciliation of an external operation with an unknown outcome is
outside the scope of this example.

## Prerequisites

- JDK 17 or later
- Docker, or a PostgreSQL database created specifically for this example

## Verify

Run the test from the repository root. When Docker is available, Testcontainers starts an isolated PostgreSQL instance:

```bash
./mvnw test \
  -pl examples/spring-boot-durable-postgresql \
  -Pexamples -am \
  -Dtest=DurableSampleApplicationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The test passes after the Timer and Effect execute, the Wait is completed, the run reaches `SUCCEEDED`, and the existing
database schema is accepted with automatic migrations disabled. If neither Docker nor an external database is
available, the test is skipped.

To use an external PostgreSQL database, set all three variables before running the same command:

```bash
export COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/compileflow_durable_example
export COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_USERNAME=compileflow
export COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_PASSWORD=change-me
```

Use a dedicated disposable database. The test applies migrations and writes Durable state. It deliberately ignores the
generic Spring datasource environment variables to reduce the risk of connecting to an unintended database.

The in-memory Outbox sink is only a test fixture. A production sink must authenticate its consumers, protect data in
transit, and deduplicate deliveries by `eventId`. Keep database credentials out of source control and restrict database
access to the application identity.
