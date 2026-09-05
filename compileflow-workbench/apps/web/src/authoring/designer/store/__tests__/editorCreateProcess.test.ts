import { configureStore } from '@reduxjs/toolkit'
import { beforeEach, describe, expect, it } from 'vitest'

import { db } from '../../api/processDatabase'
import editorReducer, { createProcess } from '../editorSlice'

import { DEFAULT_TBBPM_WITH_NODES_XML } from '@/shared/processes/tbbpmTemplates'

const SOURCE_DEFINITION = DEFAULT_TBBPM_WITH_NODES_XML

describe('createProcess', () => {
  beforeEach(async () => {
    await db.processes.clear()
  })

  it('validates and persists a supplied initial definition atomically', async () => {
    const store = configureStore({ reducer: editorReducer })

    const created = await store
      .dispatch(
        createProcess({
          type: 'TBBPM',
          name: 'Copy',
          definition: SOURCE_DEFINITION,
        })
      )
      .unwrap()

    expect(created.name).toBe('Copy')
    const stored = await db.processes.get(created.id)
    expect(stored).toMatchObject({
      id: created.id,
      name: 'Copy',
      type: 'TBBPM',
    })
    expect(stored?.definition).toContain(`code="${created.code}"`)
    expect(stored?.definition).toContain('<scriptTask')
  })

  it('does not persist an invalid supplied definition', async () => {
    const store = configureStore({ reducer: editorReducer })

    await expect(
      store
        .dispatch(createProcess({ type: 'TBBPM', name: 'Invalid', definition: '<invalid />' }))
        .unwrap()
    ).rejects.toThrow()
    await expect(db.processes.count()).resolves.toBe(0)
  })
})
