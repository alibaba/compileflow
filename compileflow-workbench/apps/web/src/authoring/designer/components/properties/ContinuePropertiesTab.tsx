import { Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

interface ContinuePropertiesTabProps {
  node: TbbpmNode
  onUpdate: (_nodeId: string, _updates: Partial<TbbpmNode['properties']>) => void
}

export function ContinuePropertiesTab({ node, onUpdate }: ContinuePropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.continue.title')}
      description={t('designer.props.node.continue.desc')}
    >
      <Form.Item
        label={t('designer.props.node.conditionOptional')}
        help={t('designer.props.node.conditionContinueHelp')}
      >
        <Input
          aria-label={t('designer.props.node.conditionOptional')}
          value={node.properties.condition || ''}
          onChange={handleInputChange('condition')}
          placeholder={labels.phCondition}
          style={{ fontFamily: 'Monaco, Consolas, monospace' }}
        />
      </Form.Item>
    </PropertiesTabLayout>
  )
}
