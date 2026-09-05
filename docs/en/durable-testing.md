# Durable Kernel Store Testing

`compileflow-durable-testkit` contains the executable first-party contract for the atomic `DurableStore`
provider protocol. It depends only on `durable-spi`, preserving `runtime -> spi <- postgres`, but is not a generic
persistence abstraction or a promise that third-party Stores are Supported. Runtime collaborators consume narrow
semantic Store roles; the first-party PostgreSQL provider owns full transaction composition. Contract tests verify
observable state transitions, while the PostgreSQL module separately locks its current V1 seven-table layout.

The Provider SPI and Kernel evolve as one strong contract. A release that adds an authoritative persistence transition
may add a required Store method; it must not hide missing capability behind a default method that throws
`UnsupportedOperationException` at runtime. A Provider must update explicitly and pass the current complete testkit
before it can claim compatibility with that CompileFlow version. Build/startup failure is preferred to partial runtime
support.

## Contract coverage

`DurableStoreContract` currently proves:

- immutable and idempotent Process registration;
- simple Start overloads allocate a new Run ID; caller-identified overloads preserve the supplied ID and reject reuse;
- Wait commit, token Trigger, resume, terminal commit, and stale Run-lease rejection;
- Pause/Resume/Cancel orthogonality;
- Timer firing and Effect completion as committed resume facts;
- stable Outbox event identity/type/payload, retry attempt counting, and stale token fencing;
- bounded batch lease renewal, partial authority loss, whole-millisecond duration validation, and crash recovery for
  Run, Effect, and Outbox claims;
- occurrence revisions, terminal retention, and run-first race/deadlock safety.

The Runtime renewal suite separately proves independent Run/Effect/Outbox lanes, bounded chunk continuation, retry
after unknown Store failures, stalled-call detection, and health degradation/recovery. The Spring suite proves that
graceful shutdown stops claiming and drains in-flight work without interruption.

Release evidence must additionally cover migration constraints, query plans/indexes, concurrency under contention,
least-privilege database roles, backup/restore, and supported PostgreSQL versions.

Each first-party PostgreSQL contract test calls `createEmptyStore()`; test methods never share state.

## First-party PostgreSQL paths

With Docker, the normal Maven suite runs
`PostgresDurableStoreContractTest` through Testcontainers:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgres \
  -am
```

Without Docker, supply an expendable PostgreSQL database. The tests clean and migrate its public schema before every
case. The wildcard also runs the process-level ACK-loss test: a child JVM fsyncs an accepted `WAIT_COMMITTED` event,
is forcibly killed before Outbox completion, and recovery must replay the same logical event before committing
DELIVERED.

```bash
export COMPILEFLOW_DURABLE_POSTGRES_URL='jdbc:postgresql://127.0.0.1:5432/compileflow_test'
export COMPILEFLOW_DURABLE_POSTGRES_USER='postgres'
export COMPILEFLOW_DURABLE_POSTGRES_PASSWORD='postgres'

./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgres \
  -am \
  -Dtest='LocalPostgresDurable*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Never point this path at a database containing valuable data.

## Required race evidence

Production release evidence must cover at least:

1. two workers claiming the same Run/Effect/Outbox row;
2. lease expiry followed by replacement claim and late old-token completion;
3. Trigger versus Cancel;
4. Pause versus in-flight Segment and Effect;
5. Timer firing versus Cancel;
6. Effect completion versus operator resolution;
7. Outbox completion versus retry/abandon;
8. concurrent Starts producing distinct valid Run IDs;
9. external durable acceptance followed by process `SIGKILL` before Outbox completion, then exact-event replay.

The invariant is always one committed winner. Worker timing is not authority.

## What the contract intentionally does not test

The Store contract does not promise:

- compatibility across application builds or runtime/resolution versions;
- Alias resolution or rollout;
- generated program/bytecode persistence;
- custom persisted codecs;
- exactly-once remote effects;
- exactly-once generic external Outbox side effects;
- a portable alternative-Store contract.

Those would expand Kernel identity without improving the atomic state-machine contract.

## Release evidence

Before release, retain:

- Maven and PostgreSQL version;
- migration checksum and schema-contract result;
- inherited Kernel Store contract result;
- race/stress duration and seed;
- PITR drill result;
- query-plan evidence for claim and operator indexes;
- proof that stale tokens changed no rows;
- process-level ACK-loss evidence preserving Outbox event ID, type, and logical payload across replay.

See the [Durable architecture](../architecture/10-DURABLE_ARCHITECTURE.en.md).
