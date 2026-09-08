import type { Graph, Node } from '@antv/x6'
import { configureStore } from '@reduxjs/toolkit'
import { describe, expect, it } from 'vitest'

import uiReducer from '../../store/uiSlice'
import {
  registerSelectionEvents,
  registerSelectionSync,
  selectionMoveRoots,
  selectionMoveTargets,
} from '../graphEvents'

import type { AppDispatch } from '@/app/store'

describe('canvas edge selection', () => {
  it.each(['edge:click', 'selection:changed'])(
    'keeps the edge properties selected after %s',
    (eventName) => {
      const handlers = new Map<string, (event: unknown) => void>()
      const graph = {
        on: (name: string, handler: (event: unknown) => void) => handlers.set(name, handler),
      } as unknown as Graph
      const store = configureStore({ reducer: { ui: uiReducer } })
      const connection = { id: 'edge-1', sourceId: 'start', targetId: 'end' }
      const connections = { current: [connection] }
      registerSelectionEvents(graph, store.dispatch as AppDispatch, connections)
      registerSelectionSync(graph, store.dispatch as AppDispatch, connections, { current: false })
      const edge = { id: connection.id, isNode: () => false, isEdge: () => true }

      handlers.get(eventName)?.({ edge, selected: [edge] })

      expect(store.getState().ui.selectedEdgeId).toBe(connection.id)
      expect(store.getState().ui.selectedNodeId).toBeNull()
      expect(store.getState().ui.rightPanelTab).toBe('edge')
    }
  )
})

describe('pointer selection before dragging', () => {
  it('selects an unselected node on pointer down so stale selected nodes do not follow its drag', () => {
    const handlers = new Map<string, (event: unknown) => void>()
    const node = { id: 'target' } as Node
    const resetSelection = vi.fn()
    const graph = {
      on: (name: string, handler: (event: unknown) => void) => handlers.set(name, handler),
      getSelectedCells: () => [{ id: 'stale' }],
      resetSelection,
    } as unknown as Graph
    const store = configureStore({ reducer: { ui: uiReducer } })

    registerSelectionEvents(graph, store.dispatch as AppDispatch, { current: [] })
    handlers.get('node:mousedown')?.({ e: { ctrlKey: false, metaKey: false }, node })

    expect(resetSelection).toHaveBeenCalledWith(node)
  })

  it('preserves the current group during additive selection', () => {
    const handlers = new Map<string, (event: unknown) => void>()
    const resetSelection = vi.fn()
    const graph = {
      on: (name: string, handler: (event: unknown) => void) => handlers.set(name, handler),
      getSelectedCells: () => [],
      resetSelection,
    } as unknown as Graph
    const store = configureStore({ reducer: { ui: uiReducer } })

    registerSelectionEvents(graph, store.dispatch as AppDispatch, { current: [] })
    handlers.get('node:mousedown')?.({
      e: { ctrlKey: true, metaKey: false },
      node: { id: 'target' },
    })

    expect(resetSelection).not.toHaveBeenCalled()
  })
})

describe('selection move roots', () => {
  it('excludes selected descendants so a container move is applied once', () => {
    const byId = new Map<string, { id: string; getData: () => Record<string, string> }>()
    const nodes = [
      { id: 'deep-child', getData: () => ({ parent: 'unselected-middle' }) },
      { id: 'container', getData: () => ({}) },
      { id: 'peer', getData: () => ({ parent: 'outside' }) },
    ]
    ;[...nodes, { id: 'unselected-middle', getData: () => ({ parent: 'container' }) }].forEach(
      (node) => byId.set(node.id, node)
    )
    const graph = {
      getCellById: (id: string) => {
        const node = byId.get(id)
        return node ? { ...node, isNode: () => true } : null
      },
    } as unknown as Graph

    expect(
      selectionMoveRoots(graph, nodes as unknown as Node[], 'parent').map((node) => node.id)
    ).toEqual(['container', 'peer'])
  })

  it('persists every selected root when one selected node is dragged', () => {
    const selected = [
      { id: 'dragged', isNode: () => true, getData: () => ({}) },
      { id: 'peer', isNode: () => true, getData: () => ({}) },
    ] as unknown as Node[]
    const graph = {
      getSelectedCells: () => selected,
    } as unknown as Graph

    expect(selectionMoveTargets(graph, selected[0], 'parent').map((node) => node.id)).toEqual([
      'dragged',
      'peer',
    ])
  })

  it('persists only the moved node when it is not part of a multi-selection', () => {
    const moved = { id: 'dragged', isNode: () => true, getData: () => ({}) } as unknown as Node
    const graph = {
      getSelectedCells: () => [{ id: 'other', isNode: () => true, getData: () => ({}) }],
    } as unknown as Graph

    expect(selectionMoveTargets(graph, moved, 'parent')).toEqual([moved])
  })
})
