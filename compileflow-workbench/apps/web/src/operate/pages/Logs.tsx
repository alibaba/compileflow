import { DeleteOutlined, DownloadOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons'
import type { TableColumnsType } from 'antd'
import { Alert, App, Button, DatePicker, Descriptions, Input, Select, Space, Table } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import dayjs, { type Dayjs } from 'dayjs'
import type { TFunction } from 'i18next'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import styles from './Logs.module.css'

import { exportLogs, getLogById, getLogs, purgeLogs } from '@/operate/api/logs'
import { LoadErrorAlert } from '@/shared/components/LoadErrorAlert'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { DataPageShell, FilterBar, SemanticTag, statusToTagKind } from '@/shared/components/page'
import type { ExecutionLog, ExecutionStatus } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import {
  type FilterParsers,
  type FilterUpdater,
  useFilterState,
} from '@/shared/hooks/useFilterState'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { usePaginationItemRender } from '@/shared/hooks/usePaginationItemRender'
import { formatDateTime } from '@/shared/i18n/dateTime'
import { createLogger } from '@/shared/logging/logger'

const { Option } = Select
const { RangePicker } = DatePicker
const logger = createLogger('Logs')
const PAGE_SIZE = 20

type LogStatusFilter = ExecutionStatus

interface LogFilterState {
  keyword: string
  traceId: string
  parentInvocationId: string
  status: LogStatusFilter | ''
  startTime: string
  endTime: string
  page: number
}

const LOG_FILTER_DEFAULTS: LogFilterState = {
  keyword: '',
  traceId: '',
  parentInvocationId: '',
  status: '',
  startTime: '',
  endTime: '',
  page: 1,
}

const LOG_FILTER_PARSERS: FilterParsers<LogFilterState> = {
  status: (raw) => (raw === 'success' || raw === 'failed' ? raw : ''),
  startTime: (raw) => {
    const date = dayjs(raw)
    return date.isValid() ? date.toISOString() : ''
  },
  endTime: (raw) => {
    const date = dayjs(raw)
    return date.isValid() ? date.toISOString() : ''
  },
  page: (raw) => {
    const page = Number(raw)
    return Number.isInteger(page) && page > 0 ? page : 1
  },
}

interface LogPageState {
  detailVisible: boolean
  filterState: LogFilterState
  handleExport: () => Promise<void>
  handlePurge: (before: string) => Promise<boolean>
  handleViewDetail: (id: string) => Promise<void>
  loadError: boolean
  loading: boolean
  exporting: boolean
  purging: boolean
  logs: ExecutionLog[]
  page: number
  reload: () => void
  selectedLog: ExecutionLog | null
  setDetailVisible: (visible: boolean) => void
  setDateRange: (startTime: string, endTime: string) => void
  setPage: (page: number) => void
  setSelectedLog: (log: ExecutionLog | null) => void
  total: number
  updateFilter: FilterUpdater<LogFilterState>
}

function downloadBlob(blob: Blob, fileName: string): void {
  const url = window.URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = fileName
  anchor.click()
  window.URL.revokeObjectURL(url)
}

export function formatDuration(durationMs: number): string {
  if (durationMs < 1_000) return `${durationMs} ms`
  if (durationMs < 60_000) return `${(durationMs / 1_000).toFixed(durationMs < 10_000 ? 1 : 0)} s`

  const minutes = Math.floor(durationMs / 60_000)
  const seconds = Math.floor((durationMs % 60_000) / 1_000)
  return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`
}

function routingSourceLabel(source: string | undefined, t: TFunction): string {
  if (source === 'alias' || source === 'version' || source === 'definition') {
    return t(`logs.routingSource.${source}`)
  }
  return source ?? '-'
}

function DurationValue({ duration }: { duration?: number }) {
  return duration == null ? (
    <>-</>
  ) : (
    <span title={`${duration} ms`}>{formatDuration(duration)}</span>
  )
}

function useLogMaintenance(
  filterState: LogFilterState,
  reload: () => Promise<void>,
  message: MessageInstance,
  t: TFunction
) {
  const [exporting, setExporting] = useState(false)
  const [purging, setPurging] = useState(false)
  const exportInFlight = useRef(false)
  const purgeInFlight = useRef(false)
  const mounted = useRef(true)
  const activeReload = useRef(reload)
  activeReload.current = reload

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const handleExport = useCallback(async () => {
    if (!mounted.current || exportInFlight.current) return
    exportInFlight.current = true
    setExporting(true)
    const hasCompleteDateRange = Boolean(filterState.startTime && filterState.endTime)
    try {
      const blob = await exportLogs({
        keyword: filterState.keyword || undefined,
        traceId: filterState.traceId || undefined,
        parentInvocationId: filterState.parentInvocationId || undefined,
        status: filterState.status || undefined,
        startTime: hasCompleteDateRange ? filterState.startTime : undefined,
        endTime: hasCompleteDateRange ? filterState.endTime : undefined,
      })
      if (!mounted.current) return
      downloadBlob(blob, `logs_${Date.now()}.csv`)
      message.success(t('logs.exportSuccess'))
    } catch (error) {
      if (!mounted.current) return
      logger.error('Failed to export logs', toError(error))
      message.error(t('error.exportFailed'))
    } finally {
      exportInFlight.current = false
      if (mounted.current) setExporting(false)
    }
  }, [filterState, message, t])

  const handlePurge = useCallback(
    async (before: string): Promise<boolean> => {
      if (!mounted.current || purgeInFlight.current) return false
      purgeInFlight.current = true
      setPurging(true)
      let deletedCount = 0
      try {
        let hasMore: boolean
        do {
          const result = await purgeLogs({ before })
          if (!mounted.current) return false
          deletedCount += result.deletedCount
          hasMore = result.hasMore
        } while (hasMore)
        message.success(t('logs.purgeSuccess', { count: deletedCount }))
        await activeReload.current()
        return mounted.current
      } catch (error) {
        if (!mounted.current) return false
        logger.error('Failed to purge logs', toError(error), { before })
        message.error(t('logs.purgeFailed'))
        return false
      } finally {
        purgeInFlight.current = false
        if (mounted.current) setPurging(false)
      }
    },
    [message, t]
  )

  return { exporting, handleExport, handlePurge, purging }
}

export function useLogsPageState(t: TFunction): LogPageState {
  const { message } = App.useApp()
  const [logs, setLogs] = useState<ExecutionLog[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [total, setTotal] = useState(0)
  const [detailVisible, setDetailVisible] = useState(false)
  const [selectedLog, setSelectedLog] = useState<ExecutionLog | null>(null)
  const [filterState, , , updateFilters] = useFilterState(LOG_FILTER_DEFAULTS, LOG_FILTER_PARSERS)
  const listGeneration = useRef(0)
  const detailGeneration = useRef(0)

  const loadLogs = useCallback(async () => {
    const generation = ++listGeneration.current
    const hasCompleteDateRange = Boolean(filterState.startTime && filterState.endTime)
    try {
      setLoading(true)
      setLoadError(false)
      const response = await getLogs({
        page: filterState.page,
        pageSize: PAGE_SIZE,
        keyword: filterState.keyword || undefined,
        traceId: filterState.traceId || undefined,
        parentInvocationId: filterState.parentInvocationId || undefined,
        status: filterState.status || undefined,
        startTime: hasCompleteDateRange ? filterState.startTime : undefined,
        endTime: hasCompleteDateRange ? filterState.endTime : undefined,
      })
      if (generation === listGeneration.current) {
        setLogs(response.data)
        setTotal(response.total)
      }
    } catch (error) {
      if (generation !== listGeneration.current) return
      logger.error('Failed to load logs', toError(error))
      setLoadError(true)
    } finally {
      if (generation === listGeneration.current) setLoading(false)
    }
  }, [
    filterState.endTime,
    filterState.keyword,
    filterState.parentInvocationId,
    filterState.page,
    filterState.startTime,
    filterState.status,
    filterState.traceId,
  ])
  const maintenance = useLogMaintenance(filterState, loadLogs, message, t)

  useEffect(() => {
    setLogs([])
    setTotal(0)
    void loadLogs()
    return () => {
      listGeneration.current += 1
    }
  }, [loadLogs])

  useEffect(
    () => () => {
      detailGeneration.current += 1
    },
    []
  )

  useEffect(() => {
    if (Boolean(filterState.startTime) !== Boolean(filterState.endTime)) {
      updateFilters({ startTime: '', endTime: '', page: 1 })
    }
  }, [filterState.endTime, filterState.startTime, updateFilters])

  const handleViewDetail = useCallback(
    async (id: string) => {
      const generation = ++detailGeneration.current
      try {
        const log = await getLogById(id)
        if (generation !== detailGeneration.current) return
        setSelectedLog(log)
        setDetailVisible(true)
      } catch (error) {
        if (generation !== detailGeneration.current) return
        logger.error('Failed to load log detail', toError(error), { id })
        message.error(t('error.loadFailed'))
      }
    },
    [message, t]
  )

  const setDateRange = useCallback(
    (startTime: string, endTime: string) => updateFilters({ startTime, endTime, page: 1 }),
    [updateFilters]
  )
  const updateLogFilter: FilterUpdater<LogFilterState> = useCallback(
    (key, value) => updateFilters({ [key]: value, page: 1 } as Partial<LogFilterState>),
    [updateFilters]
  )

  return {
    detailVisible,
    exporting: maintenance.exporting,
    filterState,
    handleExport: maintenance.handleExport,
    handlePurge: maintenance.handlePurge,
    handleViewDetail,
    loadError,
    loading,
    logs,
    page: filterState.page,
    purging: maintenance.purging,
    reload: () => void loadLogs(),
    selectedLog,
    setDateRange,
    setDetailVisible,
    setPage: (page) => updateFilters({ page }),
    setSelectedLog,
    total,
    updateFilter: updateLogFilter,
  }
}

function LogFilters({ state, t }: { state: LogPageState; t: TFunction }) {
  const rangeValue = useMemo((): [Dayjs, Dayjs] | null => {
    if (!state.filterState.startTime || !state.filterState.endTime) return null
    const start = dayjs(state.filterState.startTime)
    const end = dayjs(state.filterState.endTime)
    if (!start.isValid() || !end.isValid()) return null
    return [start, end]
  }, [state.filterState.endTime, state.filterState.startTime])

  return (
    <FilterBar>
      <Input
        placeholder={t('logs.searchPlaceholder')}
        aria-label={t('logs.searchPlaceholder')}
        prefix={<SearchOutlined aria-hidden="true" />}
        style={{ width: 280 }}
        value={state.filterState.keyword}
        onChange={(event) => state.updateFilter('keyword', event.target.value)}
        allowClear
      />
      <Input
        placeholder={t('logs.traceId')}
        aria-label={t('logs.traceId')}
        style={{ width: 220 }}
        value={state.filterState.traceId}
        onChange={(event) => state.updateFilter('traceId', event.target.value)}
        allowClear
      />
      <Input
        placeholder={t('logs.parentInvocationId')}
        aria-label={t('logs.parentInvocationId')}
        style={{ width: 220 }}
        value={state.filterState.parentInvocationId}
        onChange={(event) => state.updateFilter('parentInvocationId', event.target.value)}
        allowClear
      />
      <Select<LogStatusFilter>
        placeholder={t('logs.selectStatus')}
        aria-label={t('logs.selectStatus')}
        style={{ width: 120 }}
        allowClear
        value={state.filterState.status || undefined}
        onChange={(value) => state.updateFilter('status', value ?? '')}
      >
        <Option value="success">{t('logs.status.success')}</Option>
        <Option value="failed">{t('logs.status.failed')}</Option>
      </Select>
      <RangePicker
        aria-label={t('logs.dateRange')}
        placeholder={[t('logs.startDatePlaceholder'), t('logs.endDatePlaceholder')]}
        value={rangeValue}
        onChange={(dates) => {
          state.setDateRange(dates?.[0]?.toISOString() ?? '', dates?.[1]?.toISOString() ?? '')
        }}
      />
    </FilterBar>
  )
}

function createLogColumns(
  t: TFunction,
  onViewDetail: (id: string) => Promise<void>
): TableColumnsType<ExecutionLog> {
  return [
    { title: t('logs.processCode'), dataIndex: 'processCode', key: 'processCode', width: 180 },
    { title: t('logs.invocationId'), dataIndex: 'invocationId', key: 'invocationId', width: 200 },
    {
      title: t('logs.status'),
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <SemanticTag kind={statusToTagKind(status)}>{t(`logs.status.${status}`)}</SemanticTag>
      ),
    },
    {
      title: t('logs.effectiveVersion'),
      dataIndex: 'effectiveVersion',
      key: 'effectiveVersion',
      width: 140,
      render: (version?: string) =>
        version ? <SemanticTag kind="status-info">{version}</SemanticTag> : '-',
    },
    {
      title: t('logs.duration'),
      dataIndex: 'duration',
      key: 'duration',
      width: 120,
      render: (duration?: number) => <DurationValue duration={duration} />,
    },
    {
      title: t('logs.startTime'),
      dataIndex: 'startTime',
      key: 'startTime',
      width: 180,
      render: formatDateTime,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 120,
      render: (_: unknown, record: ExecutionLog) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => {
            void onViewDetail(record.id)
          }}
        >
          {t('common.detail')}
        </Button>
      ),
    },
  ]
}

function LogDetailModal({ state, t }: { state: LogPageState; t: TFunction }) {
  const selectedLog = state.selectedLog

  return (
    <Modal
      title={t('logs.detail')}
      open={state.detailVisible}
      onCancel={() => {
        state.setDetailVisible(false)
        state.setSelectedLog(null)
      }}
      footer={null}
      width={800}
      destroyOnHidden
    >
      {selectedLog && (
        <div>
          <Descriptions bordered column={1} size="small">
            <Descriptions.Item label={t('logs.invocationId')}>
              {selectedLog.invocationId}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.parentInvocationId')}>
              {selectedLog.parentInvocationId ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.callDepth')}>
              {selectedLog.callDepth}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.traceId')}>{selectedLog.traceId}</Descriptions.Item>
            <Descriptions.Item label={t('logs.processCode')}>
              {selectedLog.processCode}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.status')}>
              <SemanticTag kind={statusToTagKind(selectedLog.status)}>
                {t(`logs.status.${selectedLog.status}`)}
              </SemanticTag>
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.duration')}>
              <DurationValue duration={selectedLog.duration} />
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.startTime')}>
              {formatDateTime(selectedLog.startTime)}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.endTime')}>
              {formatDateTime(selectedLog.endTime)}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.modelType')}>
              {selectedLog.modelType}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.namespace')}>
              {selectedLog.namespace}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.requestedVersion')}>
              {selectedLog.requestedVersion ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.effectiveVersion')}>
              {selectedLog.effectiveVersion ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.routingSource')}>
              {routingSourceLabel(selectedLog.routingSource, t)}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.routeAlias')}>
              {selectedLog.routeAlias ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.routeRevision')}>
              {selectedLog.routeRevision ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('logs.sourceDigest')}>
              {selectedLog.sourceDigest ?? '-'}
            </Descriptions.Item>
          </Descriptions>

          {selectedLog.errorCode && (
            <div className={styles.errorBlock}>
              <strong>{t('logs.errorCode')}:</strong> {selectedLog.errorCode}
            </div>
          )}
          {selectedLog.errorMessage && (
            <div className={styles.errorBlock}>
              <strong>{t('logs.errorMessage')}:</strong>
              <pre className={styles.errorPre}>{selectedLog.errorMessage}</pre>
            </div>
          )}
        </div>
      )}
    </Modal>
  )
}

function LogPageActions({ state, t }: { state: LogPageState; t: TFunction }) {
  const [purgeOpen, setPurgeOpen] = useState(false)
  const [purgeBefore, setPurgeBefore] = useState<Dayjs | null>(null)
  const validCutoff = purgeBefore?.isBefore(dayjs()) ?? false

  return (
    <>
      <Space>
        <Button
          icon={<DownloadOutlined />}
          loading={state.exporting}
          disabled={state.exporting || state.purging}
          onClick={() => void state.handleExport()}
        >
          {t('logs.export')}
        </Button>
        <Button
          danger
          icon={<DeleteOutlined />}
          disabled={state.exporting || state.purging}
          onClick={() => {
            setPurgeBefore(dayjs().subtract(30, 'day'))
            setPurgeOpen(true)
          }}
        >
          {t('logs.purge')}
        </Button>
      </Space>
      <Modal
        open={purgeOpen}
        title={t('logs.purgeTitle')}
        okText={t('logs.purgeConfirm')}
        cancelText={t('common.cancel')}
        confirmLoading={state.purging}
        closable={!state.purging}
        keyboard={!state.purging}
        mask={{ closable: !state.purging }}
        cancelButtonProps={{ disabled: state.purging }}
        okButtonProps={{ danger: true, disabled: !validCutoff }}
        onCancel={() => {
          if (!state.purging) setPurgeOpen(false)
        }}
        onOk={async () => {
          if (!purgeBefore || !validCutoff) return
          if (await state.handlePurge(purgeBefore.toISOString())) setPurgeOpen(false)
        }}
      >
        <Alert type="warning" showIcon title={t('logs.purgeWarning')} />
        <DatePicker
          showTime
          value={purgeBefore}
          onChange={setPurgeBefore}
          aria-label={t('logs.purgeBefore')}
          placeholder={t('logs.purgeBefore')}
          style={{ width: '100%', marginTop: 16 }}
        />
      </Modal>
    </>
  )
}

function Logs() {
  usePageTitle('pageTitle.operate.logs')
  const { t } = useTranslation()
  const paginationItemRender = usePaginationItemRender()
  const state = useLogsPageState(t)
  const columns = useMemo(
    () => createLogColumns(t, state.handleViewDetail),
    [state.handleViewDetail, t]
  )

  return (
    <>
      <DataPageShell
        accent="operate"
        eyebrow={t('nav.operate')}
        title={t('logs.title')}
        subtitle={t('logs.subtitle')}
        actions={<LogPageActions state={state} t={t} />}
        filters={<LogFilters state={state} t={t} />}
      >
        {state.loadError && <LoadErrorAlert onRetry={state.reload} />}
        <Table
          columns={columns}
          dataSource={state.logs}
          rowKey="id"
          loading={state.loading}
          locale={{ emptyText: t('logs.noData') }}
          pagination={{
            current: state.page,
            pageSize: PAGE_SIZE,
            total: state.total,
            itemRender: paginationItemRender,
            onChange: state.setPage,
            showTotal: (n) => t('common.totalItems', { total: n }),
          }}
        />
      </DataPageShell>

      <LogDetailModal state={state} t={t} />
    </>
  )
}

export default Logs
