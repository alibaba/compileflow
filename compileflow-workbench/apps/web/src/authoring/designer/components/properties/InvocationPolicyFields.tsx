import { AutoComplete, Divider, Form, Input, InputNumber, Select, Switch } from 'antd'
import { useTranslation } from 'react-i18next'

import type { InvocationPolicy } from '../../types/invocationPolicy'

interface InvocationPolicyFieldsProps {
  value?: InvocationPolicy
  onChange: (_value: InvocationPolicy | undefined) => void
}

export function InvocationPolicyFields({ value, onChange }: InvocationPolicyFieldsProps) {
  const { t } = useTranslation()
  const update = (patch: Partial<InvocationPolicy>) => onChange({ ...value, ...patch })

  return (
    <>
      <Divider style={{ margin: '24px 0 16px' }}>
        {t('designer.props.section.invocationPolicyTitle')}
      </Divider>
      <Form.Item label={t('designer.props.common.invocationPolicyEnabled')}>
        <Switch
          checked={value !== undefined}
          onChange={(enabled) => onChange(enabled ? {} : undefined)}
          aria-label={t('designer.props.common.invocationPolicyEnabled')}
        />
      </Form.Item>
      {value !== undefined && (
        <>
          <Form.Item
            label={t('designer.props.common.invocationTimeout')}
            help={t('designer.props.common.invocationTimeoutHelp')}
          >
            <Input
              value={value.timeout || ''}
              onChange={(event) => update({ timeout: event.target.value || undefined })}
              placeholder={t('designer.props.common.invocationTimeoutPlaceholder')}
              aria-label={t('designer.props.common.invocationTimeout')}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.attemptTimeout')}
            help={t('designer.props.common.attemptTimeoutHelp')}
          >
            <Input
              value={value.attemptTimeout || ''}
              onChange={(event) => update({ attemptTimeout: event.target.value || undefined })}
              placeholder={t('designer.props.common.attemptTimeoutPlaceholder')}
              aria-label={t('designer.props.common.attemptTimeout')}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.maxAttempts')}
            help={t('designer.props.common.maxAttemptsHelp')}
          >
            <InputNumber
              min={1}
              max={100}
              precision={0}
              value={value.maxAttempts}
              onChange={(count) => update({ maxAttempts: count ?? undefined })}
              aria-label={t('designer.props.common.maxAttempts')}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.initialBackoff')}
            help={t('designer.props.common.initialBackoffHelp')}
          >
            <Input
              value={value.initialBackoff || ''}
              onChange={(event) => update({ initialBackoff: event.target.value || undefined })}
              placeholder="PT1S"
              aria-label={t('designer.props.common.initialBackoff')}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.backoffMultiplier')}
            help={t('designer.props.common.backoffMultiplierHelp')}
          >
            <InputNumber
              min={1}
              step={0.1}
              value={value.backoffMultiplier}
              onChange={(multiplier) => update({ backoffMultiplier: multiplier ?? undefined })}
              aria-label={t('designer.props.common.backoffMultiplier')}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.maxBackoff')}
            help={t('designer.props.common.maxBackoffHelp')}
          >
            <Input
              value={value.maxBackoff || ''}
              onChange={(event) => update({ maxBackoff: event.target.value || undefined })}
              placeholder="PT60S"
              aria-label={t('designer.props.common.maxBackoff')}
            />
          </Form.Item>
          <Form.Item
            label={t('designer.props.common.jitter')}
            help={t('designer.props.common.jitterHelp')}
          >
            <Select
              value={value.jitter ?? 'full'}
              options={[
                {
                  value: 'full',
                  label: t('designer.props.common.jitterFull'),
                },
                {
                  value: 'none',
                  label: t('designer.props.common.jitterNone'),
                },
              ]}
              onChange={(jitter) => update({ jitter: jitter === 'full' ? undefined : jitter })}
              aria-label={t('designer.props.common.jitter')}
            />
          </Form.Item>
          <Form.Item label={t('designer.props.common.retryOn')}>
            <AutoComplete
              value={value.retryOn || 'always'}
              options={['never', 'transient', 'always'].map((option) => ({ value: option }))}
              onChange={(retryOn) => update({ retryOn: retryOn || undefined })}
              aria-label={t('designer.props.common.retryOn')}
            />
          </Form.Item>
          <Form.Item label={t('designer.props.common.onFailure')}>
            <AutoComplete
              value={value.onFailure || 'propagate'}
              options={['propagate', 'continue'].map((option) => ({ value: option }))}
              onChange={(onFailure) => update({ onFailure: onFailure || undefined })}
              aria-label={t('designer.props.common.onFailure')}
            />
          </Form.Item>
        </>
      )}
    </>
  )
}
