import { Divider, Form, Input, Select } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { TbbpmNode } from '../../types/tbbpm'

import { VariableMappingsEditor } from './ActionEditor'
import { PropertiesTabLayout } from './PropertiesTabLayout'

interface BpmCallPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (_nodeId: string, _updates: Partial<TbbpmNode['properties']>) => void
}

export function BpmCallPropertiesTab({ node, onUpdate }: BpmCallPropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)
  const targetType = node.properties.version !== undefined ? 'version' : 'classpath'

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.bpmCall.title')}
      description={t('designer.props.node.bpmCall.desc')}
    >
      <Divider style={{ margin: '0 0 16px 0' }}>{labels.sectionBasic}</Divider>

      <Form.Item
        label={t('designer.props.node.bpmCall.code')}
        required
        help={t('designer.props.node.bpmCall.codeHelp')}
      >
        <Input
          value={node.properties.code || ''}
          onChange={handleInputChange('code')}
          placeholder={labels.phProcessCode}
          aria-label={t('designer.props.node.bpmCall.code')}
        />
      </Form.Item>

      <Form.Item label={t('designer.props.processCall.targetType')} required>
        <Select
          value={targetType}
          onChange={(value: 'classpath' | 'version') =>
            onUpdate(node.id, {
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
            value={node.properties.classpath || ''}
            onChange={handleInputChange('classpath')}
            placeholder={t('designer.props.processCall.classpathPlaceholder')}
            aria-label={t('designer.props.processCall.classpath')}
          />
        </Form.Item>
      ) : (
        <Form.Item label={t('designer.props.processCall.version')} required>
          <Input
            value={node.properties.version || ''}
            onChange={handleInputChange('version')}
            placeholder={t('designer.props.processCall.versionPlaceholder')}
            aria-label={t('designer.props.processCall.version')}
          />
        </Form.Item>
      )}

      <VariableMappingsEditor
        value={node.properties.callMappings || []}
        processCall
        onChange={(callMappings) => onUpdate(node.id, { callMappings })}
      />
    </PropertiesTabLayout>
  )
}
