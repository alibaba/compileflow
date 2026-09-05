export const BPMN_NODE_TYPES = [
  'bpmn:StartEvent',
  'bpmn:EndEvent',
  'bpmn:ServiceTask',
  'bpmn:ScriptTask',
  'bpmn:ReceiveTask',
  'bpmn:ExclusiveGateway',
  'bpmn:ParallelGateway',
  'bpmn:InclusiveGateway',
  'bpmn:CallActivity',
  'bpmn:SubProcess',
] as const

export type BpmnNodeType = (typeof BPMN_NODE_TYPES)[number]

const BPMN_NODE_TYPE_SET: ReadonlySet<string> = new Set(BPMN_NODE_TYPES)

export function isBpmnNodeType(value: string): value is BpmnNodeType {
  return BPMN_NODE_TYPE_SET.has(value)
}

export interface MultiInstanceLoopCharacteristics {
  type: 'multiInstance'
  isSequential: boolean
  collection: string
  item: string
  itemType?: string
  index?: string
  target?: string
  source?: string
}

export interface StandardLoopCharacteristics {
  type: 'standard'
  loopCondition?: string
  testBefore?: boolean
  loopMaximum?: number
}

export type LoopCharacteristics = MultiInstanceLoopCharacteristics | StandardLoopCharacteristics
