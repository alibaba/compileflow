import {
  BugOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ReloadOutlined,
  StepForwardOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Card,
  Collapse,
  Input,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { TFunction } from 'i18next'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { UnifiedProcessDefinition } from '../types/flowDefinition'

import { EngineDebugSection } from './EngineDebugSection'

import { generateProcessXml } from '@/authoring/designer/serialization/flowXml'
import {
  type Breakpoint,
  type ExecutionEvent,
  ExecutionState,
  type ProcessSimulationEngine,
} from '@/authoring/designer/simulation/ProcessSimulationEngine'
import { formatSimulationError } from '@/authoring/designer/simulation/simulationErrors'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { toError } from '@/shared/errors'
import { usePaginationItemRender } from '@/shared/hooks/usePaginationItemRender'
import { formatTime } from '@/shared/i18n/dateTime'

import './ProcessDebuggerPanel.css'

const { TextArea } = Input

type DebugMode = 'simulation' | 'engine'

interface ProcessDebuggerPanelProps {
  /** Process definition being debugged. */
  flowDefinition: UnifiedProcessDefinition

  /** In-browser simulation engine. */
  simulationEngine: ProcessSimulationEngine

  /** Highlights the current node on the canvas. */
  onHighlightNode?: (nodeId: string | null) => void

  /** Highlights the current connection on the canvas. */
  onHighlightConnection?: (connectionId: string | null) => void
}

interface SimulationDebuggerProps extends ProcessDebuggerPanelProps {
  initialVarsInput: string
  onInitialVarsInputChange: (value: string) => void
}

interface SimulationDebuggerState {
  executionState: ExecutionState
  breakpoints: Breakpoint[]
  variables: Record<string, unknown>
  events: ExecutionEvent[]
  currentNodeId: string | null
  start: () => Promise<void>
  stepNext: () => Promise<void>
  continueExecution: () => Promise<void>
  reset: () => void
  addBreakpoint: (nodeId: string, condition: string) => void
  deleteBreakpoint: (id: string) => void
  toggleBreakpoint: (id: string) => void
}

interface SimulationEventSync {
  event: ExecutionEvent
  simulationEngine: ProcessSimulationEngine
  updateVariables: () => void
  setCurrentNodeId: (nodeId: string | null) => void
  setExecutionState: (state: ExecutionState) => void
  onHighlightNode?: (nodeId: string | null) => void
  onHighlightConnection?: (connectionId: string | null) => void
}

const stateConfigs = (t: TFunction): Record<ExecutionState, { color: string; label: string }> => ({
  ready: { color: 'default', label: t('designer.debug.state.ready') },
  running: { color: 'processing', label: t('designer.debug.state.running') },
  paused: { color: 'warning', label: t('designer.debug.state.paused') },
  completed: { color: 'success', label: t('designer.debug.state.completed') },
  error: { color: 'error', label: t('designer.debug.state.error') },
})

const eventTypeConfigs = (t: TFunction): Record<string, { color: string; label: string }> => ({
  'node-enter': { color: 'blue', label: t('designer.debug.event.nodeEnter') },
  'node-exit': { color: 'cyan', label: t('designer.debug.event.nodeExit') },
  'edge-traverse': { color: 'green', label: t('designer.debug.event.edgeTraverse') },
  'variable-change': { color: 'purple', label: t('designer.debug.event.variableChange') },
  'breakpoint-hit': { color: 'orange', label: t('designer.debug.event.breakpointHit') },
  error: { color: 'red', label: t('designer.debug.event.error') },
})

function stateTag(state: ExecutionState, t: TFunction) {
  const config = stateConfigs(t)[state]
  return <Tag color={config.color}>{config.label}</Tag>
}

function eventTypeTag(type: string, t: TFunction) {
  const config = eventTypeConfigs(t)[type] || { color: 'default', label: type }
  return <Tag color={config.color}>{config.label}</Tag>
}

function eventDetails(record: ExecutionEvent, t: TFunction) {
  if (record.nodeId) return t('designer.debug.detail.node', { id: record.nodeId })
  if (record.connectionId) return t('designer.debug.detail.edge', { id: record.connectionId })
  if (record.variableName) return `${record.variableName} = ${JSON.stringify(record.variableValue)}`
  if (record.error) {
    return t('designer.debug.detail.error', {
      message: formatSimulationError(record.error, t),
    })
  }
  return '-'
}

function variablesFromEngine(simulationEngine: ProcessSimulationEngine) {
  const vars: Record<string, unknown> = {}
  simulationEngine.getContext().variables.forEach((value, key) => {
    vars[key] = value
  })
  return vars
}

function syncSimulationEvent({
  event,
  simulationEngine,
  updateVariables,
  setCurrentNodeId,
  setExecutionState,
  onHighlightNode,
  onHighlightConnection,
}: SimulationEventSync) {
  if (event.type === 'node-enter') {
    setCurrentNodeId(event.nodeId || null)
    if (event.nodeId) onHighlightNode?.(event.nodeId)
  }
  if (event.type === 'edge-traverse' && event.connectionId) {
    onHighlightConnection?.(event.connectionId)
  }
  if (event.type === 'variable-change') updateVariables()
  setExecutionState(simulationEngine.getState())
}

function useSimulationDebugger({
  flowDefinition,
  simulationEngine,
  onHighlightNode,
  onHighlightConnection,
  initialVarsInput,
  t,
}: SimulationDebuggerProps & { t: TFunction }): SimulationDebuggerState {
  const { message } = App.useApp()
  const [executionState, setExecutionState] = useState(ExecutionState.READY)
  const [breakpoints, setBreakpoints] = useState<Breakpoint[]>([])
  const [variables, setVariables] = useState<Record<string, unknown>>({})
  const [events, setEvents] = useState<ExecutionEvent[]>([])
  const [currentNodeId, setCurrentNodeId] = useState<string | null>(null)

  const updateVariables = useCallback(() => {
    setVariables(variablesFromEngine(simulationEngine))
  }, [simulationEngine])

  const updateBreakpoints = useCallback(() => {
    setBreakpoints(simulationEngine.getBreakpoints())
  }, [simulationEngine])

  useEffect(() => {
    setEvents([])
    setCurrentNodeId(null)
    setVariables({})
    setExecutionState(ExecutionState.READY)
    setBreakpoints(simulationEngine.getBreakpoints())
  }, [simulationEngine])

  useEffect(() => {
    const handleEvent = (event: ExecutionEvent) => {
      setEvents((prev) => [...prev, event])
      syncSimulationEvent({
        event,
        simulationEngine,
        updateVariables,
        setCurrentNodeId,
        setExecutionState,
        onHighlightNode,
        onHighlightConnection,
      })
    }
    simulationEngine.on(handleEvent)
    return () => simulationEngine.off(handleEvent)
  }, [simulationEngine, onHighlightNode, onHighlightConnection, updateVariables])

  const start = useCallback(async () => {
    try {
      const initialVars = JSON.parse(initialVarsInput)
      setEvents([])
      setCurrentNodeId(null)
      const result = await simulationEngine.start(initialVars)
      setExecutionState(result.state)
      setVariables(result.finalVariables)
      if (result.error) {
        message.error(formatSimulationError(result.error, t))
        return
      }
      if (result.state === 'completed') {
        message.success(t('designer.debug.sim.runComplete'))
      }
    } catch (error) {
      message.error(
        t('designer.debug.sim.runFailed', {
          message: formatSimulationError(toError(error).message, t),
        })
      )
    }
  }, [initialVarsInput, simulationEngine, t])

  const stepNext = useCallback(async () => {
    try {
      await simulationEngine.stepNext()
      setExecutionState(simulationEngine.getState())
      updateVariables()
    } catch (error) {
      message.error(formatSimulationError(toError(error).message, t))
    }
  }, [simulationEngine, t, updateVariables])

  const continueExecution = useCallback(async () => {
    try {
      await simulationEngine.continue()
      setExecutionState(simulationEngine.getState())
      updateVariables()
    } catch (error) {
      message.error(formatSimulationError(toError(error).message, t))
    }
  }, [simulationEngine, t, updateVariables])

  const reset = useCallback(() => {
    simulationEngine.reset()
    setExecutionState(ExecutionState.READY)
    setEvents([])
    setCurrentNodeId(null)
    setVariables({})
    onHighlightNode?.(null)
    onHighlightConnection?.(null)
    message.info(t('designer.debug.sim.resetDone'))
  }, [simulationEngine, onHighlightNode, onHighlightConnection, t])

  const addBreakpoint = useCallback(
    (nodeId: string, condition: string) => {
      const node = flowDefinition.nodes.find((candidate) => candidate.id === nodeId)
      simulationEngine.addBreakpoint(nodeId, condition.trim() || undefined)
      updateBreakpoints()
      message.success(t('designer.debug.sim.breakpointAdded', { name: node?.name || nodeId }))
    },
    [flowDefinition.nodes, message, simulationEngine, t, updateBreakpoints]
  )

  const deleteBreakpoint = useCallback(
    (id: string) => {
      simulationEngine.removeBreakpoint(id)
      updateBreakpoints()
      message.success(t('designer.debug.sim.breakpointRemoved'))
    },
    [simulationEngine, t, updateBreakpoints]
  )

  const toggleBreakpoint = useCallback(
    (id: string) => {
      simulationEngine.toggleBreakpoint(id)
      updateBreakpoints()
    },
    [simulationEngine, updateBreakpoints]
  )

  return {
    executionState,
    breakpoints,
    variables,
    events,
    currentNodeId,
    start,
    stepNext,
    continueExecution,
    reset,
    addBreakpoint,
    deleteBreakpoint,
    toggleBreakpoint,
  }
}

function SimulationControlCard({
  state,
  initialVarsInput,
  onInitialVarsInputChange,
  t,
}: {
  state: SimulationDebuggerState
  initialVarsInput: string
  onInitialVarsInputChange: (value: string) => void
  t: TFunction
}) {
  return (
    <Card
      size="small"
      title={
        <Space>
          <BugOutlined />
          <span>{t('designer.debug.sim.panelTitle')}</span>
          {stateTag(state.executionState, t)}
        </Space>
      }
      extra={<SimulationToolbar state={state} t={t} />}
    >
      <div className="debugger-vars-section">
        <div className="debugger-vars-label">{t('designer.debug.sim.initialVars')}</div>
        <TextArea
          value={initialVarsInput}
          onChange={(event) => onInitialVarsInputChange(event.target.value)}
          rows={4}
          placeholder='{"amount": 1500}'
          className="debugger-vars-textarea"
        />
      </div>
      {state.currentNodeId && (
        <div className="debugger-current-node">
          <strong>{t('designer.debug.sim.currentNode')}</strong>
          {state.currentNodeId}
        </div>
      )}
    </Card>
  )
}

function SimulationToolbar({ state, t }: { state: SimulationDebuggerState; t: TFunction }) {
  return (
    <Space>
      <Tooltip title={t('designer.debug.sim.startTooltip')}>
        <Button
          type="primary"
          size="small"
          icon={<PlayCircleOutlined />}
          onClick={state.start}
          disabled={state.executionState === 'running' || state.executionState === 'paused'}
        >
          {t('designer.debug.sim.start')}
        </Button>
      </Tooltip>
      <Tooltip title={t('designer.debug.sim.stepTooltip')}>
        <Button
          size="small"
          icon={<StepForwardOutlined />}
          onClick={state.stepNext}
          disabled={state.executionState !== 'paused'}
        >
          {t('designer.debug.sim.step')}
        </Button>
      </Tooltip>
      <Tooltip title={t('designer.debug.sim.continueTooltip')}>
        <Button
          size="small"
          icon={<PlayCircleOutlined />}
          onClick={state.continueExecution}
          disabled={state.executionState !== 'paused'}
        >
          {t('designer.debug.sim.continue')}
        </Button>
      </Tooltip>
      <Tooltip title={t('designer.debug.sim.resetTooltip')}>
        <Button size="small" icon={<ReloadOutlined />} onClick={state.reset}>
          {t('designer.debug.sim.reset')}
        </Button>
      </Tooltip>
    </Space>
  )
}

function BreakpointDialog({
  flowDefinition,
  onClose,
  state,
  t,
}: {
  flowDefinition: UnifiedProcessDefinition
  onClose: () => void
  state: SimulationDebuggerState
  t: TFunction
}) {
  const [nodeId, setNodeId] = useState<string>()
  const [condition, setCondition] = useState('')
  const nodeOptions = useMemo(
    () =>
      flowDefinition.nodes.map((node) => ({
        label: `${node.name || node.id} (${node.type})`,
        value: node.id,
      })),
    [flowDefinition.nodes]
  )

  return (
    <Modal
      title={t('designer.debug.sim.addBreakpointTitle')}
      open
      okText={t('designer.debug.sim.add')}
      cancelText={t('common.cancel')}
      okButtonProps={{ disabled: !nodeId }}
      onCancel={onClose}
      onOk={() => {
        if (!nodeId) return
        state.addBreakpoint(nodeId, condition)
        onClose()
      }}
      destroyOnHidden
    >
      <Space vertical style={{ width: '100%', marginTop: 8 }} size="middle">
        <Select
          aria-label={t('designer.debug.sim.selectBreakpointNode')}
          style={{ width: '100%' }}
          placeholder={t('designer.debug.sim.selectBreakpointNode')}
          options={nodeOptions}
          value={nodeId}
          showSearch
          filterOption={(input, option) =>
            String(option?.label).toLowerCase().includes(input.toLowerCase())
          }
          onChange={setNodeId}
        />
        <Input
          data-testid="breakpoint-condition-input"
          placeholder={t('designer.debug.sim.breakpointConditionPh')}
          aria-label={t('designer.debug.sim.breakpointCondition')}
          allowClear
          value={condition}
          onChange={(event) => setCondition(event.target.value)}
        />
      </Space>
    </Modal>
  )
}

function useBreakpointColumns(state: SimulationDebuggerState, t: TFunction) {
  return useMemo<ColumnsType<Breakpoint>>(
    () => [
      { title: t('designer.debug.col.nodeId'), dataIndex: 'nodeId', key: 'nodeId' },
      {
        title: t('designer.debug.col.condition'),
        dataIndex: 'condition',
        key: 'condition',
        ellipsis: true,
        render: (value?: string) => value || t('designer.debug.sim.breakpointUnconditional'),
      },
      {
        title: t('designer.debug.col.status'),
        dataIndex: 'enabled',
        key: 'enabled',
        render: (enabled) => (
          <Tag color={enabled ? 'success' : 'default'}>
            {enabled ? t('designer.debug.enabled') : t('designer.debug.disabled')}
          </Tag>
        ),
      },
      {
        title: t('designer.debug.col.hitCount'),
        dataIndex: 'hitCount',
        key: 'hitCount',
        width: 72,
      },
      {
        title: t('designer.debug.col.actions'),
        key: 'actions',
        render: (_, record) => (
          <Space size="small">
            <Button type="link" size="small" onClick={() => state.toggleBreakpoint(record.id)}>
              {record.enabled ? t('designer.debug.disabled') : t('designer.debug.enabled')}
            </Button>
            <Button
              type="link"
              size="small"
              danger
              onClick={() => state.deleteBreakpoint(record.id)}
            >
              {t('common.delete')}
            </Button>
          </Space>
        ),
      },
    ],
    [state, t]
  )
}

function useEventColumns(t: TFunction) {
  return useMemo<ColumnsType<ExecutionEvent>>(
    () => [
      {
        title: t('designer.debug.col.time'),
        dataIndex: 'timestamp',
        key: 'timestamp',
        width: 100,
        render: formatTime,
      },
      {
        title: t('designer.debug.col.type'),
        dataIndex: 'type',
        key: 'type',
        width: 100,
        render: (type) => eventTypeTag(String(type), t),
      },
      {
        title: t('designer.debug.col.details'),
        key: 'details',
        render: (_, record) => eventDetails(record, t),
      },
    ],
    [t]
  )
}

function DebuggerCollapse({
  flowDefinition,
  state,
  t,
}: {
  flowDefinition: UnifiedProcessDefinition
  state: SimulationDebuggerState
  t: TFunction
}) {
  const paginationItemRender = usePaginationItemRender()
  const [breakpointDialogOpen, setBreakpointDialogOpen] = useState(false)
  const breakpointColumns = useBreakpointColumns(state, t)
  const eventColumns = useEventColumns(t)

  return (
    <>
      <Collapse
        defaultActiveKey={['breakpoints', 'variables']}
        style={{ marginTop: 16 }}
        items={[
          {
            key: 'breakpoints',
            label: (
              <Space>
                <span>{t('designer.debug.sim.breakpoints')}</span>
                <Tag>{state.breakpoints.length}</Tag>
              </Space>
            ),
            extra: (
              <Button
                type="link"
                size="small"
                icon={<PlusOutlined />}
                onClick={(event) => {
                  event.stopPropagation()
                  setBreakpointDialogOpen(true)
                }}
              >
                {t('designer.debug.sim.add')}
              </Button>
            ),
            children: (
              <Table
                dataSource={state.breakpoints}
                columns={breakpointColumns}
                rowKey="id"
                size="small"
                pagination={false}
              />
            ),
          },
          {
            key: 'variables',
            label: (
              <Space>
                <EyeOutlined />
                <span>{t('designer.debug.sim.variables')}</span>
                <Tag>{Object.keys(state.variables).length}</Tag>
              </Space>
            ),
            children: <DebuggerVariables variables={state.variables} t={t} />,
          },
          {
            key: 'events',
            label: (
              <Space>
                <ClockCircleOutlined />
                <span>{t('designer.debug.sim.eventLog')}</span>
                <Tag>{state.events.length}</Tag>
              </Space>
            ),
            children: (
              <Table
                dataSource={state.events}
                columns={eventColumns}
                rowKey={(_record, index) => `event_${index}`}
                size="small"
                pagination={{ pageSize: 10, itemRender: paginationItemRender }}
                scroll={{ y: 300 }}
              />
            ),
          },
        ]}
      />
      {breakpointDialogOpen && (
        <BreakpointDialog
          flowDefinition={flowDefinition}
          onClose={() => setBreakpointDialogOpen(false)}
          state={state}
          t={t}
        />
      )}
    </>
  )
}

function DebuggerVariables({ variables, t }: { variables: Record<string, unknown>; t: TFunction }) {
  const entries = Object.entries(variables)
  return (
    <div className="variables-viewer">
      {entries.map(([key, value]) => (
        <div key={key} className="variable-item">
          <div className="variable-name">{key}</div>
          <div className="variable-value">{JSON.stringify(value, null, 2)}</div>
        </div>
      ))}
      {entries.length === 0 && (
        <div className="debugger-empty-variables">{t('designer.debug.sim.noVariables')}</div>
      )}
    </div>
  )
}

function SimulationDebugger(props: SimulationDebuggerProps & { t: TFunction }) {
  const state = useSimulationDebugger(props)
  return (
    <>
      <Alert
        type="warning"
        showIcon
        banner
        className="flow-debugger-simulation-banner"
        title={props.t('designer.debug.simulationBanner')}
      />
      <SimulationControlCard
        state={state}
        initialVarsInput={props.initialVarsInput}
        onInitialVarsInputChange={props.onInitialVarsInputChange}
        t={props.t}
      />
      <DebuggerCollapse flowDefinition={props.flowDefinition} state={state} t={props.t} />
    </>
  )
}

const ProcessDebuggerPanel = React.memo(function ProcessDebuggerPanel({
  flowDefinition,
  simulationEngine,
  onHighlightNode,
  onHighlightConnection,
}: ProcessDebuggerPanelProps) {
  const { t } = useTranslation()
  const [debugMode, setDebugMode] = useState<DebugMode>('simulation')
  const [initialVarsInput, setInitialVarsInput] = useState('{}')

  useEffect(() => {
    setInitialVarsInput('{}')
  }, [flowDefinition.id])
  const previewXml = useMemo(() => {
    try {
      return generateProcessXml(flowDefinition)
    } catch {
      return undefined
    }
  }, [flowDefinition])

  return (
    <div className="flow-debugger-panel">
      <div className="flow-debugger-mode-switch">
        <Segmented<DebugMode>
          size="small"
          value={debugMode}
          onChange={setDebugMode}
          options={[
            { label: t('designer.debug.modeSimulation'), value: 'simulation' },
            { label: t('designer.debug.modeEngine'), value: 'engine' },
          ]}
        />
      </div>

      {debugMode === 'engine' ? (
        <EngineDebugSection
          processCode={flowDefinition.code}
          modelType={flowDefinition.type}
          flowXml={previewXml}
          paramsJson={initialVarsInput}
          onParamsJsonChange={setInitialVarsInput}
        />
      ) : (
        <SimulationDebugger
          flowDefinition={flowDefinition}
          simulationEngine={simulationEngine}
          onHighlightNode={onHighlightNode}
          onHighlightConnection={onHighlightConnection}
          initialVarsInput={initialVarsInput}
          onInitialVarsInputChange={setInitialVarsInput}
          t={t}
        />
      )}
    </div>
  )
})

ProcessDebuggerPanel.displayName = 'ProcessDebuggerPanel'

export default ProcessDebuggerPanel
