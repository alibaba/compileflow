import { describe, expect, it } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import editorReducer, {
  loadOperateProcess,
  loadProcess,
  saveProcess,
  updateProcessInfo,
} from '../editorSlice'

function process(id: string, name: string): UnifiedProcessDefinition {
  return {
    id,
    code: id,
    name,
    type: 'TBBPM',
    nodes: [],
    connections: [],
    createdAt: 1,
    updatedAt: 1,
  }
}

describe('editor async correctness', () => {
  it('keeps the latest process when an older load completes last', () => {
    let state = editorReducer(undefined, { type: '@@INIT' })
    state = editorReducer(state, loadProcess.pending('load-a', 'process-a'))
    state = editorReducer(state, loadOperateProcess.pending('load-b', 'process-b'))
    state = editorReducer(
      state,
      loadOperateProcess.fulfilled(
        {
          flow: process('process-b', 'Process B'),
          operateProcessCode: 'process-b',
          revision: 7,
          warnings: [],
        },
        'load-b',
        'process-b'
      )
    )

    state = editorReducer(
      state,
      loadProcess.fulfilled(
        { flow: process('process-a', 'Process A'), warnings: [] },
        'load-a',
        'process-a'
      )
    )

    expect(state.currentProcess?.id).toBe('process-b')
    expect(state.operateBinding).toEqual({ processCode: 'process-b', revision: 7 })
    expect(state.isLoading).toBe(false)
  })

  it('does not mark edits made during save as persisted', () => {
    let state = editorReducer(undefined, { type: '@@INIT' })
    state = editorReducer(state, loadProcess.pending('load-a', 'process-a'))
    state = editorReducer(
      state,
      loadProcess.fulfilled(
        { flow: process('process-a', 'Process A'), warnings: [] },
        'load-a',
        'process-a'
      )
    )
    state = editorReducer(state, updateProcessInfo({ name: 'Snapshot A' }))
    const firstToken = state.changeToken
    state = editorReducer(state, saveProcess.pending('save-a', undefined))
    state = editorReducer(state, updateProcessInfo({ name: 'Snapshot B' }))
    state = editorReducer(
      state,
      saveProcess.fulfilled(
        {
          id: 'process-a',
          updatedAt: 2,
          operateRevision: null,
          savedChangeToken: firstToken,
        },
        'save-a',
        undefined
      )
    )

    expect(state.currentProcess?.name).toBe('Snapshot B')
    expect(state.isModified).toBe(true)

    const secondToken = state.changeToken
    state = editorReducer(state, saveProcess.pending('save-b', undefined))
    state = editorReducer(
      state,
      saveProcess.fulfilled(
        {
          id: 'process-a',
          updatedAt: 3,
          operateRevision: null,
          savedChangeToken: secondToken,
        },
        'save-b',
        undefined
      )
    )

    expect(state.isModified).toBe(false)
  })

  it('advances remote CAS revision while preserving edits made during save', () => {
    let state = editorReducer(undefined, { type: '@@INIT' })
    state = editorReducer(state, loadOperateProcess.pending('load-a', 'process-a'))
    state = editorReducer(
      state,
      loadOperateProcess.fulfilled(
        {
          flow: process('process-a', 'Process A'),
          operateProcessCode: 'process-a',
          revision: 1,
          warnings: [],
        },
        'load-a',
        'process-a'
      )
    )
    state = editorReducer(state, updateProcessInfo({ name: 'Snapshot A' }))
    const savedToken = state.changeToken
    state = editorReducer(state, saveProcess.pending('save-a', undefined))
    state = editorReducer(state, updateProcessInfo({ name: 'Snapshot B' }))
    state = editorReducer(
      state,
      saveProcess.fulfilled(
        {
          id: 'process-a',
          updatedAt: 2,
          operateRevision: 2,
          savedChangeToken: savedToken,
        },
        'save-a',
        undefined
      )
    )

    expect(state.operateBinding?.revision).toBe(2)
    expect(state.isModified).toBe(true)
  })
})
