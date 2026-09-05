# Workbench Storage

The Build workspace stores editable processes locally in IndexedDB through Dexie. This storage is for browser authoring
state; it is not a deployment registry or a substitute for `compileflow-workbench-server`.

## Source Of Truth

| Concern                | Source                                                                                                                                                                                                                                                      |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Database schema        | [`processDatabase.ts`](../../apps/web/src/authoring/designer/api/processDatabase.ts)                                                                                                                                                                        |
| Storage implementation | [`processStorage.indexeddb.ts`](../../apps/web/src/authoring/designer/api/processStorage.indexeddb.ts)                                                                                                                                                      |
| Import/export boundary | [`processStorage.ts`](../../apps/web/src/authoring/designer/api/processStorage.ts)                                                                                                                                                                          |
| Stored types           | [`processStorageTypes.ts`](../../apps/web/src/authoring/designer/api/processStorageTypes.ts)                                                                                                                                                                |
| Integration tests      | [`processStorage.indexeddb.test.ts`](../../apps/web/src/authoring/designer/api/__tests__/processStorage.indexeddb.test.ts) and [`processDatabase.migration.test.ts`](../../apps/web/src/authoring/designer/api/__tests__/processDatabase.migration.test.ts) |

Application code imports the `processStorage` facade. Components must not issue Dexie queries directly.

## Current Schema

The database is named `CompileFlowWorkbenchProcesses` and has three stores:

| Store       | Primary key | Indexed queries                                 |
| ----------- | ----------- | ----------------------------------------------- |
| `processes` | `id`        | `type`, `updatedAt`, tags, and type/update time |
| `snapshots` | `id`        | Parent `processId` and creation time            |
| `templates` | `id`        | Type and category                               |

Only queried properties are indexed. XML definitions and other large values are stored but never indexed. Dexie schema
version 1 is the frozen browser-storage baseline. Version 2 migrated legacy version rows to snapshots and removed draft
publication metadata, version 3 bounded retained snapshots, and version 4 removed the unused metadata store. Future
versions must add an explicit forward migration and pass the checked-in v1 recovery fixture.

## Storage Contract

`ProcessStorage` exposes these operation groups:

- Process save, load, list, delete, and recent-process queries.
- Snapshot save and list.
- Template save, list, and lookup.
- Available-name suggestion for new processes.
- Consistent full-data reads and atomic replacement.

An available-name suggestion improves the default UI label but is not a uniqueness reservation. Process identity is the
stable `id`; callers must not use the display name as a key.

## Consistency Rules

- Updating a process preserves its original `createdAt`.
- Deleting a process also deletes its snapshots in one transaction.
- A snapshot cannot be saved without its parent process.
- Snapshot IDs are immutable; replaying identical content is idempotent and conflicting content is rejected.
- Each process retains at most the newest 100 snapshots using deterministic creation-time and ID ordering.
- Unannotated head snapshots with unchanged definitions are deduplicated without collapsing historical state recurrence.
- Full replacement validates duplicate IDs and references before clearing any data.
- Full replacement clears and writes every store in one transaction.
- Storage errors propagate to the caller; there is no silent memory or `localStorage` fallback.

Dexie transactions are used for multi-record invariants. Bulk writes only run inside the replacement transaction, so an
error rolls back the complete replacement.

## Import And Export

Exports contain:

- Format and Workbench version metadata.
- Processes.
- Snapshots.
- Templates.

The current export format is integer `formatVersion: 2`; `workbenchVersion` independently records the application build
that produced the file. The format uses `processes` and `processId`. Earlier pre-release shapes are not accepted.
Recent processes are derived from `updatedAt` and are not serialized as separate state.

The import boundary accepts `unknown`, then validates the complete JSON shape with a strict Zod schema. Unknown
properties, malformed records, unsupported format versions, duplicate IDs, and dangling references are rejected before
an atomic replacement. Two explicit modes are supported:

| Mode      | Behavior                                                                           |
| --------- | ---------------------------------------------------------------------------------- |
| `merge`   | Keeps existing IDs, imports new records, and reports success/skipped/failed counts |
| `replace` | Validates first, then atomically replaces all local workspace data                 |

Merge mode isolates individual record failures so valid independent records can still be imported. Replace mode is
all-or-nothing.

## Testing

The test environment uses `fake-indexeddb`, so IndexedDB tests execute in Vitest instead of being skipped. Run:

```bash
pnpm --filter @compileflow/workbench-web test
pnpm --filter @compileflow/workbench-web type-check
pnpm --filter @compileflow/workbench-web lint
```

When changing persistence behavior, add a test for both the successful path and the invariant or rollback condition that
could corrupt data.
