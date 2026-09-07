# Workbench Product Surfaces

CompileFlow Workbench has three user-facing areas. They share one application and HTTP backend, with distinct data
ownership and execution behavior.

| Domain      | Responsibility                                                                                           | State authority                                             |
| ----------- | -------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| **Learn**   | Examples, concepts, and guided draft preview                                                             | Server catalog in real mode; bundled catalog in mock mode   |
| **Build**   | BPMN/TBBPM authoring, local workspaces, validation, and draft preview                                    | IndexedDB for local drafts; Server for Operate-bound drafts |
| **Operate** | Server drafts, immutable versions, aliases, rollouts, published execution, logs, and runtime diagnostics | `compileflow-workbench-server` and its configured database  |

Draft preview executes the supplied XML without creating a published version. Operate execution selects an immutable
version directly or through an alias.

Opening a draft from Operate binds the designer to that Server draft. Save updates it with an expected revision;
it does not create a local workspace copy or publish a Version.

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

Mock mode and `apps/dev-gateway` support frontend development and testing only. Production does not use them.

## Security Invariants

- Browsers use same-origin relative paths and never hold a shared service key.
- An authentication-capable production gateway authorizes the end user, strips client-supplied internal headers, and
  injects the private Server credential.
- The Workbench Server API key represents the gateway service principal, not an individual user.
- Workbench Server and its selected database are not directly public.
- CORS and network routing do not replace authentication.
