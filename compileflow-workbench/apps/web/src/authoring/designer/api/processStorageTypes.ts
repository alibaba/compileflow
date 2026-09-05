import type { ProcessModelType } from '@/shared/contracts'

/** Process draft persisted in the browser-local workspace. */
export interface StoredProcess {
  id: string
  code: string
  name: string
  type: ProcessModelType
  definition: string
  createdAt: number
  updatedAt: number
  description?: string
  category?: string
  tags?: string[]
}

export interface ProcessDraftQuery {
  type?: ProcessModelType
  tags?: string[]
  category?: string
  searchTerm?: string
  sortBy?: 'createdAt' | 'updatedAt' | 'name'
  sortOrder?: 'asc' | 'desc'
}

/** Immutable browser-local snapshot of a process definition. */
export interface ProcessSnapshot {
  id: string
  processId: string
  definition: string
  createdAt: number
  changeLog?: string
  tag?: string
}

export interface ProcessTemplate {
  id: string
  name: string
  type: ProcessModelType
  description?: string
  content: string
  category?: string
  tags?: string[]
}

/** Consistent point-in-time view of all browser-local workspace data. */
export interface LocalWorkspaceData {
  processes: StoredProcess[]
  snapshots: ProcessSnapshot[]
  templates: ProcessTemplate[]
}

export interface ProcessStorage {
  saveProcess(
    process: StoredProcess,
    snapshot?: Omit<ProcessSnapshot, 'definition' | 'processId'>
  ): Promise<StoredProcess>

  loadProcess(id: string): Promise<StoredProcess | null>

  listProcesses(query?: ProcessDraftQuery): Promise<StoredProcess[]>

  deleteProcess(id: string): Promise<void>

  saveSnapshot(snapshot: ProcessSnapshot): Promise<boolean>

  getProcessSnapshots(processId: string): Promise<ProcessSnapshot[]>

  listTemplates(): Promise<ProcessTemplate[]>

  getTemplate(id: string): Promise<ProcessTemplate | null>

  saveTemplate(template: ProcessTemplate): Promise<ProcessTemplate>

  getRecentProcesses(limit?: number): Promise<StoredProcess[]>

  suggestAvailableProcessName(baseName: string, type: ProcessModelType): Promise<string>

  readAll(): Promise<LocalWorkspaceData>

  replaceAll(data: {
    processes: StoredProcess[]
    snapshots: ProcessSnapshot[]
    templates: ProcessTemplate[]
  }): Promise<void>
}
