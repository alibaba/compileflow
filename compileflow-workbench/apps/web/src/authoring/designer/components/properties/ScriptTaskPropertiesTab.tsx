import { Divider, Form } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { ActionDefinition } from '../../types/action'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { ActionEditor } from './ActionEditor'
import { BpmnLoopCharacteristicsFields } from './BpmnLoopCharacteristicsFields'
import { EffectPolicyFields } from './EffectPolicyFields'
import { InvocationPolicyFields } from './InvocationPolicyFields'

const SCRIPT_ACTION_TYPES = ['script'] as const

export default function ScriptTaskPropertiesTab({ node, onUpdate }: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const properties = node.properties

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...properties, [field]: value })
  }
  const action: ActionDefinition = {
    actionType: 'script',
    language: properties.scriptFormat || 'qlexpress',
    source: properties.script,
    execution: properties.execution || 'replayable',
    mappings: properties.mappings,
    invocationPolicy: properties.invocationPolicy,
    effectPolicy: properties.effectPolicy,
  }
  const updateAction = (value: ActionDefinition) => {
    onUpdate(node.id, {
      ...properties,
      scriptFormat: value.language,
      script: value.source,
      execution: value.execution,
      mappings: value.mappings,
      invocationPolicy: value.invocationPolicy,
      effectPolicy: value.effectPolicy,
    })
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Divider style={{ fontSize: 12 }}>{t('designer.props.section.scriptConfig')}</Divider>
        <ActionEditor
          value={action}
          onChange={updateAction}
          allowedActionTypes={SCRIPT_ACTION_TYPES}
        />
        <Divider style={{ margin: '16px 0' }}>{labels.sectionExecControl}</Divider>
        <InvocationPolicyFields
          value={properties.invocationPolicy}
          onChange={(invocationPolicy) => update('invocationPolicy', invocationPolicy)}
        />
        <EffectPolicyFields
          execution={properties.execution}
          value={properties.effectPolicy}
          onChange={(effectPolicy) => update('effectPolicy', effectPolicy)}
        />
        <BpmnLoopCharacteristicsFields
          value={properties.loopCharacteristics}
          onChange={(loopCharacteristics) => update('loopCharacteristics', loopCharacteristics)}
        />
      </Form>
    </div>
  )
}
