import type { ComponentType } from 'react'

import type { BpmnNode } from './flowDefinition'
import type { TbbpmNode } from './tbbpm'

export interface BpmnNodePropertyTabProps {
  node: BpmnNode
  onUpdate: (nodeId: string, properties: Record<string, unknown>) => void
}

export interface BpmnNodePropertyTabConfig {
  key: string
  labelKey: string
  component: ComponentType<BpmnNodePropertyTabProps>
}

export interface NodePropertyTabProps {
  node: TbbpmNode
  onUpdate: (nodeId: string, updates: Partial<TbbpmNode['properties']>) => void
}

export interface NodePropertyTabConfig {
  key: string
  labelKey: string
  component: ComponentType<NodePropertyTabProps>
}
