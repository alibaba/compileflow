# Workbench Product Surfaces

CompileFlow Workbench has three user-facing domains. They share one product and HTTP backend while retaining different
data ownership and execution semantics.

| Domain      | Responsibility                                                                                           | State authority                                           |
| ----------- | -------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| **Learn**   | Examples, concepts, and guided draft preview                                                             | Server catalog in real mode; bundled catalog in mock mode |
| **Build**   | BPMN/TBBPM authoring, local workspaces, validation, and draft preview                                    | Browser IndexedDB until explicit publication              |
| **Operate** | Server drafts, immutable versions, aliases, rollouts, published execution, logs, and runtime diagnostics | `compileflow-workbench-server` and PostgreSQL             |

Draft preview executes the exact supplied XML and creates no published state. Operate execution requires an explicit
immutable version or alias. A browser flag never turns a local draft into a published version.

## Contract Sources

- HTTP wire contract:
  [`docs/specs/openapi/compileflow-workbench-server.openapi.json`](../../docs/specs/openapi/compileflow-workbench-server.openapi.json)
- Generated TypeScript wire types:
  [`apps/web/src/shared/contracts/generated/workbenchServerOpenApi.ts`](../apps/web/src/shared/contracts/generated/workbenchServerOpenApi.ts)
- Refined UI domain contracts, compile-time parity, and targeted runtime validation:
  [`apps/web/src/shared/contracts`](../apps/web/src/shared/contracts) and
  [`apps/web/src/shared/api`](../apps/web/src/shared/api)
- Backend implementation: [`compileflow-workbench-server`](../../compileflow-workbench-server)
- Deployment topology: [DEPLOYMENT.md](../DEPLOYMENT.md)

Mock mode and `apps/dev-gateway` exist only for frontend development. They are not production availability or
correctness fallbacks.

## Security Invariants

- Browsers use same-origin relative paths and never hold a shared service key.
- An authentication-capable production gateway authorizes the end user, strips client-supplied internal headers, and
  injects the private Server credential.
- The Workbench Server API key represents the gateway service principal, not an individual user.
- Workbench Server and PostgreSQL are not directly public.
- CORS and plain Ingress routing do not replace authentication.
