import { EyeOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import type { TableColumnsType } from 'antd'
import { Alert, Button, Empty, Input, Select, Table } from 'antd'
import type { TFunction } from 'i18next'
import { useTranslation } from 'react-i18next'

import styles from './AsyncInvocationOperations.module.css'
import { formatTimestamp, routeLabel, statusTagKind } from './asyncInvocationView'
import { type AsyncInvocationLedgerState, INVOCATION_PAGE_SIZE } from './useAsyncInvocationLedger'

import { SemanticTag } from '@/shared/components/page'
import type { AsyncInvocationResponse, AsyncInvocationStatus } from '@/shared/contracts'
import { usePaginationItemRender } from '@/shared/hooks/usePaginationItemRender'

const STATUS_OPTIONS = ['queued', 'running', 'succeeded', 'dead_letter'] as const

interface AsyncInvocationLedgerProps {
  ledger: AsyncInvocationLedgerState
  onInspect: (invocation: AsyncInvocationResponse) => void
}

function createColumns(
  t: TFunction,
  onInspect: (invocation: AsyncInvocationResponse) => void
): TableColumnsType<AsyncInvocationResponse> {
  return [
    {
      title: t('monitoring.invocationProcess'),
      dataIndex: 'processCode',
      key: 'processCode',
      width: 180,
    },
    {
      title: t('monitoring.invocationId'),
      dataIndex: 'invocationId',
      key: 'invocationId',
      width: 250,
      className: styles.monospace,
      ellipsis: true,
    },
    {
      title: t('monitoring.invocationStatus'),
      dataIndex: 'status',
      key: 'status',
      width: 125,
      render: (status: AsyncInvocationStatus) => (
        <SemanticTag kind={statusTagKind(status)}>
          {t(`monitoring.invocationStatus.${status}`)}
        </SemanticTag>
      ),
    },
    {
      title: t('monitoring.invocationAttempts'),
      key: 'attempts',
      width: 120,
      render: (_, invocation) =>
        `${invocation.totalAttemptCount} (${invocation.currentAttemptCount}/${invocation.maxAttempts})`,
    },
    {
      title: t('monitoring.invocationVersion'),
      key: 'version',
      width: 150,
      render: (_, invocation) => routeLabel(invocation),
    },
    {
      title: t('monitoring.invocationUpdatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 190,
      render: formatTimestamp,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      fixed: 'right',
      width: 100,
      render: (_, invocation) => (
        <Button type="link" icon={<EyeOutlined />} onClick={() => onInspect(invocation)}>
          {t('common.view')}
        </Button>
      ),
    },
  ]
}

export function AsyncInvocationLedger({ ledger, onInspect }: AsyncInvocationLedgerProps) {
  const { t } = useTranslation()
  const paginationItemRender = usePaginationItemRender()
  const columns = createColumns(t, onInspect)

  return (
    <section className={styles.panel}>
      <header className={styles.panelHeader}>
        <div>
          <div className={styles.eyebrow}>{t('monitoring.invocationLedgerEyebrow')}</div>
          <h2>{t('monitoring.invocationLedger')}</h2>
          <p>{t('monitoring.invocationLedgerDescription')}</p>
        </div>
        <Button
          icon={<ReloadOutlined />}
          loading={ledger.loading}
          onClick={() => void ledger.loadInvocations()}
        >
          {t('common.refresh')}
        </Button>
      </header>

      <div className={styles.filters}>
        <Input.Search
          aria-label={t('monitoring.invocationProcessFilter')}
          allowClear
          enterButton={<SearchOutlined aria-label={t('common.search')} />}
          placeholder={t('monitoring.invocationProcessFilter')}
          value={ledger.processInput}
          onChange={(event) => ledger.setProcessInput(event.target.value)}
          onSearch={ledger.applyProcessFilter}
        />
        <Select<AsyncInvocationStatus>
          aria-label={t('monitoring.invocationStatusFilter')}
          allowClear
          placeholder={t('monitoring.invocationStatusFilter')}
          value={ledger.status}
          onChange={(value) => {
            ledger.setPage(1)
            ledger.setStatus(value)
          }}
          options={STATUS_OPTIONS.map((value) => ({
            value,
            label: t(`monitoring.invocationStatus.${value}`),
          }))}
        />
      </div>

      {ledger.stale && (
        <Alert
          className={styles.staleAlert}
          type="warning"
          showIcon
          title={t('monitoring.invocationLedgerStale')}
        />
      )}

      <Table
        columns={columns}
        dataSource={ledger.invocations.data}
        rowKey="invocationId"
        loading={ledger.loading}
        scroll={{ x: 'max-content' }}
        locale={{
          emptyText: <Empty description={t('monitoring.noInvocations')} />,
        }}
        pagination={{
          current: ledger.page,
          pageSize: INVOCATION_PAGE_SIZE,
          total: ledger.invocations.total,
          itemRender: paginationItemRender,
          showSizeChanger: false,
          onChange: ledger.setPage,
          showTotal: (total) => t('common.totalItems', { total }),
        }}
      />
    </section>
  )
}
