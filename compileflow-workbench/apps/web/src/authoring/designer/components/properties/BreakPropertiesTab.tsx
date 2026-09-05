import { Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

interface BreakPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (_nodeId: string, _updates: Partial<TbbpmNode['properties']>) => void
}

export function BreakPropertiesTab({ node, onUpdate }: BreakPropertiesTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.break.title')}
      description={t('designer.props.node.break.desc')}
    >
      <Form.Item
        label={t('designer.props.node.conditionOptional')}
        help={t('designer.props.node.conditionBreakHelp')}
      >
        <Input
          value={node.properties.condition || ''}
          onChange={handleInputChange('condition')}
          placeholder={labels.phCondition}
          aria-label={t('designer.props.node.conditionOptional')}
          style={{ fontFamily: 'Monaco, Consolas, monospace' }}
        />
      </Form.Item>
    </PropertiesTabLayout>
  )
}
