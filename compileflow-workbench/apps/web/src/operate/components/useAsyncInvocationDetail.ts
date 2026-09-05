import { App } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { getAsyncInvocation, listAsyncInvocationAttempts } from '@/shared/api/processes'
import type { AsyncInvocationAttempt, AsyncInvocationResponse } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('AsyncInvocationDetail')
const ATTEMPT_PAGE_SIZE = 20

interface AttemptPage {
  data: AsyncInvocationAttempt[]
  hasMore: boolean
  nextAfterSequence?: number
}

const EMPTY_ATTEMPT_PAGE: AttemptPage = { data: [], hasMore: false }

export interface AsyncInvocationDetailState {
  detailOpen: boolean
  detailLoading: boolean
  detailError: string | null
  selected: AsyncInvocationResponse | null
  attemptPage: AttemptPage
  loadingMore: boolean
  inspectInvocation: (invocation: AsyncInvocationResponse) => void
  closeDetail: () => void
  loadDetail: (invocationId: string) => Promise<boolean>
  loadMoreAttempts: () => Promise<void>
  replaceSelected: (invocation: AsyncInvocationResponse) => void
}

export function useAsyncInvocationDetail(): AsyncInvocationDetailState {
  const { t } = useTranslation()
  const { message } = App.useApp()
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [selected, setSelected] = useState<AsyncInvocationResponse | null>(null)
  const [attemptPage, setAttemptPage] = useState<AttemptPage>(EMPTY_ATTEMPT_PAGE)
  const [loadingMore, setLoadingMore] = useState(false)
  const detailGeneration = useRef(0)
  const loadingMoreRef = useRef(false)

  useEffect(
    () => () => {
      detailGeneration.current += 1
      loadingMoreRef.current = false
    },
    []
  )

  const loadDetail = useCallback(
    async (invocationId: string): Promise<boolean> => {
      const generation = ++detailGeneration.current
      loadingMoreRef.current = false
      setDetailLoading(true)
      setLoadingMore(false)
      setDetailError(null)
      try {
        const [invocation, attempts] = await Promise.all([
          getAsyncInvocation(invocationId),
          listAsyncInvocationAttempts(invocationId, { limit: ATTEMPT_PAGE_SIZE }),
        ])
        if (generation !== detailGeneration.current) return true
        setSelected(invocation)
        setAttemptPage(attempts)
        return true
      } catch (error) {
        if (generation !== detailGeneration.current) return true
        const failure = toError(error)
        logger.warn('Failed to load async invocation detail', {
          invocationId,
          error: failure.message,
        })
        setDetailError(t('error.loadFailed'))
        return false
      } finally {
        if (generation === detailGeneration.current) setDetailLoading(false)
      }
    },
    [t]
  )

  const inspectInvocation = useCallback(
    (invocation: AsyncInvocationResponse) => {
      setDetailOpen(true)
      setSelected(invocation)
      setAttemptPage(EMPTY_ATTEMPT_PAGE)
      void loadDetail(invocation.invocationId)
    },
    [loadDetail]
  )

  const closeDetail = useCallback(() => {
    detailGeneration.current += 1
    loadingMoreRef.current = false
    setLoadingMore(false)
    setDetailOpen(false)
  }, [])

  const loadMoreAttempts = useCallback(async () => {
    if (
      loadingMoreRef.current ||
      !selected ||
      !attemptPage.hasMore ||
      attemptPage.nextAfterSequence === undefined
    ) {
      return
    }
    const invocationId = selected.invocationId
    const generation = detailGeneration.current
    loadingMoreRef.current = true
    setLoadingMore(true)
    try {
      const next = await listAsyncInvocationAttempts(invocationId, {
        afterSequence: attemptPage.nextAfterSequence,
        limit: ATTEMPT_PAGE_SIZE,
      })
      if (generation !== detailGeneration.current) return
      setAttemptPage((current) => ({
        data: [...current.data, ...next.data],
        hasMore: next.hasMore,
        nextAfterSequence: next.nextAfterSequence,
      }))
    } catch (error) {
      if (generation !== detailGeneration.current) return
      logger.warn('Failed to load more async invocation attempts', {
        invocationId,
        error: toError(error).message,
      })
      message.error(t('error.loadFailed'))
    } finally {
      if (generation === detailGeneration.current) {
        loadingMoreRef.current = false
        setLoadingMore(false)
      }
    }
  }, [attemptPage.hasMore, attemptPage.nextAfterSequence, message, selected, t])

  const replaceSelected = useCallback((invocation: AsyncInvocationResponse) => {
    setSelected(invocation)
  }, [])

  return {
    detailOpen,
    detailLoading,
    detailError,
    selected,
    attemptPage,
    loadingMore,
    inspectInvocation,
    closeDetail,
    loadDetail,
    loadMoreAttempts,
    replaceSelected,
  }
}
