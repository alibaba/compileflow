import CallActivityPropertiesTab from '../components/properties/CallActivityPropertiesTab'
import ExclusiveGatewayPropertiesTab from '../components/properties/ExclusiveGatewayPropertiesTab'
import InclusiveGatewayPropertiesTab from '../components/properties/InclusiveGatewayPropertiesTab'
import ParallelGatewayPropertiesTab from '../components/properties/ParallelGatewayPropertiesTab'
import ReceiveTaskPropertiesTab from '../components/properties/ReceiveTaskPropertiesTab'
import ScriptTaskPropertiesTab from '../components/properties/ScriptTaskPropertiesTab'
import ServiceTaskPropertiesTab from '../components/properties/ServiceTaskPropertiesTab'
import SubProcessPropertiesTab from '../components/properties/SubProcessPropertiesTab'
import type { BpmnNodeType } from '../types/flowDefinition'
import type { BpmnNodePropertyTabConfig } from '../types/propertyTabs'

const BPMN_NODE_PROPERTY_CONFIGS: Record<BpmnNodeType, BpmnNodePropertyTabConfig | null> = {
  'bpmn:StartEvent': null,
  'bpmn:EndEvent': null,
  'bpmn:ServiceTask': {
    key: 'service',
    labelKey: 'designer.properties.tab.service',
    component: ServiceTaskPropertiesTab,
  },
  'bpmn:ScriptTask': {
    key: 'script',
    labelKey: 'designer.properties.tab.script',
    component: ScriptTaskPropertiesTab,
  },
  'bpmn:ReceiveTask': {
    key: 'receive',
    labelKey: 'designer.properties.tab.receive',
    component: ReceiveTaskPropertiesTab,
  },
  'bpmn:ExclusiveGateway': {
    key: 'exclusive',
    labelKey: 'designer.properties.tab.exclusive',
    component: ExclusiveGatewayPropertiesTab,
  },
  'bpmn:ParallelGateway': {
    key: 'parallel',
    labelKey: 'designer.properties.tab.parallelGateway',
    component: ParallelGatewayPropertiesTab,
  },
  'bpmn:InclusiveGateway': {
    key: 'inclusive',
    labelKey: 'designer.properties.tab.inclusiveGateway',
    component: InclusiveGatewayPropertiesTab,
  },
  'bpmn:CallActivity': {
    key: 'callactivity',
    labelKey: 'designer.properties.tab.callActivity',
    component: CallActivityPropertiesTab,
  },
  'bpmn:SubProcess': {
    key: 'subprocess',
    labelKey: 'designer.properties.tab.subProcess',
    component: SubProcessPropertiesTab,
  },
}

export function getBpmnPropertyConfig(nodeType: BpmnNodeType): BpmnNodePropertyTabConfig | null {
  return BPMN_NODE_PROPERTY_CONFIGS[nodeType] || null
}
