import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { AutoComplete, Button, Form, Input, Select, Space, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useCallback, useMemo } from 'react'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import { selectCurrentProcess } from '../../store/editorSlice'
import {
  type ActionDefinition,
  type ActionInvocationDefinition,
  type ReconcileActionDefinition,
  type ReconcileInputMapping,
  type ActionType,
  type VariableMapping,
  type VariableMappingUpdate,
  applyVariableMappingUpdate,
  mappingDefaultUpdate,
  mappingDirectionUpdate,
  mappingReferenceUpdate,
} from '../../types/action'

import { MappedVariableDefaultEditor } from './MappedVariableDefaultEditor'
import { MappedVariableTargetEditor } from './MappedVariableTargetEditor'
import { ScriptSourceEditor } from './ScriptSourceEditor'

import { useAppSelector } from '@/app/hooks'

type ActionUpdate = Partial<ActionDefinition>
type ActionInvocationUpdate = Partial<ActionInvocationDefinition>

interface ActionEditorProps {
  value: ActionDefinition
  onChange: (value: ActionDefinition) => void
  allowedActionTypes?: readonly ActionType[]
}

export function createDefaultAction(): ActionDefinition {
  return { actionType: 'java', execution: 'replayable' }
}

function mergeAction(current: ActionDefinition, updates: ActionUpdate): ActionDefinition {
  return { ...current, ...updates }
}

function buildActionTypeUpdate(actionType: ActionType): ActionInvocationUpdate {
  switch (actionType) {
    case 'java':
      return {
        actionType,
        bean: undefined,
        language: undefined,
        source: undefined,
      }
    case 'spring-bean':
      return {
        actionType,
        language: undefined,
        source: undefined,
      }
    case 'script':
      return {
        actionType,
        bean: undefined,
        className: undefined,
        method: undefined,
        language: 'java',
        source: undefined,
      }
    default:
      return { actionType }
  }
}

function ActionTypeSelect({
  actionType,
  onChange,
  allowedActionTypes,
}: {
  actionType: ActionType
  onChange: (actionType: ActionType) => void
  allowedActionTypes?: readonly ActionType[]
}) {
  const labels = usePropertyLabels()
  const allOptions: Array<{ value: ActionType; label: string }> = [
    { value: 'java', label: labels.actionTypeJava },
    { value: 'spring-bean', label: labels.actionTypeSpringBean },
    { value: 'script', label: labels.actionTypeScript },
  ]
  const builtInOptions = allOptions.filter(
    (option) => !allowedActionTypes || allowedActionTypes.includes(option.value)
  )
  const currentIsListed = builtInOptions.some((option) => option.value === actionType)

  return (
    <Form.Item label={labels.actionType} required>
      <Select<ActionType> value={actionType} onChange={onChange} aria-label={labels.actionType}>
        {!currentIsListed && <Select.Option value={actionType}>{actionType}</Select.Option>}
        {builtInOptions.map((option) => (
          <Select.Option key={option.value} value={option.value}>
            {option.label}
          </Select.Option>
        ))}
      </Select>
    </Form.Item>
  )
}

function JavaActionFields({
  value,
  onUpdate,
}: {
  value: ActionInvocationDefinition
  onUpdate: (updates: ActionInvocationUpdate) => void
}) {
  const labels = usePropertyLabels()

  return (
    <>
      <Form.Item label={labels.className} required help={labels.classNameHelp}>
        <Input
          value={value.className || ''}
          onChange={(event) => onUpdate({ className: event.target.value })}
          placeholder={labels.phJavaClass}
          aria-label={labels.className}
        />
      </Form.Item>
      <Form.Item label={labels.methodNameOptional} help={labels.methodNameOptionalHelp}>
        <Input
          value={value.method ?? 'execute'}
          onChange={(event) => onUpdate({ method: event.target.value })}
          placeholder={labels.phMethodExecute}
          aria-label={labels.methodNameOptional}
        />
      </Form.Item>
    </>
  )
}

