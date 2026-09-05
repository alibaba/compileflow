import Dexie, { type Table, type Transaction } from 'dexie'

import { compareProcessSnapshotsNewestFirst } from './processSnapshots'
import type { ProcessSnapshot, ProcessTemplate, StoredProcess } from './processStorageTypes'

export const MAX_SNAPSHOTS_PER_PROCESS = 100

async function trimSnapshots(transaction: Transaction): Promise<void> {
  const snapshots = (await transaction.table('snapshots').toArray()) as ProcessSnapshot[]
  const byProcess = new Map<string, ProcessSnapshot[]>()
  for (const snapshot of snapshots) {
    const processSnapshots = byProcess.get(snapshot.processId) ?? []
    processSnapshots.push(snapshot)
    byProcess.set(snapshot.processId, processSnapshots)
  }
  const staleIds = Array.from(byProcess.values()).flatMap((processSnapshots) =>
    processSnapshots
      .sort(compareProcessSnapshotsNewestFirst)
      .slice(MAX_SNAPSHOTS_PER_PROCESS)
      .map((snapshot) => snapshot.id)
  )
  if (staleIds.length > 0) {
    await transaction.table('snapshots').bulkDelete(staleIds)
  }
}

export class ProcessDatabase extends Dexie {
  processes!: Table<StoredProcess, string>
  snapshots!: Table<ProcessSnapshot, string>
  templates!: Table<ProcessTemplate, string>

  constructor(name = 'CompileFlowWorkbenchProcesses') {
    super(name)

    this.version(1).stores({
      processes: 'id, type, [type+status], [type+author], [type+updatedAt], updatedAt, *tags',
      versions: 'id, processId, createdAt',
      templates: 'id, type, category',
      meta: 'key',
    })

    this.version(2)
      .stores({
        processes: 'id, type, [type+updatedAt], updatedAt, *tags',
        versions: 'id, processId, createdAt',
        snapshots: 'id, processId, createdAt',
        templates: 'id, type, category',
        meta: 'key',
      })
      .upgrade(async (transaction) => {
        const legacySnapshots = await transaction.table('versions').toArray()
        if (legacySnapshots.length > 0) {
          await transaction.table('snapshots').bulkPut(
            legacySnapshots.map(({ id, processId, definition, createdAt, changeLog, tag }) => ({
              id,
              processId,
              definition,
              createdAt,
              changeLog,
              tag,
            }))
          )
        }
        await transaction
          .table('processes')
          .toCollection()
          .modify((process) => {
            delete process.version
            delete process.status
            delete process.createdBy
            delete process.updatedBy
            delete process.author
            delete process.isFavorite
          })
      })

    this.version(3)
      .stores({
        processes: 'id, type, [type+updatedAt], updatedAt, *tags',
        versions: null,
        snapshots: 'id, processId, createdAt',
        templates: 'id, type, category',
        meta: 'key',
      })
      .upgrade(trimSnapshots)

    this.version(4).stores({
      processes: 'id, type, [type+updatedAt], updatedAt, *tags',
      snapshots: 'id, processId, createdAt',
      templates: 'id, type, category',
      meta: null,
    })
  }
}

export const db = new ProcessDatabase()
