import type { BpmnNode, BpmnNodeType } from './flowDefinition'
import type { TbbpmNode, TbbpmNodeType } from './tbbpm'

const TBBPM_ACTION_PROPERTIES = ['action'] as const

const TBBPM_NODE_PROPERTIES: Record<TbbpmNodeType, readonly string[]> = {
  start: [],
  end: [],
  autoTask: TBBPM_ACTION_PROPERTIES,
  scriptTask: TBBPM_ACTION_PROPERTIES,
  exclusive: [],
  parallel: [],
  inclusive: [],
  subBpm: [],
  bpmCall: ['code', 'classpath', 'version', 'callMappings'],
  waitTask: ['timeout'],
  waitEventTask: ['event', 'timeout'],
  timerTask: ['duration', 'durationExpression', 'wakeAtExpression'],
  while: ['condition', 'index', 'maxIterations'],
  foreach: ['execution', 'collection', 'item', 'index', 'itemType', 'output'],
  break: ['condition'],
  continue: ['condition'],
  note: ['comment'],
}

const BPMN_NODE_PROPERTIES: Record<BpmnNodeType, readonly string[]> = {
  'bpmn:StartEvent': [],
  'bpmn:EndEvent': [],
  'bpmn:ServiceTask': ['action', 'loopCharacteristics'],
  'bpmn:ScriptTask': [
    'scriptFormat',
    'script',
    'execution',
    'mappings',
    'invocationPolicy',
    'effectPolicy',
    'loopCharacteristics',
  ],
  'bpmn:ReceiveTask': ['messageRef'],
  'bpmn:ExclusiveGateway': ['default'],
  'bpmn:ParallelGateway': [],
  'bpmn:InclusiveGateway': ['default'],
  'bpmn:CallActivity': ['calledElement', 'classpath', 'version', 'mappings', 'loopCharacteristics'],
  'bpmn:SubProcess': ['loopCharacteristics'],
}

export function findInapplicableTbbpmNodeProperties(node: TbbpmNode): string[] {
  return findInapplicableProperties(node.properties, TBBPM_NODE_PROPERTIES[node.type])
}

export function findInapplicableBpmnNodeProperties(node: BpmnNode): string[] {
  return findInapplicableProperties(node.properties, BPMN_NODE_PROPERTIES[node.type])
}

function findInapplicableProperties(
  properties: Record<string, unknown>,
  allowedProperties: readonly string[]
): string[] {
  const allowed = new Set(allowedProperties)
  return Object.entries(properties)
    .filter(([property, value]) => value !== undefined && !allowed.has(property))
    .map(([property]) => property)
    .sort()
}
