import { act, renderHook, waitFor } from '@testing-library/react'
import type { MessageInstance } from 'antd/es/message/interface'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  abortCanary,
  getDeploymentRoute,
  getDeployments,
  rollbackDeployment,
} from '@/operate/api/deployments'
import { mockDeployments } from '@/operate/api/mockDeploymentData'
import { useDeploymentManagementData } from '@/operate/hooks/useDeploymentManagementData'
import type { DeploymentListResponse, DeploymentRoute } from '@/shared/contracts'

vi.mock('@/operate/api/deployments', () => ({
  abortCanary: vi.fn(),
  getDeploymentRoute: vi.fn(),
  getDeployments: vi.fn(),
  rollbackDeployment: vi.fn(),
}))

const message = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
} as unknown as MessageInstance
const confirm = vi.fn()

vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>()
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({ message, modal: { confirm } }),
    },
  }
})

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

describe('useDeploymentManagementData', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('does not append a stale load-more response after a reload', async () => {
    const loadMore = deferred<DeploymentListResponse>()
    const initial = {
      deployments: [mockDeployments[0]],
      hasMore: true,
      nextCursor: 'next-page',
    }
    const refreshed = {
      deployments: [mockDeployments[1]],
      hasMore: false,
      nextCursor: null,
    }
    vi.mocked(getDeployments)
      .mockResolvedValueOnce(initial)
      .mockReturnValueOnce(loadMore.promise)
      .mockResolvedValueOnce(refreshed)

    const { result } = renderHook(() => useDeploymentManagementData())
    await waitFor(() => expect(result.current.deployments).toEqual(initial.deployments))

    act(() => result.current.loadMore())
    await act(async () => {
      result.current.reload()
      await Promise.resolve()
    })
    await waitFor(() => expect(result.current.deployments).toEqual(refreshed.deployments))

    loadMore.resolve({ deployments: [mockDeployments[2]], hasMore: false, nextCursor: null })
    await act(async () => {
      await loadMore.promise
    })

    expect(result.current.deployments).toEqual(refreshed.deployments)
    expect(result.current.loading).toBe(false)
  })

  it('starts only one load-more request for rapid repeated calls', async () => {
    const nextPage = deferred<DeploymentListResponse>()
    vi.mocked(getDeployments)
      .mockResolvedValueOnce({
        deployments: [mockDeployments[0]],
        hasMore: true,
        nextCursor: 'next-page',
      })
      .mockReturnValueOnce(nextPage.promise)

    const { result } = renderHook(() => useDeploymentManagementData())
    await waitFor(() => expect(result.current.queryReady).toBe(true))

    act(() => {
      result.current.loadMore()
      result.current.loadMore()
    })
    expect(getDeployments).toHaveBeenCalledTimes(2)

    nextPage.resolve({ deployments: [], hasMore: false, nextCursor: null })
    await act(async () => nextPage.promise)
  })

  it('never combines a previous cursor with a newly selected filter', async () => {
    const nextQuery = deferred<DeploymentListResponse>()
    vi.mocked(getDeployments)
      .mockResolvedValueOnce({
        deployments: [mockDeployments[0]],
        hasMore: true,
        nextCursor: 'old-query-cursor',
      })
      .mockReturnValueOnce(nextQuery.promise)

    const { result, rerender } = renderHook<
      ReturnType<typeof useDeploymentManagementData>,
      { keyword: string }
    >(({ keyword }) => useDeploymentManagementData({ keyword }), {
      initialProps: { keyword: 'order' },
    })
    await waitFor(() => expect(result.current.queryReady).toBe(true))

    rerender({ keyword: 'payment' })
    await waitFor(() => expect(getDeployments).toHaveBeenCalledTimes(2))
    expect(result.current.queryReady).toBe(false)
    act(() => result.current.loadMore())
    expect(getDeployments).toHaveBeenCalledTimes(2)
    expect(getDeployments).toHaveBeenLastCalledWith({ keyword: 'payment', limit: 100 })

    nextQuery.resolve({ deployments: [], hasMore: false, nextCursor: null })
    await act(async () => nextQuery.promise)
  })

  it('keeps load failures visible until a retry succeeds', async () => {
    const recovered = {
      deployments: [mockDeployments[0]],
      hasMore: false,
      nextCursor: null,
    }
    vi.mocked(getDeployments)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce(recovered)

    const { result } = renderHook(() => useDeploymentManagementData())
    await waitFor(() => expect(result.current.error).toBe(true))

    await act(async () => {
      result.current.reload()
      await Promise.resolve()
    })
    await waitFor(() => expect(result.current.error).toBe(false))
    expect(result.current.deployments).toEqual(recovered.deployments)
  })

  it('reloads from the first server page when filters change', async () => {
    vi.mocked(getDeployments).mockResolvedValue({
      deployments: [],
      hasMore: false,
      nextCursor: null,
    })

    const { rerender } = renderHook<
      ReturnType<typeof useDeploymentManagementData>,
      { keyword: string; alias: 'production' | 'staging' }
    >(({ keyword, alias }) => useDeploymentManagementData({ keyword, alias }), {
      initialProps: { keyword: 'pay', alias: 'production' },
    })
    await waitFor(() => expect(getDeployments).toHaveBeenCalledTimes(1))
    expect(getDeployments).toHaveBeenLastCalledWith({
      keyword: 'pay',
      alias: 'production',
      limit: 100,
    })

    rerender({ keyword: 'refund', alias: 'staging' })

    await waitFor(() => expect(getDeployments).toHaveBeenCalledTimes(2))
    expect(getDeployments).toHaveBeenLastCalledWith({
      keyword: 'refund',
      alias: 'staging',
      limit: 100,
    })
  })

  it('does not retain results from an earlier filter after the new query fails', async () => {
    const initial = {
      deployments: [mockDeployments[0]],
      hasMore: false,
      nextCursor: null,
    }
    vi.mocked(getDeployments)
      .mockResolvedValueOnce(initial)
      .mockRejectedValueOnce(new Error('unavailable'))

    const { result, rerender } = renderHook<
      ReturnType<typeof useDeploymentManagementData>,
      { keyword: string }
    >(({ keyword }) => useDeploymentManagementData({ keyword }), {
      initialProps: { keyword: 'order' },
    })
    await waitFor(() => expect(result.current.deployments).toEqual(initial.deployments))

    rerender({ keyword: 'refund' })

    await waitFor(() => expect(result.current.error).toBe(true))
    expect(result.current.deployments).toEqual([])
    expect(result.current.hasMore).toBe(false)
  })

  it('does not open an action for a deployment from an earlier query', async () => {
    const deployment = mockDeployments[1]
    const route = deferred<DeploymentRoute | undefined>()
    vi.mocked(getDeployments)
      .mockResolvedValueOnce({ deployments: [deployment], hasMore: false, nextCursor: null })
      .mockResolvedValueOnce({ deployments: [], hasMore: false, nextCursor: null })
    vi.mocked(getDeploymentRoute).mockReturnValue(route.promise)

    const { result, rerender } = renderHook<
      ReturnType<typeof useDeploymentManagementData>,
      { keyword: string }
    >(({ keyword }) => useDeploymentManagementData({ keyword }), {
      initialProps: { keyword: 'payment' },
    })
    await waitFor(() => expect(result.current.deployments).toEqual([deployment]))

    act(() => result.current.handleRestoreBaseline(deployment.id))
    rerender({ keyword: 'refund' })
    await waitFor(() => expect(result.current.deployments).toEqual([]))

    route.resolve({
      processCode: deployment.processCode,
      alias: deployment.alias,
      stableVersion: deployment.baselineVersion!,
      candidateVersion: deployment.version,
      candidateWeightBps: deployment.canaryWeightBps,
      revision: deployment.routeRevision,
      updatedBy: deployment.createdBy,
      updatedAt: Date.parse(deployment.createdAt),
    })
    await act(async () => {
      await route.promise
    })

    expect(confirm).not.toHaveBeenCalled()
  })

  it('does not run a confirmed action after the query changes', async () => {
    const deployment = mockDeployments[1]
    vi.mocked(getDeployments)
      .mockResolvedValueOnce({ deployments: [deployment], hasMore: false, nextCursor: null })
      .mockResolvedValueOnce({ deployments: [], hasMore: false, nextCursor: null })
    vi.mocked(getDeploymentRoute).mockResolvedValue({
      processCode: deployment.processCode,
      alias: deployment.alias,
      stableVersion: deployment.baselineVersion!,
      candidateVersion: deployment.version,
      candidateWeightBps: deployment.canaryWeightBps,
      revision: deployment.routeRevision,
      updatedBy: deployment.createdBy,
      updatedAt: Date.parse(deployment.createdAt),
    })

    const { result, rerender } = renderHook<
      ReturnType<typeof useDeploymentManagementData>,
      { keyword: string }
    >(({ keyword }) => useDeploymentManagementData({ keyword }), {
      initialProps: { keyword: 'payment' },
    })
    await waitFor(() => expect(result.current.deployments).toEqual([deployment]))

    act(() => result.current.handleRestoreBaseline(deployment.id))
    await waitFor(() => expect(confirm).toHaveBeenCalledOnce())
    const onOk = confirm.mock.calls[0]?.[0]?.onOk as (() => Promise<void>) | undefined
    expect(onOk).toBeDefined()

    rerender({ keyword: 'refund' })
    await waitFor(() => expect(result.current.deployments).toEqual([]))
    await act(async () => onOk?.())

    expect(abortCanary).not.toHaveBeenCalled()
    expect(rollbackDeployment).not.toHaveBeenCalled()
  })
})
