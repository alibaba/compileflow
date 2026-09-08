import { type Cell, type Edge, type Graph, type Node } from '@antv/x6'
import { type MutableRefObject, useEffect, useRef } from 'react'

import { selectEdge, selectNode, showContextMenu } from '../store/uiSlice'
import type { ProcessConnection } from '../types/flowDefinition'

import type { AppDispatch } from '@/app/store'

export type SyncingRef = MutableRefObject<boolean>
export type ConnectionsRef<Connection extends ProcessConnection> = MutableRefObject<Connection[]>

export function useLatestRef<T>(value: T): MutableRefObject<T> {
  const ref = useRef(value)
  useEffect(() => {
    ref.current = value
  }, [value])
  return ref
}

export function runGraphMutation(isSyncingRef: SyncingRef, mutation: () => void): void {
  isSyncingRef.current = true
  try {
    mutation()
  } finally {
    isSyncingRef.current = false
  }
}

export function selectionMoveRoots(graph: Graph, nodes: Node[], parentDataKey: string): Node[] {
  const selectedIds = new Set(nodes.map((node) => node.id))
  return nodes.filter((node) => {
    let parentId = (node.getData() as Record<string, unknown>)[parentDataKey]
    const visited = new Set<string>()
    while (typeof parentId === 'string' && !visited.has(parentId)) {
      if (selectedIds.has(parentId)) return false
      visited.add(parentId)
      const parent = graph.getCellById(parentId)
      if (!parent?.isNode()) break
      parentId = (parent.getData() as Record<string, unknown>)[parentDataKey]
    }
    return true
  })
}

export function selectionMoveTargets(graph: Graph, movedNode: Node, parentDataKey: string): Node[] {
  const selectedNodes = graph.getSelectedCells().filter((cell): cell is Node => cell.isNode())
  if (selectedNodes.length < 2 || !selectedNodes.some((node) => node.id === movedNode.id)) {
    return [movedNode]
  }
  return selectionMoveRoots(graph, selectedNodes, parentDataKey)
}

function showGraphContextMenu(
  dispatch: AppDispatch,
  event: MouseEvent,
  type: 'canvas' | 'node' | 'edge',
  targetId?: string
): void {
  event.preventDefault()
  dispatch(showContextMenu({ position: { x: event.clientX, y: event.clientY }, type, targetId }))
}

export function registerSelectionEvents<Connection extends ProcessConnection>(
  graph: Graph,
  dispatch: AppDispatch,
  connectionsRef: ConnectionsRef<Connection>
): void {
  graph.on('node:mousedown', ({ e, node }: { e: MouseEvent; node: Node }) => {
    const additiveSelection = e.ctrlKey || e.metaKey
    const alreadySelected = graph.getSelectedCells().some((cell) => cell.id === node.id)
    if (!additiveSelection && !alreadySelected) graph.resetSelection(node)
  })

  graph.on('node:click', ({ node }: { node: Node }) => {
    dispatch(selectNode(node.id))
  })

  graph.on('edge:click', ({ edge }: { edge: Edge }) => {
    const connection = connectionsRef.current.find((candidate) => candidate.id === edge.id)
    dispatch(selectEdge(connection?.id ?? null))
  })

  graph.on('blank:click', () => {
    dispatch(selectNode(null))
    dispatch(selectEdge(null))
  })
}

export function registerContextMenuEvents(graph: Graph, dispatch: AppDispatch): void {
  graph.on('node:contextmenu', ({ e, node }: { e: MouseEvent; node: Node }) => {
    showGraphContextMenu(dispatch, e, 'node', node.id)
  })

  graph.on('edge:contextmenu', ({ e, edge }: { e: MouseEvent; edge: Edge }) => {
    showGraphContextMenu(dispatch, e, 'edge', edge.id)
  })

  graph.on('blank:contextmenu', ({ e }: { e: MouseEvent }) => {
    showGraphContextMenu(dispatch, e, 'canvas')
  })
}

export function registerSelectionSync<Connection extends ProcessConnection>(
  graph: Graph,
  dispatch: AppDispatch,
  connectionsRef: ConnectionsRef<Connection>,
  isSyncingRef: SyncingRef
): void {
  graph.on('selection:changed', ({ selected }: { selected: Cell[] }) => {
    if (isSyncingRef.current) return

    if (selected.length === 0) {
      dispatch(selectNode(null))
      dispatch(selectEdge(null))
      return
    }
    if (selected.length > 1) return

    const cell = selected[0]
    if (cell.isNode()) {
      dispatch(selectNode(cell.id))
      return
    }
    if (cell.isEdge()) {
      const connection = connectionsRef.current.find((candidate) => candidate.id === cell.id)
      dispatch(selectEdge(connection?.id ?? null))
    }
  })
}
