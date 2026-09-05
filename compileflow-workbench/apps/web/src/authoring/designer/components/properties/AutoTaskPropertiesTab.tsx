import { Divider } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { ActionDefinition } from '../../types/action'
import type { InvocationPolicy } from '../../types/invocationPolicy'
import type { TbbpmNode } from '../../types/tbbpm'

import { ActionEditor } from './ActionEditor'
import { EffectPolicyFields } from './EffectPolicyFields'
import { InvocationPolicyFields } from './InvocationPolicyFields'
import { PropertiesTabLayout } from './PropertiesTabLayout'

type AutoTaskProperties = TbbpmNode['properties']
const AUTO_TASK_ACTION_TYPES = ['java', 'spring-bean'] as const

interface AutoTaskPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (nodeId: string, updates: Partial<AutoTaskProperties>) => void
}

export function AutoTaskPropertiesTab({ node, onUpdate }: AutoTaskPropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()

  const handleActionChange = useCallback(
    (action: ActionDefinition) => {
      onUpdate(node.id, { action })
    },
    [node.id, onUpdate]
  )

  const handleInvocationPolicyChange = useCallback(
    (invocationPolicy: InvocationPolicy | undefined) => {
      onUpdate(node.id, {
        action: { ...(node.properties.action || { actionType: 'java' }), invocationPolicy },
      })
    },
    [node.id, node.properties.action, onUpdate]
  )

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.autoTask.title')}
      description={t('designer.props.node.autoTask.desc')}
    >
      <Divider style={{ margin: '0 0 16px 0' }}>{labels.sectionActionType}</Divider>
      <ActionEditor
        value={node.properties.action || { actionType: 'java' }}
        onChange={handleActionChange}
        allowedActionTypes={AUTO_TASK_ACTION_TYPES}
      />

      <InvocationPolicyFields
        value={node.properties.action?.invocationPolicy}
        onChange={handleInvocationPolicyChange}
      />
      <EffectPolicyFields
        execution={node.properties.action?.execution}
        value={node.properties.action?.effectPolicy}
        onChange={(effectPolicy) =>
          onUpdate(node.id, {
            action: { ...(node.properties.action || { actionType: 'java' }), effectPolicy },
          })
        }
      />
    </PropertiesTabLayout>
  )
}