function SpringBeanActionFields({
  value,
  onUpdate,
}: {
  value: ActionInvocationDefinition
  onUpdate: (updates: ActionInvocationUpdate) => void
}) {
  const labels = usePropertyLabels()

  return (
    <>
      <Form.Item label={labels.beanName} required help={labels.beanNameHelp}>
        <Input
          value={value.bean || ''}
          onChange={(event) => onUpdate({ bean: event.target.value })}
          placeholder={labels.phBeanName}
          aria-label={labels.beanName}
        />
      </Form.Item>
      <JavaActionFields value={value} onUpdate={onUpdate} />
    </>
  )
}

function ScriptActionFields({
  value,
  onUpdate,
}: {
  value: ActionInvocationDefinition
  onUpdate: (updates: ActionInvocationUpdate) => void
}) {
  const labels = usePropertyLabels()

  return (
    <>
      <Form.Item label={labels.scriptLanguage} required>
        <AutoComplete
          value={value.language || ''}
          options={[
            { value: 'java', label: 'Java 17' },
            { value: 'qlexpress', label: 'QLExpress' },
          ]}
          onChange={(language) => onUpdate({ language })}
          placeholder={labels.phScriptLanguage}
          aria-label={labels.scriptLanguage}
        />
      </Form.Item>
      <Form.Item label={labels.scriptSource} required>
        <ScriptSourceEditor
          language={value.language || ''}
          value={value.source || ''}
          onChange={(source) => onUpdate({ source })}
          ariaLabel={labels.scriptSource}
        />
      </Form.Item>
    </>
  )
}

function ActionImplementationFields({
  value,
  onUpdate,
}: {
  value: ActionInvocationDefinition
  onUpdate: (updates: ActionInvocationUpdate) => void
}) {
  switch (value.actionType) {
    case 'java':
      return <JavaActionFields value={value} onUpdate={onUpdate} />
    case 'spring-bean':
      return <SpringBeanActionFields value={value} onUpdate={onUpdate} />
    case 'script':
      return <ScriptActionFields value={value} onUpdate={onUpdate} />
    default:
      return null
  }
}

function createDefaultVariableMapping(processCall: boolean): VariableMapping {
  return processCall
    ? { target: '', direction: 'input' }
    : { target: '', dataType: 'java.lang.String', direction: 'input' }
}

function indexMappings(
  mappings: VariableMapping[]
): Array<VariableMapping & { _idx: number; key: number }> {
  return mappings.map((variable, index) => ({
    ...variable,
    _idx: index,
    key: index,
  }))
}

interface VariableMappingColumnsOptions {
  mappings: VariableMapping[]
  processVariableNames: string[]
  processCall: boolean
  updateVar: (index: number, updates: VariableMappingUpdate) => void
  handleDirectionChange: (index: number, direction: VariableMapping['direction']) => void
  onChange: (mappings: VariableMapping[]) => void
}

function useVariableMappingColumns({
  mappings,
  processVariableNames,
  processCall,
  updateVar,
  handleDirectionChange,
  onChange,
}: VariableMappingColumnsOptions) {
  const labels = usePropertyLabels()
  const columns: ColumnsType<VariableMapping & { _idx: number }> = useMemo(() => {
    const definitions: ColumnsType<VariableMapping & { _idx: number }> = [
      {
        title: labels.colLocalVarName,
        dataIndex: 'binding',
        width: 180,
        render: (_, record) => (
          <Input
            size="small"
            value={record.direction === 'input' ? record.target : record.source || ''}
            disabled={!processCall && record.direction === 'output'}
            onChange={(event) =>
              updateVar(
                record._idx,
                record.direction === 'input'
                  ? { target: event.target.value }
                  : { source: event.target.value }
              )
            }
            placeholder={labels.phVarName}
            aria-label={labels.colLocalVarName}
          />
        ),
      },
      {
        title: labels.colDirection,
        dataIndex: 'direction',
        width: 120,
        render: (value, record) => (
          <Select
            size="small"
            value={value}
            onChange={(nextValue) => handleDirectionChange(record._idx, nextValue)}
            aria-label={labels.colDirection}
            style={{ width: '100%' }}
          >
            <Select.Option value="input">input</Select.Option>
            <Select.Option value="output">output</Select.Option>
          </Select>
        ),
      },
      {
        title: labels.colMappingReference,
        dataIndex: 'reference',
        width: 220,
        render: (_, record) => (
          <MappedVariableTargetEditor
            variable={record}
            processVariableNames={processVariableNames}
            onChange={(reference) =>
              updateVar(record._idx, mappingReferenceUpdate(record, reference))
            }
          />
        ),
      },
      {
        title: labels.colDefaultValue,
        dataIndex: 'defaultValue',
        width: 220,
        render: (_, record) => (
          <MappedVariableDefaultEditor
            variable={record}
            onChange={(defaultValue) =>
              updateVar(record._idx, mappingDefaultUpdate(record, defaultValue))
            }
          />
        ),
      },
      {
        title: '',
        key: 'delete',
        width: 64,
        render: (_, record) => (
          <Button
            type="text"
            danger
            size="small"
            icon={<DeleteOutlined />}
            aria-label={labels.delete}
            onClick={() =>
              onChange(mappings.filter((_, currentIndex) => currentIndex !== record._idx))
            }
          />
        ),
      },
    ]
    if (!processCall) {
      definitions.splice(1, 0, {
        title: labels.colJavaType,
        dataIndex: 'dataType',
        width: 220,
        render: (value, record) => (
          <Input
            size="small"
            value={value || ''}
            onChange={(event) => updateVar(record._idx, { dataType: event.target.value })}
            placeholder={labels.phJavaType}
            aria-label={labels.colJavaType}
          />
        ),
      })
    }
    return definitions
  }, [
    mappings,
    handleDirectionChange,
    labels,
    onChange,
    processVariableNames,
    processCall,
    updateVar,
  ])
  return { columns, labels }
}

