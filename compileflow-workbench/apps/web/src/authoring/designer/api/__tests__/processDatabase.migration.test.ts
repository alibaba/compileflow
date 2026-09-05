import Dexie from 'dexie'
import { afterEach, describe, expect, it } from 'vitest'

import { MAX_SNAPSHOTS_PER_PROCESS, ProcessDatabase } from '../processDatabase'

const createdDatabases: string[] = []

describe('ProcessDatabase migrations', () => {
  afterEach(async () => {
    await Promise.all(createdDatabases.splice(0).map((name) => Dexie.delete(name)))
  })

  it('migrates v1 drafts and version rows to the minimal snapshot model', async () => {
    const name = `CompileFlowWorkbenchProcesses-v1-${Date.now()}-${Math.random()}`
    createdDatabases.push(name)
    const legacy = new Dexie(name)
    legacy.version(1).stores({
      processes: 'id, type, [type+status], [type+author], [type+updatedAt], updatedAt, *tags',
      versions: 'id, processId, createdAt',
      templates: 'id, type, category',
      meta: 'key',
    })
    await legacy.table('processes').put({
      id: 'legacy-process',
      code: 'LEGACY',
      name: 'Legacy process',
      type: 'BPMN',
      definition: '<definitions />',
      version: '1',
      createdAt: 1,
      updatedAt: 2,
      status: 'draft',
    })
    await legacy.table('meta').put({ key: 'favorites', value: ['legacy-process'] })
    await legacy.table('versions').put({
      id: 'legacy-version',
      processId: 'legacy-process',
      version: '1',
      definition: '<definitions />',
      createdAt: 3,
      createdBy: 'legacy-user',
    })
    legacy.close()

    const current = new ProcessDatabase(name)
    await current.open()

    const migrated = await current.processes.get('legacy-process')
    expect(migrated).toMatchObject({
      code: 'LEGACY',
      updatedAt: 2,
    })
    expect(migrated).not.toHaveProperty('version')
    expect(migrated).not.toHaveProperty('status')
    await expect(current.snapshots.get('legacy-version')).resolves.toEqual({
      id: 'legacy-version',
      processId: 'legacy-process',
      definition: '<definitions />',
      createdAt: 3,
    })
    expect(current.tables.map((table) => table.name)).not.toContain('meta')
    current.close()
  })

  it('bounds legacy version history while migrating it to snapshots', async () => {
    const name = `CompileFlowWorkbenchProcesses-bounded-${Date.now()}-${Math.random()}`
    createdDatabases.push(name)
    const legacy = new Dexie(name)
    legacy.version(1).stores({
      processes: 'id, type, [type+status], [type+author], [type+updatedAt], updatedAt, *tags',
      versions: 'id, processId, createdAt',
      templates: 'id, type, category',
      meta: 'key',
    })
    await legacy.table('processes').put({
      id: 'legacy-process',
      code: 'LEGACY',
      name: 'Legacy process',
      type: 'BPMN',
      definition: '<definitions />',
      createdAt: 1,
      updatedAt: 2,
    })
    await legacy.table('versions').bulkPut(
      Array.from({ length: MAX_SNAPSHOTS_PER_PROCESS + 1 }, (_, index) => ({
        id: `legacy-${index}`,
        processId: 'legacy-process',
        definition: `<definitions id="${index}" />`,
        createdAt: index,
      }))
    )
    legacy.close()

    const current = new ProcessDatabase(name)
    await current.open()
    const snapshots = await current.snapshots.orderBy('createdAt').toArray()

    expect(snapshots).toHaveLength(MAX_SNAPSHOTS_PER_PROCESS)
    expect(snapshots[0]?.id).toBe('legacy-1')
    expect(snapshots[snapshots.length - 1]?.id).toBe(`legacy-${MAX_SNAPSHOTS_PER_PROCESS}`)
    current.close()
  })
})
