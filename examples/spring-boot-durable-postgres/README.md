# CompileFlow Durable PostgreSQL Example

This runnable example demonstrates the documented Durable TBBPM strict profile end to end:

1. start Spring Boot with the opt-in Durable starter;
2. migrate an isolated PostgreSQL database;
3. verify and prepare one immutable TBBPM Version;
4. start a Run and persist a Timer boundary;
5. execute a demo governed Effect (`payments.charge@v1`);
6. optionally simulate a provider timeout and reconcile the UNKNOWN outcome;
7. deliver a Wait token through an idempotent Outbox sink;
8. complete the exact Wait occurrence and observe terminal success;
9. verify that a host application's default Flyway history coexists with Durable's
   `compileflow_durable.cf_durable_schema_history`, then restart with Durable DDL disabled and validate the exact
   schema.

Run the Testcontainers-backed contract from the repository root:

```bash
./mvnw test \
  -pl examples/spring-boot-durable-postgres \
  -Pexamples -am \
  -Dtest=DurableSampleApplicationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

By default, Docker is required and its absence is a test failure. To run the same contract against a freshly created,
isolated local PostgreSQL database, set all three dedicated variables
`COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_URL`,
`COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_USERNAME`, and
`COMPILEFLOW_DURABLE_EXAMPLE_TEST_POSTGRES_PASSWORD`. The test deliberately does not consume generic application
datasource variables. Never point it at a shared or production database; it applies migrations and writes Durable state.

The in-memory Outbox sink is a development fixture only. Production applications must deliver Outbox events to an
authenticated downstream consumer that deduplicates by `eventId`. Transport, database, and secret protection remain
host deployment responsibilities; they are not Durable Process identity or recovery inputs.
