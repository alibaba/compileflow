import {
  ArrowLeftOutlined,
  ExperimentOutlined,
  HistoryOutlined,
  RiseOutlined,
  RollbackOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Descriptions, Empty, Slider, Space, Spin } from 'antd'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'
import { useNavigate, useParams } from 'react-router-dom'

import styles from './DeploymentDetail.module.css'

import type { DeploymentAction } from '@/operate/hooks/useDeploymentDetailData'
import { useDeploymentDetailData } from '@/operate/hooks/useDeploymentDetailData'
import {
  DataPageShell,
  SemanticList,
  SemanticListItem,
  SemanticListMeta,
  SemanticTag,
  SurfacePanel,
} from '@/shared/components/page'
import { buildDeploymentDetailPath, getDeploymentAliasPreset, ROUTES } from '@/shared/constants'
import type {
  CanaryHealthEvaluationResponse,
  Deployment,
  DeploymentEvent,
  DeploymentRoute,
} from '@/shared/contracts'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { formatDateTime } from '@/shared/i18n/dateTime'

const STATUS_KIND: Record<
  string,
  'status-success' | 'status-error' | 'status-warning' | 'default'
> = {
  completed: 'status-success',
  in_progress: 'default',
  failed: 'status-error',
  aborted: 'status-warning',
}

const EVENT_KIND: Record<string, 'status-success' | 'status-warning' | 'default'> = {
  COMPLETED: 'status-success',
  PROMOTED: 'status-success',
  ABORTED: 'status-warning',
}

function DeploymentSummary({ deployment, t }: { deployment: Deployment; t: TFunction }) {
  const aliasPreset = getDeploymentAliasPreset(deployment.alias)
  return (
    <Descriptions bordered column={2} size="small" className={styles.descriptions}>
      <Descriptions.Item label={t('deployment.version')}>{deployment.version}</Descriptions.Item>
      <Descriptions.Item label={t('deployment.baselineVersion')}>
        {deployment.baselineVersion ?? '-'}
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.alias')}>
        <SemanticTag kind="status-info">
          {aliasPreset ? t(aliasPreset.labelKey) : deployment.alias}
        </SemanticTag>
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.operation')}>
        {t(`deployment.operation.${deployment.operation}`)}
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.strategy')}>
        {t(`deployment.strategy.${deployment.strategy}`)}
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.status')}>
        <SemanticTag kind={STATUS_KIND[deployment.status] ?? 'default'}>
          {t(`deployment.status.${deployment.status}`)}
        </SemanticTag>
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.routeRevision')}>
        {deployment.routeRevision}
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.baseRouteRevision')}>
        {deployment.baseRouteRevision}
      </Descriptions.Item>
      <Descriptions.Item label={t('deployment.deployedAt')}>
        {formatDateTime(deployment.deployedAt)}
      </Descriptions.Item>
      <Descriptions.Item label={t('process.createdBy')}>{deployment.createdBy}</Descriptions.Item>
      {deployment.notes && (
        <Descriptions.Item label={t('deployment.notes')} span={2}>
          {deployment.notes}
        </Descriptions.Item>
      )}
      {deployment.status === 'in_progress' && deployment.canaryWeightBps != null && (
        <Descriptions.Item label={t('deployment.canaryWeightBps')} span={2}>
          {deployment.canaryWeightBps} bps ({deployment.canaryWeightBps / 100}%)
        </Descriptions.Item>
      )}
    </Descriptions>
  )
}

function CanaryHealth({ health, t }: { health: CanaryHealthEvaluationResponse; t: TFunction }) {
  const alertType =
    health.decision === 'healthy'
      ? 'success'
      : health.decision === 'unhealthy'
        ? 'error'
        : 'warning'
  return (
    <div className={styles.healthResult}>
      <Alert
        type={alertType}
        showIcon
        title={t(`deployment.health.${health.decision}`)}
        description={
          health.reason === 'Canary metrics are within configured thresholds.'
            ? t('deployment.health.reason.withinThresholds')
            : health.reason
        }
      />
      <Descriptions bordered column={2} size="small">
        <Descriptions.Item label={t('deployment.health.metricsScope')}>
          {t(`deployment.health.metricsScope.${health.metricsScope}`)}
        </Descriptions.Item>
        <Descriptions.Item label={t('deployment.health.metricsSource')}>
          {t(`deployment.health.metricsSource.${health.metricsSource}`)}
        </Descriptions.Item>
        <Descriptions.Item label={t('deployment.health.candidateSamples')}>
          {health.canary.samples}
        </Descriptions.Item>
        <Descriptions.Item label={t('deployment.health.baselineSamples')}>
          {health.baseline.samples}
        </Descriptions.Item>
        <Descriptions.Item label={t('deployment.health.candidateErrorRate')}>
          {(health.canary.errorRate * 100).toFixed(2)}%
        </Descriptions.Item>
        <Descriptions.Item label={t('deployment.health.candidateP95')}>
          {health.canary.p95DurationMs} ms
        </Descriptions.Item>
      </Descriptions>
    </div>
  )
}

