import { Form, Modal, Select } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { canConnectNodes, canStartConnection } from '../canvas/connectionRules'
import type { ProcessNode, UnifiedProcessDefinition } from '../types/flowDefinition'

interface CreateConnectionDialogProps {
  initialNodeIds: string[]
  process: UnifiedProcessDefinition
  onCancel: () => void
  onCreate: (sourceId: string, targetId: string) => void
}

interface ConnectionFields {
  sourceId: string
  targetId: string
}

function nodeLabel(node: ProcessNode): string {
  return node.name ? `${node.name} · ${node.id}` : node.id
}

export default function CreateConnectionDialog({
  initialNodeIds,
  process,
  onCancel,
  onCreate,
}: CreateConnectionDialogProps) {
  const { t } = useTranslation()
  const [form] = Form.useForm<ConnectionFields>()
  const sourceId = Form.useWatch('sourceId', form)

  const sourceOptions = useMemo(
    () =>
      process.nodes.map((node) => ({
        value: node.id,
        label: nodeLabel(node),
        disabled: !canStartConnection(process, node),
      })),
    [process]
  )
  const targetOptions = useMemo(
    () =>
      process.nodes.map((node) => ({
        value: node.id,
        label: nodeLabel(node),
        disabled: !canConnectNodes(process, sourceId, node.id),
      })),
    [process, sourceId]
  )

  const [firstId, secondId] = initialNodeIds
  const first = process.nodes.find((node) => node.id === firstId)
  const second = process.nodes.find((node) => node.id === secondId)

  return (
    <Modal
      open
      title={t('designer.connection.createTitle')}
      okText={t('designer.connection.create')}
      cancelText={t('common.cancel')}
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      <p>{t('designer.connection.createHint')}</p>
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          sourceId: first && canStartConnection(process, first) ? first.id : undefined,
          targetId:
            first && second && canConnectNodes(process, first.id, second.id)
              ? second.id
              : undefined,
        }}
        onFinish={({ sourceId: source, targetId: target }) => onCreate(source, target)}
      >
        <Form.Item
          name="sourceId"
          label={t('designer.connection.source')}
          rules={[{ required: true, message: t('designer.connection.sourceRequired') }]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={sourceOptions}
            placeholder={t('designer.connection.sourcePlaceholder')}
          />
        </Form.Item>
        <Form.Item
          name="targetId"
          label={t('designer.connection.target')}
          dependencies={['sourceId']}
          rules={[
            { required: true, message: t('designer.connection.targetRequired') },
            {
              validator: (_, targetId: string | undefined) => {
                const target = process.nodes.find((node) => node.id === targetId)
                return target && canConnectNodes(process, form.getFieldValue('sourceId'), target.id)
                  ? Promise.resolve()
                  : Promise.reject(new Error(t('designer.connection.invalid')))
              },
            },
          ]}
        >
          <Select
            showSearch
            optionFilterProp="label"
            options={targetOptions}
            placeholder={t('designer.connection.targetPlaceholder')}
          />
        </Form.Item>
      </Form>
    </Modal>
  )
}
