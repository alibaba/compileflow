import { CloudServerOutlined, PlayCircleOutlined, ReloadOutlined } from '@ant-design/icons'
import { Alert, App, Button, Card, Input, Space, Tag } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { executePreview, getEngineStatus } from '@/shared/api/execution'
import type { ExecutionResponse } from '@/shared/contracts/executionContract'
import type { ProcessModelType } from '@/shared/contracts/processContract'
import { toError } from '@/shared/errors'
import { formatTime } from '@/shared/i18n/dateTime'

import './EngineDebugSection.css'

const { TextArea } = Input

export interface EngineDebugSectionProps {
  /** Business flow code passed to the active execution backend. */
  processCode: string | undefined
  modelType: ProcessModelType | undefined
  flowXml: string | undefined
  paramsJson: string
  onParamsJsonChange: (value: string) => void
}

interface EngineLogEntry {
  key: string
  level: 'info' | 'success' | 'error'
  message: string
  at: number
}

interface EngineStatusBannerProps {
  engineAvailable: boolean | null
  statusMessage: string
}

interface EngineDebugCardProps {
  engineAvailable: boolean | null
  executing: boolean
  processCode: string | undefined
  modelType: ProcessModelType | undefined
  flowXml: string | undefined
  lastResult: ExecutionResponse | null
  paramsJson: string
  onExecute: () => void
  onParamsJsonChange: (value: string) => void
  onRefreshStatus: () => void
  onReset: () => void
}

const createLogEntry = (
  level: EngineLogEntry['level'],
  text: string,
  index: number
): EngineLogEntry => ({
  key: `${Date.now()}-${index}`,
  level,
  message: text,
  at: Date.now(),
})

const parseExecutionParams = (paramsJson: string): Record<string, unknown> => {
  const parsed = JSON.parse(paramsJson || '{}') as unknown
  if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('Execution params must be a JSON object.')
  }
  return parsed as Record<string, unknown>
}

function EngineStatusBanner({ engineAvailable, statusMessage }: EngineStatusBannerProps) {
  const { t } = useTranslation()

  return (
    <Alert
      type={engineAvailable ? 'info' : 'warning'}
      showIcon
      banner
      className="engine-debug-banner"
      title={
        engineAvailable ? t('designer.debug.engineBanner') : t('designer.debug.engineUnavailable')
      }
      description={statusMessage || undefined}
    />
  )
}

function EngineDebugCard({
  engineAvailable,
  executing,
  processCode,
  modelType,
  flowXml,
  lastResult,
  paramsJson,
  onExecute,
  onParamsJsonChange,
  onRefreshStatus,
  onReset,
}: EngineDebugCardProps) {
  const { t } = useTranslation()

  return (
    <Card
      size="small"
      title={
        <Space>
          <CloudServerOutlined />
          <span>{t('designer.debug.engineTitle')}</span>
          <Tag color={engineAvailable ? 'processing' : 'default'}>
            {engineAvailable ? t('designer.debug.engineOnline') : t('designer.debug.engineOffline')}
          </Tag>
        </Space>
      }
      extra={
        <Space>
          <Button size="small" onClick={onRefreshStatus}>
            {t('designer.debug.engineRefreshStatus')}
          </Button>
          <Button
            type="primary"
            size="small"
            icon={<PlayCircleOutlined />}
            loading={executing}
            disabled={!engineAvailable || !processCode || !modelType || !flowXml}
            onClick={onExecute}
          >
            {t('designer.debug.engineRun')}
          </Button>
          <Button size="small" icon={<ReloadOutlined />} onClick={onReset}>
            {t('designer.debug.engineReset')}
          </Button>
        </Space>
      }
    >
      <Alert
        type="warning"
        showIcon
        title={t('designer.debug.engineExecutionWarningTitle')}
        description={t('designer.debug.engineExecutionWarning')}
        className="engine-debug-banner"
      />

      <div className="engine-debug-code">
        <span className="engine-debug-code-label">{t('designer.debug.engineProcessCode')}</span>
        <code>{processCode ?? '—'}</code>
      </div>

      <div className="debugger-vars-section">
        <div className="debugger-vars-label">{t('designer.debug.engineParams')}</div>
        <TextArea
          value={paramsJson}
          onChange={(event) => onParamsJsonChange(event.target.value)}
          rows={4}
          placeholder='{"amount": 1500}'
          className="debugger-vars-textarea"
        />
      </div>

      {lastResult && (
        <pre className="engine-debug-result">{JSON.stringify(lastResult, null, 2)}</pre>
      )}
    </Card>
  )
}

