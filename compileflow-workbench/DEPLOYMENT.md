# CompileFlow Workbench Deployment

Workbench supports a bundled application image and a split Web/Server topology. Both expose one browser origin and use
the same Workbench Server API.

## Architecture

### Bundled Application

```text
Browser
  |
  | HTTPS, authenticated user
  v
Authentication-capable gateway
  |
  | private hop, injected Workbench Server API key
  v
Workbench Server
  |- compiled React SPA
  |- /api/**
  |- /actuator/health
  |- /actuator/health/liveness
  `- /actuator/health/readiness
  |
  v
PostgreSQL
```

`docker/Dockerfile.all-in-one` builds one executable Java process containing the SPA and API. "All-in-one" means one
Workbench application process; PostgreSQL and the production authentication gateway remain external.

The 2.0 release workflow is configured to attach
`compileflow-workbench-all-in-one-<version>.jar`. After a release is published, run it like the split Server JAR, with
the same database, authentication, and gateway configuration:

```bash
java -jar compileflow-workbench-all-in-one-2.0.0.jar \
  --spring.profiles.active=prod
```

The bundled JAR and image contain the same Web build. The separate
`compileflow-workbench-server-<version>.jar` contains no SPA.

### Split

```text
Browser
  |
  v
Authentication-capable gateway
  |- static routes/assets -> Web image
  `- /api/**            -> Workbench Server -> PostgreSQL
```

Use split deployment only when static delivery and Java capacity need separate scaling or release control. The browser
still uses relative same-origin paths.

### Frontend Development

`apps/dev-gateway` is a loopback-only mock of `/api/status` and
`/api/executions/preview`. It is not a production component and cannot proxy Workbench Server.

## Local Evaluation

The Compose files bind published ports to loopback. They are evaluation topologies, not examples of production end-user
authentication.

Local Compose inputs:

| Variable                                                               | Default                   | Applies to      |
| ---------------------------------------------------------------------- | ------------------------- | --------------- |
| `COMPILEFLOW_WORKBENCH_DATABASE_NAME`                                  | `compileflow`             | Both topologies |
| `COMPILEFLOW_WORKBENCH_DATABASE_USERNAME`                              | `compileflow`             | Both topologies |
| `COMPILEFLOW_WORKBENCH_DATABASE_PASSWORD`                              | Required                  | Both topologies |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`           | Required                  | Split topology  |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL` | `workbench-local-gateway` | Split topology  |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED`        | `false`                   | Split topology  |
| `VITE_COMPILEFLOW_DEBUG`                                               | `false`                   | Split Web build |
| `VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES`                               | `false`                   | Split Web build |

These names are Compose interpolation inputs, not another application configuration namespace. Compose maps the database
values to PostgreSQL and standard Spring datasource variables. Its private local-gateway variable is derived from the
required Server API key and is not a separate user input.

Create a database password:

```bash
export COMPILEFLOW_WORKBENCH_DATABASE_PASSWORD='replace-with-a-local-password'
```

Bundled application:

```bash
docker compose -f compileflow-workbench/docker-compose.all-in-one.yml up --build
```

Equivalent from `compileflow-workbench/` (writes a non-empty `.env` database password when unset or blank; accepts only
`up` flags such as `-d`):

```bash
pnpm up:all-in-one
```

If datasource auth fails against an existing Postgres volume after a password change, reset the volume with
`docker compose -f compileflow-workbench/docker-compose.all-in-one.yml down -v`.

Open `http://127.0.0.1:4173`.

This Compose file activates the `dev` profile: API-key authentication is disabled and trusted draft execution is
enabled. Its loopback port is a hard deployment boundary; do not expose this profile or topology publicly.

Split application:

```bash
export COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY="$(openssl rand -hex 32)"
docker compose -f compileflow-workbench/docker-compose.yml up --build
```

Draft execution is disabled by default. For trusted local authoring only, set
`COMPILEFLOW_WORKBENCH_SERVER_CONFIG_PREVIEW_EXECUTION_ENABLED=true`. This endpoint executes submitted Java, scripts,
and Spring actions with Server privileges; it must not be enabled as an anonymous validation service.

