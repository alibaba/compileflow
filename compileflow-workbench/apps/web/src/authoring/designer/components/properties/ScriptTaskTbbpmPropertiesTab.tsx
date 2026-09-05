import { Divider } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { ActionDefinition } from '../../types/action'
import type { TbbpmNode } from '../../types/tbbpm'

import { ActionEditor } from './ActionEditor'
import { EffectPolicyFields } from './EffectPolicyFields'
import { InvocationPolicyFields } from './InvocationPolicyFields'
import { PropertiesTabLayout } from './PropertiesTabLayout'

const SCRIPT_ACTION_TYPES = ['script'] as const

interface ScriptTaskTbbpmPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (nodeId: string, updates: Partial<TbbpmNode['properties']>) => void
}

export function ScriptTaskTbbpmPropertiesTab({
  node,
  onUpdate,
}: ScriptTaskTbbpmPropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const handleActionChange = useCallback(
    (action: ActionDefinition) => {
      onUpdate(node.id, { action })
    },
    [node.id, onUpdate]
  )

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.scriptTbbpm.title')}
      description={t('designer.props.node.scriptTbbpm.desc')}
    >
      <Divider style={{ margin: '0 0 16px 0' }}>{t('designer.props.section.scriptConfig')}</Divider>
      <ActionEditor
        value={node.properties.action || { actionType: 'script', language: 'qlexpress' }}
        onChange={handleActionChange}
        allowedActionTypes={SCRIPT_ACTION_TYPES}
      />
      <Divider style={{ margin: '16px 0' }}>{labels.sectionExecControl}</Divider>
      <InvocationPolicyFields
        value={node.properties.action?.invocationPolicy}
        onChange={(invocationPolicy) =>
          onUpdate(node.id, {
            action: {
              ...(node.properties.action || { actionType: 'script', language: 'qlexpress' }),
              invocationPolicy,
            },
          })
        }
      />
      <EffectPolicyFields
        execution={node.properties.action?.execution}
        value={node.properties.action?.effectPolicy}
        onChange={(effectPolicy) =>
          onUpdate(node.id, {
            action: {
              ...(node.properties.action || { actionType: 'script', language: 'qlexpress' }),
              effectPolicy,
            },
          })
        }
      />
    </PropertiesTabLayout>
  )
}
