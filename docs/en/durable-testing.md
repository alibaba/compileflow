# Durable Kernel Store Testing

`compileflow-durable-testkit` contains the executable first-party contract for the atomic `DurableStore`
provider protocol. It depends only on `compileflow-durable-spi`, preserving `runtime -> spi <- postgres|mysql`, but is not a generic
persistence abstraction or a promise that third-party Stores are Supported. Runtime collaborators consume narrow
semantic Store roles; each first-party Provider owns full transaction composition. Contract tests verify
observable state transitions, while each Provider module separately locks its native V1 seven-table layout.

The Provider SPI and Kernel form one strong contract. An authoritative persistence transition may require a Store method;
missing capability must not be hidden behind a default method that throws `UnsupportedOperationException` at runtime. Every
Provider must implement the complete contract and pass the testkit. Build/startup failure is preferred to partial
runtime support.

## Contract coverage

`DurableStoreContract` proves:

- immutable and idempotent Process registration;
- Start preserves the caller-supplied Run ID and rejects reuse;
- Wait commit, token-based completion, resume, terminal commit, and stale Run-lease rejection;
- Pause/Resume/Cancel orthogonality;
- Timer firing and Effect completion as committed resume facts;
- stable Outbox event identity/type/payload, retry attempt counting, and stale token fencing;
- bounded batch lease renewal, partial authority loss, whole-millisecond duration validation, and crash recovery for
  Run, Effect, and Outbox claims;
- occurrence revisions, terminal retention, and run-first race/deadlock safety.

The Runtime renewal suite separately proves independent Run/Effect/Outbox lanes, bounded chunk continuation, retry
after unknown Store failures, stalled-call detection, and health degradation/recovery. The Spring suite proves that
graceful shutdown stops claiming and drains in-flight work without interruption.

Provider verification must additionally cover migration constraints, query plans/indexes, concurrency under contention,
least-privilege database roles, backup/restore, and every supported PostgreSQL/MySQL version.

Each first-party Provider contract test calls `createEmptyStore()`; test methods never share state.

## First-party Provider paths

With Docker, the normal Maven suite runs
`PostgresDurableStoreContractTest` through Testcontainers:

```bash
JAVA_HOME=/path/to/jdk-21 ./mvnw test \
  -pl compileflow-durable/compileflow-durable-postgresql \
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
  -pl compileflow-durable/compileflow-durable-postgresql \
  -am \
  -Dtest='LocalPostgresDurable*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Never point this path at a database containing valuable data.

The MySQL 8.4 contract is independently implemented and executed through Testcontainers:

```bash
./mvnw test \
  -pl compileflow-durable/compileflow-durable-mysql \
  -am
```

To test an existing disposable MySQL database, set `COMPILEFLOW_DURABLE_MYSQL_URL`,
`COMPILEFLOW_DURABLE_MYSQL_USER`, and `COMPILEFLOW_DURABLE_MYSQL_PASSWORD`. Use
`COMPILEFLOW_DURABLE_MYSQL_MIGRATION_USER` and `COMPILEFLOW_DURABLE_MYSQL_MIGRATION_PASSWORD`
for the separate migration identity. The test cleans the database before each case and uses a non-UTC session;
never point it at a database containing valuable data. The migration identity owns DDL and trigger creation;
the runtime identity does not need `SUPER` or a global trust-setting change.

```bash
./mvnw test \
  -pl compileflow-durable/compileflow-durable-mysql \
  -am \
  -Dtest=LocalMySqlDurableStoreContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

## Required race coverage

Supported Provider verification must cover at least:

1. two workers claiming the same Run/Effect/Outbox row;
2. lease expiry followed by replacement claim and late old-token completion;
3. Wait completion versus Cancel;
4. Pause versus in-flight Segment and Effect;
5. Timer firing versus Cancel;
6. Effect completion versus operator resolution;
7. Outbox completion versus retry/abandon;
8. concurrent Starts producing distinct valid Run IDs;
9. external durable acceptance followed by process `SIGKILL` before Outbox completion, then exact-event replay.

The invariant is always one committed winner. Worker timing is not authority.

## Out of scope

The Store contract does not promise:

- compatibility across application builds or runtime/resolution versions;
- Alias resolution or rollout;
- generated program/bytecode persistence;
- custom persisted codecs;
- exactly-once remote effects;
- exactly-once generic external Outbox side effects.

Those would expand Kernel identity without improving the atomic state-machine contract.

## Verification records

The shared [production-drill evidence schema](../specs/compileflow-durable-production-drill-evidence-v1.schema.json)
certifies PostgreSQL campaigns only: it requires PostgreSQL system and timeline identity. MySQL contract-test results
do not substitute for provider-specific failover, restore, and production-capacity evidence.

Verification records include:

- Maven and exact database Provider/version;
- migration checksum and schema-contract result;
- inherited Kernel Store contract result;
- race/stress duration and seed;
- PITR drill result;
- query-plan evidence for claim and operator indexes;
- proof that stale tokens changed no rows;
- process-level ACK-loss evidence preserving Outbox event ID, type, and logical payload across replay.

See the [Durable architecture](architecture/durable-architecture.md).