function CanaryControl({
  action,
  canaryValue,
  health,
  onAbort,
  onEvaluate,
  onPromote,
  onUpdate,
  routeOwned,
  setCanaryValue,
  t,
}: {
  action: DeploymentAction | undefined
  canaryValue: number
  health: CanaryHealthEvaluationResponse | undefined
  onAbort: () => void
  onEvaluate: () => void
  onPromote: () => void
  onUpdate: () => void
  routeOwned: boolean
  setCanaryValue: (value: number) => void
  t: TFunction
}) {
  const busy = action != null
  return (
    <SurfacePanel
      title={t('deployment.canaryControl')}
      icon={<RiseOutlined />}
      className={styles.canaryPanel}
    >
      <p className={styles.hint}>{t('deployment.canaryControlHint')}</p>
      {!routeOwned && <Alert type="warning" showIcon title={t('deployment.routeSuperseded')} />}
      <Slider
        min={1}
        max={9_999}
        value={canaryValue}
        disabled={busy || !routeOwned}
        onChange={setCanaryValue}
        marks={{ 100: '1%', 5_000: '50%', 9_900: '99%' }}
        aria-label={t('deployment.canaryWeightBps')}
        aria-valuetext={`${canaryValue} bps (${canaryValue / 100}%)`}
      />
      <Space className={styles.actions} wrap>
        <Button
          loading={action === 'evaluate-health'}
          disabled={busy || !routeOwned}
          icon={<ExperimentOutlined />}
          onClick={onEvaluate}
        >
          {t('deployment.evaluateHealth')}
        </Button>
        <Button
          loading={action === 'update-canary'}
          disabled={busy || !routeOwned}
          onClick={onUpdate}
        >
          {t('deployment.updateCanary')}
        </Button>
        <Button
          type="primary"
          loading={action === 'promote'}
          disabled={busy || !routeOwned}
          onClick={onPromote}
        >
          {t('deployment.promoteCanary')}
        </Button>
        <Button
          danger
          loading={action === 'restore-baseline'}
          disabled={busy || !routeOwned}
          onClick={onAbort}
        >
          {t('deployment.abort')}
        </Button>
      </Space>
      {health && <CanaryHealth health={health} t={t} />}
    </SurfacePanel>
  )
}

function RollbackControl({
  action,
  onRollback,
  routeOwned,
  t,
}: {
  action: DeploymentAction | undefined
  onRollback: () => void
  routeOwned: boolean
  t: TFunction
}) {
  return (
    <SurfacePanel title={t('deployment.routeControl')} className={styles.canaryPanel}>
      {!routeOwned && <Alert type="warning" showIcon title={t('deployment.routeSuperseded')} />}
      <p className={styles.hint}>{t('deployment.rollbackHint')}</p>
      <Button
        danger
        icon={<RollbackOutlined />}
        loading={action === 'restore-baseline'}
        disabled={action != null || !routeOwned}
        onClick={onRollback}
      >
        {t('deployment.rollback')}
      </Button>
    </SurfacePanel>
  )
}

