# CompileFlow Workbench

CompileFlow Workbench provides browser-based tools for learning, visual process design, validation, publication, and
operations. It includes three areas:

- **Learn**: runnable TBBPM and BPMN examples;
- **Build**: visual authoring, validation, simulation, and draft preview;
- **Operate**: draft storage, publication, version routing, execution logs, and deployment operations.

Implement Agent calls as Java Actions to compose them with service operations and business rules in the same process.

The deployable product combines the React application in this pnpm workspace with the sibling
[`compileflow-workbench-server`](../compileflow-workbench-server/) Spring Boot service.

Workbench is optional. It is not required by the embedded Java engine, and its development gateway is not a production
authentication or API boundary.

## Architecture

```text
Production:
Browser -> trusted authentication gateway -> Web + Workbench Server -> PostgreSQL or MySQL

Frontend development:
Vite -> loopback dev-gateway preview mock
```

The Node development gateway is not a production backend-for-frontend (BFF). Production browser calls use same-origin
`/api/**` routes to Workbench Server through the deployment gateway. See
[Product Surfaces](docs/PRODUCT_SURFACES.md) and
[`DEPLOYMENT.md`](DEPLOYMENT.md).

Workbench distributions include both the split `compileflow-workbench-server-<version>.jar` and the single-process
`compileflow-workbench-all-in-one-<version>.jar`.

## Requirements

- Node.js 24 LTS (use the repository-pinned version)
- pnpm 11.11.0
- Java 17 or newer for Workbench Server
- PostgreSQL 16, 17, or 18, or MySQL 8.4 for Workbench Server persistence

## Development

```bash
pnpm install

# Web + loopback development gateway (Open http://127.0.0.1:5173)
pnpm dev:with-gateway
```

Or start the two processes yourself:

```bash
pnpm --filter @compileflow/workbench-dev-gateway dev
pnpm --filter @compileflow/workbench-web dev
```

The optional `COMPILEFLOW_DEV_GATEWAY_PORT` value must be present in both processes when overriding the default `3001`
port.

For a local all-in-one stack (PostgreSQL + bundled SPA/API), see
[`DEPLOYMENT.md`](DEPLOYMENT.md) or run `pnpm up:all-in-one` and open
`http://127.0.0.1:4173`.

For the real backend, from the repository root:

```bash
./mvnw package -pl compileflow-workbench-server -am -DskipTests
export SPRING_DATASOURCE_PASSWORD='your-local-postgres-password'
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

The dev profile expects a local PostgreSQL database; follow the
[Server quick start](../compileflow-workbench-server/README.md) to provision it.

Then start Web in real mode:

```bash
VITE_COMPILEFLOW_OPERATE_MODE=real pnpm --filter @compileflow/workbench-web dev
```

Vite routes mock mode to the configured loopback development-gateway port. Real mode defaults to `127.0.0.1:8080`;
`COMPILEFLOW_API_PROXY_TARGET` can override that development proxy target. Browser code always uses relative paths.

## Commands

```bash
pnpm dev:with-gateway
pnpm up:all-in-one
pnpm type-check
pnpm lint
pnpm test:web
pnpm --filter @compileflow/workbench-dev-gateway test
pnpm --filter @compileflow/workbench-web test:e2e:smoke
pnpm build:web
pnpm check:workbench-server-contract
pnpm verify:delivery
```

Use pnpm for all workspace commands.

## Execution Semantics

Learn and Build preview the current XML through
`POST /api/executions/preview`. Preview does not persist, publish, or route a definition.

Operate executes immutable published versions selected explicitly by version or alias. The UI does not choose a
fallback alias.

## BPMN Authoring Boundary

The BPMN designer preserves embedded subprocess hierarchy, including container-local nodes and sequence flows. It rejects
event subprocesses, cross-container edges, and trigger entries inside a subprocess. Browser simulation stops at an
embedded subprocess; use Workbench Server preview to verify compiled execution. See the
[node support matrix](../docs/en/node-support.md).

## Configuration

| Namespace                               | Scope                      |
| --------------------------------------- | -------------------------- |
| `VITE_COMPILEFLOW_*`                    | Public Web build inputs    |
| `COMPILEFLOW_DEV_GATEWAY_*`             | Loopback development mock  |
| `compileflow.workbench.server.*`        | Workbench Server           |
| `COMPILEFLOW_WORKBENCH_SERVER_CONFIG_*` | Server environment mapping |

Secrets must never use `VITE_*`. The full contract is documented in
[`../docs/en/configuration.md`](../docs/en/configuration.md).

## Project Layout

```text
apps/
├── web/          React application
└── dev-gateway/  local status and preview mock

docker/
├── Dockerfile.all-in-one
├── Dockerfile.local-gateway
├── Dockerfile.web
├── Dockerfile.workbench-server
├── nginx.local-gateway.conf.template
└── nginx.web.conf
```

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md), the root contribution guide, and the
Web [architecture guide](docs/architecture/WEB_ARCHITECTURE.md) before changing cross-surface contracts.
