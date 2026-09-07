# Contributing to CompileFlow Workbench

CompileFlow Workbench follows the repository-wide [contributing guide](../CONTRIBUTING.md),
[code of conduct](../CODE_OF_CONDUCT.md), and [security policy](../SECURITY.md).

The rules below cover only Workbench-specific development expectations.

## Prerequisites

- Node.js 24 LTS (use the version pinned by `.node-version`)
- pnpm 11.11.0 (pinned by the root `packageManager` field)
- Run commands from `compileflow-workbench/`

Use pnpm only. Do not add npm or yarn lockfiles.

## Local Development

```bash
cd compileflow-workbench
pnpm install
pnpm dev:with-gateway
```

That starts Vite and the loopback development gateway together. Use
`pnpm dev` when you only need the web process. The development gateway is a deterministic mock; it cannot proxy
Workbench Server and refuses
`NODE_ENV=production`.

For the bundled evaluation stack (without frontend HMR), run `pnpm up:all-in-one` and open
`http://127.0.0.1:4173`. The script supplies a local database password when `.env` leaves it blank. Use a dedicated
Compose volume and follow the storage rules in [`DEPLOYMENT.md`](DEPLOYMENT.md).

Start `compileflow-workbench-server` from the repository root when validating Operate real mode:

```bash
./mvnw package -pl compileflow-workbench-server -am -DskipTests
export SPRING_DATASOURCE_PASSWORD='your-local-postgres-password'
java -jar compileflow-workbench-server/target/compileflow-workbench-server-2.0.0-SNAPSHOT.jar \
  --spring.profiles.active=dev
```

The dev profile expects a local PostgreSQL database; see the
[`compileflow-workbench-server` quick start](../compileflow-workbench-server/README.md).

## Required Checks

Run the narrowest check that proves your change, then use the delivery gate for cross-cutting changes.

```bash
pnpm --filter @compileflow/workbench-web type-check
pnpm --filter @compileflow/workbench-dev-gateway type-check
pnpm --filter @compileflow/workbench-dev-gateway test
pnpm --filter @compileflow/workbench-web test:e2e:smoke
pnpm verify:delivery
```

For documentation-only changes, run the repository hygiene check from the repository root:

```bash
python3 scripts/check_internal_links.py
```

## Architecture Boundaries

- `shared` may not depend on Learn, Build, Operate, or Shell implementation details.
- API contracts belong in `apps/web/src/shared/contracts`.
- Transport clients belong in `apps/web/src/shared/api` or the owning domain API folder.
- Page components should not own low-level HTTP or WebSocket details.
- Browser clients use relative same-origin paths. Vite chooses the local mock or real Server target; production routing
  belongs to the trusted edge.
- Never expose credentials through `VITE_*`. The production gateway injects its private Workbench Server credential
  after user authorization.

## Documentation Updates

Update the relevant docs when changing routes, environment variables, scripts, Docker behavior, development-gateway
behavior, or Workbench API contracts:

- [README.md](README.md)
- [DEPLOYMENT.md](DEPLOYMENT.md)
- [apps/dev-gateway/README.md](apps/dev-gateway/README.md)
- [apps/web/src/operate/API_SPEC.md](apps/web/src/operate/API_SPEC.md)
- [docs/PRODUCT_SURFACES.md](docs/PRODUCT_SURFACES.md)
- [docs/architecture/WEB_ARCHITECTURE.md](docs/architecture/WEB_ARCHITECTURE.md)
