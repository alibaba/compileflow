import { HistoryOutlined, RollbackOutlined } from '@ant-design/icons'
import { Button, Space, Tag, Typography } from 'antd'
import type { TFunction } from 'i18next'

import type { Deployment } from '@/shared/contracts'
import { formatDateTime } from '@/shared/i18n/dateTime'

/** Stable color maps — kept as module-level constants to avoid per-render allocation. */
const ALIAS_COLOR_MAP: Record<string, string> = {
  dev: 'default',
  staging: 'processing',
  production: 'error',
}

const STATUS_COLOR_MAP: Record<Deployment['status'], string> = {
  in_progress: 'processing',
  completed: 'success',
  aborted: 'warning',
}

export interface DeploymentColumnDeps {
  t: TFunction
  onRestoreBaseline: (id: string) => void
  onViewDetail: (id: string) => void
}

export function createDeploymentColumns({
  t,
  onRestoreBaseline,
  onViewDetail,
}: DeploymentColumnDeps) {
  return [
    {
      title: t('deployment.processCode'),
      dataIndex: 'processCode',
      key: 'processCode',
      width: 200,
      render: (processCode: string, record: Deployment) => (
        <Space orientation="vertical" size={0}>
          <span>{processCode}</span>
          <Typography.Text type="secondary" copyable>
            {record.id}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: t('deployment.version'),
      dataIndex: 'version',
      key: 'version',
      width: 100,
    },
    {
      title: t('deployment.alias'),
      dataIndex: 'alias',
      key: 'alias',
      width: 120,
      render: (alias: string) => <Tag color={ALIAS_COLOR_MAP[alias] ?? 'default'}>{alias}</Tag>,
    },
    {
      title: t('deployment.operation'),
      dataIndex: 'operation',
      key: 'operation',
      width: 110,
      render: (operation: Deployment['operation']) => (
        <Tag>{t(`deployment.operation.${operation}`)}</Tag>
      ),
    },
    {
      title: t('deployment.strategy'),
      dataIndex: 'strategy',
      key: 'strategy',
      width: 120,
      render: (strategy: Deployment['strategy']) => t(`deployment.strategy.${strategy}`),
    },
    {
      title: t('deployment.status'),
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: Deployment['status']) => (
        <Tag color={STATUS_COLOR_MAP[status]}>{t(`deployment.status.${status}`)}</Tag>
      ),
    },
    {
      title: t('deployment.deployedAt'),
      dataIndex: 'deployedAt',
      key: 'deployedAt',
      width: 180,
      render: formatDateTime,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 180,
      fixed: 'right' as const,
      render: (_: unknown, record: Deployment) => (
        <Space size="small">
          {record.baselineVersion &&
            (record.status === 'completed' || record.status === 'in_progress') && (
              <Button
                type="link"
                size="small"
                icon={<RollbackOutlined />}
                onClick={() => onRestoreBaseline(record.id)}
                danger
              >
                {t(record.status === 'in_progress' ? 'deployment.abort' : 'deployment.rollback')}
              </Button>
            )}
          <Button
            type="link"
            size="small"
            icon={<HistoryOutlined />}
            onClick={() => onViewDetail(record.id)}
          >
            {t('common.detail')}
          </Button>
        </Space>
      ),
    },
  ]
}
