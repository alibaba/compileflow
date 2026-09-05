# CompileFlow Workbench

CompileFlow Workbench is the browser authoring and operations product for CompileFlow. It combines:

- **Learn**: runnable TBBPM and BPMN examples;
- **Build**: visual authoring, validation, simulation, and draft preview;
- **Operate**: flow persistence, publication, version routing, execution logs, and deployment operations.

The production product consists of the React SPA in this pnpm workspace and the sibling
[`compileflow-workbench-server`](../compileflow-workbench-server/) Spring Boot application.

Workbench is optional. It is not required by the embedded Java engine, and its development gateway is not a production
authentication or API boundary.

## Architecture

```text
Production:
Browser -> authentication gateway -> Web + Workbench Server -> PostgreSQL

Frontend development:
Vite -> loopback dev-gateway preview mock
```

The Node development gateway is not a production BFF. Production browser calls use same-origin `/api/**` routes directly
to Workbench Server through the deployment gateway. See [Product Surfaces](docs/PRODUCT_SURFACES.md) and
[`DEPLOYMENT.md`](DEPLOYMENT.md).

The 2.0 release workflow is configured to attach both the split
`compileflow-workbench-server-<version>.jar` and the single-process
`compileflow-workbench-all-in-one-<version>.jar` to a published release.

## Requirements

- Node.js 24.18.0 (the repository-pinned version)
- pnpm 11.11.0
- Java 17 or newer for Workbench Server
- PostgreSQL 16, 17, or 18 for persistent real mode; PostgreSQL 18.6 is recommended for new deployments

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

For a full local evaluation stack (Postgres + bundled SPA/API), see
[`DEPLOYMENT.md`](DEPLOYMENT.md) or run `pnpm up:all-in-one` and open
`http://127.0.0.1:4173`.

For the real backend, from the repository root:

```bash
./mvnw package -pl compileflow-workbench-server -am -DskipTests
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

Then start Web in real mode:

```bash
VITE_COMPILEFLOW_OPERATE_MODE=real pnpm --filter @compileflow/workbench-web dev
```

Vite routes mock mode to the configured loopback development-gateway port and real mode to `127.0.0.1:8080`. Browser
code always uses relative paths.

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

Use pnpm only.

## Execution Semantics

Learn and Designer preview the current XML through
`POST /api/executions/preview`. Preview does not persist, publish, or route a definition.

Operate executes published immutable artifacts and requires an explicit version or alias. The UI never invents a
fallback alias.

## Authoring Boundary

The BPMN designer preserves and edits embedded subprocess hierarchy, including container-local nodes and sequence flows.
It rejects event subprocesses, cross-container edges, and trigger entries inside a subprocess instead of flattening or
dropping their semantics. Browser simulation stops at an embedded subprocess; use Workbench Server draft execution to
verify generated runtime behavior. See the [node support matrix](../docs/en/node-support.md).

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
