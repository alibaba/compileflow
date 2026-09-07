# Workbench Documentation

Start with the product README and deployment guide. The remaining documents cover Workbench boundaries, user workflows,
and implementation contracts.

## Start Here

| Document                                                                                       | Audience                         | Purpose                                                           |
| ---------------------------------------------------------------------------------------------- | -------------------------------- | ----------------------------------------------------------------- |
| [`../README.md`](../README.md)                                                                 | Users and contributors           | Product scope, local setup, commands, and configuration           |
| [`../DEPLOYMENT.md`](../DEPLOYMENT.md)                                                         | Operators                        | Local stack, production topology, security, and acceptance checks |
| [`../CONTRIBUTING.md`](../CONTRIBUTING.md)                                                     | Contributors                     | Workbench development workflow and required checks                |
| [`../../compileflow-workbench-server/README.md`](../../compileflow-workbench-server/README.md) | Backend developers and operators | Server setup, persistence, HTTP contract, and health checks       |
| [`PRODUCT_SURFACES.md`](PRODUCT_SURFACES.md)                                                   | All readers                      | Learn, Build, and Operate ownership boundaries                    |
| [`architecture/WEB_ARCHITECTURE.md`](architecture/WEB_ARCHITECTURE.md)                         | Maintainers                      | Web application boundaries, state ownership, and data flow        |
| [`architecture/DESIGN_SYSTEM.md`](architecture/DESIGN_SYSTEM.md)                               | UI contributors                  | Design token and component usage rules                            |
| [`developer-guide/BPMN_DESIGNER_GUIDE.md`](developer-guide/BPMN_DESIGNER_GUIDE.md)             | Developers                       | BPMN designer implementation and extension points                 |
| [`developer-guide/TBBPM_DESIGNER_GUIDE.md`](developer-guide/TBBPM_DESIGNER_GUIDE.md)           | Developers                       | TBBPM designer implementation and extension points                |
| [`developer-guide/STORAGE.md`](developer-guide/STORAGE.md)                                     | Developers                       | Local workspace persistence and import/export contracts           |
| [`user-guide/BPMN_DESIGNER_USER_GUIDE.md`](user-guide/BPMN_DESIGNER_USER_GUIDE.md)             | Users                            | BPMN designer workflow                                            |
| [`../apps/dev-gateway/README.md`](../apps/dev-gateway/README.md)                               | Frontend developers              | Local preview mock and configuration                              |
| [`../apps/web/src/operate/API_SPEC.md`](../apps/web/src/operate/API_SPEC.md)                   | API developers                   | Operate API behavior beyond the OpenAPI schema                    |

## Contract Sources

Generated and executable sources take precedence over prose documentation:

| Contract                             | Authority                                                        |
| ------------------------------------ | ---------------------------------------------------------------- |
| Routes and designer entry parameters | `apps/web/src/shared/constants.ts`                               |
| Browser build configuration          | `apps/web/src/shared/config/buildConfigSchema.ts`                |
| Operate REST shapes                  | `apps/web/src/shared/contracts/` and `apps/web/src/operate/api/` |
| Development-gateway configuration    | `apps/dev-gateway/src/config.ts`                                 |
| BPMN/TBBPM node support              | Designer type unions, palettes, parsers, validators, and tests   |
| Local storage schema                 | `apps/web/src/authoring/designer/api/processDatabase.ts`         |
| Visual tokens                        | `apps/web/src/shared/styles/variables.css`                       |

When behavior changes, update the authoritative source, its focused tests, and the corresponding concise guide in the
same change. Do not copy API inventories into prose; they drift quickly and are already covered by the listed sources.
