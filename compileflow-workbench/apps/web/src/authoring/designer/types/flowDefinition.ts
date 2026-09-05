import type { ActionDefinition, ActionExecution, EffectPolicy, VariableMapping } from './action'
import type { BpmnNodeType, LoopCharacteristics } from './bpmnNodeTypes'
import type { BaseConnection, BaseNode } from './graphTypes'
import type { InvocationPolicy } from './invocationPolicy'
import type { TbbpmConnection, TbbpmNode } from './tbbpm'

export type { BpmnNodeType } from './bpmnNodeTypes'
export type { BaseConnection, BaseNode } from './graphTypes'

export interface BpmnNode extends BaseNode {
  type: BpmnNodeType
  properties: {
    /** CompileFlow action used by service tasks. */
    action?: ActionDefinition
    /** Variable mappings used by script tasks and call activities. */
    mappings?: VariableMapping[]
    scriptFormat?: string
    script?: string
    execution?: ActionExecution
    invocationPolicy?: InvocationPolicy
    effectPolicy?: EffectPolicy
    default?: string
    calledElement?: string
    classpath?: string
    version?: string
    messageRef?: string
    /** Supported only by synchronous BPMN activities, never receiveTask. */
    loopCharacteristics?: LoopCharacteristics
  }
}

export type BpmnConnection = BaseConnection

export type ProcessNode = BpmnNode | TbbpmNode
export type ProcessConnection = BpmnConnection | TbbpmConnection

export interface ProcessVariable {
  name: string
  type: string
  defaultValue?: string
  description?: string
  inOutType: 'param' | 'return' | 'inner'
}

export interface BpmnMessageDefinition {
  id: string
  name: string
}

interface ProcessDefinitionBase {
  id: string
  code: string
  name: string
  description?: string
  createdAt?: number
  updatedAt?: number
  category?: string
  tags?: string[]
  variables?: ProcessVariable[]
  messages?: BpmnMessageDefinition[]
  properties?: Record<string, unknown>
  namespace?: string
}

export interface BpmnProcessDefinition extends ProcessDefinitionBase {
  type: 'BPMN'
  nodes: BpmnNode[]
  connections: BpmnConnection[]
}

export interface TbbpmProcessDefinition extends ProcessDefinitionBase {
  type: 'TBBPM'
  nodes: TbbpmNode[]
  connections: TbbpmConnection[]
}

/** Canonical in-memory model shared by the visual and XML designer views. */
export type UnifiedProcessDefinition = BpmnProcessDefinition | TbbpmProcessDefinition
