import type { ProcessConnection, ProcessNode } from '../types/flowDefinition'

export interface ConnectionGraph {
  type: 'BPMN' | 'TBBPM'
  nodes: ProcessNode[]
  connections: ProcessConnection[]
}

export function canStartConnection(graph: ConnectionGraph, node: ProcessNode): boolean {
  return graph.type === 'BPMN'
    ? node.type !== 'bpmn:EndEvent'
    : !['end', 'break', 'continue', 'note'].includes(node.type)
}

export function canEndConnection(graph: ConnectionGraph, node: ProcessNode): boolean {
  return graph.type === 'BPMN'
    ? node.type !== 'bpmn:StartEvent'
    : !['start', 'note'].includes(node.type)
}

export function canConnectNodes(
  graph: ConnectionGraph,
  sourceId: string | undefined,
  targetId: string | undefined,
  editedConnectionId?: string
): boolean {
  const source = graph.nodes.find((node) => node.id === sourceId)
  const target = graph.nodes.find((node) => node.id === targetId)
  if (!source || !target || source.id === target.id) return false
  if (!canStartConnection(graph, source) || !canEndConnection(graph, target)) return false
  if (source.parentId !== target.parentId) return false
  return !(
    graph.type === 'BPMN' &&
    graph.connections.some(
      (connection) =>
        connection.id !== editedConnectionId &&
        connection.sourceId === source.id &&
        connection.targetId === target.id
    )
  )
}
