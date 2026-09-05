# CompileFlow Workbench Server

`compileflow-workbench-server` is the Java backend of CompileFlow Workbench. It hosts draft preview, flow storage,
publication, published execution, deployment control, persisted asynchronous invocation, monitoring, and the Learn
catalog.

It is a Workbench product component, not a general remote facade for embedding CompileFlow. Business applications should
use `compileflow-spring-boot-starter` and call the in-process Java API. The server is optional and is not required by
the embedded engine.

## Runtime Boundary

Workbench Server remains an independent Maven module and Spring Boot process. Production does not require a Node.js BFF:

```text
Browser -> authentication-capable gateway -> Workbench Server -> PostgreSQL
```

The edge owns end-user authentication and authorization, TLS, request and rate limits, same-origin routing, and removal
of client-supplied internal headers. After authorization it injects the private Workbench Server API key. Plain
Kubernetes Ingress is not an authentication mechanism.

The API key authenticates the trusted gateway service. Its configured service principal is the audit actor; it does not
prove an individual browser user's identity. Do not turn an unsigned user header into an audit principal.

## Quick Start

Build with Java 17 or newer:

```bash
./mvnw install -pl compileflow-workbench-server -am -DskipTests
```

Start a local PostgreSQL 16, 17, or 18 instance. PostgreSQL 18.6 is recommended for new deployments:

```bash
export SPRING_DATASOURCE_PASSWORD='your-local-postgres-password'
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

The `dev` profile binds to PostgreSQL and explicitly disables API-key authentication. It is not a production profile and
requires a non-empty database password.

For a production process:

```bash
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/compileflow
export SPRING_DATASOURCE_USERNAME=compileflow
export SPRING_DATASOURCE_PASSWORD='replace-with-a-secret'
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

Flyway is the only schema authority. It runs before JPA, while Hibernate uses
`ddl-auto=validate`. By default the Server applies the packaged Deploy and Workbench migrations by explicitly selecting
`db/compileflow-deploy/migration` and
`db/compileflow-workbench-server/migration`; neither library location is under Flyway's default discovery root. A
production deployment may apply them with a separate DDL identity and set
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE=false`; startup still validates Flyway checksums and rejects
pending migrations. Do not disable
`spring.flyway.enabled`, because that would bypass schema admission. The executable artifact packages PostgreSQL
support; H2 is test-scoped and is not a production fallback.

Workbench draft tables and deployment control-plane tables remain separate bounded contexts even when one Workbench
Server process hosts both. Publication freezes an immutable source version; alias changes and rollout history use the
deployment facade and transactional outbox.

Workbench Server persists terminal execution observations synchronously before the request returns. This avoids silently
biasing dashboards and canary samples when a best-effort event queue is saturated. Listener failures remain isolated
from process outcomes, so this operational log is not an exactly-once audit ledger.

## HTTP Contract

The canonical machine-readable contract is
[`docs/specs/openapi/compileflow-workbench-server.openapi.json`](../docs/specs/openapi/compileflow-workbench-server.openapi.json).

It is generated from a real Spring application context. Springdoc is test-scoped, so production does not expose Swagger
UI or `/v3/api-docs`.

Regenerate after an intentional controller or transport change:

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
assertions. Review route, method, status, request, response, and schema changes together.

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

PostgreSQL and the production authentication gateway remain external in both forms. The bundled image does not supervise
Node.js or multiple application processes. See
[`compileflow-workbench/DEPLOYMENT.md`](../compileflow-workbench/DEPLOYMENT.md).

## Verification

Use targeted checks:

```bash
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=OpenApiContractTest,PreviewExecutionControllerTest,PreviewExecutionServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw checkstyle:check -pl compileflow-workbench-server -am
```

PostgreSQL-specific persistence and migration behavior is validated in the Workbench Server CI workflow.
