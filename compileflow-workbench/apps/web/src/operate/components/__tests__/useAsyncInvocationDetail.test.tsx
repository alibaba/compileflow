import { act, renderHook } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useAsyncInvocationDetail } from '../useAsyncInvocationDetail'

import { getAsyncInvocation, listAsyncInvocationAttempts } from '@/shared/api/processes'
import type { AsyncInvocationResponse } from '@/shared/contracts'

vi.mock('@/shared/api/processes', () => ({
  getAsyncInvocation: vi.fn(),
  listAsyncInvocationAttempts: vi.fn(),
}))

const first: AsyncInvocationResponse = {
  invocationId: 'first',
  processCode: 'order',
  status: 'dead_letter',
  currentAttemptCount: 1,
  totalAttemptCount: 1,
  redriveCount: 0,
  maxAttempts: 1,
  retryDelayMs: 1000,
  createdAt: '2026-09-07T00:00:00Z',
  updatedAt: '2026-09-07T00:00:00Z',
}
const second = { ...first, invocationId: 'second' }

describe('async invocation detail ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getAsyncInvocation).mockImplementation(async (id) =>
      id === first.invocationId ? first : second
    )
    vi.mocked(listAsyncInvocationAttempts).mockResolvedValue({ data: [], hasMore: false })
  })

  it('ignores a detail response arriving after a newer selection', async () => {
    let finish!: (value: AsyncInvocationResponse) => void
    vi.mocked(getAsyncInvocation).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    const { result } = renderHook(useAsyncInvocationDetail)
    act(() => result.current.inspectInvocation(first))
    await act(async () => result.current.inspectInvocation(second))
    await act(async () => finish(first))
    expect(result.current.selected).toEqual(second)
    expect(result.current.detailLoading).toBe(false)
  })

  it('ignores an attempt page arriving after the drawer closes', async () => {
    vi.mocked(listAsyncInvocationAttempts).mockResolvedValueOnce({
      data: [],
      hasMore: true,
      nextAfterSequence: 1,
    })
    const { result } = renderHook(useAsyncInvocationDetail)
    await act(async () => result.current.inspectInvocation(first))
    let finish!: (value: { data: []; hasMore: boolean }) => void
    vi.mocked(listAsyncInvocationAttempts).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve
      })
    )
    let loading!: Promise<void>
    act(() => {
      loading = result.current.loadMoreAttempts()
    })
    act(() => result.current.closeDetail())
    await act(async () => {
      finish({ data: [], hasMore: false })
      await loading
    })
    expect(result.current.attemptPage.hasMore).toBe(true)
    expect(result.current.loadingMore).toBe(false)
    expect(result.current.detailOpen).toBe(false)
  })

  it('ignores mutation callbacks for a record that is no longer selected', async () => {
    const { result } = renderHook(useAsyncInvocationDetail)
    await act(async () => result.current.inspectInvocation(first))
    const pendingOperationDetail = result.current
    await act(async () => result.current.inspectInvocation(second))

    await act(async () => {
      pendingOperationDetail.replaceSelected({ ...first, status: 'queued' })
      await pendingOperationDetail.loadDetail(first.invocationId)
    })

    expect(result.current.selected).toEqual(second)
    expect(getAsyncInvocation).toHaveBeenCalledTimes(2)
  })

  it('does not restart detail requests after the drawer is closed', async () => {
    const { result } = renderHook(useAsyncInvocationDetail)
    await act(async () => result.current.inspectInvocation(first))
    const pendingOperationDetail = result.current
    act(() => result.current.closeDetail())

    await act(async () => {
      pendingOperationDetail.replaceSelected({ ...first, status: 'queued' })
      await pendingOperationDetail.loadDetail(first.invocationId)
    })

    expect(result.current.detailOpen).toBe(false)
    expect(getAsyncInvocation).toHaveBeenCalledOnce()
  })
})
