import { Divider, Form, Input, Select } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { VariableMappingsEditor } from './ActionEditor'
import { BpmnLoopCharacteristicsFields } from './BpmnLoopCharacteristicsFields'

export default function CallActivityPropertiesTab({ node, onUpdate }: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const properties = node.properties
  const targetType = properties.version !== undefined ? 'version' : 'classpath'

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...properties, [field]: value })
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Divider style={{ fontSize: 12 }}>{t('designer.props.section.callConfig')}</Divider>
        <Form.Item
          label={t('designer.props.node.callActivity.calledElement')}
          required
          help={t('designer.props.node.callActivity.calledElementHelp')}
        >
          <Input
            value={(properties.calledElement as string) || ''}
            onChange={(event) => update('calledElement', event.target.value)}
            placeholder={labels.phCallActivity}
            aria-label={t('designer.props.node.callActivity.calledElement')}
          />
        </Form.Item>
        <Form.Item label={t('designer.props.processCall.targetType')} required>
          <Select
            value={targetType}
            onChange={(value: 'classpath' | 'version') =>
              onUpdate(node.id, {
                ...properties,
                classpath: value === 'classpath' ? '' : undefined,
                version: value === 'version' ? '' : undefined,
              })
            }
            aria-label={t('designer.props.processCall.targetType')}
          >
            <Select.Option value="classpath">
              {t('designer.props.processCall.targetType.classpath')}
            </Select.Option>
            <Select.Option value="version">
              {t('designer.props.processCall.targetType.version')}
            </Select.Option>
          </Select>
        </Form.Item>
        {targetType === 'classpath' ? (
          <Form.Item label={t('designer.props.processCall.classpath')} required>
            <Input
              value={properties.classpath || ''}
              onChange={(event) => update('classpath', event.target.value)}
              placeholder={t('designer.props.processCall.classpathPlaceholder')}
              aria-label={t('designer.props.processCall.classpath')}
            />
          </Form.Item>
        ) : (
          <Form.Item label={t('designer.props.processCall.version')} required>
            <Input
              value={properties.version || ''}
              onChange={(event) => update('version', event.target.value)}
              placeholder={t('designer.props.processCall.versionPlaceholder')}
              aria-label={t('designer.props.processCall.version')}
            />
          </Form.Item>
        )}
        <VariableMappingsEditor
          value={properties.mappings || []}
          processCall
          onChange={(mappings) => update('mappings', mappings)}
        />
        <BpmnLoopCharacteristicsFields
          value={properties.loopCharacteristics}
          onChange={(loopCharacteristics) => update('loopCharacteristics', loopCharacteristics)}
        />
      </Form>
    </div>
  )
}
