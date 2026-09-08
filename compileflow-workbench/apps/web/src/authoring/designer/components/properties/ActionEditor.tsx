import { DeleteOutlined, PlusOutlined } from '@ant-design/icons'
import { AutoComplete, Button, Form, Input, Select, Space } from 'antd'
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

import './ActionEditor.css'

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

function MappingCard({
  variable,
  index,
  processVariableNames,
  processCall,
  onUpdate,
  onDirectionChange,
  onDelete,
}: {
  variable: VariableMapping
  index: number
  processVariableNames: string[]
  processCall: boolean
  onUpdate: (updates: VariableMappingUpdate) => void
  onDirectionChange: (direction: VariableMapping['direction']) => void
  onDelete: () => void
}) {
  const labels = usePropertyLabels()
  const groupLabel = `${labels.varParams} ${index + 1}`

  return (
    <div className="action-mapping-card" role="group" aria-label={groupLabel}>
      <div className="action-mapping-card-header">
        <span>{groupLabel}</span>
        <Button
          type="text"
          danger
          size="small"
          icon={<DeleteOutlined />}
          aria-label={`${labels.delete} ${index + 1}`}
          onClick={onDelete}
        />
      </div>
      <Form.Item label={labels.colDirection}>
        <Select
          size="small"
          value={variable.direction}
          onChange={onDirectionChange}
          aria-label={labels.colDirection}
          options={[
            { value: 'input', label: 'input' },
            { value: 'output', label: 'output' },
          ]}
        />
      </Form.Item>
      <Form.Item label={labels.colLocalVarName}>
        <Input
          size="small"
          value={variable.direction === 'input' ? variable.target : variable.source || ''}
          disabled={!processCall && variable.direction === 'output'}
          onChange={(event) =>
            onUpdate(
              variable.direction === 'input'
                ? { target: event.target.value }
                : { source: event.target.value }
            )
          }
          placeholder={labels.phVarName}
          aria-label={labels.colLocalVarName}
        />
      </Form.Item>
      {!processCall && (
        <Form.Item label={labels.colJavaType}>
          <Input
            size="small"
            value={variable.dataType || ''}
            onChange={(event) => onUpdate({ dataType: event.target.value })}
            placeholder={labels.phJavaType}
            aria-label={labels.colJavaType}
          />
        </Form.Item>
      )}
      <Form.Item label={labels.colMappingReference}>
        <MappedVariableTargetEditor
          variable={variable}
          processVariableNames={processVariableNames}
          onChange={(reference) => onUpdate(mappingReferenceUpdate(variable, reference))}
        />
      </Form.Item>
      <Form.Item label={labels.colDefaultValue} className="action-mapping-card-last-field">
        <MappedVariableDefaultEditor
          variable={variable}
          onChange={(defaultValue) => onUpdate(mappingDefaultUpdate(variable, defaultValue))}
        />
      </Form.Item>
    </div>
  )
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

  const labels = usePropertyLabels()

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
      {mappings.length === 0 ? (
        <div className="action-mapping-empty" role="status">
          {processCall ? labels.tableEmptySubVars : labels.tableEmptyParams}
        </div>
      ) : (
        <div className="action-mapping-list">
          {mappings.map((variable, index) => (
            <MappingCard
              key={index}
              variable={variable}
              index={index}
              processVariableNames={processVariableNames}
              processCall={processCall}
              onUpdate={(updates) => updateVar(index, updates)}
              onDirectionChange={(direction) => handleDirectionChange(index, direction)}
              onDelete={() =>
                onChange(mappings.filter((_, currentIndex) => currentIndex !== index))
              }
            />
          ))}
        </div>
      )}
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
  const updateInput = (index: number, changes: Partial<ReconcileInputMapping>) =>
    update({
      inputs: (value.inputs ?? []).map((input, current) =>
        current === index ? { ...input, ...changes } : input
      ),
    })
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
      {(value.inputs ?? []).length === 0 ? (
        <div className="action-mapping-empty" role="status">
          {labels.tableEmptyParams}
        </div>
      ) : (
        <div className="action-mapping-list">
          {(value.inputs ?? []).map((input, index) => (
            <div
              className="action-mapping-card"
              role="group"
              aria-label={`${labels.varParams} ${index + 1}`}
              key={index}
            >
              <div className="action-mapping-card-header">
                <span>{`${labels.varParams} ${index + 1}`}</span>
                <Button
                  type="text"
                  danger
                  size="small"
                  icon={<DeleteOutlined />}
                  aria-label={`${labels.delete} ${index + 1}`}
                  onClick={() =>
                    update({
                      inputs: (value.inputs ?? []).filter(
                        (_, currentIndex) => currentIndex !== index
                      ),
                    })
                  }
                />
              </div>
              <Form.Item label={labels.colMappingReference}>
                <Input
                  size="small"
                  value={input.source}
                  onChange={(event) => updateInput(index, { source: event.target.value })}
                  aria-label={labels.colMappingReference}
                />
              </Form.Item>
              <Form.Item label={labels.colLocalVarName}>
                <Input
                  size="small"
                  value={input.target}
                  onChange={(event) => updateInput(index, { target: event.target.value })}
                  aria-label={labels.colLocalVarName}
                />
              </Form.Item>
              <Form.Item label={labels.colJavaType} className="action-mapping-card-last-field">
                <Input
                  size="small"
                  value={input.dataType}
                  onChange={(event) => updateInput(index, { dataType: event.target.value })}
                  aria-label={labels.colJavaType}
                />
              </Form.Item>
            </div>
          ))}
        </div>
      )}
    </>
  )
}
