import { useTranslation } from 'react-i18next'

const BPMN_TYPE_KEYS: Record<string, string> = {
  'bpmn:StartEvent': 'designer.node.bpmn.start',
  'bpmn:EndEvent': 'designer.node.bpmn.end',
  'bpmn:ServiceTask': 'designer.node.bpmn.serviceTask',
  'bpmn:ScriptTask': 'designer.node.bpmn.scriptTask',
  'bpmn:ReceiveTask': 'designer.node.bpmn.receiveTask',
  'bpmn:ExclusiveGateway': 'designer.node.bpmn.exclusiveGateway',
  'bpmn:ParallelGateway': 'designer.node.bpmn.parallelGateway',
  'bpmn:InclusiveGateway': 'designer.node.bpmn.inclusiveGateway',
  'bpmn:CallActivity': 'designer.node.bpmn.callActivity',
  'bpmn:SubProcess': 'designer.node.bpmn.subProcess',
}

const TBBPM_TYPE_KEYS: Record<string, string> = {
  start: 'designer.nodeSearch.type.start',
  end: 'designer.nodeSearch.type.end',
  autoTask: 'designer.nodeSearch.type.autoTask',
  scriptTask: 'designer.nodeSearch.type.scriptTask',
  exclusive: 'designer.nodeSearch.type.exclusive',
  parallel: 'designer.nodeSearch.type.parallel',
  inclusive: 'designer.nodeSearch.type.inclusive',
  subBpm: 'designer.nodeSearch.type.subBpm',
  bpmCall: 'designer.nodeSearch.type.bpmCall',
  while: 'designer.nodeSearch.type.while',
  foreach: 'designer.nodeSearch.type.foreach',
  waitTask: 'designer.nodeSearch.type.waitTask',
  waitEventTask: 'designer.nodeSearch.type.waitEventTask',
  timerTask: 'designer.nodeSearch.type.timerTask',
  break: 'designer.nodeSearch.type.break',
  continue: 'designer.nodeSearch.type.continue',
  note: 'designer.nodeSearch.type.note',
}

export function useNodeDisplayLabel(nodeType: string, explicitLabel?: string): string {
  const { t } = useTranslation()
  if (explicitLabel) return explicitLabel
  const key = BPMN_TYPE_KEYS[nodeType] || TBBPM_TYPE_KEYS[nodeType]
  return key ? t(key) : nodeType
}
