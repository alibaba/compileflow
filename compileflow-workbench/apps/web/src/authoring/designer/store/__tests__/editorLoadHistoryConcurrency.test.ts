import { configureStore } from '@reduxjs/toolkit'
import undoable, { ActionCreators } from 'redux-undo'
import { describe, expect, it } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import editorReducer, { loadProcess, updateProcessInfo } from '../editorSlice'

function process(id: string): UnifiedProcessDefinition {
  return {
    id,
    code: id,
    name: id,
    type: 'TBBPM',
    nodes: [],
    connections: [],
    createdAt: 1,
    updatedAt: 1,
  }
}

describe('editor load and undo-history concurrency', () => {
  it('does not let a stale load replace the active process or alter its undo history', () => {
    const store = configureStore({
      reducer: {
        editor: undoable(editorReducer, {
          filter: (action) => action.type === updateProcessInfo.type,
        }),
      },
    })
    store.dispatch(loadProcess.pending('load-a', 'process-a'))
    store.dispatch(loadProcess.pending('load-b', 'process-b'))
    store.dispatch(
      loadProcess.fulfilled({ flow: process('process-b'), warnings: [] }, 'load-b', 'process-b')
    )
    store.dispatch(ActionCreators.clearHistory())
    store.dispatch(updateProcessInfo({ name: 'Process B edited' }))
    expect(store.getState().editor.past).toHaveLength(1)

    store.dispatch(
      loadProcess.fulfilled({ flow: process('process-a'), warnings: [] }, 'load-a', 'process-a')
    )

    expect(store.getState().editor.present.currentProcess?.id).toBe('process-b')
    expect(store.getState().editor.present.currentProcess?.name).toBe('Process B edited')
    expect(store.getState().editor.past).toHaveLength(1)
  })
})