Open `http://127.0.0.1:4173`. A dedicated loopback-only nginx gateway routes Web and API traffic and injects the private
server key because this evaluation stack has no external identity gateway. The Web image itself contains no credential
or API proxy. The local gateway does not authenticate human users and must not be exposed publicly. Its fixed `100m`
transport ceiling prevents NGINX's smaller implicit default from overriding the Server contract; the Server's
`max-request-size` remains the authoritative effective limit.

## Production Edge Requirements

The edge must provide all of the following:

- TLS termination and an HTTPS-only public origin;
- end-user authentication and authorization;
- rate limits and request limits appropriate to administrative APIs;
- same-origin routing for Web assets and `/api/**`;
- removal of client-supplied `X-API-Key`, forwarding, and internal identity headers;
- injection of the private Workbench Server API key only after authorization;
- network policy that prevents direct public access to Workbench Server;
- trusted proxy and audit logging configuration;
- an orchestrator termination grace period longer than the configured Spring lifecycle and engine-executor shutdown
  budgets. The provided Compose topologies use 75 seconds for the default budgets.

Plain Kubernetes Ingress provides routing and may terminate TLS; it is not automatically an authentication or
authorization boundary. Use an authentication-capable gateway or pair the router with an identity-aware proxy.

The current Workbench Server API key identifies the gateway service. The configured service principal is the durable
audit actor represented by that credential. Do not forward an arbitrary browser user header and treat it as a verified
identity.

## Workbench Server Configuration

Required production values:

| Variable                                                               | Purpose                                                                       |
| ---------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `SPRING_PROFILES_ACTIVE=prod`                                          | Enables production guardrails                                                 |
| `SPRING_DATASOURCE_URL`                                                | PostgreSQL JDBC URL                                                           |
| `SPRING_DATASOURCE_USERNAME`                                           | Database role                                                                 |
| `SPRING_DATASOURCE_PASSWORD`                                           | Database credential                                                           |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_DATABASE_MIGRATE=false`           | Keep DDL in the deployment pipeline while retaining startup schema validation |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_MODE=API_KEY`      | Private service authentication                                                |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_API_KEY`           | 32..256 URL-safe-character gateway credential                                 |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_AUTHENTICATION_SERVICE_PRINCIPAL` | Stable service audit actor                                                    |

Optional resource policy:

| Variable                                                                   | Default | Purpose                              |
| -------------------------------------------------------------------------- | ------- | ------------------------------------ |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_HTTP_MAX_REQUEST_SIZE`                | `10MB`  | Request body limit                   |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_EXECUTION_LOG_MAX_QUERY_ROWS`         | `10000` | Complete log query/export limit      |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_CONCURRENCY`         | `4`     | Concurrent ProcessEngine invocations |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_QUEUE_CAPACITY`      | `256`   | Local waiting queue capacity         |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_ASYNC_INVOCATION_DISPATCH_BATCH_SIZE` | `50`    | Claim batch size                     |

See [`../docs/en/configuration.md`](../docs/en/configuration.md) for the full strict configuration contract.

Production platforms should supply the database password and API key through their secret manager. For file-mounted
secrets, set
`spring.config.import=configtree:/run/secrets/` and name the files
`spring.datasource.password` and
`compileflow.workbench.server.authentication.api-key`. The Compose files use environment interpolation only because they
are loopback evaluation stacks.

Before a rollout with `DATABASE_MIGRATE=false`, the deployment identity must apply the exact packaged Deploy V1 and
Workbench V2 Flyway migrations. The runtime database role needs DML on the owned tables and read access to
`flyway_schema_history`, but does not need migration DDL. Keep
`spring.flyway.enabled=true`: the Server uses Flyway validation and pending migration detection as a mandatory startup
admission gate even when it does not execute DDL.

## Browser Build Configuration

Production Web builds accept only public `VITE_COMPILEFLOW_*` values. Never put API keys, tokens, database credentials,
or internal URLs in Vite variables; they are embedded in browser assets.

```bash
VITE_COMPILEFLOW_OPERATE_MODE=real \
VITE_COMPILEFLOW_DEBUG=false \
VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES=false \
pnpm --filter @compileflow/workbench-web build
```

Routing is a deployment concern, so the browser has no API base URL variable.

## Build A Bundled JAR

From a source checkout:

```bash
cd compileflow-workbench
pnpm install --frozen-lockfile
pnpm --filter @compileflow/workbench-web build
cd ..
./mvnw package -pl compileflow-workbench-server -am \
  -Pworkbench-bundled -DskipTests
