import { beforeEach, describe, expect, it } from 'vitest'

import { loadOperateProcess, saveProcess, updateProcessInfo } from '../editorSlice'

import { store, UndoActionCreators } from '@/app/store'

describe('editor history persistence metadata', () => {
  beforeEach(() => {
    store.dispatch(loadOperateProcess.pending('document', 'draft'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow: {
            id: 'draft',
            code: 'draft',
            name: 'Original',
            type: 'TBBPM',
            nodes: [],
            connections: [],
          },
          operateProcessCode: 'draft',
          revision: 1,
          warnings: [],
        },
        'document',
        'draft'
      )
    )
    store.dispatch(UndoActionCreators.clearHistory())
  })

  it('undo and redo preserve the latest CAS revision and compare against saved content', () => {
    store.dispatch(updateProcessInfo({ name: 'Saved' }))
    const savedChangeToken = store.getState().editor.present.changeToken
    store.dispatch(saveProcess.pending('save', undefined))
    store.dispatch(
      saveProcess.fulfilled(
        {
          id: 'draft',
          updatedAt: 1788739200000,
          operateRevision: 2,
          savedChangeToken,
          documentRequestId: 'document',
        },
        'save',
        undefined
      )
    )
    const savedTime = store.getState().editor.present.lastSavedTime
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present).toMatchObject({
      currentProcess: { name: 'Original' },
      isModified: true,
      operateBinding: { revision: 2 },
      lastSavedTime: savedTime,
    })
    store.dispatch(UndoActionCreators.redo())
    expect(store.getState().editor.present).toMatchObject({
      currentProcess: { name: 'Saved' },
      isModified: false,
      operateBinding: { revision: 2 },
    })
  })

  it('undo does not cancel or forget an in-flight save', () => {
    store.dispatch(updateProcessInfo({ name: 'Pending' }))
    store.dispatch(saveProcess.pending('save', undefined))
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present).toMatchObject({
      currentProcess: { name: 'Original' },
      isSaving: true,
      activeSaveRequestId: 'save',
    })
  })
})
