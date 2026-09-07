import { z } from 'zod'

import { compareProcessSnapshotsNewestFirst } from './processSnapshots'
import { processStorage } from './processStorage.indexeddb'
import type { ProcessSnapshot, ProcessTemplate, StoredProcess } from './processStorageTypes'

import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import { logger } from '@/shared/logging/logger'

// ==================== Storage Singleton ====================

export { processStorage }

// ==================== Data Export / Import ====================

/** Workbench full-data export format for backup and cross-device transfer. */
export interface WorkbenchDataExport {
  /** Version of the export format. */
  formatVersion: number
  /** Unix timestamp in milliseconds. */
  exportedAt: number
  workbenchVersion: string
  processes: StoredProcess[]
  snapshots: ProcessSnapshot[]
  templates: ProcessTemplate[]
}

/** Import mode options. */
export interface ImportOptions {
  /** merge: keep existing data and skip duplicate IDs; replace: wipe before import. */
  mode: 'merge' | 'replace'
}

/** Per-category import result counts. */
export interface ImportResult {
  success: number
  skipped: number
  failed: number
}

const EXPORT_FORMAT_VERSION = 2
const requiredString = z.string().refine((value) => value.trim().length > 0)
const sourceContent = z
  .string()
  .refine((value) => value.trim().length > 0, 'Source content must not be blank')
const optionalString = z.string().optional()
const timestamp = z.number().int().nonnegative().finite()

const processSchema: z.ZodType<StoredProcess> = z
  .object({
    id: requiredString,
    code: requiredString,
    name: requiredString,
    type: z.enum(['BPMN', 'TBBPM']),
    definition: sourceContent,
    createdAt: timestamp,
    updatedAt: timestamp,
    description: optionalString,
    category: optionalString,
    tags: z.array(requiredString).optional(),
  })
  .strict()

const processSnapshotSchema: z.ZodType<ProcessSnapshot> = z
  .object({
    id: requiredString,
    processId: requiredString,
    definition: sourceContent,
    createdAt: timestamp,
    changeLog: optionalString,
    tag: optionalString,
  })
  .strict()

const processTemplateSchema: z.ZodType<ProcessTemplate> = z
  .object({
    id: requiredString,
    name: requiredString,
    type: z.enum(['BPMN', 'TBBPM']),
    description: optionalString,
    content: sourceContent,
    category: optionalString,
    tags: z.array(requiredString).optional(),
  })
  .strict()

const workbenchDataExportSchema = z
  .object({
    formatVersion: z.literal(EXPORT_FORMAT_VERSION),
    exportedAt: timestamp,
    workbenchVersion: requiredString,
    processes: z.array(processSchema),
    snapshots: z.array(processSnapshotSchema).optional().default([]),
    templates: z.array(processTemplateSchema).optional().default([]),
  })
  .strict()

const emptyImportResult = (): ImportResult => ({ success: 0, skipped: 0, failed: 0 })

function validateImportData(data: unknown): WorkbenchDataExport {
  if (!data || typeof data !== 'object') {
    throw new Error('Invalid import data: malformed object')
  }
  const formatVersion = Reflect.get(data, 'formatVersion')
  if (formatVersion !== EXPORT_FORMAT_VERSION) {
    throw new Error(
      `Unsupported export format version: ${String(formatVersion)}. Supported: ${EXPORT_FORMAT_VERSION}`
    )
  }
  const parsed = workbenchDataExportSchema.safeParse(data)
  if (!parsed.success) {
    throw new Error(
      `Invalid import data: ${parsed.error.issues[0]?.message ?? 'schema validation failed'}`
    )
  }
  return parsed.data
}

function addImportResults(target: ImportResult, source: ImportResult): void {
  target.success += source.success
  target.skipped += source.skipped
  target.failed += source.failed
}

export async function exportAllData(): Promise<WorkbenchDataExport> {
  const { processes, snapshots, templates } = await processStorage.readAll()
  return {
    formatVersion: EXPORT_FORMAT_VERSION,
    exportedAt: Date.now(),
    workbenchVersion: APP_BUILD_CONFIG.appVersion,
    processes,
    snapshots,
    templates,
  }
}

export async function importData(
  data: unknown,
  options: ImportOptions = { mode: 'merge' }
): Promise<ImportResult> {
  const validatedData = validateImportData(data)

  if (options.mode === 'replace') {
    return replaceImportData(validatedData)
  }

  return mergeImportData(validatedData)
}

async function replaceImportData(data: WorkbenchDataExport): Promise<ImportResult> {
  await processStorage.replaceAll({
    processes: data.processes,
    snapshots: data.snapshots,
    templates: data.templates,
  })
  return {
    success: data.processes.length + data.snapshots.length + data.templates.length,
    skipped: 0,
    failed: 0,
  }
}

async function mergeImportData(data: WorkbenchDataExport): Promise<ImportResult> {
  const result = emptyImportResult()
  addImportResults(result, await importNewProcesses(data.processes))
  addImportResults(result, await importNewSnapshots(data.snapshots))
  addImportResults(result, await importNewTemplates(data.templates))
  return result
}

async function importNewProcesses(processes: StoredProcess[]): Promise<ImportResult> {
  const result = emptyImportResult()
  for (const process of processes) {
    try {
      const existing = await processStorage.loadProcess(process.id)
      if (existing) {
        result.skipped++
        continue
      }
      await processStorage.saveProcess(process)
      result.success++
    } catch {
      result.failed++
    }
  }
  return result
}

async function importNewSnapshots(snapshots: ProcessSnapshot[]): Promise<ImportResult> {
  const result = emptyImportResult()
  for (const snapshot of [...snapshots].sort(compareProcessSnapshotsNewestFirst)) {
    await saveSnapshotForImport(snapshot, result)
  }
  return result
}

async function saveSnapshotForImport(
  snapshot: ProcessSnapshot,
  result: ImportResult
): Promise<void> {
  try {
    if (await processStorage.saveSnapshot(snapshot)) {
      result.success++
    } else {
      result.skipped++
    }
  } catch (error) {
    result.failed++
    logger.warn('[importData] snapshot write failed.', {
      data: { snapshotId: snapshot.id, error },
    })
  }
}

async function importNewTemplates(templates: ProcessTemplate[]): Promise<ImportResult> {
  const result = emptyImportResult()
  for (const template of templates) {
    try {
      const existing = await processStorage.getTemplate(template.id)
      if (existing) {
        result.skipped++
        continue
      }
      await processStorage.saveTemplate(template)
      result.success++
    } catch {
      result.failed++
    }
  }
  return result
}