```

The profile fails if `apps/web/dist/index.html` is absent. Its output is
`compileflow-workbench-server/target/compileflow-workbench-all-in-one-<version>.jar`. Before copying the current Web
build, the profile removes the previous
`target/classes/static` tree so an incremental package cannot retain obsolete hashed assets.
`scripts/verify-bundled-web-assets.sh` then requires the JAR's static-file manifest to match `apps/web/dist` exactly.
Delivery and release CI run this check; the presence of `index.html` alone is not sufficient.

## Release-Like Acceptance

`pnpm verify:delivery` builds the production Web bundle, assembles the all-in-one JAR, and checks the cross-stack
contracts. The managed Playwright suite then starts that JAR with the `prod` profile, a real PostgreSQL database,
API-key authentication, and a loopback trusted edge:

```bash
pnpm test:e2e:integration
pnpm test:e2e:process-lifecycle
```

The lifecycle scenario publishes immutable v1 and v2 artifacts, replays a publication idempotency key after the draft
changes, deploys v2 as a canary, selects stable and candidate cohorts with the runtime's deterministic routing hash,
evaluates observed health, promotes, rolls back, and executes the final effective version. It also rejects browser
credential leakage and any failed browser API response.

The process-lifecycle experiment runs a flow for twice its configured ownership lease. It sends `SIGTERM` to prove HTTP
admission closes while the in-flight attempt keeps renewing and commits exactly once, then sends `SIGKILL` to prove a
fresh Server recovers the abandoned lease as the second fenced attempt. The machine-readable result is written to
`apps/web/test-results/async-invocation-process-lifecycle.json` and retained by delivery CI with the Playwright report.

## Health and Readiness

Infrastructure probes call Workbench Server directly on its private network:

```text
/actuator/health/liveness
/actuator/health/readiness
```

The browser-facing `/api/status` reports product availability. It is protected like the rest of `/api/**` and is not a
replacement for platform probes.

The split Web image may expose its own container health endpoint. Do not infer database or engine readiness from static
Web health.

## OpenAPI Contract

The committed description is:

```text
docs/specs/openapi/compileflow-workbench-server.openapi.json
```

After a deliberate Server API change:

```bash
./mvnw test -pl compileflow-workbench-server -am \
  -Dtest=OpenApiContractTest \
  -Dcompileflow.openapi.update=true \
  -Dsurefire.failIfNoSpecifiedTests=false

cd compileflow-workbench
pnpm generate:workbench-server-contract
pnpm check:workbench-server-contract
```

Review route, method, request, response, and schema changes together. Generated TypeScript is committed so downstream
diffs remain visible.

## Production Checklist

- Workbench Server, PostgreSQL, and any credential-injecting internal proxy are not publicly reachable.
- The edge authenticates and authorizes users before forwarding `/api/**`.
- The edge strips user-supplied internal headers and injects its own service credential.
- Server authentication remains `API_KEY`; `DISABLED` is limited to explicit development and test profiles.
- TLS, secure cookies or bearer-token policy, CSP, request limits, and rate limits are tested at the actual edge
  implementation.
- PostgreSQL backups, restore drills, Flyway migration policy, and credential rotation are documented operationally.
- Liveness and readiness probes target Actuator directly.
- Logs and metrics are collected from Workbench Server and the edge.
- The committed OpenAPI snapshot and generated TypeScript are current.
- The bundled image runs one Java process; no hidden Node supervisor exists.

## Extraction Criteria

Do not extract a separate Workbench control-plane service merely because a second client appears. Revisit the boundary
when production evidence shows one or more independent requirements:

- materially different scaling profile;
- distinct availability objective;
- security or compliance isolation;
- independent persistence ownership;
- separate release cadence or team ownership.

Until then, the Workbench Server remains a modular monolith.