function MappingTable({
  mappings,
  processVariableNames,
  onChange,
  processCall = false,
}: {
  mappings: VariableMapping[]
  processVariableNames: string[]
  onChange: (mappings: VariableMapping[]) => void
  processCall?: boolean
}) {
  const updateVar = useCallback(
    (index: number, updates: VariableMappingUpdate) => {
      onChange(
        mappings.map((variable, currentIndex) =>
          currentIndex === index ? applyVariableMappingUpdate(variable, updates) : variable
        )
      )
    },
    [mappings, onChange]
  )

  const handleDirectionChange = useCallback(
    (index: number, direction: VariableMapping['direction']) => {
      const variable = mappings[index]
      if (!variable) return
      updateVar(
        index,
        mappingDirectionUpdate(variable, direction, processVariableNames, processCall)
      )
    },
    [mappings, processCall, processVariableNames, updateVar]
  )

  const { columns, labels } = useVariableMappingColumns({
    mappings,
    processVariableNames,
    processCall,
    updateVar,
    handleDirectionChange,
    onChange,
  })

  return (
    <>
      <Space style={{ marginBottom: 8 }}>
        {processCall ? labels.varTransfer : labels.varParams}
        <Button
          type="link"
          size="small"
          icon={<PlusOutlined />}
          onClick={() => onChange([...mappings, createDefaultVariableMapping(processCall)])}
        >
          {labels.add}
        </Button>
      </Space>
      <Table
        columns={columns}
        dataSource={indexMappings(mappings)}
        size="small"
        pagination={false}
        scroll={{ x: processCall ? 1024 : 1244 }}
        locale={{ emptyText: processCall ? labels.tableEmptySubVars : labels.tableEmptyParams }}
      />
    </>
  )
}

export function VariableMappingsEditor({
  value,
  onChange,
  processCall = false,
}: {
  value: VariableMapping[]
  onChange: (value: VariableMapping[]) => void
  processCall?: boolean
}) {
  const processVariables = useAppSelector(selectCurrentProcess)?.variables
  const processVariableNames = useMemo(
    () => processVariables?.map((variable) => variable.name).filter(Boolean) || [],
    [processVariables]
  )
  return (
    <MappingTable
      mappings={value}
      processVariableNames={processVariableNames}
      processCall={processCall}
      onChange={onChange}
    />
  )
}

