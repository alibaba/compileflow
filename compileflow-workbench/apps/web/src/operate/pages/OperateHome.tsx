import {
  ArrowRightOutlined,
  ClockCircleOutlined,
  DashboardOutlined,
  RocketOutlined,
} from '@ant-design/icons'
import { Alert, Button, Empty, Spin } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { NavigateFunction } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'

import styles from './OperateHome.module.css'

import { getDeployments } from '@/operate/api/deployments'
import { getMetrics } from '@/operate/api/monitoring'
import { HeroBanner } from '@/shared/components/HeroBanner'
import {
  HubMetrics,
  HubPanel,
  HubRow,
  type HubRowAccent,
  HubRowList,
  HubSection,
  HubSurface,
  ListPanel,
  MetricGrid,
  type MetricItem,
  SemanticList,
  SemanticListItem,
  SemanticListMeta,
  SemanticTag,
  statusToTagKind,
} from '@/shared/components/page'
import {
  buildDeploymentDetailPath,
  createOperateDeployWizardPath,
  ROUTES,
} from '@/shared/constants'
import type { Deployment } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { formatDateTime } from '@/shared/i18n/dateTime'
import { createLogger } from '@/shared/logging/logger'

interface FeatureItem {
  accent: HubRowAccent
  titleKey: string
  descKey: string
  path: string
}

interface OperateDashboardState {
  loading: boolean
  metrics: MetricItem[]
  recentDeployments: Deployment[] | null
  recentDeploymentsFailed: boolean
}

const logger = createLogger('OperateHome')

function buildFeatureItems(): FeatureItem[] {
  return [
    {
      accent: 'operate',
      titleKey: 'operate.processMgmt',
      descKey: 'operate.processMgmtDesc',
      path: ROUTES.OPERATE_PROCESSES,
    },
    {
      accent: 'success',
      titleKey: 'operate.deployMgmt',
      descKey: 'operate.deployMgmtDesc',
      path: ROUTES.OPERATE_DEPLOYMENTS,
    },
    {
      accent: 'warning',
      titleKey: 'operate.realtimeMonitor',
      descKey: 'operate.realtimeMonitorDesc',
      path: ROUTES.OPERATE_MONITORING,
    },
    {
      accent: 'primary',
      titleKey: 'operate.logQuery',
      descKey: 'operate.logQueryDesc',
      path: ROUTES.OPERATE_LOGS,
    },
  ]
}

function unavailableMetrics(t: ReturnType<typeof useTranslation>['t']): MetricItem[] {
  return [
    { key: 'aliased', label: t('operate.processesWithAliases'), value: '-', accent: 'success' },
    { key: 'executions', label: t('operate.processExecutions'), value: '-', accent: 'primary' },
    { key: 'success', label: t('operate.successRate'), value: '-', accent: 'warning' },
    { key: 'failed', label: t('operate.failedExecutions'), value: '-', accent: 'error' },
  ]
}

