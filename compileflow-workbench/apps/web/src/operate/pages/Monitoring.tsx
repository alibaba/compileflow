import { Alert, App, Button, Col, Empty, Row, Select, Space, Spin } from 'antd'
import type { TFunction } from 'i18next'
import type { ReactNode } from 'react'
import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import styles from './Monitoring.module.css'

import { requeueDeploymentDeadLetters } from '@/operate/api/deployments'
import { AsyncInvocationOperations } from '@/operate/components/AsyncInvocationOperations'
import { type MonitoringSource, useMonitoringData } from '@/operate/hooks/useMonitoringData'
import { requeueAsyncInvocationDeadLetters } from '@/shared/api/processes'
import { FilterBar, MetricGrid, PageHeader } from '@/shared/components/page'
import { useTheme } from '@/shared/contexts/ThemeContext'
import type {
  AsyncInvocationHealth,
  DeploymentControlHealth,
  DeploymentRuntimeAvailableDiagnostics,
  DeploymentRuntimeDiagnostics,
  ErrorSummary,
  ExecutionTrend,
  MonitoringMetrics,
  MonitoringTimeRange,
  TopProcessStats,
  VersionDistributionStats,
} from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { type FilterParsers, useFilterState } from '@/shared/hooks/useFilterState'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { formatDateTime } from '@/shared/i18n/dateTime'
import { createLogger } from '@/shared/logging/logger'

const { Option } = Select
const logger = createLogger('Monitoring')

type RuntimeStatus = 'healthy' | 'degraded' | 'down' | 'stopped' | 'unavailable'

interface ChartColors {
  grid: string
  axis: string
}

const CHART_COLORS_LIGHT: ChartColors = {
  grid: 'var(--border-color)',
  axis: 'var(--text-tertiary)',
}
const CHART_COLORS_DARK: ChartColors = {
  grid: 'rgba(255,255,255,0.12)',
  axis: 'var(--text-tertiary)',
}
const MONITORING_FILTER_DEFAULTS = { timeRange: '24h' as MonitoringTimeRange }
const MONITORING_FILTER_PARSERS: FilterParsers<typeof MONITORING_FILTER_DEFAULTS> = {
  timeRange: (raw) =>
    raw === '1h' || raw === '6h' || raw === '24h' || raw === '7d' || raw === '30d' ? raw : '24h',
}
interface ChartPanelProps {
  title: string
  children: ReactNode
}

interface RuntimeStatProps {
  label: string
  value: ReactNode
}

interface ChartProps {
  colors: ChartColors
  isDark: boolean
  t: TFunction
}

const tooltipStyle = (isDark: boolean) => ({
  background: isDark ? 'var(--bg-elevated)' : 'var(--bg-primary)',
  border: `1px solid var(--border-color)`,
  borderRadius: 'var(--radius-lg)',
  fontSize: 13,
})

function ChartPanel({ title, children }: ChartPanelProps) {
  return (
    <section className={styles.chartPanel}>
      <header className={styles.chartPanelHeader}>
        <h2 className={styles.chartPanelTitle}>{title}</h2>
      </header>
      <div className={styles.chartPanelBody}>{children}</div>
    </section>
  )
}

