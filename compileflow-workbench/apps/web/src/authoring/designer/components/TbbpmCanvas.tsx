import { Edge, Graph, Node } from '@antv/x6'
import { memo, useEffect, useRef } from 'react'

import {
  type ConnectionsRef,
  registerContextMenuEvents,
  registerSelectionEvents,
  registerSelectionSync,
  runGraphMutation,
  type SyncingRef,
  useLatestRef,
} from '../canvas/graphEvents'
import { useDesignerContext } from '../context'
import { useCanvasSync } from '../hooks/useCanvasSync'
import { useX6Graph } from '../hooks/useX6Graph'
import {
  addConnection,
  addNode,
  deleteConnection,
  deleteNode,
  moveNode,
  selectTbbpmConnections,
  selectTbbpmNodes,
  updateConnection,
} from '../store/editorSlice'
import { selectNode, selectSelectedNodeId, selectShowGridlines } from '../store/uiSlice'
import type { TbbpmConnection, TbbpmNode } from '../types/tbbpm'

import { getNodeConfig, getNodeTypeByShape, registerTbbpmNodes } from './nodes/registerNodes'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import type { AppDispatch } from '@/app/store'
import { createLogger } from '@/shared/logging/logger'

import './TbbpmCanvas.css'

const logger = createLogger('TbbpmCanvas')
const PARENT_ID_DATA_KEY = '__compileflowParentId'

interface TbbpmCanvasProps {
  onGraphReady?: (graph: Graph) => void
}

type TbbpmConnectionsRef = ConnectionsRef<TbbpmConnection>

interface TbbpmGraphEventOptions {
  connectionsRef: TbbpmConnectionsRef
  dispatch: AppDispatch
  isSyncingRef: SyncingRef
}

function camelToKebab(str: string): string {
  return str.replace(/([A-Z])/g, '-$1').toLowerCase()
}

function tbbpmNodeToX6Cell(node: TbbpmNode): Record<string, unknown> {
  const nodeConfig = getNodeConfig(node.type)
  if (!nodeConfig) logger.error(`Unknown node type: ${node.type}`)
  return {
    id: node.id,
    shape: `tbbpm-${camelToKebab(node.type)}`,
    x: node.position.x,
    y: node.position.y,
    width: nodeConfig?.width || 100,
    height: nodeConfig?.height || 80,
    data: {
      ...node.properties,
      label: node.name,
      [PARENT_ID_DATA_KEY]: node.parentId,
    },
  }
}

function tbbpmConnectionToX6Edge(connection: TbbpmConnection): Record<string, unknown> {
  return {
    id: connection.id,
    source: connection.sourceId,
    target: connection.targetId,
    labels: connection.name ? [{ attrs: { label: { text: connection.name } } }] : undefined,
    data: { condition: connection.condition },
    // Wider transparent interaction area so edge:click fires reliably.
    attrs: {
      line: {
        stroke: '#8f8f8f',
        strokeWidth: 2,
        targetMarker: { name: 'block', width: 12, height: 8 },
      },
      wrap: {
        stroke: 'transparent',
        strokeWidth: 16,
      },
    },
  }
}

function x6CellToTbbpmNode(cell: Node): TbbpmNode | null {
  const position = cell.position()
  const cellSize = cell.getSize()
  const data = cell.getData() as Record<string, unknown>
  const type = getNodeTypeByShape(cell.shape)
  if (!type) {
    logger.error(`Unknown TBBPM shape: ${cell.shape}`)
    return null
  }
  const { label: _label, type: _type, [PARENT_ID_DATA_KEY]: parentId, ...properties } = data
  return {
    id: cell.id,
    parentId: typeof parentId === 'string' ? parentId : undefined,
    type,
    name: (data.label as string) || '',
    position: { x: position.x, y: position.y },
    size: { width: cellSize.width, height: cellSize.height },
    properties: properties as TbbpmNode['properties'],
  }
}