export function ActionEditor({ value, onChange, allowedActionTypes }: ActionEditorProps) {
  const labels = usePropertyLabels()
  const processVariables = useAppSelector(selectCurrentProcess)?.variables
  const processVariableNames = useMemo(
    () => processVariables?.map((variable) => variable.name).filter(Boolean) || [],
    [processVariables]
  )

  const updateAction = useCallback(
    (updates: ActionUpdate) => {
      onChange(mergeAction(value, updates))
    },
    [onChange, value]
  )

  const handleActionTypeChange = useCallback(
    (actionType: ActionType) => {
      updateAction(buildActionTypeUpdate(actionType))
    },
    [updateAction]
  )
  const actionTypeIsFixed =
    allowedActionTypes?.length === 1 && allowedActionTypes[0] === value.actionType

  return (
    <>
      {!actionTypeIsFixed && (
        <ActionTypeSelect
          actionType={value.actionType}
          onChange={handleActionTypeChange}
          allowedActionTypes={allowedActionTypes}
        />
      )}
      <Form.Item label={labels.execution} help={labels.executionHelp}>
        <Select
          value={value.execution || 'replayable'}
          onChange={(execution) =>
            updateAction({
              execution,
              effectPolicy: execution === 'effect' ? value.effectPolicy : undefined,
            })
          }
          aria-label={labels.execution}
          options={[
            { value: 'replayable', label: labels.executionReplayable },
            { value: 'effect', label: labels.executionEffect },
          ]}
        />
      </Form.Item>
      <ActionImplementationFields value={value} onUpdate={updateAction} />
      <MappingTable
        mappings={value.mappings || []}
        processVariableNames={processVariableNames}
        onChange={(mappings) => updateAction({ mappings })}
      />
    </>
  )
}

export function ReconcileActionEditor({
  value,
  onChange,
}: {
  value: ReconcileActionDefinition
  onChange: (value: ReconcileActionDefinition) => void
}) {
  const labels = usePropertyLabels()
  const update = useCallback(
    (changes: Partial<ReconcileActionDefinition>) => onChange({ ...value, ...changes }),
    [onChange, value]
  )
  const indexedInputs = (value.inputs ?? []).map((input, index) => ({
    ...input,
    key: index,
    index,
  }))
  const updateInput = (index: number, changes: Partial<ReconcileInputMapping>) =>
    update({
      inputs: (value.inputs ?? []).map((input, current) =>
        current === index ? { ...input, ...changes } : input
      ),
    })
  const columns: ColumnsType<ReconcileInputMapping & { index: number }> = [
    {
      title: labels.colMappingReference,
      dataIndex: 'source',
      width: 220,
      render: (source, input) => (
        <Input
          size="small"
          value={source}
          onChange={(event) => updateInput(input.index, { source: event.target.value })}
          aria-label={labels.colMappingReference}
        />
      ),
    },
    {
      title: labels.colLocalVarName,
      dataIndex: 'target',
      width: 180,
      render: (target, input) => (
        <Input
          size="small"
          value={target}
          onChange={(event) => updateInput(input.index, { target: event.target.value })}
          aria-label={labels.colLocalVarName}
        />
      ),
    },
    {
      title: labels.colJavaType,
      dataIndex: 'dataType',
      width: 220,
      render: (dataType, input) => (
        <Input
          size="small"
          value={dataType}
          onChange={(event) => updateInput(input.index, { dataType: event.target.value })}
          aria-label={labels.colJavaType}
        />
      ),
    },
    {
      title: '',
      key: 'delete',
      width: 64,
      render: (_, input) => (
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          aria-label={labels.delete}
          onClick={() =>
            update({ inputs: (value.inputs ?? []).filter((_, index) => index !== input.index) })
          }
        />
      ),
    },
  ]

  return (
    <>
      <ActionTypeSelect
        actionType={value.actionType}
        onChange={(actionType) => update(buildActionTypeUpdate(actionType))}
      />
      <ActionImplementationFields value={value} onUpdate={update} />
      <Space style={{ marginBottom: 8 }}>
        {labels.varParams}
        <Button
          type="link"
          size="small"
          icon={<PlusOutlined />}
          onClick={() =>
            update({
              inputs: [
                ...(value.inputs ?? []),
                { source: '', target: '', dataType: 'java.lang.String' },
              ],
            })
          }
        >
          {labels.add}
        </Button>
      </Space>
      <Table
        columns={columns}
        dataSource={indexedInputs}
        size="small"
        pagination={false}
        scroll={{ x: 684 }}
        locale={{ emptyText: labels.tableEmptyParams }}
      />
    </>
  )
}
