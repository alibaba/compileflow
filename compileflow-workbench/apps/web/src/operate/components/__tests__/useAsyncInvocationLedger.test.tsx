import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useAsyncInvocationLedger } from '../useAsyncInvocationLedger'

import { listAsyncInvocations } from '@/shared/api/processes'
import { TIMEOUTS } from '@/shared/constants'
import type { AsyncInvocationListResponse } from '@/shared/contracts'

vi.mock('@/shared/api/processes', () => ({ listAsyncInvocations: vi.fn() }))

const emptyPage: AsyncInvocationListResponse = {
  data: [],
  total: 0,
  page: 1,
  pageSize: 10,
}

describe('useAsyncInvocationLedger polling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => vi.useRealTimers())

  it('waits for the current request before scheduling another poll', async () => {
    let resolveFirst: (value: AsyncInvocationListResponse) => void = () => undefined
    const firstRequest = new Promise<AsyncInvocationListResponse>((resolve) => {
      resolveFirst = resolve
    })
    vi.mocked(listAsyncInvocations).mockReturnValueOnce(firstRequest).mockResolvedValue(emptyPage)

    renderHook(() => useAsyncInvocationLedger(0))
    expect(listAsyncInvocations).toHaveBeenCalledOnce()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(TIMEOUTS.MONITORING_POLL_INTERVAL * 2)
    })
    expect(listAsyncInvocations).toHaveBeenCalledOnce()

    await act(async () => {
      resolveFirst(emptyPage)
      await firstRequest
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TIMEOUTS.MONITORING_POLL_INTERVAL)
    })

    expect(listAsyncInvocations).toHaveBeenCalledTimes(2)
  })
})
