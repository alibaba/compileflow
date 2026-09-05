import Dexie from 'dexie'

import { db, MAX_SNAPSHOTS_PER_PROCESS } from './processDatabase'
import { compareProcessSnapshotsNewestFirst } from './processSnapshots'
import type {
  ProcessSnapshot,
  ProcessStorage,
  ProcessTemplate,
  StoredProcess,
  ProcessDraftQuery,
  LocalWorkspaceData,
} from './processStorageTypes'

import type { ProcessModelType } from '@/shared/contracts'

type SortBy = NonNullable<ProcessDraftQuery['sortBy']>
type SortOrder = NonNullable<ProcessDraftQuery['sortOrder']>

interface ProcessQueryResult {
  processes: StoredProcess[]
  sortedByStorage: boolean
}

interface ReplacementData {
  processes: StoredProcess[]
  snapshots: ProcessSnapshot[]
  templates: ProcessTemplate[]
}

const DEFAULT_SORT_BY: SortBy = 'updatedAt'
const DEFAULT_SORT_ORDER: SortOrder = 'desc'
export { MAX_SNAPSHOTS_PER_PROCESS }

async function queryProcessesByIndexedFields(
  filters: ProcessDraftQuery,
  sortBy: SortBy,
  sortOrder: SortOrder
): Promise<ProcessQueryResult> {
  const { type } = filters
  if (type && sortBy === 'updatedAt') {
    const query = db.processes
      .where('[type+updatedAt]')
      .between([type, Dexie.minKey], [type, Dexie.maxKey])
    return {
      processes: sortOrder === 'desc' ? await query.reverse().toArray() : await query.toArray(),
      sortedByStorage: true,
    }
  }
  if (type) {
    return {
      processes: await db.processes.where('type').equals(type).toArray(),
      sortedByStorage: false,
    }
  }
  return { processes: await db.processes.toArray(), sortedByStorage: false }
}

function applyProcessFilters(
  processes: StoredProcess[],
  filters: ProcessDraftQuery
): StoredProcess[] {
  return processes
    .filter((process) => matchesTagsAndCategory(process, filters))
    .filter((process) => matchesSearchTerm(process, filters.searchTerm))
}

function matchesTagsAndCategory(process: StoredProcess, filters: ProcessDraftQuery): boolean {
  if (filters.category && process.category !== filters.category) return false
  if (filters.tags?.length && !filters.tags.every((tag) => process.tags?.includes(tag)))
    return false
  return true
}

function matchesSearchTerm(process: StoredProcess, searchTerm?: string): boolean {
  if (!searchTerm) return true
  const term = searchTerm.toLowerCase()
  return process.name.toLowerCase().includes(term) || process.code.toLowerCase().includes(term)
}

function sortProcesses(
  processes: StoredProcess[],
  sortBy: SortBy,
  sortOrder: SortOrder
): StoredProcess[] {
  return processes.sort((a, b) => compareProcessField(a[sortBy], b[sortBy], sortOrder))
}

function compareProcessField(
  aValue: StoredProcess[SortBy],
  bValue: StoredProcess[SortBy],
  sortOrder: SortOrder
): number {
  if (typeof aValue === 'number' && typeof bValue === 'number') {
    return sortOrder === 'asc' ? aValue - bValue : bValue - aValue
  }
  if (typeof aValue === 'string' && typeof bValue === 'string') {
    const comparison = aValue.localeCompare(bValue)
    return sortOrder === 'asc' ? comparison : -comparison
  }
  return 0
}

function sameSnapshot(left: ProcessSnapshot, right: ProcessSnapshot): boolean {
  return (
    left.id === right.id &&
    left.processId === right.processId &&
    left.definition === right.definition &&
    left.createdAt === right.createdAt &&
    left.changeLog === right.changeLog &&
    left.tag === right.tag
  )
}

function isRedundantSnapshot(
  snapshot: ProcessSnapshot,
  newer: ProcessSnapshot | undefined,
  older: ProcessSnapshot | undefined
): boolean {
  return (
    snapshot.changeLog === undefined &&
    snapshot.tag === undefined &&
    (snapshot.definition === newer?.definition || snapshot.definition === older?.definition)
  )
}

function assertUniqueIds(resource: string, values: Array<{ id: string }>): void {
  const ids = new Set<string>()
  for (const value of values) {
    if (ids.has(value.id)) {
      throw new Error(`Duplicate ${resource} ID: ${value.id}`)
    }
    ids.add(value.id)
  }
}

