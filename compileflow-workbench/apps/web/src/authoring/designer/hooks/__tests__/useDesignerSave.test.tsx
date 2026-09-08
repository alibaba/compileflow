import type { Graph } from '@antv/x6'
import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useDesignerPageActions } from '../useDesignerPageActions'

import { store, UndoActionCreators } from '@/app/store'
import { loadOperateProcess, updateProcessInfo } from '@/authoring/designer/store/editorSlice'
import type { UnifiedProcessDefinition } from '@/authoring/designer/types/flowDefinition'

const mocks = vi.hoisted(() => ({
  update: vi.fn(),
  exportBoth: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  warning: vi.fn(),
}))
vi.mock('antd', () => ({ App: { useApp: () => ({ message: mocks }) } }))
vi.mock('@/shared/api/processes', () => ({
  updateProcess: mocks.update,
  getProcessByCode: vi.fn(),
}))
vi.mock('@/authoring/designer/canvas/canvasExport', () => ({ exportBoth: mocks.exportBoth }))

const flow: UnifiedProcessDefinition = {
  id: 'draft',
  code: 'draft',
  name: 'Draft',
  type: 'TBBPM',
  nodes: [],
  connections: [],
}

function load(requestId: string) {
  store.dispatch(loadOperateProcess.pending(requestId, 'draft'))
  store.dispatch(
    loadOperateProcess.fulfilled(
      {
        flow,
        operateProcessCode: 'draft',
        revision: 1,
        warnings: [],
      },
      requestId,
      'draft'
    )
  )
  store.dispatch(UndoActionCreators.clearHistory())
}

