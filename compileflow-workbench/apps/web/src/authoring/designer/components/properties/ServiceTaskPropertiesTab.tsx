import { Divider, Form } from 'antd'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { ActionDefinition } from '../../types/action'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { ActionEditor, createDefaultAction } from './ActionEditor'
import { BpmnLoopCharacteristicsFields } from './BpmnLoopCharacteristicsFields'
import { EffectPolicyFields } from './EffectPolicyFields'
import { InvocationPolicyFields } from './InvocationPolicyFields'

const SERVICE_TASK_ACTION_TYPES = ['java', 'spring-bean'] as const

export default function ServiceTaskPropertiesTab({ node, onUpdate }: BpmnNodePropertyTabProps) {
  const labels = usePropertyLabels()
  const properties = node.properties
  const action = (properties.action as ActionDefinition | undefined) || createDefaultAction()

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...properties, [field]: value })
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Divider style={{ fontSize: 12 }}>{labels.sectionActionConfig}</Divider>
        <ActionEditor
          value={action}
          onChange={(value) => update('action', value)}
          allowedActionTypes={SERVICE_TASK_ACTION_TYPES}
        />
        <InvocationPolicyFields
          value={action.invocationPolicy}
          onChange={(invocationPolicy) => update('action', { ...action, invocationPolicy })}
        />
        <EffectPolicyFields
          execution={action.execution}
          value={action.effectPolicy}
          onChange={(effectPolicy) => update('action', { ...action, effectPolicy })}
        />
        <BpmnLoopCharacteristicsFields
          value={properties.loopCharacteristics}
          onChange={(loopCharacteristics) => update('loopCharacteristics', loopCharacteristics)}
        />
      </Form>
    </div>
  )
}