function EngineDebugLog({ logs }: { logs: EngineLogEntry[] }) {
  if (logs.length === 0) return null

  return (
    <div className="engine-debug-log">
      {logs.map((entry) => (
        <div
          key={entry.key}
          className={`engine-debug-log-line engine-debug-log-line--${entry.level}`}
        >
          <span className="engine-debug-log-time">{formatTime(entry.at)}</span>
          <span>{entry.message}</span>
        </div>
      ))}
    </div>
  )
}

export function EngineDebugSection({
  processCode,
  modelType,
  flowXml,
  paramsJson,
  onParamsJsonChange,
}: EngineDebugSectionProps) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [engineAvailable, setEngineAvailable] = useState<boolean | null>(null)
  const [statusMessage, setStatusMessage] = useState('')
  const [executing, setExecuting] = useState(false)
  const [lastResult, setLastResult] = useState<ExecutionResponse | null>(null)
  const [logs, setLogs] = useState<EngineLogEntry[]>([])
  const statusGeneration = useRef(0)
  const executionGeneration = useRef(0)
  const executionInFlight = useRef(false)

  const appendLog = useCallback((level: EngineLogEntry['level'], text: string) => {
    setLogs((prev) => [...prev, createLogEntry(level, text, prev.length)])
  }, [])

  const refreshStatus = useCallback(async () => {
    const generation = ++statusGeneration.current
    try {
      const status = await getEngineStatus()
      if (generation !== statusGeneration.current) return
      setEngineAvailable(status.engineAvailable)
      setStatusMessage(status.message)
    } catch (error) {
      if (generation !== statusGeneration.current) return
      setEngineAvailable(false)
      setStatusMessage(toError(error, t('designer.debug.engineUnavailable')).message)
    }
  }, [t])

  useEffect(() => {
    void refreshStatus()
    return () => {
      statusGeneration.current += 1
    }
  }, [refreshStatus])

  useEffect(() => {
    executionGeneration.current += 1
    executionInFlight.current = false
    setExecuting(false)
    setLastResult(null)
    setLogs([])
  }, [modelType, flowXml, processCode])

  const handleExecute = useCallback(async () => {
    if (executionInFlight.current) return
    if (!processCode?.trim()) {
      message.warning(t('designer.debug.engineMissingCode'))
      return
    }

    let params: Record<string, unknown>
    try {
      params = parseExecutionParams(paramsJson)
    } catch {
      message.error(t('designer.debug.engineInvalidParams'))
      return
    }

    const generation = ++executionGeneration.current
    executionInFlight.current = true
    setExecuting(true)
    setLastResult(null)
    appendLog('info', t('designer.debug.engineStart', { code: processCode }))

    try {
      if (!modelType || !flowXml?.trim()) {
        message.warning(t('designer.debug.engineMissingCode'))
        return
      }
      const response = await executePreview({
        code: processCode.trim(),
        modelType: modelType,
        xml: flowXml,
        params,
      })
      if (generation !== executionGeneration.current) return
      setLastResult(response)
      if (response.success) {
        appendLog('success', response.message)
        if (response.traceId) appendLog('info', `traceId: ${response.traceId}`)
        message.success(t('designer.debug.engineSuccess'))
      } else {
        appendLog('error', response.error)
        message.error(response.error)
      }
    } catch (error) {
      if (generation !== executionGeneration.current) return
      const msg = toError(error, t('designer.debug.engineFailed')).message
      appendLog('error', msg)
      message.error(msg)
    } finally {
      if (generation === executionGeneration.current) {
        executionInFlight.current = false
        setExecuting(false)
      }
    }
  }, [appendLog, processCode, modelType, flowXml, paramsJson, t])

  const handleReset = useCallback(() => {
    executionGeneration.current += 1
    executionInFlight.current = false
    setExecuting(false)
    setLastResult(null)
    setLogs([])
  }, [])

  return (
    <div className="engine-debug-section">
      <EngineStatusBanner engineAvailable={engineAvailable} statusMessage={statusMessage} />
      <EngineDebugCard
        engineAvailable={engineAvailable}
        executing={executing}
        processCode={processCode}
        modelType={modelType}
        flowXml={flowXml}
        lastResult={lastResult}
        paramsJson={paramsJson}
        onExecute={() => void handleExecute()}
        onParamsJsonChange={onParamsJsonChange}
        onRefreshStatus={() => void refreshStatus()}
        onReset={handleReset}
      />
      <EngineDebugLog logs={logs} />
    </div>
  )
}
