import { Edge, Graph, Node } from '@antv/x6'
import { type MutableRefObject, useEffect, useRef } from 'react'

import { scheduleInitialGraphFit } from '../canvas/graphViewport'
import type { BaseConnection, BaseNode } from '../types/flowDefinition'

export interface UseCanvasSyncOptions<
  DesignerNode extends BaseNode,
  DesignerConnection extends BaseConnection,
> {
  nodes: DesignerNode[]
  connections: DesignerConnection[]
  selectedNodeId: string | null
  nodeToX6Cell: (node: DesignerNode) => Record<string, unknown>
  connectionToX6Edge: (connection: DesignerConnection) => Record<string, unknown>
  checkNodeChanged: (cell: Node, node: DesignerNode) => boolean
  syncNodeToCell: (cell: Node, node: DesignerNode) => void
  checkEdgeChanged: (edge: Edge, connection: DesignerConnection) => boolean
  syncEdgeToCell: (edge: Edge, connection: DesignerConnection) => void
}

export function useCanvasSync<
  DesignerNode extends BaseNode,
  DesignerConnection extends BaseConnection,
>(
  graphRef: MutableRefObject<Graph | null>,
  isSyncingRef: MutableRefObject<boolean>,
  options: UseCanvasSyncOptions<DesignerNode, DesignerConnection>
): void {
  const { nodes, connections, selectedNodeId } = options

  const optionsRef = useRef(options)
  optionsRef.current = options
  const hasFittedInitialContentRef = useRef(false)

  useEffect(() => {
    const graph = graphRef.current
    if (!graph || isSyncingRef.current) return

    const {
      nodeToX6Cell,
      connectionToX6Edge,
      checkNodeChanged,
      syncNodeToCell,
      checkEdgeChanged,
      syncEdgeToCell,
    } = optionsRef.current

    isSyncingRef.current = true

    let cancelScheduledFit: (() => void) | undefined

    try {
      const graphNodes = graph.getNodes()
      const graphNodeIds = new Set(graphNodes.map((n) => n.id))

      const nodeMap = new Map<string, DesignerNode>()
      nodes.forEach((n) => nodeMap.set(n.id, n))
      const contextNodeIds = new Set(nodeMap.keys())

      // Remove nodes not in state
      graphNodeIds.forEach((id) => {
        if (!contextNodeIds.has(id)) {
          const cell = graph.getCellById(id)
          if (cell) graph.removeCell(cell)
        }
      })

      // Add or update nodes
      contextNodeIds.forEach((id) => {
        const node = nodeMap.get(id)!
        if (!graphNodeIds.has(id)) {
          if (!graph.getCellById(id)) graph.addNode(nodeToX6Cell(node))
        } else {
          const cell = graph.getCellById(id)
          if (cell?.isNode()) {
            const nextCell = nodeToX6Cell(node)
            if (typeof nextCell.shape === 'string' && cell.shape !== nextCell.shape) {
              graph.removeCell(cell)
              graph.addNode(nextCell)
            } else if (checkNodeChanged(cell, node)) {
              syncNodeToCell(cell, node)
            }
          }
        }
      })

      const graphEdges = graph.getEdges()
      const graphEdgeIds = new Set(graphEdges.map((e) => e.id))

      const connMap = new Map<string, DesignerConnection>()
      connections.forEach((c) => {
        if (c.id) connMap.set(c.id, c)
      })
      const contextConnIds = new Set(connMap.keys())

      // Remove edges not in state
      graphEdgeIds.forEach((id) => {
        if (!contextConnIds.has(id)) {
          const cell = graph.getCellById(id)
          if (cell) graph.removeCell(cell)
        }
      })

      // Add or update edges
      contextConnIds.forEach((id) => {
        const conn = connMap.get(id)!
        if (!graphEdgeIds.has(id)) {
          graph.addEdge(connectionToX6Edge(conn))
        } else {
          const edge = graph.getCellById(id)
          if (edge?.isEdge() && checkEdgeChanged(edge, conn)) {
            syncEdgeToCell(edge, conn)
          }
        }
      })
    } finally {
      isSyncingRef.current = false
    }

    if (nodes.length > 0 && !hasFittedInitialContentRef.current) {
      hasFittedInitialContentRef.current = true
      cancelScheduledFit = scheduleInitialGraphFit(graph)
    }

    return () => cancelScheduledFit?.()
  }, [nodes, connections, graphRef, isSyncingRef])

  // Handle selected node highlighting separately with optimized performance
  const prevSelectedIdRef = useRef<string | null>(null)

  useEffect(() => {
    const graph = graphRef.current
    if (!graph) return

    const prevSelectedId = prevSelectedIdRef.current

    // 只对上一次和当前选中的节点做增量更新，避免全量遍历
    if (prevSelectedId && prevSelectedId !== selectedNodeId) {
      const prevCell = graph.getCellById(prevSelectedId)
      if (prevCell?.isNode()) {
        prevCell.removeTools()
      }
    }

    // 添加边界工具到新选中节点
    if (selectedNodeId) {
      const cell = graph.getCellById(selectedNodeId)
      if (cell?.isNode()) {
        cell.addTools([
          {
            name: 'boundary',
            args: {
              attrs: {
                fill: '#1677ff',
                stroke: '#1677ff',
                'stroke-width': 2,
                'fill-opacity': 0.1,
              },
            },
          },
        ])
      }
    }

    // 更新上一次选中ID
    prevSelectedIdRef.current = selectedNodeId
  }, [selectedNodeId, graphRef])
}
