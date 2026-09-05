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
  selectBpmnConnections,
  selectBpmnNodes,
  updateConnection,
} from '../store/editorSlice'
import { selectNode, selectSelectedNodeId, selectShowGridlines } from '../store/uiSlice'
import type { ActionDefinition } from '../types/action'
import type { BpmnConnection, BpmnNode } from '../types/flowDefinition'

import { getBpmnNodeConfig, getBpmnTypeByShape, registerBpmnNodes } from './nodes/registerBpmnNodes'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import type { AppDispatch } from '@/app/store'
import { createLogger } from '@/shared/logging/logger'

// Use typed hooks from shared store; avoids direct import of store.ts in designer components.
import './BpmnCanvas.css'

const logger = createLogger('BpmnCanvas')
const PARENT_ID_DATA_KEY = '__parentId'

interface BpmnCanvasProps {
  onGraphReady?: (graph: Graph) => void
}

type BpmnConnectionsRef = ConnectionsRef<BpmnConnection>

interface BpmnGraphEventOptions {
  connectionsRef: BpmnConnectionsRef
  dispatch: AppDispatch
  isSyncingRef: SyncingRef
}

function bpmnNodeToX6Cell(node: BpmnNode): Record<string, unknown> {
  const nodeConfig = getBpmnNodeConfig(node.type)
  if (!nodeConfig) logger.error(`Unknown BPMN node type: ${node.type}`)
  return {
    id: node.id,
    shape: nodeConfig?.shape || 'rect',
    x: node.position.x,
    y: node.position.y,
    width: node.size?.width || nodeConfig?.width || 100,
    height: node.size?.height || nodeConfig?.height || 80,
    data: {
      label: node.name,
      ...node.properties,
      [PARENT_ID_DATA_KEY]: node.parentId,
    },
  }
}

function bpmnNodeFromX6Node(node: Node): BpmnNode | null {
  const position = node.position()
  const data = node.getData() as Record<string, unknown>
  const type = getBpmnTypeByShape(node.shape)

  if (!type) {
    logger.error(`Unknown BPMN shape: ${node.shape}`)
    return null
  }

  const properties: BpmnNode['properties'] =
    type === 'bpmn:ServiceTask'
      ? { action: { actionType: 'java' } }
      : type === 'bpmn:ScriptTask'
        ? { scriptFormat: 'qlexpress', script: '' }
        : {}

  return {
    id: node.id,
    parentId:
      typeof data[PARENT_ID_DATA_KEY] === 'string' ? String(data[PARENT_ID_DATA_KEY]) : undefined,
    type,
    name: (data.label as string) || '',
    position: { x: position.x, y: position.y },
    size: { width: node.getSize().width, height: node.getSize().height },
    properties,
  }
}

function registerBpmnMutationEvents(
  graph: Graph,
  { connectionsRef, dispatch, isSyncingRef }: BpmnGraphEventOptions
) {
  const shouldSkipMutation = () => isSyncingRef.current

  graph.on('edge:connected', ({ edge }: { edge: Edge }) => {
    if (shouldSkipMutation()) return
    const sourceId = edge.getSourceCellId()
    const targetId = edge.getTargetCellId()
    if (!sourceId || !targetId) return

    runGraphMutation(isSyncingRef, () => {
      const vertices = (edge.getVertices() ?? []) as Array<{ x: number; y: number }>
      dispatch(
        addConnection({
          id: edge.id,
          sourceId,
          targetId,
          name: '',
          waypoints: vertices.length > 0 ? vertices : undefined,
        })
      )
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
      const bpmnNode = bpmnNodeFromX6Node(node)
      if (bpmnNode) {
        dispatch(addNode(bpmnNode))
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
            waypoints: vertices.length > 0 ? vertices : undefined,
          },
        })
      )
    })
  })
}

function registerBpmnGraphEvents(graph: Graph, options: BpmnGraphEventOptions) {
  registerSelectionEvents(graph, options.dispatch, options.connectionsRef)
  registerContextMenuEvents(graph, options.dispatch)
  registerBpmnMutationEvents(graph, options)
  registerSelectionSync(graph, options.dispatch, options.connectionsRef, options.isSyncingRef)
}