function RuntimeStat({ label, value }: RuntimeStatProps) {
  return (
    <div className={styles.runtimeStat}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function StatusPill({ status, t }: { status: RuntimeStatus; t: TFunction }) {
  return (
    <span className={`${styles.runtimeStatus} ${styles[`runtimeStatus_${status}`]}`}>
      {t(`monitoring.runtimeStatus.${status}`)}
    </span>
  )
}

function runtimeTopologyLabel(
  topology: DeploymentRuntimeAvailableDiagnostics['topology'],
  t: TFunction
): string {
  return t(`monitoring.runtimeTopology.${topology}`)
}

function StaleDataAlert({ t }: { t: TFunction }) {
  return (
    <Alert
      type="warning"
      showIcon
      title={t('monitoring.staleData')}
      className={styles.runtimeMessage}
    />
  )
}

function metricItems(metrics: MonitoringMetrics | null, t: TFunction) {
  const successRate =
    metrics && metrics.totalExecutions > 0
      ? ((metrics.successExecutions / metrics.totalExecutions) * 100).toFixed(1)
      : '-'

  return [
    {
      key: 'total',
      label: t('monitoring.totalExecutions'),
      value: metrics?.totalExecutions ?? '-',
      accent: 'primary' as const,
    },
    {
      key: 'success',
      label: t('monitoring.successExecutions'),
      value: metrics?.successExecutions ?? '-',
      accent: 'success' as const,
    },
    {
      key: 'failed',
      label: t('monitoring.failedExecutions'),
      value: metrics?.failedExecutions ?? '-',
      accent: 'error' as const,
    },
    {
      key: 'rate',
      label: t('monitoring.successRate'),
      value: successRate,
      suffix: successRate === '-' ? undefined : '%',
      accent:
        successRate !== '-' && Number(successRate) >= 90
          ? ('success' as const)
          : ('warning' as const),
    },
  ]
}

function deployRuntimeStatus(deployRuntime: DeploymentRuntimeDiagnostics): RuntimeStatus {
  if (!deployRuntime.available) return 'unavailable'
  if (deployRuntime.failedAliasCount > 0 || deployRuntime.backedOffVersions.length > 0) {
    return 'degraded'
  }
  return deployRuntime.started ? 'healthy' : 'stopped'
}

function deploymentControlStatus(routingOutboxControl: DeploymentControlHealth): RuntimeStatus {
  if (routingOutboxControl.status === 'UP') return 'healthy'
  if (routingOutboxControl.status === 'DOWN') return 'down'
  return 'degraded'
}

function asyncQueueStatus(asyncHealth: AsyncInvocationHealth): RuntimeStatus {
  return asyncHealth.status === 'healthy' ? 'healthy' : 'degraded'
}

function formatQueueAge(ageMs: number): string {
  if (ageMs < 1000) return `${ageMs} ms`
  if (ageMs < 60000) return `${(ageMs / 1000).toFixed(ageMs < 10000 ? 1 : 0)} s`
  return `${(ageMs / 60000).toFixed(ageMs < 600000 ? 1 : 0)} min`
}

function OpsControlPlanePanel({
  routingOutboxControl,
  asyncHealth,
  staleSources,
  reloadOpsData,
  onAsyncQueueChanged,
  t,
}: {
  routingOutboxControl: DeploymentControlHealth | null
  asyncHealth: AsyncInvocationHealth | null
  staleSources: MonitoringSource[]
  reloadOpsData: () => Promise<void>
  onAsyncQueueChanged: () => void
  t: TFunction
}) {
  const [opsAction, setOpsAction] = useState<string | null>(null)
  const { message } = App.useApp()

  const runOpsAction = useCallback(
    async (key: string, action: () => Promise<unknown>, success: string) => {
      try {
        setOpsAction(key)
        await action()
        message.success(success)
        try {
          await reloadOpsData()
        } catch (err) {
          logger.warn('Operation succeeded but monitoring refresh failed', {
            action: key,
            error: toError(err).message,
          })
          message.warning(t('monitoring.opsRefreshFailed'))
        }
      } catch (err) {
        logger.error('Operation control action failed', toError(err), { action: key })
        message.error(t('monitoring.opsActionFailed'))
      } finally {
        setOpsAction(null)
      }
    },
    [message, reloadOpsData, t]
  )

  return (
    <ChartPanel title={t('monitoring.opsControlPlane')}>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <DeploymentOutboxCard
            routingOutboxControl={routingOutboxControl}
            stale={staleSources.includes('routingOutboxControl')}
            opsAction={opsAction}
            runOpsAction={runOpsAction}
            t={t}
          />
        </Col>
        <Col xs={24} lg={12}>
          <AsyncQueueCard
            asyncHealth={asyncHealth}
            stale={staleSources.includes('asyncHealth')}
            opsAction={opsAction}
            runOpsAction={runOpsAction}
            onAsyncQueueChanged={onAsyncQueueChanged}
            t={t}
          />
        </Col>
      </Row>
    </ChartPanel>
  )
}

function DeploymentOutboxCard({
  routingOutboxControl,
  stale,
  opsAction,
  runOpsAction,
  t,
}: {
  routingOutboxControl: DeploymentControlHealth | null
  stale: boolean
  opsAction: string | null
  runOpsAction: (key: string, action: () => Promise<unknown>, success: string) => void
  t: TFunction
}) {
  if (!routingOutboxControl) {
    return <UnavailableOpsCard eyebrow={t('monitoring.deploymentOutbox')} t={t} />
  }

  return (
    <div className={styles.opsCard}>
      <div className={styles.runtimeHeader}>
        <div>
          <div className={styles.runtimeEyebrow}>{t('monitoring.deploymentOutbox')}</div>
          <div className={styles.runtimeTitle}>
            {routingOutboxControl.dispatcherRunning
              ? t('monitoring.deploymentRunning')
              : t('monitoring.deploymentStopped')}
          </div>
        </div>
        <StatusPill status={deploymentControlStatus(routingOutboxControl)} t={t} />
      </div>

      <div className={styles.runtimeStats}>
        <RuntimeStat
          label={t('monitoring.outboxPending')}
          value={routingOutboxControl.pendingCount}
        />
        <RuntimeStat
          label={t('monitoring.outboxProcessing')}
          value={routingOutboxControl.processingCount}
        />
        <RuntimeStat
          label={t('monitoring.outboxExpiredClaims')}
          value={routingOutboxControl.expiredClaimCount}
        />
        <RuntimeStat
          label={t('monitoring.outboxDeadLetters')}
          value={routingOutboxControl.failedCount}
        />
        <RuntimeStat
          label={t('monitoring.dispatcherRunning')}
          value={t(routingOutboxControl.dispatcherRunning ? 'common.yes' : 'common.no')}
        />
      </div>

      {stale && <StaleDataAlert t={t} />}

      <Space wrap>
        <Button
          loading={opsAction === 'deployment-requeue'}
          onClick={() =>
            runOpsAction(
              'deployment-requeue',
              () => requeueDeploymentDeadLetters(),
              t('monitoring.deploymentDeadLettersRequeued')
            )
          }
        >
          {t('monitoring.requeueDeploymentDeadLetters')}
        </Button>
      </Space>
    </div>
  )
}

function AsyncQueueCard({
  asyncHealth,
  stale,
  opsAction,
  runOpsAction,
  onAsyncQueueChanged,
  t,
}: {
  asyncHealth: AsyncInvocationHealth | null
  stale: boolean
  opsAction: string | null
  runOpsAction: (key: string, action: () => Promise<unknown>, success: string) => void
  onAsyncQueueChanged: () => void
  t: TFunction
}) {
  if (!asyncHealth) {
    return <UnavailableOpsCard eyebrow={t('monitoring.asyncInvocationQueue')} t={t} />
  }

  return (
    <div className={styles.opsCard}>
      <div className={styles.runtimeHeader}>
        <div>
          <div className={styles.runtimeEyebrow}>{t('monitoring.asyncInvocationQueue')}</div>
          <div className={styles.runtimeTitle}>{t('monitoring.asyncInvocationQueueTitle')}</div>
        </div>
        <StatusPill status={asyncQueueStatus(asyncHealth)} t={t} />
      </div>

      <div className={styles.runtimeStats}>
        <RuntimeStat label={t('monitoring.asyncReady')} value={asyncHealth.readyQueuedCount} />
        <RuntimeStat
          label={t('monitoring.asyncOldestReady')}
          value={formatQueueAge(asyncHealth.oldestReadyAgeMs)}
        />
        <RuntimeStat label={t('monitoring.asyncDelayed')} value={asyncHealth.delayedQueuedCount} />
        <RuntimeStat label={t('monitoring.asyncRunning')} value={asyncHealth.runningCount} />
        <RuntimeStat label={t('monitoring.asyncExpired')} value={asyncHealth.expiredRunningCount} />
        <RuntimeStat label={t('monitoring.asyncDeadLetters')} value={asyncHealth.deadLetterCount} />
      </div>

      <div className={styles.opsMeta}>
        <span>
          {t('monitoring.asyncWorker')}: {asyncHealth.workerId}
        </span>
        <span>
          {t('monitoring.asyncLease')}: {asyncHealth.leaseDurationMs}ms
        </span>
        <span>
          {t('monitoring.asyncConcurrency')}: {asyncHealth.concurrency}
        </span>
      </div>

      {stale && <StaleDataAlert t={t} />}

      <Space wrap>
        <Button
          loading={opsAction === 'async-requeue'}
          onClick={() =>
            runOpsAction(
              'async-requeue',
              async () => {
                const result = await requeueAsyncInvocationDeadLetters({ limit: 100 })
                onAsyncQueueChanged()
                return result
              },
              t('monitoring.asyncDeadLettersRequeued')
            )
          }
        >
          {t('monitoring.requeueAsyncInvocationDeadLetters')}
        </Button>
      </Space>
    </div>
  )
}

function UnavailableOpsCard({ eyebrow, t }: { eyebrow: string; t: TFunction }) {
  return (
    <div className={styles.opsCard}>
      <div className={styles.runtimeHeader}>
        <div>
          <div className={styles.runtimeEyebrow}>{eyebrow}</div>
          <div className={styles.runtimeTitle}>{t('monitoring.statusUnavailable')}</div>
        </div>
        <StatusPill status="unavailable" t={t} />
      </div>
      <Empty description={t('monitoring.noData')} />
    </div>
  )
}

function DeploymentRuntimePanel({
  deployRuntime,
  stale,
  t,
}: {
  deployRuntime: DeploymentRuntimeDiagnostics | null
  stale: boolean
  t: TFunction
}) {
  if (!deployRuntime) {
    return (
      <ChartPanel title={t('monitoring.deployRuntime')}>
        <Empty description={t('monitoring.noData')} />
      </ChartPanel>
    )
  }

  const status = deployRuntimeStatus(deployRuntime)
  if (!deployRuntime.available) {
    return (
      <ChartPanel title={t('monitoring.deployRuntime')}>
        <div className={styles.runtimePanel}>
          <div className={styles.runtimeHeader}>
            <div>
              <div className={styles.runtimeEyebrow}>{t('monitoring.deployRuntimeLocalNode')}</div>
              <div className={styles.runtimeTitle}>{t('monitoring.deployRuntimeUnavailable')}</div>
            </div>
            <StatusPill status="unavailable" t={t} />
          </div>
          <Alert
            type="info"
            showIcon
            title={deployRuntime.message}
            className={styles.runtimeMessage}
          />
        </div>
      </ChartPanel>
    )
  }

  return (
    <ChartPanel title={t('monitoring.deployRuntime')}>
      <div className={styles.runtimePanel}>
        <div className={styles.runtimeHeader}>
          <div>
            <div className={styles.runtimeEyebrow}>{t('monitoring.deployRuntimeLocalNode')}</div>
            <div className={styles.runtimeTitle}>
              {deployRuntime.available
                ? t('monitoring.deployRuntimeAvailable')
                : t('monitoring.deployRuntimeUnavailable')}
            </div>
          </div>
          <StatusPill status={status} t={t} />
        </div>

        <div className={styles.runtimeStats}>
          <RuntimeStat
            label={t('monitoring.runtimeStarted')}
            value={deployRuntime.started ? t('common.yes') : t('common.no')}
          />
          <RuntimeStat
            label={t('monitoring.runtimeTopology')}
            value={runtimeTopologyLabel(deployRuntime.topology, t)}
          />
          <RuntimeStat
            label={t('monitoring.runtimeInflight')}
            value={`${deployRuntime.inflightCount}/${deployRuntime.inflightCapacity}`}
          />
          <RuntimeStat
            label={t('monitoring.runtimeDesiredAliases')}
            value={deployRuntime.desiredAliasCount}
          />
          <RuntimeStat
            label={t('monitoring.runtimeLocalReadyAliases')}
            value={deployRuntime.localReadyAliasCount}
          />
          <RuntimeStat
            label={t('monitoring.runtimePendingAliases')}
            value={deployRuntime.pendingAliasCount}
          />
          <RuntimeStat
            label={t('monitoring.runtimeFailedAliases')}
            value={deployRuntime.failedAliasCount}
          />
          <RuntimeStat
            label={t('monitoring.runtimeDemanded')}
            value={deployRuntime.demandedVersions.length}
          />
          <RuntimeStat
            label={t('monitoring.runtimeBackedOff')}
            value={deployRuntime.backedOffVersions.length}
          />
        </div>

        {stale && <StaleDataAlert t={t} />}

        <RuntimeLists deployRuntime={deployRuntime} t={t} />
      </div>
    </ChartPanel>
  )
}

function RuntimeLists({
  deployRuntime,
  t,
}: {
  deployRuntime: DeploymentRuntimeAvailableDiagnostics
  t: TFunction
}) {
  return (
    <Row gutter={[16, 16]}>
      <Col xs={24}>
        <RuntimeList title={t('monitoring.runtimeAliases')} emptyText={t('monitoring.noData')}>
          {deployRuntime.aliases.slice(0, 8).map((alias) => (
            <div
              key={`${alias.namespace}/${alias.code}@${alias.alias}`}
              className={styles.runtimeListItem}
            >
              <span>
                {alias.code}@{alias.alias}
              </span>
              <strong>{t(`monitoring.runtimeAliasState.${alias.state}`)}</strong>
              <small>
                {t('monitoring.runtimeAliasRevisions', {
                  desired: alias.desiredRevision,
                  localReady: alias.localReadyRevision,
                })}
                {alias.failureReason ? ` | ${alias.failureReason}` : ''}
              </small>
            </div>
          ))}
        </RuntimeList>
      </Col>
      <Col xs={24} lg={8}>
        <RuntimeList title={t('monitoring.runtimeDemandedList')} emptyText={t('monitoring.noData')}>
          {deployRuntime.demandedVersions.slice(0, 6).map((version) => (
            <div key={version.id} className={styles.runtimeListItem}>
              <span>{version.code}</span>
              <strong>{version.version}</strong>
            </div>
          ))}
        </RuntimeList>
      </Col>
      <Col xs={24} lg={8}>
        <RuntimeList
          title={t('monitoring.runtimeBackedOffList')}
          emptyText={t('monitoring.noErrors')}
        >
          {deployRuntime.backedOffVersions.slice(0, 6).map((version) => (
            <div key={version.id} className={styles.runtimeListItem}>
              <span>
                {version.code}@{version.version}
              </span>
              <strong>{Math.ceil(version.remainingMs / 1000)}s</strong>
              <small>{version.reason}</small>
            </div>
          ))}
        </RuntimeList>
      </Col>
      <Col xs={24} lg={8}>
        <RuntimeList title={t('monitoring.runtimeDeployedList')} emptyText={t('monitoring.noData')}>
          {deployRuntime.deployedVersions.slice(0, 6).map((process) => (
            <div key={`${process.namespace}/${process.code}`} className={styles.runtimeListItem}>
              <span>
                {process.namespace}/{process.code}
              </span>
              <strong>{process.versions.join(', ')}</strong>
            </div>
          ))}
        </RuntimeList>
      </Col>
    </Row>
  )
}

function RuntimeList({
  title,
  emptyText,
  children,
}: {
  title: string
  emptyText: string
  children: ReactNode
}) {
  const hasChildren = Array.isArray(children) ? children.length > 0 : Boolean(children)

  return (
    <div className={styles.runtimeList}>
      <div className={styles.runtimeListTitle}>{title}</div>
      {hasChildren ? children : <div className={styles.runtimeEmpty}>{emptyText}</div>}
    </div>
  )
}

function ExecutionTrendsChart({
  trends,
  colors,
  isDark,
  t,
}: ChartProps & { trends: ExecutionTrend[] }) {
  const axisTickProps = { fill: colors.axis, fontSize: 12 }
  const title = t('monitoring.executionTrends')
  const chartLabel = [
    title,
    ...trends.map(
      (point) =>
        `${point.time}: ${t('monitoring.success')} ${point.success}, ${t('monitoring.failed')} ${point.failed}`
    ),
  ].join('. ')

  return (
    <ChartPanel title={title}>
      {trends.length > 0 ? (
        <div role="img" aria-label={chartLabel}>
          <ResponsiveContainer width="100%" height={300}>
            <LineChart data={trends} accessibilityLayer={false}>
              <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
              <XAxis dataKey="time" stroke={colors.axis} tick={axisTickProps} />
              <YAxis stroke={colors.axis} tick={axisTickProps} />
              <Tooltip contentStyle={tooltipStyle(isDark)} />
              <Legend />
              <Line
                type="monotone"
                dataKey="success"
                stroke="var(--success-main)"
                name={t('monitoring.success')}
              />
              <Line
                type="monotone"
                dataKey="failed"
                stroke="var(--error-main)"
                name={t('monitoring.failed')}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <Empty description={t('monitoring.noData')} />
      )}
    </ChartPanel>
  )
}

function VersionDistributionChart({
  versionDistribution,
  colors,
  isDark,
  t,
}: ChartProps & { versionDistribution: VersionDistributionStats[] }) {
  const axisTickProps = { fill: colors.axis, fontSize: 12 }
  const title = t('monitoring.versionDistribution')
  const chartLabel = [
    title,
    ...versionDistribution.map(
      (version) =>
        `${version.effectiveVersion}: ${t('monitoring.success')} ${version.success}, ${t('monitoring.failed')} ${version.failed}`
    ),
  ].join('. ')

  return (
    <ChartPanel title={title}>
      {versionDistribution.length > 0 ? (
        <div role="img" aria-label={chartLabel}>
          <ResponsiveContainer width="100%" height={260}>
            <BarChart data={versionDistribution} accessibilityLayer={false}>
              <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
              <XAxis dataKey="effectiveVersion" stroke={colors.axis} tick={axisTickProps} />
              <YAxis stroke={colors.axis} tick={axisTickProps} />
              <Tooltip contentStyle={tooltipStyle(isDark)} />
              <Legend />
              <Bar dataKey="success" fill="var(--success-main)" name={t('monitoring.success')} />
              <Bar dataKey="failed" fill="var(--error-main)" name={t('monitoring.failed')} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      ) : (
        <Empty description={t('monitoring.noData')} />
      )}
    </ChartPanel>
  )
}

function TopProcessesAndErrorsPanel({
  topProcesses,
  errors,
  colors,
  isDark,
  t,
}: ChartProps & { topProcesses: TopProcessStats[]; errors: ErrorSummary[] }) {
  const axisTickProps = { fill: colors.axis, fontSize: 12 }
  const topProcessesTitle = t('monitoring.topProcesses')
  const topProcessesLabel = [
    topProcessesTitle,
    ...topProcesses.map(
      (process) =>
        `${process.processCode}: ${t('monitoring.totalExecutions')} ${process.executionCount}`
    ),
  ].join('. ')

  return (
    <Row gutter={[24, 24]} className={styles.chartsRow}>
      <Col xs={24} md={12}>
        <ChartPanel title={topProcessesTitle}>
          {topProcesses.length > 0 ? (
            <div role="img" aria-label={topProcessesLabel}>
              <ResponsiveContainer width="100%" height={300}>
                <BarChart data={topProcesses} accessibilityLayer={false}>
                  <CartesianGrid strokeDasharray="3 3" stroke={colors.grid} />
                  <XAxis dataKey="processCode" stroke={colors.axis} tick={axisTickProps} />
                  <YAxis stroke={colors.axis} tick={axisTickProps} />
                  <Tooltip contentStyle={tooltipStyle(isDark)} />
                  <Bar dataKey="executionCount" fill="var(--primary-500)" radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <Empty description={t('monitoring.noData')} />
          )}
        </ChartPanel>
      </Col>
      <Col xs={24} md={12}>
        <RecentErrorsPanel errors={errors} t={t} />
      </Col>
    </Row>
  )
}

function RecentErrorsPanel({ errors, t }: { errors: ErrorSummary[]; t: TFunction }) {
  return (
    <ChartPanel title={t('monitoring.recentErrors')}>
      {errors.length > 0 ? (
        <ul className={styles.errorListContainer}>
          {errors.map((error, index) => (
            <li key={`${error.processCode}-${index}`} className={styles.errorItem}>
              <div className={styles.errorFlowCode}>{error.processCode}</div>
              <div className={styles.errorMessage}>{error.errorMessage}</div>
              <div className={styles.errorMeta}>{formatErrorMeta(error, t)}</div>
            </li>
          ))}
        </ul>
      ) : (
        <Empty description={t('monitoring.noErrors')} />
      )}
    </ChartPanel>
  )
}

function formatErrorMeta(error: ErrorSummary, t: TFunction) {
  const version = error.effectiveVersion
    ? `${t('monitoring.version')}: ${error.effectiveVersion} | `
    : ''
  return `${error.errorCode} | ${version}${t('monitoring.errorCount')}: ${error.count} | ${t('monitoring.lastOccurred')}: ${formatDateTime(error.lastOccurred)}`
}

const Monitoring: React.FC = () => {
  usePageTitle('pageTitle.operate.monitoring')
  const { t } = useTranslation()
  const { theme } = useTheme()
  const [filters, updateFilter] = useFilterState(
    MONITORING_FILTER_DEFAULTS,
    MONITORING_FILTER_PARSERS
  )
  const timeRange = filters.timeRange
  const [asyncLedgerRevision, setAsyncLedgerRevision] = useState(0)
  const { data, failedSources, loading, reloadAllData, reloadOpsData } =
    useMonitoringData(timeRange)
  const refreshAsyncLedger = useCallback(
    () => setAsyncLedgerRevision((revision) => revision + 1),
    []
  )

  const isDark = theme === 'dark'
  const chartColors = isDark ? CHART_COLORS_DARK : CHART_COLORS_LIGHT
  const failedSourceNames = failedSources.map((source) => t(`monitoring.source.${source}`))

  return (
    <div className={styles.page}>
      {failedSources.length > 0 && (
        <Alert
          type="warning"
          showIcon
          title={t('monitoring.partialLoadError')}
          description={t('monitoring.partialLoadErrorDescription', {
            sources: failedSourceNames.join(', '),
          })}
          action={
            <Button size="small" loading={loading} onClick={() => void reloadAllData()}>
              {t('common.retry')}
            </Button>
          }
          className={styles.loadError}
        />
      )}

      <PageHeader
        accent="operate"
        eyebrow={t('nav.ops.monitoring')}
        title={t('monitoring.title')}
        subtitle={t('monitoring.subtitle')}
      />

      <FilterBar>
        <Select<MonitoringTimeRange>
          value={timeRange}
          onChange={(value) => updateFilter('timeRange', value)}
          style={{ minWidth: 140 }}
          aria-label={t('monitoring.timeRangeLabel')}
        >
          <Option value="1h">{t('monitoring.timeRange.1h')}</Option>
          <Option value="6h">{t('monitoring.timeRange.6h')}</Option>
          <Option value="24h">{t('monitoring.timeRange.24h')}</Option>
          <Option value="7d">{t('monitoring.timeRange.7d')}</Option>
          <Option value="30d">{t('monitoring.timeRange.30d')}</Option>
        </Select>
      </FilterBar>

      <Spin spinning={loading}>
        <MetricGrid metrics={metricItems(data.metrics, t)} columns={4} />
        <OpsControlPlanePanel
          routingOutboxControl={data.routingOutboxControl}
          asyncHealth={data.asyncHealth}
          staleSources={failedSources}
          reloadOpsData={reloadOpsData}
          onAsyncQueueChanged={refreshAsyncLedger}
          t={t}
        />
        <AsyncInvocationOperations
          onQueueChanged={reloadOpsData}
          refreshToken={asyncLedgerRevision}
        />
        <DeploymentRuntimePanel
          deployRuntime={data.deployRuntime}
          stale={failedSources.includes('deployRuntime')}
          t={t}
        />
        <ExecutionTrendsChart trends={data.trends} colors={chartColors} isDark={isDark} t={t} />
        <VersionDistributionChart
          versionDistribution={data.versionDistribution}
          colors={chartColors}
          isDark={isDark}
          t={t}
        />
        <TopProcessesAndErrorsPanel
          topProcesses={data.topProcesses}
          errors={data.errors}
          colors={chartColors}
          isDark={isDark}
          t={t}
        />
      </Spin>
    </div>
  )
}

export default Monitoring
