interface NodePosition {
  x: number
  y: number
}

interface NodeSize {
  width: number
  height: number
}

interface NodeMetadata {
  editable?: boolean
  deletable?: boolean
  style?: Record<string, unknown>
  uiState?: Record<string, unknown>
}

export interface BaseNode {
  id: string
  /** Container node for formats that support nested graphs. */
  parentId?: string
  type: string
  name?: string
  documentation?: string
  position: NodePosition
  size?: NodeSize
  properties: Record<string, unknown>
  metadata?: NodeMetadata
}

export const CONNECTION_PORTS = ['top', 'bottom', 'left', 'right'] as const

export interface BaseConnection {
  id: string
  sourceId: string
  targetId: string
  sourcePort?: string
  targetPort?: string
  name?: string
  condition?: string
  /** Edge bend points in canvas coordinates. */
  waypoints?: Array<{ x: number; y: number }>
}