function registerTbbpmMutationEvents(
  graph: Graph,
  { connectionsRef, dispatch, isSyncingRef }: TbbpmGraphEventOptions
) {
  const shouldSkipMutation = () => isSyncingRef.current

  graph.on('edge:connected', ({ edge }: { edge: Edge }) => {
    if (shouldSkipMutation()) return
    const sourceId = edge.getSourceCellId()
    const targetId = edge.getTargetCellId()
    if (!sourceId || !targetId) return

    runGraphMutation(isSyncingRef, () => {
      dispatch(addConnection({ id: edge.id, sourceId, targetId, name: '' }))
    })
  })

  graph.on('node:moved', ({ node }: { node: Node }) => {
    if (shouldSkipMutation()) return
    runGraphMutation(isSyncingRef, () => {
      const position = node.position()
      dispatch(moveNode({ id: node.id, x: position.x, y: position.y }))
    })
  })

  graph.on('node:added', ({ node }: { node: Node }) => {
    if (shouldSkipMutation()) return
    let wasAdded = false
    runGraphMutation(isSyncingRef, () => {
      const tbbpmNode = x6CellToTbbpmNode(node)
      if (tbbpmNode) {
        dispatch(addNode(tbbpmNode))
        wasAdded = true
      }
    })
    if (wasAdded) {
      graph.select(node)
      dispatch(selectNode(node.id))
    }
  })

  graph.on('node:removed', ({ node }: { node: Node }) => {
    if (shouldSkipMutation()) return
    runGraphMutation(isSyncingRef, () => dispatch(deleteNode(node.id)))
  })

  graph.on('edge:removed', ({ edge }: { edge: Edge }) => {
    if (shouldSkipMutation()) return
    runGraphMutation(isSyncingRef, () => dispatch(deleteConnection(edge.id)))
  })

  graph.on('edge:change:vertices', ({ edge }: { edge: Edge }) => {
    if (shouldSkipMutation()) return
    const conn = connectionsRef.current.find((c) => c.id === edge.id)
    if (!conn) return

    runGraphMutation(isSyncingRef, () => {
      const vertices = (edge.getVertices() ?? []) as Array<{ x: number; y: number }>
      dispatch(
        updateConnection({
          id: edge.id,
          updates: {
            condition: conn.condition,
            name: conn.name,
            waypoints: vertices.length > 0 ? vertices : undefined,
          },
        })
      )
    })
  })
}

function registerTbbpmGraphEvents(graph: Graph, options: TbbpmGraphEventOptions) {
  registerSelectionEvents(graph, options.dispatch, options.connectionsRef)
  registerContextMenuEvents(graph, options.dispatch)
  registerTbbpmMutationEvents(graph, options)
  registerSelectionSync(graph, options.dispatch, options.connectionsRef, options.isSyncingRef)
}

const TbbpmCanvas = memo(function TbbpmCanvas({ onGraphReady }: TbbpmCanvasProps) {
  const dispatch = useAppDispatch()
  const { graphRef } = useDesignerContext()

  const nodes = useAppSelector(selectTbbpmNodes)
  const connections = useAppSelector(selectTbbpmConnections)
  const selectedNodeId = useAppSelector(selectSelectedNodeId)
  const showGridlines = useAppSelector(selectShowGridlines)

  const containerRef = useRef<HTMLDivElement>(null)
  const isSyncingRef = useRef(false)
  const connectionsRef = useLatestRef(connections)

  const localGraphRef = useX6Graph(containerRef, {
    showGridlines,
    onReady: (graph) => {
      graphRef.current = graph
      onGraphReady?.(graph)

      registerTbbpmGraphEvents(graph, {
        connectionsRef,
        dispatch,
        isSyncingRef,
      })
    },
  })

  useEffect(() => {
    registerTbbpmNodes()
  }, [])

  const POS_TOLERANCE = 0.5

  useCanvasSync(localGraphRef, isSyncingRef, {
    nodes,
    connections,
    selectedNodeId,
    nodeToX6Cell: tbbpmNodeToX6Cell,
    connectionToX6Edge: tbbpmConnectionToX6Edge,
    checkNodeChanged: (cell, node) => {
      const currentPos = cell.position()
      const currentData = cell.getData() as {
        label?: string
        [PARENT_ID_DATA_KEY]?: string
      }
      return (
        Math.abs(currentPos.x - node.position.x) > POS_TOLERANCE ||
        Math.abs(currentPos.y - node.position.y) > POS_TOLERANCE ||
        currentData.label !== node.name ||
        currentData[PARENT_ID_DATA_KEY] !== node.parentId
      )
    },
    syncNodeToCell: (cell, node) => {
      cell.position(node.position.x, node.position.y)
      cell.setData({
        ...node.properties,
        label: node.name,
        [PARENT_ID_DATA_KEY]: node.parentId,
      })
    },
    checkEdgeChanged: (edge, conn) => {
      const currentData = (edge.getData() ?? {}) as Record<string, unknown>
      const currentLabel = (edge.getLabels() || [])[0]?.attrs?.label?.text
      return currentData.condition !== conn.condition || currentLabel !== conn.name
    },
    syncEdgeToCell: (edge, conn) => {
      const currentData = (edge.getData() ?? {}) as Record<string, unknown>
      edge.setData({ ...currentData, condition: conn.condition })
      edge.setLabels(conn.name ? [{ attrs: { label: { text: conn.name } } }] : [])
    },
  })

  return (
    <div
      className="tbbpm-canvas-wrapper"
      style={{ position: 'relative', width: '100%', height: '100%' }}
    >
      <div ref={containerRef} className="tbbpm-canvas" style={{ width: '100%', height: '100%' }} />
    </div>
  )
})

export default TbbpmCanvas
