import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Descriptions, Drawer, Empty, Space } from 'antd'
import { useTranslation } from 'react-i18next'

import styles from './AsyncInvocationOperations.module.css'
import { attemptTagKind, formatTimestamp, routeLabel, statusTagKind } from './asyncInvocationView'
import type { AsyncInvocationDetailState } from './useAsyncInvocationDetail'

import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { SemanticTag } from '@/shared/components/page'
import type { AsyncInvocationAttempt } from '@/shared/contracts'

interface AsyncInvocationDetailDrawerProps {
  detail: AsyncInvocationDetailState
  requeueing: boolean
  requeueConfirmOpen: boolean
  onRequestRequeue: () => void
  onCancelRequeue: () => void
  onConfirmRequeue: () => void
  onClose: () => void
}

function InvocationAttemptList({
  attempts,
  loadingMore,
  hasMore,
  onLoadMore,
}: {
  attempts: AsyncInvocationAttempt[]
  loadingMore: boolean
  hasMore: boolean
  onLoadMore: () => void
}) {
  const { t } = useTranslation()
  if (attempts.length === 0) {
    return <Empty description={t('monitoring.noInvocationAttempts')} />
  }

  return (
    <>
      <div className={styles.attemptList} role="list">
        {attempts.map((attempt) => (
          <article key={attempt.attemptId} className={styles.attemptItem} role="listitem">
            <div className={styles.attemptHeader}>
              <div>
                <strong>#{attempt.sequence}</strong>
                <span>
                  {t('monitoring.invocationAttemptGeneration', {
                    redrive: attempt.redriveCount,
                    attempt: attempt.attemptNumber,
                  })}
                </span>
              </div>
              <Space size={4} wrap>
                <SemanticTag kind={attemptTagKind(attempt)}>
                  {t(`monitoring.invocationOutcome.${attempt.outcome}`)}
                </SemanticTag>
                {attempt.disposition && (
                  <SemanticTag kind="default">
                    {t(`monitoring.invocationDisposition.${attempt.disposition}`)}
                  </SemanticTag>
                )}
              </Space>
            </div>
            <div className={styles.attemptFacts}>
              <span>
                {t('monitoring.invocationWorker')}: {attempt.workerId}
              </span>
              <span>
                {t('monitoring.invocationStartedAt')}: {formatTimestamp(attempt.startedAt)}
              </span>
              <span>
                {t('monitoring.invocationFinishedAt')}: {formatTimestamp(attempt.finishedAt)}
              </span>
              <span>
                {t('monitoring.invocationDuration')}:{' '}
                {attempt.durationMs === undefined ? '-' : `${attempt.durationMs} ms`}
              </span>
              {attempt.traceId && (
                <span className={styles.monospace}>
                  {t('monitoring.invocationTraceId')}: {attempt.traceId}
                </span>
              )}
              {attempt.nextAttemptAt && (
                <span>
                  {t('monitoring.invocationNextAttemptAt')}:{' '}
                  {formatTimestamp(attempt.nextAttemptAt)}
                </span>
              )}
            </div>
            {(attempt.errorCode || attempt.error) && (
              <Alert
                className={styles.attemptError}
                type="error"
                showIcon
                title={attempt.errorCode ?? t('monitoring.invocationAttemptFailed')}
                description={attempt.error}
              />
            )}
          </article>
        ))}
      </div>
      {hasMore && (
        <Button block loading={loadingMore} onClick={onLoadMore}>
          {t('common.loadMore')}
        </Button>
      )}
    </>
  )
}

export function AsyncInvocationDetailDrawer({
  detail,
  requeueing,
  requeueConfirmOpen,
  onRequestRequeue,
  onCancelRequeue,
  onConfirmRequeue,
  onClose,
}: AsyncInvocationDetailDrawerProps) {
  const { t } = useTranslation()
  const selected = detail.selected

  return (
    <>
      <Drawer
        open={detail.detailOpen}
        size="large"
        title={t('monitoring.invocationDetail')}
        onClose={onClose}
        extra={
          selected?.status === 'dead_letter' ? (
            <Button danger loading={requeueing} onClick={onRequestRequeue}>
              {t('monitoring.requeueInvocation')}
            </Button>
          ) : null
        }
      >
        {detail.detailError && (
          <Alert
            type="error"
            showIcon
            title={t('monitoring.invocationDetailFailed')}
            description={detail.detailError}
            action={
              selected?.invocationId ? (
                <Button size="small" onClick={() => void detail.loadDetail(selected.invocationId)}>
                  {t('common.retry')}
                </Button>
              ) : undefined
            }
          />
        )}
        {selected && <InvocationDetail detail={detail} />}
        {!selected && !detail.detailError && (
          <div className={styles.detailLoading} aria-busy={detail.detailLoading}>
            {t('common.loading')}
          </div>
        )}
      </Drawer>
      <Modal
        open={requeueConfirmOpen}
        title={t('monitoring.invocationRequeueConfirm')}
        okText={t('common.confirm')}
        cancelText={t('common.cancel')}
        okButtonProps={{ danger: true }}
        confirmLoading={requeueing}
        onOk={onConfirmRequeue}
        onCancel={onCancelRequeue}
      >
        <Alert type="warning" showIcon title={t('monitoring.invocationRequeueWarning')} />
      </Modal>
    </>
  )
}

function InvocationDetail({ detail }: { detail: AsyncInvocationDetailState }) {
  const { t } = useTranslation()
  const selected = detail.selected
  if (!selected) return null

  return (
    <div className={styles.detail}>
      <Descriptions bordered column={1} size="small">
        <Descriptions.Item label={t('monitoring.invocationId')}>
          <span className={styles.monospace}>{selected.invocationId}</span>
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.invocationProcess')}>
          {selected.processCode}
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.invocationStatus')}>
          <SemanticTag kind={statusTagKind(selected.status)}>
            {t(`monitoring.invocationStatus.${selected.status}`)}
          </SemanticTag>
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.invocationAttempts')}>
          {t('monitoring.invocationAttemptSummary', {
            total: selected.totalAttemptCount,
            current: selected.currentAttemptCount,
            maximum: selected.maxAttempts,
            redrives: selected.redriveCount,
          })}
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.invocationVersion')}>
          {routeLabel(selected)}
        </Descriptions.Item>
        <Descriptions.Item label={t('monitoring.invocationUpdatedAt')}>
          {formatTimestamp(selected.updatedAt)}
        </Descriptions.Item>
        {selected.traceId && (
          <Descriptions.Item label={t('monitoring.invocationTraceId')}>
            <span className={styles.monospace}>{selected.traceId}</span>
          </Descriptions.Item>
        )}
      </Descriptions>

      {(selected.errorCode || selected.error) && (
        <Alert
          type="error"
          showIcon
          title={selected.errorCode ?? t('monitoring.invocationAttemptFailed')}
          description={selected.error}
        />
      )}

      <div className={styles.attemptSectionHeader}>
        <h3>{t('monitoring.invocationAttemptHistory')}</h3>
        <Button
          size="small"
          icon={<ReloadOutlined />}
          loading={detail.detailLoading}
          onClick={() => void detail.loadDetail(selected.invocationId)}
        >
          {t('common.refresh')}
        </Button>
      </div>
      <InvocationAttemptList
        attempts={detail.attemptPage.data}
        loadingMore={detail.loadingMore}
        hasMore={detail.attemptPage.hasMore}
        onLoadMore={() => void detail.loadMoreAttempts()}
      />
    </div>
  )
}
