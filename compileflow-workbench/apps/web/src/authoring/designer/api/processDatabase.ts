import Dexie, { type Table } from 'dexie'

import type { ProcessSnapshot, ProcessTemplate, StoredProcess } from './processStorageTypes'

export const MAX_SNAPSHOTS_PER_PROCESS = 100

export class ProcessDatabase extends Dexie {
  processes!: Table<StoredProcess, string>
  snapshots!: Table<ProcessSnapshot, string>
  templates!: Table<ProcessTemplate, string>

  constructor(name = 'CompileFlowWorkbenchProcesses') {
    super(name)

    this.version(1).stores({
      processes: 'id, type, [type+updatedAt], updatedAt, *tags',
      snapshots: 'id, processId, createdAt',
      templates: 'id, type, category',
    })
  }
}

export const db = new ProcessDatabase()
