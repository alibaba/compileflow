import { Divider, Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

interface WaitTaskPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (nodeId: string, updates: Partial<TbbpmNode['properties']>) => void
}

function TriggerTip() {
  const { t } = useTranslation()

  return (
    <div
      style={{
        padding: 8,
        background: 'var(--color-warning-bg)',
        border: '1px solid var(--color-warning-border)',
        borderRadius: 4,
        fontSize: 12,
        color: 'var(--warning-dark)',
        marginTop: 8,
      }}
    >
      {t('designer.props.node.waitTask.triggerTip')}
    </div>
  )
}

export function WaitTaskPropertiesTab({ node, onUpdate }: WaitTaskPropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)
  const isWaitEventTask = node.type === 'waitEventTask'

  return (
    <PropertiesTabLayout
      title={
        isWaitEventTask
          ? t('designer.props.node.waitEventTask.title')
          : t('designer.props.node.waitTask.title')
      }
      description={t('designer.props.node.waitTask.desc')}
    >
      <Divider style={{ margin: '0 0 16px 0' }}>{labels.sectionBasic}</Divider>
      {isWaitEventTask && (
        <>
          <Form.Item
            label={t('designer.props.node.waitTask.eventName')}
            required
            help={t('designer.props.node.waitTask.eventHelp')}
          >
            <Input
              value={node.properties.event || ''}
              onChange={handleInputChange('event')}
              placeholder={labels.phEventName}
              aria-label={t('designer.props.node.waitTask.eventName')}
            />
          </Form.Item>
        </>
      )}
      <Form.Item label={t('designer.props.common.timeout')}>
        <Input
          value={node.properties.timeout || ''}
          onChange={handleInputChange('timeout')}
          aria-label={t('designer.props.common.timeout')}
        />
      </Form.Item>

      <TriggerTip />
    </PropertiesTabLayout>
  )
}
