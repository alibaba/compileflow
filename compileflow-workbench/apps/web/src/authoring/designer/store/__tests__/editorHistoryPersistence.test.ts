import { beforeEach, describe, expect, it } from 'vitest'

import {
  addNode,
  loadOperateProcess,
  moveNode,
  replaceImportedProcess,
  saveProcess,
  updateProcessInfo,
} from '../editorSlice'

import { store, UndoActionCreators } from '@/app/store'
import type { TbbpmNode } from '@/authoring/designer/types/tbbpm'

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

  it('undoes and redoes an imported document as one atomic edit', () => {
    store.dispatch(
      replaceImportedProcess({
        xml: '<bpm code="draft" name="Imported"><start id="imported-start"/></bpm>',
        type: 'TBBPM',
        documentRequestId: 'document',
      })
    )

    expect(store.getState().editor.present.currentProcess?.nodes).toMatchObject([
      { id: 'imported-start', type: 'start' },
    ])
    expect(store.getState().editor.past).toHaveLength(1)
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present.currentProcess?.nodes).toEqual([])
    store.dispatch(UndoActionCreators.redo())
    expect(store.getState().editor.present.currentProcess?.nodes).toMatchObject([
      { id: 'imported-start', type: 'start' },
    ])
  })

  it('does not add malformed XML to history', () => {
    expect(() =>
      store.dispatch(
        replaceImportedProcess({
          xml: '<bpm><broken></bpm>',
          type: 'TBBPM',
          documentRequestId: 'document',
        })
      )
    ).toThrow()

    expect(store.getState().editor.past).toHaveLength(0)
    expect(store.getState().editor.present.currentProcess).toMatchObject({
      name: 'Original',
      nodes: [],
    })
  })

  it('keeps separate node drags undoable while grouping one multi-node layout action', () => {
    const node = (id: string, x: number): TbbpmNode => ({
      id,
      type: 'note',
      name: id,
      position: { x, y: 0 },
      properties: { comment: '' },
    })
    store.dispatch(addNode(node('a', 0)))
    store.dispatch(addNode(node('b', 100)))
    store.dispatch(UndoActionCreators.clearHistory())

    store.dispatch(moveNode({ id: 'a', x: 0, y: 0 }))
    expect(store.getState().editor.past).toHaveLength(0)

    store.dispatch(moveNode({ id: 'a', x: 20, y: 0 }))
    store.dispatch(moveNode({ id: 'a', x: 40, y: 0 }))
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present.currentProcess?.nodes[0].position.x).toBe(20)
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present.currentProcess?.nodes[0].position.x).toBe(0)

    store.dispatch(moveNode({ id: 'a', x: 20, y: 0 }, 'align-1'))
    store.dispatch(moveNode({ id: 'b', x: 20, y: 0 }, 'align-1'))
    store.dispatch(UndoActionCreators.undo())
    expect(
      store.getState().editor.present.currentProcess?.nodes.map((item) => item.position.x)
    ).toEqual([0, 100])
  })
})
