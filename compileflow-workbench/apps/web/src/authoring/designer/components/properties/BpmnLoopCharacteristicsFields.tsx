import { AutoComplete, Divider, Form, Input, InputNumber, Select, Switch } from 'antd'
import { useTranslation } from 'react-i18next'

import { selectCurrentProcess } from '../../store/editorSlice'
import type {
  LoopCharacteristics,
  MultiInstanceLoopCharacteristics,
  StandardLoopCharacteristics,
} from '../../types/bpmnNodeTypes'
import { MAX_LOOP_ITERATIONS } from '../../types/loopLimits'

import { useAppSelector } from '@/app/hooks'

interface BpmnLoopCharacteristicsFieldsProps {
  value?: LoopCharacteristics
  onChange: (value: LoopCharacteristics | undefined) => void
}

type LoopMode = 'none' | 'standard' | 'multiInstance'

export function BpmnLoopCharacteristicsFields({
  value,
  onChange,
}: BpmnLoopCharacteristicsFieldsProps) {
  const { t } = useTranslation()
  const mode: LoopMode = value?.type || 'none'

  const changeMode = (nextMode: LoopMode) => {
    if (nextMode === 'none') {
      onChange(undefined)
    } else if (nextMode === 'standard') {
      onChange({ type: 'standard', testBefore: true, loopMaximum: 1 })
    } else {
      onChange({
        type: 'multiInstance',
        isSequential: true,
        collection: '',
        item: 'item',
      })
    }
  }

  return (
    <>
      <Divider style={{ fontSize: 12 }}>{t('designer.props.bpmnLoop.title')}</Divider>
      <Form.Item label={t('designer.props.bpmnLoop.mode')}>
        <Select<LoopMode>
          value={mode}
          onChange={changeMode}
          aria-label={t('designer.props.bpmnLoop.mode')}
          options={[
            { value: 'none', label: t('designer.props.bpmnLoop.none') },
            { value: 'standard', label: t('designer.props.bpmnLoop.standard') },
            { value: 'multiInstance', label: t('designer.props.bpmnLoop.multiInstance') },
          ]}
        />
      </Form.Item>

      {value?.type === 'standard' && <StandardLoopFields value={value} onChange={onChange} />}
      {value?.type === 'multiInstance' && (
        <MultiInstanceLoopFields value={value} onChange={onChange} />
      )}
    </>
  )
}

function StandardLoopFields({
  value,
  onChange,
}: {
  value: StandardLoopCharacteristics
  onChange: (value: LoopCharacteristics) => void
}) {
  const { t } = useTranslation()
  const update = (updates: Partial<StandardLoopCharacteristics>) =>
    onChange({ ...value, ...updates })
  return (
    <>
      <Form.Item
        label={t('designer.props.bpmnLoop.testBefore')}
        help={t('designer.props.bpmnLoop.testBeforeHelp')}
      >
        <Switch
          checked={value.testBefore ?? false}
          onChange={(testBefore) => update({ testBefore })}
          aria-label={t('designer.props.bpmnLoop.testBefore')}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.node.loop.whileExpr')}>
        <Input.TextArea
          value={value.loopCondition || ''}
          onChange={(event) => update({ loopCondition: event.target.value || undefined })}
          aria-label={t('designer.props.node.loop.whileExpr')}
          rows={3}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.bpmnLoop.maximum')}>
        <InputNumber
          min={1}
          max={MAX_LOOP_ITERATIONS}
          precision={0}
          value={value.loopMaximum}
          onChange={(loopMaximum) =>
            update({ loopMaximum: loopMaximum === null ? undefined : loopMaximum })
          }
          aria-label={t('designer.props.bpmnLoop.maximum')}
          style={{ width: '100%' }}
        />
      </Form.Item>
    </>
  )
}

function MultiInstanceLoopFields({
  value,
  onChange,
}: {
  value: MultiInstanceLoopCharacteristics
  onChange: (value: LoopCharacteristics) => void
}) {
  const { t } = useTranslation()
  const processVariables = useAppSelector(selectCurrentProcess)?.variables || []
  const update = (updates: Partial<MultiInstanceLoopCharacteristics>) =>
    onChange({ ...value, ...updates })
  return (
    <>
      <Form.Item label={t('designer.props.node.loop.collection')} required>
        <AutoComplete
          value={value.collection}
          onChange={(collection) => update({ collection })}
          options={processVariables.map((variable) => ({
            value: variable.name,
            label: `${variable.name} (${variable.type})`,
          }))}
          filterOption={(input, option) =>
            String(option?.label ?? option?.value ?? '')
              .toLowerCase()
              .includes(input.toLowerCase())
          }
          aria-label={t('designer.props.node.loop.collection')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.execution')}
        help={t('designer.props.node.loop.executionHelp')}
      >
        <Select<'sequential' | 'parallel'>
          value={value.isSequential ? 'sequential' : 'parallel'}
          onChange={(execution) => update({ isSequential: execution === 'sequential' })}
          aria-label={t('designer.props.node.loop.execution')}
          options={[
            {
              value: 'sequential',
              label: t('designer.props.node.loop.sequentialExecution'),
            },
            {
              value: 'parallel',
              label: t('designer.props.node.loop.parallelExecution'),
            },
          ]}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.node.loop.item')} required>
        <Input
          value={value.item}
          onChange={(event) => update({ item: event.target.value })}
          aria-label={t('designer.props.node.loop.item')}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.node.loop.itemType')}>
        <Input
          value={value.itemType || ''}
          onChange={(event) => update({ itemType: event.target.value || undefined })}
          placeholder="java.lang.String"
          aria-label={t('designer.props.node.loop.itemType')}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.node.loop.index')}>
        <Input
          value={value.index || ''}
          onChange={(event) => update({ index: event.target.value || undefined })}
          placeholder="index"
          aria-label={t('designer.props.node.loop.index')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.outputTarget')}
        help={t('designer.props.node.loop.outputTargetHelp')}
      >
        <Select
          value={value.target}
          onChange={(target) => update({ target })}
          options={processVariables.map((variable) => ({
            value: variable.name,
            label: `${variable.name} (${variable.type})`,
          }))}
          allowClear
          showSearch
          optionFilterProp="label"
          aria-label={t('designer.props.node.loop.outputTarget')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.outputSource')}
        help={t('designer.props.node.loop.outputSourceHelp')}
      >
        <Select
          value={value.source}
          onChange={(source) => update({ source })}
          options={processVariables
            .filter((variable) => variable.inOutType === 'inner')
            .map((variable) => ({
              value: variable.name,
              label: `${variable.name} (${variable.type})`,
            }))}
          allowClear
          showSearch
          optionFilterProp="label"
          aria-label={t('designer.props.node.loop.outputSource')}
        />
      </Form.Item>
    </>
  )
}
