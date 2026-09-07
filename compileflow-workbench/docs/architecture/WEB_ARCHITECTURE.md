# Workbench Web Architecture

The following ownership and change map applies to `apps/web`.

## Product Boundaries

| Domain  | Responsibility                                                       | Durable data                                 |
| ------- | -------------------------------------------------------------------- | -------------------------------------------- |
| Learn   | Examples, concepts, and explicit-definition preview                  | Server catalog or bundled mock catalog       |
| Build   | BPMN/TBBPM authoring and local workspace management                  | Browser IndexedDB                            |
| Operate | Draft persistence, publication, routing, execution, health, and logs | Workbench Server and its configured database |

Build owns local editable state. Operate owns managed server state. Learn and Build preview current XML through
`POST /api/executions/preview`; Operate executes published state by explicit version or alias.
The shared designer can edit an Operate draft: its explicit binding carries the process code and revision, and Save
updates the Server draft with that revision instead of writing IndexedDB.

## Runtime Topology

```text
Production
Browser
  | same-origin Web assets and /api/**
  v
authentication-capable gateway
  | private service credential
  v
compileflow-workbench-server -> PostgreSQL or MySQL

Frontend development
Vite -> loopback dev-gateway preview mock
```

`apps/dev-gateway` is neither a BFF nor a production proxy. The same Web bundle can be served from the bundled Java
image or a separate static Web image.

## Source Layout

```text
apps/web/src/
├── app/       application store and typed hooks
├── learn/     Learn pages, API adapter, and components
├── authoring/ workspace and BPMN/TBBPM designer
├── operate/   server-backed operational views and API adapters
├── settings/  user-visible Workbench settings
├── shared/    cross-domain contracts, config, services, and UI primitives
└── shell/     routing shell and navigation
```

Domain code may depend on `shared`; `shared` must not depend on Learn, Build, Operate, or Shell implementations.
Cross-domain navigation uses the route builders in
[`shared/constants.ts`](../../apps/web/src/shared/constants.ts).

## State Ownership

- Component state owns transient presentation state.
- Redux owns shared designer editing state and undo history.
- IndexedDB owns local processes, snapshots, and templates.
- Workbench Server owns server drafts, published versions, routes, async invocations, and execution logs.
- `VITE_COMPILEFLOW_*` values are parsed once as public build inputs.

Server lifecycle state is not copied into local designer storage or represented as browser configuration.

## Designer Data Flow

```text
route descriptor
  -> workspace/template/server source loader
  -> format parser
  -> normalized Redux editor state
  -> X6 canvas and property panels
  -> structured state updates
  -> format writer
  -> local IndexedDB or revision-checked Server draft save, export, or explicit preview request
```

Canvas and XML are projections of the same structured state. UI components do not rewrite XML with ad hoc string
operations. BPMN and TBBPM parsers, writers, validators, and type unions evolve together.

## API Boundary

- The committed OpenAPI document is the wire authority.
- Generated TypeScript describes raw operations and schemas.
- Refined UI contracts may be narrower but must preserve property names, types, and required-versus-optional semantics
  through compile-time parity.
- Closed response envelopes that drive execution or status control flow are validated at runtime. Other responses rely
  on generated wire types, compile-time parity, and server contract tests.
- Browser calls use relative paths; routing is a deployment concern.
- Mutation requests are not replayed implicitly.
- Mock and real modes are selected at the adapter boundary and never silently fall back to each other.

## Verification

Run from `compileflow-workbench/`:

```bash
pnpm check:workbench-server-contract
pnpm type-check
pnpm lint:web
pnpm test:web
pnpm --filter @compileflow/workbench-dev-gateway test
pnpm --filter @compileflow/workbench-web test:e2e:smoke
```

The delivery gate is `pnpm verify:delivery`.
