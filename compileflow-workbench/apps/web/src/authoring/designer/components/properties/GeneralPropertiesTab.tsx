import { Form, Input } from 'antd'
import React, { useEffect } from 'react'
import { useTranslation } from 'react-i18next'

import type { BaseNode } from '../../types/flowDefinition'

interface GeneralPropertiesTabProps {
  node: BaseNode
  onUpdate: (values: Partial<BaseNode>) => void
}

export function GeneralPropertiesTab({ node, onUpdate }: GeneralPropertiesTabProps) {
  const { t } = useTranslation()
  const [form] = Form.useForm()

  useEffect(() => {
    form.setFieldsValue({
      id: node.id,
      name: node.name || '',
      type: node.type,
      documentation: node.documentation || '',
    })
  }, [node, form])

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const newName = e.target.value
    form.setFieldValue('name', newName)
    onUpdate({ name: newName })
  }

  const handleDocumentationChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const value = e.target.value
    form.setFieldValue('documentation', value)
    onUpdate({ documentation: value })
  }

  return (
    <Form form={form} layout="vertical" size="small">
      <Form.Item label={t('designer.props.nodeId')} name="id">
        <Input disabled aria-label={t('designer.props.nodeId')} />
      </Form.Item>
      <Form.Item
        label={t('designer.props.nodeName')}
        name="name"
        rules={[{ required: true, message: t('designer.props.nodeNameRequired') }]}
      >
        <Input
          aria-label={t('designer.props.nodeName')}
          placeholder={t('designer.props.nodeNamePlaceholder')}
          onChange={handleNameChange}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.nodeType')} name="type">
        <Input disabled aria-label={t('designer.props.nodeType')} />
      </Form.Item>
      <Form.Item label={t('designer.props.documentation')} name="documentation">
        <Input.TextArea
          aria-label={t('designer.props.documentation')}
          placeholder={t('designer.props.documentationPlaceholder')}
          rows={3}
          onChange={handleDocumentationChange}
        />
      </Form.Item>
    </Form>
  )
}
