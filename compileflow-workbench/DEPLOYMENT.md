# Workbench Deployment

This guide covers local evaluation and production deployment of the Workbench web app with
`compileflow-workbench-server`. For incident procedures, see the repository
[operations playbook](../docs/en/operations-playbook.md). For all configuration properties, see the
[configuration guide](../docs/en/configuration.md).

## Components

| Component                      | Role                                                             |
| ------------------------------ | ---------------------------------------------------------------- |
| `apps/web`                     | Static Workbench frontend.                                       |
| `apps/dev-gateway`             | Loopback development status and preview mock.                    |
| `compileflow-workbench-server` | Java API, persistence, execution worker, and deployment control. |
| PostgreSQL or MySQL            | Supported Server database.                                       |

The frontend and Server must use the same CompileFlow version. The committed OpenAPI description under `docs/specs/openapi`
is the wire authority; generated frontend types and runtime validators must be regenerated together with Server changes.

## Prerequisites

- Java 17, 21, or 25; Java 17 is the build baseline.
- Node.js 24 LTS and the repository-pinned pnpm version.
- PostgreSQL 16, 17, or 18, or MySQL 8.4 for a database-backed Server.
- A supported browser for the web app.

H2 is a test database only. Do not use it to validate a production deployment.

## Build the web app

From this directory:

```bash
pnpm install --frozen-lockfile
pnpm type-check
pnpm build
```

The build produces static web assets. `VITE_COMPILEFLOW_*` values are embedded at build time and visible to browser
users; never store credentials or private keys in them.

For local authoring, start the development gateway using the command in `package.json`. Configure its port and log
level with the documented `COMPILEFLOW_DEV_GATEWAY_*` variables. The gateway is only for local development and testing.

## Run the Server locally

Build the matching Java Server artifact from the repository root, apply the packaged migrations for the selected
database, and start it with the `dev` profile. Server startup fails if the schema, data source, or required authentication
configuration is unavailable.

The local Compose setup is useful for evaluating the full product. Before using an equivalent topology in production,
replace its development credentials, network exposure, and storage settings.

### PostgreSQL Compose storage

Both `docker-compose.yml` and `docker-compose.all-in-one.yml` mount the `compileflow_pg_data` named volume at
`/var/lib/postgresql`. The PostgreSQL 18 image defaults to `PGDATA=/var/lib/postgresql/18/docker`, so a new database
is stored under `18/docker` inside that volume. PostgreSQL 18 and later use a version-specific data directory and
therefore require the parent mount.

Use a dedicated named volume and mount it at `/var/lib/postgresql`, not at the version-specific data directory. Both
templates use the same volume key within a Compose project, so switching templates continues to use the same database.
Run `docker compose down` to stop the stack while retaining data. The `-v` option permanently deletes the volume and
must be used only when the local database is intentionally disposable.

## Production topology

Use a private network between the browser gateway and Server. Put TLS termination, authentication, authorization, rate
limits, and request-size limits at the trusted ingress. The Server API key authenticates the gateway service; it does
not identify individual users.

The minimum production arrangement is:

1. Serve the built web assets from a static server or trusted gateway.
2. Run one or more matching Workbench Server instances with one configured database.
3. Apply and verify the exact packaged migrations before enabling traffic.
4. Configure the Server API key or trusted authentication integration through environment or secret management.
5. Restrict access to the database and deployment data to the Server roles that need it.
6. Expose health and metrics only to the monitoring network.

For multiple Server instances, use one shared supported database and configure deployment and runtime roles
consistently. Do not combine PostgreSQL and MySQL schemas in one Server installation.

## Authentication and browser configuration

The browser must never receive the shared Server API key. Do not expose it in browser JavaScript, build-time variables,
local storage, or source control. A trusted gateway authenticates users, strips client-controlled `X-API-Key`,
`Authorization`, and forwarded identity headers, and injects the private Server credential only on the upstream request.
Use TLS and rotate the credential through secret management.

Do not trust an actor, user, or role supplied only by a browser header. Per-user authorization requires a trusted
authenticated gateway or an application-specific integration that establishes identity before the request reaches the
Server.

Browser requests use relative, same-origin paths; there is no public API base URL build variable. Configure production
upstream routing at the trusted gateway. `VITE_COMPILEFLOW_DEBUG` controls browser debug logging and should remain
disabled in production.

## Health checks

Before admitting traffic, verify:

- the Server process becomes ready only after Flyway validates the database schema;
- Flyway reports the expected migration baseline with no pending migrations;
- engine preflight and one representative execution succeed;
- deployment control-plane and runtime health are available for the enabled roles;
- asynchronous invocation health has no unexplained dead letters or expired leases;
- logs, metrics, and alerts do not contain process payloads, routing keys, API keys, or lease tokens.

After a publish or rollout, verify both the control-plane revision and the effective version observed by a real execution. In distributed mode, runtime readiness is asynchronous; a successful control-plane command alone does not prove that every worker has installed the version.

## Configuration ownership

Keep configuration in the layer that owns it:

| Concern                         | Reference                                                                         |
| ------------------------------- | --------------------------------------------------------------------------------- |
| Engine and Deploy properties    | [Configuration guide](../docs/en/configuration.md)                                |
| Server properties and auth mode | [Server guide](../compileflow-workbench-server/README.md) and configuration guide |
| Web build-time inputs           | `apps/web/.env.example` and the configuration guide                               |
| Gateway development inputs      | `apps/dev-gateway/.env.example`                                                   |
| HTTP fields                     | committed OpenAPI description under `docs/specs/openapi`                          |

Keep deployment manifests aligned with these configuration sources. The generated OpenAPI document defines the wire
contract between Workbench Web and Server; the Workbench HTTP API is intended for this product integration rather than
as a general-purpose engine API.

## Operational boundaries

- Publish immutable process versions; do not edit stored version content.
- Treat stale route revisions as concurrency failures and reread authority before retrying.
- Do not bypass schema admission, authentication, digest verification, or local-ready checks.
- Schema validation is fail-closed; the Server does not become ready with pending or inconsistent migrations.
- Database backups and retention are owned by the database operations policy.

For canary, promotion, abort, rollback, outbox, and asynchronous invocation procedures, use the
[operations playbook](../docs/en/operations-playbook.md) and [hot-deployment guide](../docs/en/hot-deploy.md).
