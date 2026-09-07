import type { Graph } from '@antv/x6'
import { configureStore } from '@reduxjs/toolkit'
import { describe, expect, it } from 'vitest'

import uiReducer from '../../store/uiSlice'
import { registerSelectionEvents, registerSelectionSync } from '../graphEvents'

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

      expect(store.getState().ui.selectedEdge).toEqual(connection)
      expect(store.getState().ui.selectedNodeId).toBeNull()
      expect(store.getState().ui.rightPanelTab).toBe('edge')
    }
  )
})