function useOperateDashboardData(): OperateDashboardState {
  const { t } = useTranslation()
  const [recentDeployments, setRecentDeployments] = useState<Deployment[] | null>(null)
  const [recentDeploymentsFailed, setRecentDeploymentsFailed] = useState(false)
  const [loading, setLoading] = useState(true)
  const [metrics, setMetrics] = useState<MetricItem[]>([])

  useEffect(() => {
    let cancelled = false
    getDeployments({ limit: 4 })
      .then((response) => {
        if (cancelled) return
        setRecentDeployments(response.deployments)
        setRecentDeploymentsFailed(false)
      })
      .catch((error) => {
        if (cancelled) return
        logger.warn('Failed to load recent deployments for operate home', {
          error: toError(error).message,
        })
        setRecentDeploymentsFailed(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    getMetrics()
      .then((dashboardMetrics) => {
        if (cancelled) return

        const successRate =
          dashboardMetrics.totalExecutions > 0
            ? (
                (dashboardMetrics.successExecutions / dashboardMetrics.totalExecutions) *
                100
              ).toFixed(1)
            : '-'

        setMetrics([
          {
            key: 'aliased',
            label: t('operate.processesWithAliases'),
            value: dashboardMetrics.processesWithAliases,
            suffix: t('operate.unit.process'),
            accent: 'success',
          },
          {
            key: 'executions',
            label: t('operate.processExecutions'),
            value: dashboardMetrics.totalExecutions,
            suffix: t('operate.unit.times'),
            accent: 'primary',
          },
          {
            key: 'success',
            label: t('operate.successRate'),
            value: successRate,
            suffix: successRate === '-' ? undefined : '%',
            accent: 'warning',
          },
          {
            key: 'failed',
            label: t('operate.failedExecutions'),
            value: dashboardMetrics.failedExecutions,
            suffix: t('operate.unit.items'),
            accent: 'error',
          },
        ])
      })
      .catch((error) => {
        if (cancelled) return
        logger.warn('Failed to load operate home metrics', { error: toError(error).message })
        setMetrics(unavailableMetrics(t))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [t])

  return {
    loading,
    metrics,
    recentDeployments,
    recentDeploymentsFailed,
  }
}

function OperateHero({ navigate }: { navigate: NavigateFunction }) {
  const { t } = useTranslation()

  return (
    <HeroBanner
      variant="operate"
      layout="simple"
      eyebrow={t('nav.operate')}
      title={t('operate.title')}
      subtitle={t('operate.subtitle')}
      primaryAction={{
        labelKey: 'operate.deploy',
        label: t('operate.deploy'),
        onClick: () => navigate(createOperateDeployWizardPath({ source: 'operate-home' })),
        icon: <RocketOutlined />,
        type: 'primary',
      }}
      secondaryAction={{
        labelKey: 'operate.monitor',
        label: t('operate.monitor'),
        onClick: () => navigate(ROUTES.OPERATE_MONITORING),
        icon: <DashboardOutlined />,
        type: 'secondary',
      }}
    />
  )
}

function ExploreSection({ navigate }: { navigate: NavigateFunction }) {
  const { t } = useTranslation()
  const featureItems = useMemo(() => buildFeatureItems(), [])

  return (
    <HubSection title={t('operate.explore')}>
      <HubRowList>
        {featureItems.map((item, index) => (
          <HubRow
            key={item.titleKey}
            index={String(index + 1).padStart(2, '0')}
            accent={item.accent}
            title={t(item.titleKey)}
            description={t(item.descKey)}
            action={
              <>
                {t('common.open')} <ArrowRightOutlined />
              </>
            }
            onClick={() => navigate(item.path)}
          />
        ))}
      </HubRowList>
    </HubSection>
  )
}

function RecentDeploymentsPanel({
  deployments,
  failed,
  navigate,
}: {
  deployments: Deployment[] | null
  failed: boolean
  navigate: NavigateFunction
}) {
  const { t } = useTranslation()

  return (
    <ListPanel
      icon={<ClockCircleOutlined />}
      title={t('operate.recentDeploys')}
      action={
        <Button type="link" size="small" onClick={() => navigate(ROUTES.OPERATE_DEPLOYMENTS)}>
          {t('operate.viewAll')}
        </Button>
      }
    >
      {failed ? (
        <Alert type="warning" showIcon title={t('operate.recentDeploymentsUnavailable')} />
      ) : (
        <Spin spinning={deployments === null}>
          {deployments !== null && deployments.length === 0 ? (
            <Empty
              description={t('operate.noRecentDeployments')}
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            />
          ) : (
            <SemanticList>
              {(deployments ?? []).map((item) => (
                <SemanticListItem
                  key={item.id}
                  actions={
                    <SemanticTag kind={statusToTagKind(item.status)}>
                      {t(`deployment.status.${item.status}`)}
                    </SemanticTag>
                  }
                  onActivate={() => navigate(buildDeploymentDetailPath(item.id))}
                >
                  <SemanticListMeta
                    title={item.processCode}
                    description={
                      <span className={styles.deployMeta}>
                        {item.alias} · {formatDateTime(item.deployedAt)}
                      </span>
                    }
                  />
                </SemanticListItem>
              ))}
            </SemanticList>
          )}
        </Spin>
      )}
    </ListPanel>
  )
}

function OperateHome() {
  usePageTitle('pageTitle.operate')
  const navigate = useNavigate()
  const { loading, metrics, recentDeployments, recentDeploymentsFailed } = useOperateDashboardData()

  return (
    <HubSurface className={styles.page}>
      <OperateHero navigate={navigate} />
      <HubMetrics>
        <Spin spinning={loading}>
          <MetricGrid metrics={metrics} columns={4} />
        </Spin>
      </HubMetrics>
      <ExploreSection navigate={navigate} />
      <HubPanel>
        <RecentDeploymentsPanel
          deployments={recentDeployments}
          failed={recentDeploymentsFailed}
          navigate={navigate}
        />
      </HubPanel>
    </HubSurface>
  )
}

export default OperateHome
