# CompileFlow Workbench Server

`compileflow-workbench-server` is the Java backend for CompileFlow Workbench. It provides draft preview and storage,
publication, published execution, deployment control, persisted asynchronous invocation, monitoring, and the Learn
catalog.

The Server API is designed for Workbench, not as a general remote interface to the CompileFlow engine. Business
applications embed CompileFlow with `compileflow-spring-boot-starter` and call its in-process Java API. Workbench Server
is optional and is not required by the embedded engine.

## Runtime Boundary

Workbench Server is a separate Maven module and Spring Boot process. Production does not require a Node.js BFF:

```text
Browser -> authentication-capable gateway -> Workbench Server -> PostgreSQL or MySQL
```

The edge owns end-user authentication and authorization, TLS, request and rate limits, same-origin routing, and removal
of client-supplied internal headers. After authorization it injects the private Workbench Server API key. Network
routing alone is not an authentication mechanism.

The API key authenticates the trusted gateway service. Its configured service principal is the audit actor; it does not
prove an individual browser user's identity. Do not turn an unsigned user header into an audit principal.

## Quick Start

From the repository root, build with Java 17 or newer:

```bash
./mvnw install -pl compileflow-workbench-server -am -DskipTests
```

Start PostgreSQL 16, 17, or 18 for the default `dev` profile. The repository Compose configuration provides a suitable
local instance:

```bash
cd compileflow-workbench
export COMPILEFLOW_WORKBENCH_DATABASE_PASSWORD='your-local-postgres-password'
docker compose up -d postgres
cd ..
```

Then start the Server:

```bash
export SPRING_DATASOURCE_PASSWORD='your-local-postgres-password'
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

The `dev` profile binds to PostgreSQL and explicitly disables API-key authentication. It is not a production profile and
requires a non-empty database password.

For production, configure the database and authentication explicitly. This example uses PostgreSQL; select `MYSQL` and
provide the corresponding JDBC URL when using MySQL:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/compileflow
export SPRING_DATASOURCE_USERNAME=compileflow
export SPRING_DATASOURCE_PASSWORD='replace-with-a-secret'
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_PROVIDER=POSTGRESQL
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE=API_KEY
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY="$(openssl rand -hex 32)"
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL=workbench-gateway
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar
```

`API_KEY` is fail-closed and is the default outside explicit `dev` or `test`
profiles. The server does not provide a production CORS bypass; deploy Web and API under one origin through the trusted
edge.

## Execution Semantics

Two execution paths have separate contracts:

- `POST /api/executions/preview` executes explicit draft XML without saving, publishing, versioning, or alias routing.
- `POST /api/processes/{code}/execute` executes an existing published definition by explicit immutable version or alias.

Learn and Designer use preview. Operate uses published execution. Neither path silently falls back to the other.

## Persistence

Flyway is the schema authority. It runs before JPA, while Hibernate uses `ddl-auto=validate`. Select PostgreSQL or MySQL
with `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_PROVIDER=POSTGRESQL|MYSQL`. The Server applies separate Deploy and
Workbench migration sets from the matching locations:
`db/compileflow-deploy/{postgres|mysql}/migration` and
`db/compileflow-workbench-server/{postgres|mysql}/migration`. Neither location is under Flyway's default discovery root. A
production deployment may apply them with a separate DDL identity and set
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE=false`; startup still validates Flyway checksums and rejects
pending migrations. Do not disable
`spring.flyway.enabled`, because that would bypass schema admission. The executable artifact supports PostgreSQL and
MySQL 8.4; H2 is test-scoped and is not a production fallback.

Workbench draft tables and deployment control-plane tables are separate data domains, even when one Server process hosts
both. Publication creates an immutable source version; alias changes and rollout events use the deployment service and
transactional outbox.

Workbench Server records completed executions synchronously before returning the response. This keeps dashboards and
canary samples complete even when a best-effort event queue is saturated. Listener failures do not change the process
result, so the execution log is not an exactly-once audit ledger.

## HTTP Contract

The canonical machine-readable contract is
[`docs/specs/openapi/compileflow-workbench-server.openapi.json`](../docs/specs/openapi/compileflow-workbench-server.openapi.json).

It is generated from a real Spring application context. Springdoc is test-scoped, so production does not expose Swagger
UI or `/v3/api-docs`.

Regenerate after changing a controller or transport contract:

```bash
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=OpenApiContractTest \
  -Dcompileflow.openapi.update=true \
  -Dsurefire.failIfNoSpecifiedTests=false

cd compileflow-workbench
pnpm generate:workbench-server-contract
pnpm check:workbench-server-contract
```

Workbench uses generated wire types, refined domain contracts, runtime response validation, and compile-time parity
assertions. Contract changes must keep routes, methods, statuses, request and response types, and schemas synchronized.

## Health

Platform probes call the private server directly:

```text
/actuator/health/liveness
/actuator/health/readiness
```

Only `/actuator/health`, `/actuator/health/liveness`, and
`/actuator/health/readiness` are exempt from the service API key. Browser-facing `/api/status` is protected like every
other `/api/**` operation.

## Distribution

Workbench provides:

- a bundled image containing the compiled SPA in this executable Java process;
- split Web and Workbench Server images behind one public origin.

The database and production authentication gateway remain external in both forms. The bundled image does not run
Node.js or supervise multiple application processes. See
[`compileflow-workbench/DEPLOYMENT.md`](../compileflow-workbench/DEPLOYMENT.md).

## Verification

Use targeted checks:

```bash
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=OpenApiContractTest,PreviewExecutionControllerTest,PreviewExecutionServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw checkstyle:check -pl compileflow-workbench-server -am
```

Workbench Server tests cover persistence and migration behavior for PostgreSQL and MySQL.