function DeploymentEvents({ events, t }: { events: DeploymentEvent[]; t: TFunction }) {
  return (
    <SurfacePanel title={t('deployment.history')} icon={<HistoryOutlined />}>
      {events.length === 0 ? (
        <Empty description={t('deployment.noEvents')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <SemanticList>
          {events.map((item, index) => (
            <SemanticListItem key={`${item.timestamp}-${item.type}-${index}`}>
              <SemanticListMeta
                title={
                  <span className={styles.logMeta}>
                    <SemanticTag kind={EVENT_KIND[item.type] ?? 'default'}>
                      {t(`deployment.event.${item.type}`)}
                    </SemanticTag>
                    {formatDateTime(item.timestamp)}
                  </span>
                }
                description={[
                  item.fromPhase == null
                    ? t('deployment.event.enteredPhase', {
                        phase: t(`deployment.phase.${item.toPhase}`),
                      })
                    : t('deployment.event.phaseTransition', {
                        from: t(`deployment.phase.${item.fromPhase}`),
                        to: t(`deployment.phase.${item.toPhase}`),
                      }),
                  t('deployment.event.actor', { actor: item.actor }),
                  item.reason,
                ]
                  .filter(Boolean)
                  .join(' | ')}
              />
            </SemanticListItem>
          ))}
        </SemanticList>
      )}
    </SurfacePanel>
  )
}

function isDeploymentRouteOwned(
  deployment: Deployment | null,
  route: DeploymentRoute | undefined
): boolean {
  return deployment != null && route?.revision === deployment.routeRevision
}

function DeploymentLoadFailure({
  hasDeployment,
  loadError,
  onRetry,
  t,
}: {
  hasDeployment: boolean
  loadError: boolean
  onRetry: () => void
  t: TFunction
}) {
  if (!loadError || hasDeployment) return null

  return (
    <Alert
      type="error"
      showIcon
      title={t('deployment.loadDetailError')}
      action={
        <Button size="small" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      }
    />
  )
}

function DeploymentDetail() {
  const { modal } = App.useApp()
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const detail = useDeploymentDetailData(id, t)
  usePageTitle('pageTitle.operate.deploymentDetail', detail.deployment?.processCode)
  const routeOwned = isDeploymentRouteOwned(detail.deployment, detail.route)

  if (!id) return null

  const confirmPromotion = () => {
    const decision = detail.health?.decision
    modal.confirm({
      title: t('deployment.promoteConfirm'),
      content: decision
        ? t(`deployment.promoteWarning.${decision}`)
        : t('deployment.promoteWarning.notEvaluated'),
      okText: t('deployment.promoteCanary'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: decision === 'unhealthy' },
      onOk: detail.promote,
    })
  }

  const confirmRestoreBaseline = () => {
    const isCanary = detail.deployment?.status === 'in_progress'
    modal.confirm({
      title: t(isCanary ? 'deployment.abortConfirm' : 'deployment.rollbackConfirm'),
      content: t(isCanary ? 'deployment.abortWarning' : 'deployment.rollbackWarning'),
      okText: t(isCanary ? 'deployment.abort' : 'deployment.rollback'),
      cancelText: t('common.cancel'),
      okButtonProps: { danger: true },
      onOk: async () => {
        const updated = await detail.restoreBaseline()
        if (updated && updated.id !== id) {
          await navigate(buildDeploymentDetailPath(updated.id), { replace: true })
        }
      },
    })
  }

  return (
    <DataPageShell
      title={detail.deployment?.processCode ?? t('deployment.detail')}
      accent="operate"
      eyebrow={t('nav.operate')}
      subtitle={id}
      actions={
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(ROUTES.OPERATE_DEPLOYMENTS)}>
          {t('common.back')}
        </Button>
      }
    >
      <Spin spinning={detail.loading}>
        <DeploymentLoadFailure
          hasDeployment={detail.deployment != null}
          loadError={detail.loadError}
          onRetry={() => void detail.reload()}
          t={t}
        />
        {detail.deployment && <DeploymentSummary deployment={detail.deployment} t={t} />}

        {detail.deployment?.status === 'in_progress' && (
          <CanaryControl
            action={detail.action}
            canaryValue={detail.canaryValue}
            health={detail.health}
            onAbort={confirmRestoreBaseline}
            onEvaluate={() => void detail.evaluateHealth()}
            onPromote={confirmPromotion}
            onUpdate={() => void detail.updateCanary()}
            routeOwned={routeOwned}
            setCanaryValue={detail.setCanaryValue}
            t={t}
          />
        )}

        {detail.deployment?.status === 'completed' && detail.deployment.baselineVersion && (
          <RollbackControl
            action={detail.action}
            onRollback={confirmRestoreBaseline}
            routeOwned={routeOwned}
            t={t}
          />
        )}

        {detail.deployment && <DeploymentEvents events={detail.events} t={t} />}
      </Spin>
    </DataPageShell>
  )
}

export default DeploymentDetail
