# Workbench Development Gateway

This package is a loopback-only mock of the Workbench Server preview API for frontend development and deterministic
browser tests. It is not a BFF, has no upstream proxy mode, and must not be deployed in production.

## Run

From `compileflow-workbench/`:

```bash
pnpm --filter @compileflow/workbench-dev-gateway dev
```

The gateway binds to `127.0.0.1:3001` by default. Vite proxies development mock requests to it.

| Method | Path                      | Purpose                               |
| ------ | ------------------------- | ------------------------------------- |
| `GET`  | `/health`                 | Development-process liveness          |
| `GET`  | `/api/status`             | Mock execution availability           |
| `POST` | `/api/executions/preview` | Simulated explicit-definition preview |

The process always binds to `127.0.0.1`. Configuration is documented in
[`.env.example`](.env.example). Unknown `COMPILEFLOW_DEV_GATEWAY_*` variables fail startup, and `NODE_ENV=production` is
rejected unconditionally.

Production Workbench traffic uses the same-origin Workbench Server API through the deployment's authentication gateway.
No Node.js process is part of that topology.
