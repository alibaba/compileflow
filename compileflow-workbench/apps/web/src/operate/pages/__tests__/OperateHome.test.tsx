import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getDeployments } from '@/operate/api/deployments'
import { getMetrics } from '@/operate/api/monitoring'
import { useOperateDashboardData } from '@/operate/pages/OperateHome'

vi.mock('@/operate/api/deployments', () => ({ getDeployments: vi.fn() }))
vi.mock('@/operate/api/monitoring', () => ({ getMetrics: vi.fn() }))

describe('Operate home recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('retries every failed dashboard source without a page refresh', async () => {
    const deployment = {
      id: 'deployment-1',
      processCode: 'order-process',
    }
    vi.mocked(getDeployments)
      .mockRejectedValueOnce(new Error('deployments unavailable'))
      .mockResolvedValueOnce({ deployments: [deployment], nextCursor: null } as never)
    vi.mocked(getMetrics)
      .mockRejectedValueOnce(new Error('metrics unavailable'))
      .mockResolvedValueOnce({
        processesWithAliases: 1,
        totalExecutions: 2,
        successExecutions: 2,
        failedExecutions: 0,
      } as never)

    const { result } = renderHook(() => useOperateDashboardData())
    await waitFor(() => expect(result.current.loadFailed).toBe(true))

    act(() => result.current.retry())

    await waitFor(() => expect(result.current.recentDeployments).not.toBeNull())
    expect(result.current.loadFailed).toBe(false)
    expect(result.current.recentDeployments).toEqual([deployment])
    expect(result.current.metrics.map((metric) => metric.value)).toEqual([1, 2, '100.0', 0])
    expect(getDeployments).toHaveBeenCalledTimes(2)
    expect(getMetrics).toHaveBeenCalledTimes(2)
  })
})
