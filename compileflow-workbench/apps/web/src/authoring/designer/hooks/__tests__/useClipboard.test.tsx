import { act, renderHook } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { clearDesignerClipboard } from '../clipboardStorage'
import { useClipboard } from '../useClipboard'

import { store, UndoActionCreators } from '@/app/store'
import { loadOperateProcess } from '@/authoring/designer/store/editorSlice'
import type { UnifiedProcessDefinition } from '@/authoring/designer/types/flowDefinition'

vi.mock('antd', () => ({
  App: { useApp: () => ({ message: { warning: vi.fn(), success: vi.fn(), error: vi.fn() } }) },
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

const flow: UnifiedProcessDefinition = {
  id: 'order',
  code: 'order',
  name: 'Order',
  type: 'TBBPM',
  nodes: [
    { id: 'loop', type: 'while', position: { x: 0, y: 0 }, properties: {} },
    { id: 'first', type: 'start', parentId: 'loop', position: { x: 10, y: 10 }, properties: {} },
    { id: 'task', type: 'autoTask', parentId: 'loop', position: { x: 20, y: 20 }, properties: {} },
    { id: 'last', type: 'end', parentId: 'loop', position: { x: 30, y: 30 }, properties: {} },
  ],
  connections: [
    { id: 'a', sourceId: 'first', targetId: 'task' },
    { id: 'b', sourceId: 'task', targetId: 'last', waypoints: [{ x: 25, y: 26 }] },
  ],
}

function load(document: UnifiedProcessDefinition) {
  store.dispatch(loadOperateProcess.pending(document.id, document.code))
  store.dispatch(
    loadOperateProcess.fulfilled(
      { flow: document, operateProcessCode: document.code, revision: 1, warnings: [] },
      document.id,
      document.code
    )
  )
  store.dispatch(UndoActionCreators.clearHistory())
}

const wrapper = ({ children }: PropsWithChildren) => <Provider store={store}>{children}</Provider>

describe('designer graph clipboard', () => {
  beforeEach(() => {
    clearDesignerClipboard()
    load(flow)
  })

  it('copies container descendants and internal edges as one undoable graph', () => {
    const { result } = renderHook(useClipboard, { wrapper })
    act(() => {
      result.current.copyNodes(['loop'])
      result.current.pasteNodes()
    })
    const pasted = store.getState().editor.present.currentProcess!
    expect(pasted.nodes).toHaveLength(8)
    expect(pasted.connections).toHaveLength(4)
    const copies = pasted.nodes.slice(4)
    const container = copies.find((node) => node.type === 'while')!
    expect(container.name).toBeUndefined()
    expect(copies.filter((node) => node.parentId === container.id)).toHaveLength(3)
    const copyIds = new Set(copies.map((node) => node.id))
    expect(
      pasted.connections
        .slice(2)
        .every((edge) => copyIds.has(edge.sourceId) && copyIds.has(edge.targetId))
    ).toBe(true)
    expect(pasted.connections[3].waypoints).toEqual([{ x: 75, y: 76 }])
    act(() => {
      store.dispatch(UndoActionCreators.undo())
    })
    expect(store.getState().editor.present.currentProcess).toEqual(flow)
  })

  it('detaches a copied child from a container that was not copied', () => {
    const { result } = renderHook(useClipboard, { wrapper })
    act(() => {
      result.current.copyNodes(['task'])
      result.current.pasteNodes()
    })
    const nodes = store.getState().editor.present.currentProcess!.nodes
    expect(nodes[nodes.length - 1].parentId).toBeUndefined()
  })

  it('duplicates the canonical node properties instead of stale canvas data', () => {
    const configured: UnifiedProcessDefinition = {
      ...flow,
      nodes: flow.nodes.map((node) =>
        node.id === 'task'
          ? {
              ...node,
              properties: {
                action: {
                  actionType: 'java',
                  className: 'com.example.OrderService',
                  method: 'execute',
                },
              },
            }
          : node
      ),
    }
    load(configured)
    const { result } = renderHook(useClipboard, { wrapper })

    act(() => result.current.duplicateNodes(['task']))

    const nodes = store.getState().editor.present.currentProcess!.nodes
    expect(nodes).toHaveLength(5)
    expect(nodes[4]).toMatchObject({
      parentId: undefined,
      position: { x: 70, y: 70 },
      properties: configured.nodes[2].properties,
    })
    expect(store.getState().editor.past).toHaveLength(1)
  })

  it('rejects a clipboard from a different model without modifying the destination', () => {
    const { result } = renderHook(useClipboard, { wrapper })
    act(() => result.current.copyNodes(['task']))
    const bpmn: UnifiedProcessDefinition = {
      ...flow,
      id: 'bpmn',
      type: 'BPMN',
      nodes: [],
      connections: [],
    }
    act(() => load(bpmn))
    expect(result.current.hasClipboardData()).toBe(false)
    act(() => result.current.pasteNodes())
    expect(store.getState().editor.present.currentProcess).toEqual(bpmn)
    expect(store.getState().editor.past).toHaveLength(0)
  })

  it('remaps BPMN default flow and message references without changing the originals', () => {
    const bpmn: UnifiedProcessDefinition = {
      ...flow,
      type: 'BPMN',
      nodes: [
        {
          id: 'gateway',
          type: 'bpmn:ExclusiveGateway',
          position: { x: 0, y: 0 },
          properties: { default: 'edge' },
        },
        {
          id: 'wait',
          type: 'bpmn:ReceiveTask',
          position: { x: 100, y: 0 },
          properties: { messageRef: 'message' },
        },
      ],
      connections: [{ id: 'edge', sourceId: 'gateway', targetId: 'wait' }],
      messages: [{ id: 'message', name: 'Payment' }],
    }
    load(bpmn)
    const { result } = renderHook(useClipboard, { wrapper })
    act(() => {
      result.current.copyNodes(['gateway', 'wait'])
      result.current.pasteNodes()
    })
    const pasted = store.getState().editor.present.currentProcess!
    expect(pasted.nodes.slice(0, 2)).toEqual(bpmn.nodes)
    expect(pasted.nodes[2].properties).toMatchObject({ default: pasted.connections[1].id })
    expect(pasted.nodes[3].properties).toMatchObject({ messageRef: pasted.messages![1].id })
    expect(pasted.messages).toHaveLength(2)
    act(() => {
      store.dispatch(UndoActionCreators.undo())
    })
    expect(store.getState().editor.present.currentProcess).toEqual(bpmn)
  })
})
