import { App } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { AsyncInvocationDetailDrawer } from './AsyncInvocationDetailDrawer'
import { AsyncInvocationLedger } from './AsyncInvocationLedger'
import { useAsyncInvocationDetail } from './useAsyncInvocationDetail'
import { useAsyncInvocationLedger } from './useAsyncInvocationLedger'

import { requeueAsyncInvocation } from '@/shared/api/processes'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('AsyncInvocationOperations')

export interface AsyncInvocationOperationsProps {
  onQueueChanged?: () => Promise<void>
  refreshToken?: number
}

export function AsyncInvocationOperations({
  onQueueChanged,
  refreshToken = 0,
}: AsyncInvocationOperationsProps) {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const ledger = useAsyncInvocationLedger(refreshToken)
  const detail = useAsyncInvocationDetail()
  const [requeueing, setRequeueing] = useState(false)
  const [requeueConfirmOpen, setRequeueConfirmOpen] = useState(false)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const requeueSelected = useCallback(async () => {
    const selected = detail.selected
    if (!mounted.current || !selected || selected.status !== 'dead_letter') return

    const invocationId = selected.invocationId
    setRequeueing(true)
    setRequeueConfirmOpen(false)
    try {
      const accepted = await requeueAsyncInvocation(invocationId)
      if (!mounted.current) return
      detail.replaceSelected(accepted)
      ledger.replaceInvocation(accepted)
      message.success(t('monitoring.invocationRequeued'))

      const refreshes: Promise<unknown>[] = [
        ledger.loadInvocations(true),
        detail.loadDetail(invocationId),
      ]
      if (onQueueChanged) refreshes.push(onQueueChanged())
      const results = await Promise.allSettled(refreshes)
      if (!mounted.current) return
      if (results.some((result) => result.status === 'rejected' || result.value === false)) {
        message.warning(t('monitoring.opsRefreshFailed'))
      }
    } catch (error) {
      if (!mounted.current) return
      logger.error('Failed to requeue async invocation', toError(error), { invocationId })
      message.error(t('monitoring.opsActionFailed'))
    } finally {
      if (mounted.current) setRequeueing(false)
    }
  }, [detail, ledger, message, onQueueChanged, t])

  return (
    <>
      <AsyncInvocationLedger ledger={ledger} onInspect={detail.inspectInvocation} />
      <AsyncInvocationDetailDrawer
        detail={detail}
        requeueing={requeueing}
        requeueConfirmOpen={requeueConfirmOpen}
        onRequestRequeue={() => setRequeueConfirmOpen(true)}
        onCancelRequeue={() => setRequeueConfirmOpen(false)}
        onConfirmRequeue={() => void requeueSelected()}
        onClose={() => {
          setRequeueConfirmOpen(false)
          detail.closeDetail()
        }}
      />
    </>
  )
}
