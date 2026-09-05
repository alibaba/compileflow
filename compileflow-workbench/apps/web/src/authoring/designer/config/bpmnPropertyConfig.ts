import { lazy } from 'react'

import type { BpmnNodeType } from '../types/flowDefinition'
import type { BpmnNodePropertyTabConfig } from '../types/propertyTabs'

// ==================== 懒加载属性Tab组件 ====================

// Tasks
const ServiceTaskPropertiesTab = lazy(
  () => import('../components/properties/ServiceTaskPropertiesTab')
)
const ScriptTaskPropertiesTab = lazy(
  () => import('../components/properties/ScriptTaskPropertiesTab')
)
const ReceiveTaskPropertiesTab = lazy(
  () => import('../components/properties/ReceiveTaskPropertiesTab')
)

// Gateways
const ExclusiveGatewayPropertiesTab = lazy(
  () => import('../components/properties/ExclusiveGatewayPropertiesTab')
)
const ParallelGatewayPropertiesTab = lazy(
  () => import('../components/properties/ParallelGatewayPropertiesTab')
)
const InclusiveGatewayPropertiesTab = lazy(
  () => import('../components/properties/InclusiveGatewayPropertiesTab')
)

// Subprocesses
const CallActivityPropertiesTab = lazy(
  () => import('../components/properties/CallActivityPropertiesTab')
)
const SubProcessPropertiesTab = lazy(
  () => import('../components/properties/SubProcessPropertiesTab')
)

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
