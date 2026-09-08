import { configureStore } from '@reduxjs/toolkit'
import undoable, { ActionCreators } from 'redux-undo'
import { describe, expect, it } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import editorReducer, {
  createProcess,
  deleteProcess,
  loadOperateProcess,
  loadProcess,
  replaceImportedProcess,
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
  const createArg = { type: 'TBBPM' as const }
  const transitions = [
    [
      createProcess.pending('old', createArg),
      createProcess.fulfilled(process('old', 'Old'), 'old', createArg),
      createProcess.rejected(new Error('old failure'), 'old', createArg),
    ],
    [
      deleteProcess.pending('old', 'old'),
      deleteProcess.fulfilled('old', 'old', 'old'),
      deleteProcess.rejected(new Error('old failure'), 'old', 'old'),
    ],
  ] as const

  it.each(transitions)(
    'fences obsolete $0.type completion and failure after navigation',
    (pending, fulfilled, rejected) => {
      for (const obsolete of [fulfilled, rejected]) {
        let state = editorReducer(undefined, pending)
        state = editorReducer(state, loadProcess.pending('new', 'new'))
        expect(editorReducer(state, obsolete)).toBe(state)
        state = editorReducer(
          state,
          loadProcess.fulfilled({ flow: process('new', 'New'), warnings: [] }, 'new', 'new')
        )
        expect(editorReducer(state, obsolete)).toBe(state)
      }
    }
  )

  it.each(transitions)('lets $0.type supersede an older navigation', (pending, fulfilled) => {
    let state = editorReducer(undefined, loadProcess.pending('load', 'loaded'))
    state = editorReducer(state, pending)
    expect(
      editorReducer(
        state,
        loadProcess.fulfilled({ flow: process('loaded', 'Loaded'), warnings: [] }, 'load', 'loaded')
      )
    ).toBe(state)
    state = editorReducer(state, fulfilled)
    expect(state.activeContentRequestId).toBeNull()
    expect(state.isLoading).toBe(false)
    expect(state.currentProcess?.id ?? null).toBe(
      deleteProcess.fulfilled.match(fulfilled) ? null : 'old'
    )
  })

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

  it('fences an imported replacement prepared for an obsolete document', () => {
    let state = editorReducer(undefined, loadProcess.pending('old-document', 'old'))
    state = editorReducer(
      state,
      loadProcess.fulfilled({ flow: process('old', 'Old'), warnings: [] }, 'old-document', 'old')
    )
    const obsoleteImport = replaceImportedProcess({
      xml: '<bpm code="old" name="Obsolete"/>',
      type: 'TBBPM',
      documentRequestId: 'old-document',
    })
    state = editorReducer(state, loadProcess.pending('new-document', 'new'))
    state = editorReducer(
      state,
      loadProcess.fulfilled({ flow: process('new', 'New'), warnings: [] }, 'new-document', 'new')
    )

    expect(editorReducer(state, obsoleteImport)).toBe(state)
  })

  it('preserves the retained document binding when a remote load fails', () => {
    let state = editorReducer(undefined, loadOperateProcess.pending('load-a', 'process-a'))
    state = editorReducer(
      state,
      loadOperateProcess.fulfilled(
        {
          flow: process('process-a', 'Process A'),
          operateProcessCode: 'process-a',
          revision: 7,
          warnings: [],
        },
        'load-a',
        'process-a'
      )
    )
    state = editorReducer(state, updateProcessInfo({ name: 'Unsaved A' }))
    state = editorReducer(state, loadOperateProcess.pending('load-b', 'process-b'))
    state = editorReducer(
      state,
      loadOperateProcess.rejected(new Error('offline'), 'load-b', 'process-b')
    )

    expect(state.currentProcess?.name).toBe('Unsaved A')
    expect(state.isModified).toBe(true)
    expect(state.operateBinding).toEqual({ processCode: 'process-a', revision: 7 })
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
          documentRequestId: 'load-a',
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
          documentRequestId: 'load-a',
        },
        'save-b',
        undefined
      )
    )

    expect(state.isModified).toBe(false)
  })

  it.each(['fulfilled', 'rejected'] as const)(
    'ignores an old save %s after reloading the same remote document',
    (completion) => {
      let state = editorReducer(undefined, loadOperateProcess.pending('load-a', 'process-a'))
      const remote = {
        flow: process('process-a', 'Process A'),
        operateProcessCode: 'process-a',
        revision: 1,
        warnings: [],
      }
      state = editorReducer(state, loadOperateProcess.fulfilled(remote, 'load-a', 'process-a'))
      state = editorReducer(state, saveProcess.pending('old-save', undefined))
      state = editorReducer(state, loadOperateProcess.pending('reload', 'process-a'))
      state = editorReducer(
        state,
        loadOperateProcess.fulfilled({ ...remote, revision: 8 }, 'reload', 'process-a')
      )
      expect(state.isSaving).toBe(false)
      state = editorReducer(state, saveProcess.pending('new-save', undefined))

      const obsolete =
        completion === 'fulfilled'
          ? saveProcess.fulfilled(
              {
                id: 'process-a',
                updatedAt: 2,
                operateRevision: 2,
                savedChangeToken: null,
                documentRequestId: 'load-a',
              },
              'old-save',
              undefined
            )
          : saveProcess.rejected(null, 'old-save', undefined, {
              message: 'old save failed',
              documentRequestId: 'load-a',
            })

      expect(editorReducer(state, obsolete)).toBe(state)
      expect(state.operateBinding?.revision).toBe(8)
      expect(state.isSaving).toBe(true)
    }
  )

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
          documentRequestId: 'load-a',
        },
        'save-a',
        undefined
      )
    )

    expect(state.operateBinding?.revision).toBe(2)
    expect(state.isModified).toBe(true)
  })

  it('accepts the active document save after undo restores an earlier edit', () => {
    const store = configureStore({
      reducer: {
        editor: undoable(editorReducer, {
          filter: (action) => action.type === updateProcessInfo.type,
        }),
      },
    })
    store.dispatch(loadOperateProcess.pending('load-a', 'process-a'))
    store.dispatch(
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
    store.dispatch(ActionCreators.clearHistory())
    store.dispatch(updateProcessInfo({ name: 'A' }))
    store.dispatch(updateProcessInfo({ name: 'B' }))
    const savedChangeToken = store.getState().editor.present.changeToken
    store.dispatch(saveProcess.pending('save-a', undefined))
    store.dispatch(ActionCreators.undo())
    store.dispatch(
      saveProcess.fulfilled(
        {
          id: 'process-a',
          updatedAt: 2,
          operateRevision: 2,
          savedChangeToken,
          documentRequestId: 'load-a',
        },
        'save-a',
        undefined
      )
    )

    expect(store.getState().editor.present.operateBinding?.revision).toBe(2)
    expect(store.getState().editor.present.currentProcess?.name).toBe('A')
    expect(store.getState().editor.present.isModified).toBe(true)
  })

  it('settles an aborted save without a rejection payload', () => {
    let state = editorReducer(undefined, saveProcess.pending('save-a', undefined))
    state = editorReducer(
      state,
      saveProcess.rejected({ name: 'AbortError', message: 'Aborted' }, 'save-a', undefined)
    )
    expect(state.isSaving).toBe(false)
    expect(state.error).toBe('Aborted')
  })
})
