import { Divider, Form, Input, InputNumber, Segmented, Select, Switch } from 'antd'
import { useTranslation } from 'react-i18next'

import type {
  ActionExecution,
  EffectPolicy,
  EffectRecovery,
  ReconcileActionDefinition,
} from '../../types/action'

import { ReconcileActionEditor } from './ActionEditor'

interface EffectPolicyFieldsProps {
  execution?: ActionExecution
  value?: EffectPolicy
  onChange: (value: EffectPolicy | undefined) => void
}

export function EffectPolicyFields({ execution, value, onChange }: EffectPolicyFieldsProps) {
  const { t } = useTranslation()
  const update = (patch: Partial<EffectPolicy>) => onChange({ ...value, ...patch })
  const dynamic = value?.recoveryPlanVariable !== undefined

  if (execution !== 'effect' && value === undefined) return null

  return (
    <>
      <Divider style={{ margin: '24px 0 16px' }}>
        {t('designer.props.section.effectPolicyTitle')}
      </Divider>
      <Form.Item label={t('designer.props.common.effectPolicyEnabled')}>
        <Switch
          checked={value !== undefined}
          disabled={execution !== 'effect'}
          onChange={(enabled) => onChange(enabled ? { recovery: 'manual' } : undefined)}
          aria-label={t('designer.props.common.effectPolicyEnabled')}
        />
      </Form.Item>
      {value !== undefined && (
        <>
          <Form.Item label={t('designer.props.common.effectPolicyMode')}>
            <Segmented
              block
              value={dynamic ? 'dynamic' : 'static'}
              aria-label={t('designer.props.common.effectPolicyMode')}
              options={[
                { value: 'static', label: t('designer.props.common.effectPolicyStatic') },
                { value: 'dynamic', label: t('designer.props.common.effectPolicyDynamic') },
              ]}
              onChange={(mode) =>
                onChange(mode === 'dynamic' ? { recoveryPlanVariable: '' } : { recovery: 'manual' })
              }
            />
          </Form.Item>
          {dynamic ? (
            <DynamicEffectPolicyFields value={value} onChange={onChange} update={update} />
          ) : (
            <StaticEffectPolicyFields value={value} onChange={onChange} update={update} />
          )}
        </>
      )}
    </>
  )
}

interface PolicyFieldsProps {
  value: EffectPolicy
  onChange: (value: EffectPolicy) => void
  update: (patch: Partial<EffectPolicy>) => void
}

function DynamicEffectPolicyFields({ value, onChange, update }: PolicyFieldsProps) {
  const { t } = useTranslation()
  return (
    <>
      <Form.Item label={t('designer.props.common.recoveryPlanVariable')} required>
        <Input
          value={value.recoveryPlanVariable ?? ''}
          onChange={(event) => update({ recoveryPlanVariable: event.target.value })}
          aria-label={t('designer.props.common.recoveryPlanVariable')}
        />
      </Form.Item>
      <ReconcileActionFields value={value} onChange={onChange} optional />
    </>
  )
}

function StaticEffectPolicyFields({ value, onChange, update }: PolicyFieldsProps) {
  const { t } = useTranslation()
  const recovery = value.recovery ?? 'manual'
  return (
    <>
      <Form.Item label={t('designer.props.common.effectRecovery')}>
        <Select<EffectRecovery>
          value={recovery}
          options={[
            { value: 'manual', label: t('designer.props.common.effectRecoveryManual') },
            { value: 'retry', label: t('designer.props.common.effectRecoveryRetry') },
            { value: 'reconcile', label: t('designer.props.common.effectRecoveryReconcile') },
          ]}
          onChange={(nextRecovery) => onChange(defaultStaticPolicy(nextRecovery))}
          aria-label={t('designer.props.common.effectRecovery')}
        />
      </Form.Item>
      {recovery !== 'manual' && (
        <>
          <Form.Item label={t('designer.props.common.maxAttempts')} required>
            <InputNumber
              min={recovery === 'retry' ? 2 : 1}
              max={100}
              precision={0}
              value={value.maxAttempts}
              onChange={(count) => update({ maxAttempts: count ?? undefined })}
              aria-label={t('designer.props.common.maxAttempts')}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <Form.Item label={t('designer.props.common.recoveryDelay')} required>
            <Input
              value={value.recoveryDelay ?? ''}
              placeholder="PT1S"
              onChange={(event) => update({ recoveryDelay: event.target.value || undefined })}
              aria-label={t('designer.props.common.recoveryDelay')}
            />
          </Form.Item>
          <Form.Item label={t('designer.props.common.maxRecoveryDuration')}>
            <Input
              value={value.maxRecoveryDuration ?? ''}
              placeholder="PT1H"
              onChange={(event) => update({ maxRecoveryDuration: event.target.value || undefined })}
              aria-label={t('designer.props.common.maxRecoveryDuration')}
            />
          </Form.Item>
        </>
      )}
      {recovery === 'reconcile' && (
        <>
          <Form.Item label={t('designer.props.common.maxReconcileAttempts')} required>
            <InputNumber
              min={1}
              max={1000}
              precision={0}
              value={value.maxReconcileAttempts}
              onChange={(count) => update({ maxReconcileAttempts: count ?? undefined })}
              aria-label={t('designer.props.common.maxReconcileAttempts')}
              style={{ width: '100%' }}
            />
          </Form.Item>
          <ReconcileActionFields value={value} onChange={onChange} />
        </>
      )}
    </>
  )
}

function ReconcileActionFields({
  value,
  onChange,
  optional = false,
}: {
  value: EffectPolicy
  onChange: (value: EffectPolicy) => void
  optional?: boolean
}) {
  const { t } = useTranslation()
  const action = value.reconcileAction
  return (
    <>
      {optional && (
        <Form.Item label={t('designer.props.common.reconcileActionEnabled')}>
          <Switch
            checked={action !== undefined}
            onChange={(enabled) =>
              onChange({
                ...value,
                reconcileAction: enabled ? defaultReconcileAction() : undefined,
              })
            }
            aria-label={t('designer.props.common.reconcileActionEnabled')}
          />
        </Form.Item>
      )}
      {(!optional || action !== undefined) && (
        <>
          <Divider style={{ margin: '16px 0' }}>
            {t('designer.props.section.reconcileActionTitle')}
          </Divider>
          <ReconcileActionEditor
            value={action ?? defaultReconcileAction()}
            onChange={(reconcileAction) => onChange({ ...value, reconcileAction })}
          />
        </>
      )}
    </>
  )
}

function defaultStaticPolicy(recovery: EffectRecovery): EffectPolicy {
  if (recovery === 'manual') return { recovery }
  if (recovery === 'retry') return { recovery, maxAttempts: 2, recoveryDelay: 'PT1S' }
  return {
    recovery,
    maxAttempts: 1,
    maxReconcileAttempts: 1,
    recoveryDelay: 'PT1S',
    reconcileAction: defaultReconcileAction(),
  }
}

function defaultReconcileAction(): ReconcileActionDefinition {
  return { actionType: 'spring-bean' }
}