describe('designer save navigation result', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    load('initial')
  })

  it('copies every selected canvas node after select-all', () => {
    const copyNodes = vi.fn()
    const graph = {
      getSelectedCells: () => [
        { id: 'first', isNode: () => true },
        { id: 'edge', isNode: () => false },
        { id: 'second', isNode: () => true },
      ],
    } as unknown as Graph
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph,
        copyNodes,
        pasteNodes: vi.fn(),
        selectedNodeId: 'stale-single-selection',
      })
    )

    act(() => result.current.actions.onCopy())

    expect(copyNodes).toHaveBeenCalledWith(['first', 'second'])
  })

  it('does not copy a stale single selection when the canvas contains only selected edges', () => {
    const copyNodes = vi.fn()
    const graph = {
      getSelectedCells: () => [{ id: 'edge', isNode: () => false }],
    } as unknown as Graph
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph,
        copyNodes,
        pasteNodes: vi.fn(),
        selectedNodeId: 'stale-single-selection',
      })
    )

    act(() => result.current.actions.onCopy())

    expect(copyNodes).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledOnce()
  })

  it('resolves the active theme background before exporting the canvas', async () => {
    document.documentElement.style.setProperty('--color-bg-base', '#141414')
    const graph = {} as Graph
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph,
        copyNodes: vi.fn(),
        pasteNodes: vi.fn(),
        selectedNodeId: null,
      })
    )

    try {
      await act(async () => result.current.actions.onExportImage())
      expect(mocks.exportBoth).toHaveBeenCalledWith(graph, {
        filename: 'Draft',
        backgroundColor: '#141414',
      })
    } finally {
      document.documentElement.style.removeProperty('--color-bg-base')
    }
  })

  it('keeps the modal XML editor open after a synchronous parse failure', () => {
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph: null,
        copyNodes: vi.fn(),
        pasteNodes: vi.fn(),
        selectedNodeId: null,
      })
    )
    act(() => result.current.actions.onShowXmlEditor())
    act(() => result.current.xmlEditorState.onChange('<invalid'))
    act(() => result.current.xmlEditorState.onApply())

    expect(result.current.xmlEditorState.open).toBe(true)
    expect(result.current.xmlEditorState.value).toBe('<invalid')
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.error).toHaveBeenCalledTimes(1)
  })

  it('ignores a late file read error after document reload', async () => {
    let input!: HTMLInputElement
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (
      this: HTMLInputElement
    ) {
      input = this
    })
    let fail!: (error: Error) => void
    const reading = new Promise<string>((_resolve, reject) => {
      fail = reject
    })
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph: null,
        copyNodes: vi.fn(),
        pasteNodes: vi.fn(),
        selectedNodeId: null,
      })
    )
    try {
      act(() => result.current.actions.onImportXml())
      Object.defineProperty(input, 'files', { value: [{ text: () => reading }] })
      act(() => {
        input.dispatchEvent(new Event('change'))
      })
      load('new-document')
      await act(async () => {
        fail(new Error('obsolete read failure'))
        await reading.catch(() => {})
      })
      expect(mocks.error).not.toHaveBeenCalled()
    } finally {
      click.mockRestore()
    }
  })

  it('does not apply a modal XML buffer to a same-id reloaded document', async () => {
    const { result, rerender } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph: null,
        copyNodes: vi.fn(),
        pasteNodes: vi.fn(),
        selectedNodeId: null,
      })
    )
    act(() => result.current.actions.onShowXmlEditor())
    act(() =>
      result.current.xmlEditorState.onChange(
        '<bpm code="obsolete" name="Obsolete"><start id="old-start"/></bpm>'
      )
    )
    act(() => load('replacement'))
    rerender()
    await act(async () => result.current.xmlEditorState.onApply())
    expect(store.getState().editor.present.currentProcess?.name).toBe('Draft')
    expect(store.getState().editor.present.currentProcess?.nodes).toEqual([])
    expect(mocks.success).not.toHaveBeenCalled()
  })

  it('does not import a late file read into a reloaded document with the same process id', async () => {
    let input!: HTMLInputElement
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(function (
      this: HTMLInputElement
    ) {
      input = this
    })
    let finish!: (xml: string) => void
    const reading = new Promise<string>((resolve) => {
      finish = resolve
    })
    const { result } = renderHook(() =>
      useDesignerPageActions({
        currentProcess: store.getState().editor.present.currentProcess,
        operateBinding: store.getState().editor.present.operateBinding,
        dispatch: store.dispatch,
        navigate: vi.fn(),
        graph: null,
        copyNodes: vi.fn(),
        pasteNodes: vi.fn(),
        selectedNodeId: null,
      })
    )
    try {
      act(() => result.current.actions.onImportXml())
      Object.defineProperty(input, 'files', { value: [{ text: () => reading }] })
      act(() => {
        input.dispatchEvent(new Event('change'))
      })
      load('new-document')
      await act(async () => {
        finish('<bpm code="obsolete" name="Obsolete"/>')
        await reading
      })
      expect(store.getState().editor.present.currentProcess?.name).toBe('Draft')
      expect(store.getState().editor.present.isModified).toBe(false)
    } finally {
      click.mockRestore()
    }
  })

  it.each(['edit', 'reload', 'unchanged'] as const)(
    'only permits leaving when the saved document is still clean: %s',
    async (change) => {
      let finish!: (value: unknown) => void
      mocks.update.mockReturnValue(
        new Promise((resolve) => {
          finish = resolve
        })
      )
      store.dispatch(updateProcessInfo({ name: 'Snapshot' }))
      const { result } = renderHook(() =>
        useDesignerPageActions({
          currentProcess: store.getState().editor.present.currentProcess,
          operateBinding: store.getState().editor.present.operateBinding,
          dispatch: store.dispatch,
          navigate: vi.fn(),
          graph: null,
          copyNodes: vi.fn(),
          pasteNodes: vi.fn(),
          selectedNodeId: null,
        })
      )
      let saving!: Promise<boolean>
      act(() => {
        saving = result.current.actions.onSave()
      })
      await waitFor(() => expect(mocks.update).toHaveBeenCalledOnce())
      if (change === 'edit') store.dispatch(updateProcessInfo({ name: 'Newer unsaved edit' }))
      if (change === 'reload') load('same-id-reload')
      let canLeave: boolean | undefined
      await act(async () => {
        finish({ revision: 2, updatedAt: '2026-09-07T00:00:00.000Z' })
        canLeave = await saving
      })
      expect(canLeave).toBe(change === 'unchanged')
      if (change === 'edit') {
        expect(store.getState().editor.present.currentProcess?.name).toBe('Newer unsaved edit')
        expect(store.getState().editor.present.isModified).toBe(true)
      }
    }
  )
})
