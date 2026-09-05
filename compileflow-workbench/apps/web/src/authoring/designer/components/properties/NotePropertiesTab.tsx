import { Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import type { TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

interface NotePropertiesTabProps {
  node: TbbpmNode
  onUpdate: (_nodeId: string, _updates: Partial<TbbpmNode['properties']>) => void
}

export function NotePropertiesTab({ node, onUpdate }: NotePropertiesTabProps) {
  const { t } = useTranslation()
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.note.title')}
      description={t('designer.props.node.note.desc')}
    >
      <Form.Item label={t('designer.props.node.note.content')}>
        <Input.TextArea
          aria-label={t('designer.props.node.note.content')}
          value={node.properties.comment || ''}
          onChange={handleInputChange('comment')}
          placeholder={t('designer.props.node.note.contentPlaceholder')}
          rows={4}
        />
      </Form.Item>
    </PropertiesTabLayout>
  )
}