// Restore edge geometry from connection-owned waypoints when re-rendering.
function bpmnConnectionToX6Edge(connection: BpmnConnection): Record<string, unknown> {
  return {
    id: connection.id,
    source: connection.sourceId,
    target: connection.targetId,
    labels: connection.name ? [{ attrs: { label: { text: connection.name } } }] : undefined,
    vertices: connection.waypoints || [],
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

const BpmnCanvas = memo(function BpmnCanvas({ onGraphReady }: BpmnCanvasProps) {
  const dispatch = useAppDispatch()
  const { graphRef } = useDesignerContext()

  // Filter to BPMN-typed nodes only using the isBpmnNode type guard — avoids double-as
  // casting and gives TypeScript a genuine narrowing so nodes: BpmnNode[].
  const nodes = useAppSelector(selectBpmnNodes)
  const connections = useAppSelector(selectBpmnConnections)
  const selectedNodeId = useAppSelector(selectSelectedNodeId)
  const showGridlines = useAppSelector(selectShowGridlines)

  const containerRef = useRef<HTMLDivElement>(null)
  const isSyncingRef = useRef(false)
  const connectionsRef = useLatestRef(connections)

  const localGraphRef = useX6Graph(containerRef, {
    showGridlines,
    allowMultiEdge: false,
    validateConnection: ({ sourceView, targetView }) => {
      if (targetView?.cell.shape === 'bpmn-start-event') return false
      if (sourceView?.cell.shape === 'bpmn-end-event') return false
      return true
    },
    onReady: (graph) => {
      graphRef.current = graph
      onGraphReady?.(graph)

      registerBpmnGraphEvents(graph, {
        connectionsRef,
        dispatch,
        isSyncingRef,
      })
    },
  })

  useEffect(() => {
    registerBpmnNodes()
  }, [])

  const POS_TOLERANCE = 0.5

  useCanvasSync(localGraphRef, isSyncingRef, {
    nodes,
    connections,
    selectedNodeId,
    nodeToX6Cell: bpmnNodeToX6Cell,
    connectionToX6Edge: bpmnConnectionToX6Edge,
    checkNodeChanged: (cell, node) => {
      const currentPos = cell.position()
      const currentData = cell.getData() as {
        label?: string
        action?: ActionDefinition
        scriptFormat?: string
        messageRef?: string
        calledElement?: string
        classpath?: string
        version?: string
        [PARENT_ID_DATA_KEY]?: string
      }
      return (
        Math.abs(currentPos.x - node.position.x) > POS_TOLERANCE ||
        Math.abs(currentPos.y - node.position.y) > POS_TOLERANCE ||
        currentData.label !== node.name ||
        currentData.action?.actionType !== node.properties.action?.actionType ||
        currentData.scriptFormat !== node.properties.scriptFormat ||
        currentData.messageRef !== node.properties.messageRef ||
        currentData.calledElement !== node.properties.calledElement ||
        currentData.classpath !== node.properties.classpath ||
        currentData.version !== node.properties.version ||
        currentData[PARENT_ID_DATA_KEY] !== node.parentId
      )
    },
    syncNodeToCell: (cell, node) => {
      cell.position(node.position.x, node.position.y)
      cell.setData({
        label: node.name,
        ...node.properties,
        [PARENT_ID_DATA_KEY]: node.parentId,
      })
    },
    checkEdgeChanged: (edge, conn) => {
      const currentData = edge.getData() as Record<string, unknown>
      const currentLabel = (edge.getLabels() || [])[0]?.attrs?.label?.text
      return currentLabel !== conn.name || currentData?.condition !== conn.condition
    },
    syncEdgeToCell: (edge, conn) => {
      edge.setData({ condition: conn.condition })
      edge.setLabels(conn.name ? [{ attrs: { label: { text: conn.name } } }] : [])
      // Sync edge geometry (waypoints) from Redux state to X6 vertices on re-render.
      if (conn.waypoints) {
        edge.setVertices(conn.waypoints)
      }
    },
  })

  return (
    <div
      className="bpmn-canvas-wrapper"
      style={{ position: 'relative', width: '100%', height: '100%' }}
    >
      <div ref={containerRef} className="bpmn-canvas" style={{ width: '100%', height: '100%' }} />
    </div>
  )
})

export default BpmnCanvas
