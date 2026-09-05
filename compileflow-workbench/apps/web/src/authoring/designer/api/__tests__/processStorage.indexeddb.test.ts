import { beforeAll, beforeEach, describe, expect, it } from 'vitest'

import type { db as DbType } from '../processDatabase'
import type { IndexedDbProcessStorage as IndexedDbProcessStorageType } from '../processStorage.indexeddb'
import type { StoredProcess } from '../processStorageTypes'

const EMPTY_XML = '<definitions><process id="empty" /></definitions>'
const TEMPLATE_XML =
  '<definitions><process id="template"><startEvent id="start" /></process></definitions>'

function processDraft(id: string, overrides: Partial<StoredProcess> = {}): StoredProcess {
  return {
    id,
    code: id.toUpperCase(),
    name: id,
    type: 'BPMN',
    definition: EMPTY_XML,
    createdAt: 1,
    updatedAt: 1,
    ...overrides,
  }
}

describe('IndexedDbProcessStorage', () => {
  let IndexedDbProcessStorage: typeof IndexedDbProcessStorageType
  let db: typeof DbType
  let maxSnapshotsPerProcess: number
  let storage: IndexedDbProcessStorageType

  beforeAll(async () => {
    const storageModule = await import('../processStorage.indexeddb')
    const dbModule = await import('../processDatabase')
    IndexedDbProcessStorage = storageModule.IndexedDbProcessStorage
    maxSnapshotsPerProcess = storageModule.MAX_SNAPSHOTS_PER_PROCESS
    db = dbModule.db
  })

  beforeEach(async () => {
    storage = new IndexedDbProcessStorage()
    await Promise.all([db.processes.clear(), db.snapshots.clear(), db.templates.clear()])
  })

  it('creates, updates, loads, lists, searches, and deletes local drafts', async () => {
    const original = processDraft('process-a', { name: 'Order approval', tags: ['orders'] })
    await storage.saveProcess(original)
    await storage.saveProcess({ ...original, name: 'Updated order approval', updatedAt: 2 })
    await storage.saveProcess(
      processDraft('process-b', { name: 'Inventory', type: 'TBBPM', updatedAt: 3 })
    )

    await expect(storage.loadProcess(original.id)).resolves.toMatchObject({
      name: 'Updated order approval',
      createdAt: 1,
      updatedAt: 2,
    })
    await expect(storage.listProcesses({ type: 'BPMN' })).resolves.toHaveLength(1)
    await expect(storage.listProcesses({ tags: ['orders'] })).resolves.toHaveLength(1)
    await expect(storage.listProcesses({ searchTerm: 'ORDER' })).resolves.toHaveLength(1)
    await expect(storage.getRecentProcesses(1)).resolves.toMatchObject([{ id: 'process-b' }])

    await storage.deleteProcess(original.id)
    await expect(storage.loadProcess(original.id)).resolves.toBeNull()
  })

  it('rejects invalid recent-process limits', async () => {
    await expect(storage.getRecentProcesses(0)).rejects.toThrow('positive integer')
  })

  it('stores immutable snapshots newest first', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    await storage.saveSnapshot({
      id: 'snapshot-1',
      processId: 'snapshot-process',
      definition: '<process id="one" />',
      createdAt: 1,
    })
    await storage.saveSnapshot({
      id: 'snapshot-2',
      processId: 'snapshot-process',
      definition: '<process id="two" />',
      createdAt: 2,
    })
    await storage.saveSnapshot({
      id: 'snapshot-3',
      processId: 'snapshot-process',
      definition: '<process id="three" />',
      createdAt: 2,
    })

    await expect(storage.getProcessSnapshots('snapshot-process')).resolves.toMatchObject([
      { id: 'snapshot-3' },
      { id: 'snapshot-2' },
      { id: 'snapshot-1' },
    ])
  })

  it('atomically saves a process and its snapshot', async () => {
    const original = processDraft('snapshot-process', { name: 'Original' })
    const snapshot = {
      id: 'snapshot-1',
      createdAt: 1,
    }
    await storage.saveProcess(original, snapshot)

    const updated = {
      ...original,
      name: 'Updated',
      definition: '<process id="updated" />',
      updatedAt: 2,
    }
    await expect(storage.saveProcess(updated, snapshot)).rejects.toThrow(
      'Snapshot ID already identifies different content'
    )

    await expect(storage.loadProcess(original.id)).resolves.toEqual(original)
    await expect(storage.getProcessSnapshots(original.id)).resolves.toEqual([
      { ...snapshot, processId: original.id, definition: original.definition },
    ])
  })

  it('deduplicates unchanged snapshots', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    const first = {
      id: 'snapshot-1',
      processId: 'snapshot-process',
      definition: EMPTY_XML,
      createdAt: 1,
    }
    await expect(storage.saveSnapshot(first)).resolves.toBe(true)
    await expect(storage.saveSnapshot({ ...first, id: 'snapshot-2', createdAt: 2 })).resolves.toBe(
      false
    )
    await expect(storage.saveSnapshot({ ...first, id: 'snapshot-0' })).resolves.toBe(false)
    await expect(storage.getProcessSnapshots('snapshot-process')).resolves.toHaveLength(1)
  })

  it('preserves historical state recurrence during out-of-order import', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    await storage.saveSnapshot({
      id: 'snapshot-new-a',
      processId: 'snapshot-process',
      definition: '<process id="a" />',
      createdAt: 3,
    })
    await storage.saveSnapshot({
      id: 'snapshot-middle-b',
      processId: 'snapshot-process',
      definition: '<process id="b" />',
      createdAt: 2,
    })

    await expect(
      storage.saveSnapshot({
        id: 'snapshot-old-a',
        processId: 'snapshot-process',
        definition: '<process id="a" />',
        createdAt: 1,
      })
    ).resolves.toBe(true)
    await expect(storage.getProcessSnapshots('snapshot-process')).resolves.toMatchObject([
      { id: 'snapshot-new-a' },
      { id: 'snapshot-middle-b' },
      { id: 'snapshot-old-a' },
    ])
  })

  it('deduplicates adjacent historical states during out-of-order import', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    await storage.saveSnapshot({
      id: 'snapshot-new-b',
      processId: 'snapshot-process',
      definition: '<process id="b" />',
      createdAt: 3,
    })
    await storage.saveSnapshot({
      id: 'snapshot-old-a',
      processId: 'snapshot-process',
      definition: '<process id="a" />',
      createdAt: 1,
    })

    await expect(
      storage.saveSnapshot({
        id: 'snapshot-middle-a',
        processId: 'snapshot-process',
        definition: '<process id="a" />',
        createdAt: 2,
      })
    ).resolves.toBe(false)
    await expect(storage.getProcessSnapshots('snapshot-process')).resolves.toMatchObject([
      { id: 'snapshot-new-b' },
      { id: 'snapshot-old-a' },
    ])
  })

  it('preserves annotated snapshots when the definition is unchanged', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    await storage.saveSnapshot({
      id: 'snapshot-1',
      processId: 'snapshot-process',
      definition: EMPTY_XML,
      createdAt: 1,
    })

    await expect(
      storage.saveSnapshot({
        id: 'snapshot-2',
        processId: 'snapshot-process',
        definition: EMPTY_XML,
        createdAt: 2,
        tag: 'release',
      })
    ).resolves.toBe(true)
  })

  it('retains only the newest bounded snapshot set', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    for (let index = 0; index <= maxSnapshotsPerProcess; index += 1) {
      await storage.saveSnapshot({
        id: `snapshot-${index}`,
        processId: 'snapshot-process',
        definition: `<process id="${index}" />`,
        createdAt: index,
      })
    }

    const snapshots = await storage.getProcessSnapshots('snapshot-process')
    expect(snapshots).toHaveLength(maxSnapshotsPerProcess)
    expect(snapshots[0].id).toBe(`snapshot-${maxSnapshotsPerProcess}`)
    expect(snapshots[snapshots.length - 1]?.id).toBe('snapshot-1')
  })

  it('does not displace a retained snapshot with an older imported candidate', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    for (let index = 1; index <= maxSnapshotsPerProcess; index += 1) {
      await storage.saveSnapshot({
        id: `snapshot-${index}`,
        processId: 'snapshot-process',
        definition: `<process id="${index}" />`,
        createdAt: index,
      })
    }

    await expect(
      storage.saveSnapshot({
        id: 'snapshot-old',
        processId: 'snapshot-process',
        definition: '<process id="old" />',
        createdAt: 0,
      })
    ).resolves.toBe(false)

    const snapshots = await storage.getProcessSnapshots('snapshot-process')
    expect(snapshots).toHaveLength(maxSnapshotsPerProcess)
    expect(snapshots[snapshots.length - 1]?.id).toBe('snapshot-1')
  })

  it('treats snapshot IDs as immutable identities', async () => {
    await storage.saveProcess(processDraft('snapshot-process'))
    const snapshot = {
      id: 'snapshot-1',
      processId: 'snapshot-process',
      definition: '<process id="one" />',
      createdAt: 1,
    }

    await expect(storage.saveSnapshot(snapshot)).resolves.toBe(true)
    await expect(storage.saveSnapshot(snapshot)).resolves.toBe(false)
    await expect(
      storage.saveSnapshot({ ...snapshot, definition: '<process id="changed" />' })
    ).rejects.toThrow('Snapshot ID already identifies different content')
  })

  it('rejects orphan snapshots and deletes snapshots with their draft', async () => {
    await expect(
      storage.saveSnapshot({
        id: 'orphan',
        processId: 'missing',
        definition: EMPTY_XML,
        createdAt: 1,
      })
    ).rejects.toThrow('Process not found: missing')

    await storage.saveProcess(processDraft('owned'))
    await storage.saveSnapshot({
      id: 'owned-snapshot',
      processId: 'owned',
      definition: EMPTY_XML,
      createdAt: 1,
    })
    await storage.deleteProcess('owned')
    await expect(storage.getProcessSnapshots('owned')).resolves.toEqual([])
  })

  it('stores and loads templates', async () => {
    await storage.saveTemplate({
      id: 'template',
      name: 'Template',
      type: 'BPMN',
      content: TEMPLATE_XML,
    })

    await expect(storage.getTemplate('template')).resolves.toMatchObject({
      name: 'Template',
      type: 'BPMN',
      content: TEMPLATE_XML,
    })
  })

  it('reads a complete workspace view in one storage operation', async () => {
    const process = processDraft('exported')
    await storage.saveProcess(process)
    await storage.saveSnapshot({
      id: 'exported-snapshot',
      processId: process.id,
      definition: EMPTY_XML,
      createdAt: 2,
    })
    await storage.saveTemplate({
      id: 'exported-template',
      name: 'Exported template',
      type: 'BPMN',
      content: TEMPLATE_XML,
    })
    await expect(storage.readAll()).resolves.toMatchObject({
      processes: [{ id: process.id }],
      snapshots: [{ id: 'exported-snapshot', processId: process.id }],
      templates: [{ id: 'exported-template' }],
    })
  })

  it('generates names after the largest existing numeric suffix', async () => {
    await storage.saveProcess(processDraft('one', { name: 'Order' }))
    await storage.saveProcess(processDraft('two', { name: 'Order #3' }))
    await expect(storage.suggestAvailableProcessName('Order', 'BPMN')).resolves.toBe('Order #4')
    await expect(storage.suggestAvailableProcessName('Order', 'TBBPM')).resolves.toBe('Order')
  })

  it('validates replacement references before clearing current data', async () => {
    const existing = processDraft('existing')
    await storage.saveProcess(existing)

    await expect(
      storage.replaceAll({
        processes: [],
        snapshots: [
          {
            id: 'orphan',
            processId: 'missing',
            definition: EMPTY_XML,
            createdAt: 1,
          },
        ],
        templates: [],
      })
    ).rejects.toThrow('references missing process')
    await expect(storage.loadProcess(existing.id)).resolves.toEqual(existing)
  })

  it('rejects replacement data that exceeds the snapshot retention limit', async () => {
    const process = processDraft('bounded')
    await storage.saveProcess(process)

    await expect(
      storage.replaceAll({
        processes: [process],
        snapshots: Array.from({ length: maxSnapshotsPerProcess + 1 }, (_, index) => ({
          id: `snapshot-${index}`,
          processId: process.id,
          definition: `<process id="${index}" />`,
          createdAt: index,
        })),
        templates: [],
      })
    ).rejects.toThrow('exceeds the 100-snapshot limit')
  })
})
