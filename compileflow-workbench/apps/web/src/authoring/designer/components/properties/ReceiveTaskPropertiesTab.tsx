import { AutoComplete, Divider, Form, Input } from 'antd'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import { selectCurrentProcess, updateProcessInfo } from '../../store/editorSlice'
import type { BpmnMessageDefinition } from '../../types/flowDefinition'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

export default function ReceiveTaskPropertiesTab({ node, onUpdate }: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const dispatch = useAppDispatch()
  const flow = useAppSelector(selectCurrentProcess)
  const messages = flow?.messages || []
  const p = node.properties
  const messageRef = typeof p.messageRef === 'string' ? p.messageRef : ''
  const messageDefinition = messages.find((message) => message.id === messageRef)
  const eventName = messageDefinition?.name || messageRef

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...p, [field]: value })
  }

  const updateMessages = (nextMessages: BpmnMessageDefinition[]) => {
    dispatch(updateProcessInfo({ messages: nextMessages }))
  }

  const handleMessageRefChange = (value: string) => {
    update('messageRef', value)
  }

  const handleMessageRefCommit = (value: string) => {
    const id = value.trim()
    if (id !== value) update('messageRef', id)
    if (!id || messages.some((message) => message.id === id)) return
    updateMessages([...messages, { id, name: id }])
  }

  const handleEventNameChange = (value: string) => {
    const id = messageRef.trim()
    if (!id) return
    const nextMessage = { id, name: value }
    updateMessages(
      messages.some((message) => message.id === id)
        ? messages.map((message) => (message.id === id ? nextMessage : message))
        : [...messages, nextMessage]
    )
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Divider style={{ fontSize: 12 }}>{t('designer.props.section.messageConfig')}</Divider>

        <Form.Item
          label={t('designer.props.node.receiveTask.messageId')}
          required
          help={t('designer.props.node.receiveTask.messageHelp')}
        >
          <AutoComplete
            value={messageRef}
            options={messages.map((message) => ({
              value: message.id,
              label: `${message.id} (${message.name})`,
            }))}
            onChange={handleMessageRefChange}
            onSelect={handleMessageRefCommit}
            onBlur={(event) =>
              handleMessageRefCommit((event.currentTarget as HTMLInputElement).value)
            }
            placeholder={labels.phMessageId}
            aria-label={t('designer.props.node.receiveTask.messageId')}
            filterOption={(input, option) =>
              String(option?.label || option?.value || '')
                .toLowerCase()
                .includes(input.toLowerCase())
            }
          />
        </Form.Item>
        <Form.Item
          label={t('designer.props.node.receiveTask.eventName')}
          required
          help={t('designer.props.node.receiveTask.eventNameHelp')}
        >
          <Input
            value={eventName}
            disabled={!messageRef.trim()}
            onChange={(event) => handleEventNameChange(event.target.value)}
            placeholder={t('designer.props.node.receiveTask.eventNamePlaceholder')}
            aria-label={t('designer.props.node.receiveTask.eventName')}
          />
        </Form.Item>
      </Form>
    </div>
  )
}