function validateReplacementData(data: ReplacementData): void {
  assertUniqueIds('process', data.processes)
  assertUniqueIds('snapshot', data.snapshots)
  assertUniqueIds('template', data.templates)

  const processIds = new Set(data.processes.map((process) => process.id))
  const snapshotCounts = new Map<string, number>()
  for (const snapshot of data.snapshots) {
    if (!processIds.has(snapshot.processId)) {
      throw new Error(`Snapshot ${snapshot.id} references missing process ${snapshot.processId}`)
    }
    const count = (snapshotCounts.get(snapshot.processId) ?? 0) + 1
    if (count > MAX_SNAPSHOTS_PER_PROCESS) {
      throw new Error(
        `Process ${snapshot.processId} exceeds the ${MAX_SNAPSHOTS_PER_PROCESS}-snapshot limit`
      )
    }
    snapshotCounts.set(snapshot.processId, count)
  }
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

async function persistSnapshot(snapshot: ProcessSnapshot): Promise<boolean> {
  if (!(await db.processes.get(snapshot.processId))) {
    throw new Error(`Process not found: ${snapshot.processId}`)
  }

  const sameId = await db.snapshots.get(snapshot.id)
  if (sameId) {
    if (!sameSnapshot(sameId, snapshot)) {
      throw new Error(`Snapshot ID already identifies different content: ${snapshot.id}`)
    }
    return false
  }

  const existing = await db.snapshots.where('processId').equals(snapshot.processId).toArray()
  const ordered = [...existing, snapshot].sort(compareProcessSnapshotsNewestFirst)
  const snapshotIndex = ordered.findIndex((candidate) => candidate.id === snapshot.id)
  if (snapshotIndex >= MAX_SNAPSHOTS_PER_PROCESS) {
    return false
  }
  if (isRedundantSnapshot(snapshot, ordered[snapshotIndex - 1], ordered[snapshotIndex + 1])) {
    return false
  }

  const retained = ordered.slice(0, MAX_SNAPSHOTS_PER_PROCESS)
  await db.snapshots.put(snapshot)
  const retainedIds = new Set(retained.map((candidate) => candidate.id))
  const staleIds = existing
    .filter((candidate) => !retainedIds.has(candidate.id))
    .map((candidate) => candidate.id)
  if (staleIds.length > 0) {
    await db.snapshots.bulkDelete(staleIds)
  }
  return true
}

export class IndexedDbProcessStorage implements ProcessStorage {
  async saveProcess(
    process: StoredProcess,
    snapshot?: Omit<ProcessSnapshot, 'definition' | 'processId'>
  ): Promise<StoredProcess> {
    return db.transaction('rw', db.processes, db.snapshots, async () => {
      const existing = await db.processes.get(process.id)
      const savedProcess: StoredProcess = {
        ...process,
        createdAt: existing?.createdAt ?? process.createdAt,
      }
      await db.processes.put(savedProcess)
      if (snapshot) {
        await persistSnapshot({
          ...snapshot,
          processId: process.id,
          definition: process.definition,
        })
      }
      return savedProcess
    })
  }

  async loadProcess(id: string): Promise<StoredProcess | null> {
    return (await db.processes.get(id)) ?? null
  }

  async listProcesses(filters?: ProcessDraftQuery): Promise<StoredProcess[]> {
    const activeFilters = filters ?? {}
    const sortBy = activeFilters.sortBy ?? DEFAULT_SORT_BY
    const sortOrder = activeFilters.sortOrder ?? DEFAULT_SORT_ORDER
    const queryResult = await queryProcessesByIndexedFields(activeFilters, sortBy, sortOrder)
    const processes = applyProcessFilters(queryResult.processes, activeFilters)

    return queryResult.sortedByStorage ? processes : sortProcesses(processes, sortBy, sortOrder)
  }

  async deleteProcess(id: string): Promise<void> {
    await db.transaction('rw', db.processes, db.snapshots, async () => {
      await db.processes.delete(id)
      await db.snapshots.where('processId').equals(id).delete()
    })
  }

  async getRecentProcesses(limit = 10): Promise<StoredProcess[]> {
    if (!Number.isSafeInteger(limit) || limit < 1) {
      throw new Error('Recent process limit must be a positive integer')
    }
    return db.processes.orderBy('updatedAt').reverse().limit(limit).toArray()
  }

  async saveSnapshot(snapshot: ProcessSnapshot): Promise<boolean> {
    return db.transaction('rw', db.processes, db.snapshots, () => persistSnapshot(snapshot))
  }

  async getProcessSnapshots(processId: string): Promise<ProcessSnapshot[]> {
    const snapshots = await db.snapshots.where('processId').equals(processId).toArray()
    snapshots.sort(compareProcessSnapshotsNewestFirst)
    return snapshots
  }

  async saveTemplate(template: ProcessTemplate): Promise<ProcessTemplate> {
    await db.templates.put(template)
    return template
  }

  async listTemplates(): Promise<ProcessTemplate[]> {
    return db.templates.toArray()
  }

  async getTemplate(id: string): Promise<ProcessTemplate | null> {
    return (await db.templates.get(id)) ?? null
  }

  async suggestAvailableProcessName(baseName: string, type: ProcessModelType): Promise<string> {
    const suffixPattern = new RegExp(`^${escapeRegExp(baseName)} #(\\d+)$`)
    const existingProcesses = await db.processes.where('type').equals(type).toArray()
    let largestSuffix = -1

    for (const process of existingProcesses) {
      if (process.name === baseName) {
        largestSuffix = Math.max(largestSuffix, 0)
        continue
      }
      const match = suffixPattern.exec(process.name)
      if (match) {
        largestSuffix = Math.max(largestSuffix, Number.parseInt(match[1], 10))
      }
    }

    return largestSuffix < 0 ? baseName : `${baseName} #${largestSuffix + 1}`
  }

  async readAll(): Promise<LocalWorkspaceData> {
    return db.transaction('r', db.processes, db.snapshots, db.templates, async () => {
      const [processes, snapshots, templates] = await Promise.all([
        db.processes.toArray(),
        db.snapshots.toArray(),
        db.templates.toArray(),
      ])
      return {
        processes,
        snapshots,
        templates,
      }
    })
  }

  async replaceAll(data: ReplacementData): Promise<void> {
    validateReplacementData(data)

    await db.transaction('rw', db.processes, db.snapshots, db.templates, async () => {
      await Promise.all([db.processes.clear(), db.snapshots.clear(), db.templates.clear()])

      if (data.processes.length > 0) {
        await db.processes.bulkPut(data.processes)
      }
      if (data.snapshots.length > 0) {
        await db.snapshots.bulkPut(data.snapshots)
      }
      if (data.templates.length > 0) {
        await db.templates.bulkPut(data.templates)
      }
    })
  }
}

export const processStorage = new IndexedDbProcessStorage()
