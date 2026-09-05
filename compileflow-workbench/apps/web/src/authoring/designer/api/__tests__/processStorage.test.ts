import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { WorkbenchDataExport } from '../processStorage'
import type { ProcessSnapshot, ProcessTemplate, StoredProcess } from '../processStorageTypes'

const mocks = vi.hoisted(() => ({
  processStorage: {
    getTemplate: vi.fn(),
    listProcesses: vi.fn(),
    listTemplates: vi.fn(),
    loadProcess: vi.fn(),
    readAll: vi.fn(),
    replaceAll: vi.fn(),
    saveProcess: vi.fn(),
    saveTemplate: vi.fn(),
    saveSnapshot: vi.fn(),
  },
  logger: {
    warn: vi.fn(),
  },
}))

vi.mock('../processStorage.indexeddb', () => ({
  processStorage: mocks.processStorage,
}))

vi.mock('@/shared/logging/logger', () => ({
  logger: mocks.logger,
}))

vi.mock('@/shared/config/buildConfig', () => ({
  APP_BUILD_CONFIG: {
    appVersion: '2.0.0-test',
  },
}))

const createProcess = (id: string): StoredProcess => ({
  id,
  code: id.toUpperCase(),
  name: `Process ${id}`,
  type: 'BPMN',
  definition: '<definitions />',
  createdAt: 1,
  updatedAt: 2,
})

const createSnapshot = (id: string, processId: string): ProcessSnapshot => ({
  id,
  processId,
  definition: '<definitions />',
  createdAt: 3,
})

const createTemplate = (id: string): ProcessTemplate => ({
  id,
  name: `Template ${id}`,
  type: 'BPMN',
  description: 'Template',
  content: '<definitions />',
})

const createExport = (overrides: Partial<WorkbenchDataExport> = {}): WorkbenchDataExport => ({
  formatVersion: 2,
  exportedAt: 1,
  workbenchVersion: '1.0.0',
  processes: [createProcess('process-a')],
  snapshots: [createSnapshot('snapshot-a', 'process-a')],
  templates: [createTemplate('template-a')],
  ...overrides,
})

describe('processStorage import/export helpers', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.processStorage.getTemplate.mockResolvedValue(null)
    mocks.processStorage.listProcesses.mockResolvedValue([])
    mocks.processStorage.listTemplates.mockResolvedValue([])
    mocks.processStorage.loadProcess.mockResolvedValue(null)
    mocks.processStorage.readAll.mockResolvedValue({
      processes: [],
      snapshots: [],
      templates: [],
    })
    mocks.processStorage.replaceAll.mockResolvedValue(undefined)
    mocks.processStorage.saveProcess.mockImplementation(async (flow: StoredProcess) => flow)
    mocks.processStorage.saveTemplate.mockImplementation(
      async (template: ProcessTemplate) => template
    )
    mocks.processStorage.saveSnapshot.mockResolvedValue(true)
  })

  it('exports processes, snapshots, and templates', async () => {
    const { exportAllData } = await import('../processStorage')
    mocks.processStorage.readAll.mockResolvedValue({
      processes: [createProcess('process-a')],
      snapshots: [createSnapshot('snapshot-a', 'process-a')],
      templates: [createTemplate('template-a')],
    })

    const data = await exportAllData()

    expect(data.processes).toHaveLength(1)
    expect(data.snapshots).toHaveLength(1)
    expect(data.templates).toHaveLength(1)
    expect(data.formatVersion).toBe(2)
    expect(data.workbenchVersion).toBe('2.0.0-test')
    expect(mocks.processStorage.readAll).toHaveBeenCalledOnce()
  })

  it('uses atomic replace mode with normalized optional arrays', async () => {
    const { importData } = await import('../processStorage')
    const data = createExport({
      snapshots: undefined as unknown as ProcessSnapshot[],
      templates: undefined as unknown as ProcessTemplate[],
    })

    await expect(importData(data, { mode: 'replace' })).resolves.toEqual({
      success: 1,
      skipped: 0,
      failed: 0,
    })
    expect(mocks.processStorage.replaceAll).toHaveBeenCalledWith({
      processes: data.processes,
      snapshots: [],
      templates: [],
    })
  })

  it('merges new records and skips duplicate processes, snapshots, and templates', async () => {
    const { importData } = await import('../processStorage')
    mocks.processStorage.loadProcess.mockResolvedValueOnce(createProcess('process-a'))
    mocks.processStorage.saveSnapshot.mockResolvedValueOnce(false)
    mocks.processStorage.getTemplate.mockResolvedValue(createTemplate('template-a'))

    const result = await importData(createExport())

    expect(result).toEqual({ success: 0, skipped: 3, failed: 0 })
    expect(mocks.processStorage.saveProcess).not.toHaveBeenCalled()
    expect(mocks.processStorage.saveSnapshot).toHaveBeenCalledOnce()
    expect(mocks.processStorage.saveTemplate).not.toHaveBeenCalled()
  })

  it('counts every newly merged record', async () => {
    const { importData } = await import('../processStorage')

    await expect(importData(createExport())).resolves.toEqual({
      success: 3,
      skipped: 0,
      failed: 0,
    })
  })

  it('merges snapshots in deterministic newest-first order', async () => {
    const { importData } = await import('../processStorage')
    const oldSnapshot = createSnapshot('snapshot-old', 'process-a')
    const newSnapshot = { ...createSnapshot('snapshot-new', 'process-a'), createdAt: 4 }

    await importData(createExport({ snapshots: [oldSnapshot, newSnapshot] }))

    expect(mocks.processStorage.saveSnapshot.mock.calls.map(([snapshot]) => snapshot.id)).toEqual([
      'snapshot-new',
      'snapshot-old',
    ])
  })

  it('surfaces non-fatal snapshot write failures without aborting the import', async () => {
    const { importData } = await import('../processStorage')
    mocks.processStorage.saveSnapshot.mockRejectedValueOnce(new Error('write failed'))

    const result = await importData(createExport())

    expect(result).toEqual({ success: 2, skipped: 0, failed: 1 })
    expect(mocks.processStorage.saveProcess).toHaveBeenCalledTimes(1)
    expect(mocks.processStorage.saveTemplate).toHaveBeenCalledTimes(1)
    expect(mocks.logger.warn).toHaveBeenCalledWith(
      '[importData] snapshot write failed.',
      expect.objectContaining({ data: expect.objectContaining({ snapshotId: 'snapshot-a' }) })
    )
  })

  it('rejects unsupported export versions before writing data', async () => {
    const { importData } = await import('../processStorage')

    await expect(importData(createExport({ formatVersion: 9 }))).rejects.toThrow(
      'Unsupported export format version'
    )
    expect(mocks.processStorage.saveProcess).not.toHaveBeenCalled()
    expect(mocks.processStorage.replaceAll).not.toHaveBeenCalled()
  })

  it('rejects malformed process records before writing data', async () => {
    const { importData } = await import('../processStorage')
    const malformed = createExport({
      processes: [{ id: 'process-a' }] as StoredProcess[],
    })

    await expect(importData(malformed)).rejects.toThrow('Invalid import data')
    expect(mocks.processStorage.saveProcess).not.toHaveBeenCalled()
    expect(mocks.processStorage.replaceAll).not.toHaveBeenCalled()
  })
})
