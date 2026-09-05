import { useCallback, useEffect, useRef, useState } from 'react'

import { listAsyncInvocations } from '@/shared/api/processes'
import { TIMEOUTS } from '@/shared/constants'
import type { AsyncInvocationResponse, AsyncInvocationStatus } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('AsyncInvocationLedger')
export const INVOCATION_PAGE_SIZE = 10

interface InvocationPage {
  data: AsyncInvocationResponse[]
  total: number
}

const EMPTY_INVOCATION_PAGE: InvocationPage = { data: [], total: 0 }

export interface AsyncInvocationLedgerState {
  page: number
  status?: AsyncInvocationStatus
  processInput: string
  invocations: InvocationPage
  loading: boolean
  stale: boolean
  setPage: (page: number) => void
  setStatus: (status?: AsyncInvocationStatus) => void
  setProcessInput: (processCode: string) => void
  applyProcessFilter: (processCode: string) => void
  loadInvocations: (background?: boolean) => Promise<boolean>
  replaceInvocation: (invocation: AsyncInvocationResponse) => void
}

export function useAsyncInvocationLedger(refreshToken: number): AsyncInvocationLedgerState {
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState<AsyncInvocationStatus | undefined>()
  const [processInput, setProcessInput] = useState('')
  const [processCode, setProcessCode] = useState<string | undefined>()
  const [invocations, setInvocations] = useState<InvocationPage>(EMPTY_INVOCATION_PAGE)
  const [loading, setLoading] = useState(true)
  const [stale, setStale] = useState(false)
  const listGeneration = useRef(0)
  const foregroundLoads = useRef(0)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      listGeneration.current += 1
    }
  }, [])

  const loadInvocations = useCallback(
    async (background = false): Promise<boolean> => {
      if (!mounted.current) return true
      const generation = ++listGeneration.current
      if (!background) {
        foregroundLoads.current += 1
        setLoading(true)
      }
      try {
        const response = await listAsyncInvocations({
          page,
          pageSize: INVOCATION_PAGE_SIZE,
          status,
          processCode,
        })
        if (generation !== listGeneration.current) return true
        setInvocations({ data: response.data, total: response.total })
        setStale(false)
        return true
      } catch (error) {
        if (generation !== listGeneration.current) return true
        logger.warn('Failed to load async invocations', {
          error: toError(error).message,
          page,
          status,
          processCode,
        })
        setStale(true)
        return false
      } finally {
        if (!background) {
          foregroundLoads.current -= 1
          if (mounted.current && foregroundLoads.current === 0) setLoading(false)
        }
      }
    },
    [processCode, page, status]
  )

  useEffect(() => {
    let stopped = false
    let nextPoll: number | undefined

    const poll = async (background: boolean) => {
      await loadInvocations(background)
      if (!stopped) {
        nextPoll = window.setTimeout(() => void poll(true), TIMEOUTS.MONITORING_POLL_INTERVAL)
      }
    }

    void poll(false)
    return () => {
      stopped = true
      if (nextPoll !== undefined) window.clearTimeout(nextPoll)
      listGeneration.current += 1
    }
  }, [loadInvocations, refreshToken])

  const applyProcessFilter = useCallback((value: string) => {
    setPage(1)
    setProcessCode(value.trim() || undefined)
  }, [])

  const replaceInvocation = useCallback(
    (accepted: AsyncInvocationResponse) => {
      if (!mounted.current) return
      setInvocations((current) => {
        const filteredOut = status !== undefined && accepted.status !== status
        return {
          data: filteredOut
            ? current.data.filter((item) => item.invocationId !== accepted.invocationId)
            : current.data.map((item) =>
                item.invocationId === accepted.invocationId ? accepted : item
              ),
          total: filteredOut ? Math.max(0, current.total - 1) : current.total,
        }
      })
    },
    [status]
  )

  return {
    page,
    status,
    processInput,
    invocations,
    loading,
    stale,
    setPage,
    setStatus,
    setProcessInput,
    applyProcessFilter,
    loadInvocations,
    replaceInvocation,
  }
}
