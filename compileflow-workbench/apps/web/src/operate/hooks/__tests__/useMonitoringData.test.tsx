import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { useMonitoringData } from '../useMonitoringData'

import { getDeploymentControlHealth } from '@/operate/api/deployments'
import {
  getDeployRuntimeDiagnostics,
  getExecutionTrends,
  getMetrics,
  getRecentErrors,
  getTopProcesses,
  getVersionDistribution,
} from '@/operate/api/monitoring'
import { getAsyncInvocationHealth } from '@/shared/api/processes'
import { TIMEOUTS } from '@/shared/constants'
import type { MonitoringMetrics, MonitoringTimeRange } from '@/shared/contracts'

vi.mock('@/operate/api/deployments', () => ({ getDeploymentControlHealth: vi.fn() }))
vi.mock('@/shared/api/processes', () => ({ getAsyncInvocationHealth: vi.fn() }))
vi.mock('@/operate/api/monitoring', () => ({
  getDeployRuntimeDiagnostics: vi.fn(),
  getExecutionTrends: vi.fn(),
  getMetrics: vi.fn(),
  getRecentErrors: vi.fn(),
  getTopProcesses: vi.fn(),
  getVersionDistribution: vi.fn(),
}))

const metrics: MonitoringMetrics = {
  scope: 'workbench_server',
  totalExecutions: 1,
  successExecutions: 1,
  failedExecutions: 0,
  avgExecutionTime: 1,
  processesWithAliases: 1,
  timeRange: '24h',
  windowStart: '2026-08-01T00:00:00Z',
  windowEnd: '2026-08-02T00:00:00Z',
  timestamp: '2026-08-02T00:00:00Z',
}

describe('useMonitoringData polling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
    vi.mocked(getExecutionTrends).mockResolvedValue([])
    vi.mocked(getTopProcesses).mockResolvedValue([])
    vi.mocked(getRecentErrors).mockResolvedValue([])
    vi.mocked(getVersionDistribution).mockResolvedValue([])
    vi.mocked(getDeployRuntimeDiagnostics).mockResolvedValue({
      available: false,
      started: false,
      message: 'not configured',
      timestamp: '2026-08-02T00:00:00Z',
    })
    vi.mocked(getDeploymentControlHealth).mockResolvedValue({
      status: 'UP',
      pendingCount: 0,
      processingCount: 0,
      expiredClaimCount: 0,
      failedCount: 0,
      dispatcherRunning: true,
      outboxStateAvailable: true,
      checkedAt: '2026-08-02T00:00:00Z',
    })
    vi.mocked(getAsyncInvocationHealth).mockResolvedValue({
      status: 'healthy',
      queuedCount: 0,
      readyQueuedCount: 0,
      oldestReadyAgeMs: 0,
      delayedQueuedCount: 0,
      runningCount: 0,
      succeededCount: 0,
      deadLetterCount: 0,
      expiredRunningCount: 0,
      localRunningCount: 0,
      dispatchedCount: 0,
      workerId: 'worker-test',
      leaseDurationMs: 30000,
      dispatchBatchSize: 50,
      checkedAt: '2026-08-02T00:00:00Z',
    })
  })

  afterEach(() => vi.useRealTimers())

  it('waits for a refresh to finish before scheduling the next poll', async () => {
    let resolveMetrics: (value: MonitoringMetrics) => void = () => undefined
    const firstMetrics = new Promise<MonitoringMetrics>((resolve) => {
      resolveMetrics = resolve
    })
    vi.mocked(getMetrics).mockReturnValueOnce(firstMetrics).mockResolvedValue(metrics)

    renderHook(() => useMonitoringData('24h'))
    expect(getMetrics).toHaveBeenCalledOnce()

    await act(async () => {
      await vi.advanceTimersByTimeAsync(TIMEOUTS.MONITORING_POLL_INTERVAL * 2)
    })
    expect(getMetrics).toHaveBeenCalledOnce()

    await act(async () => {
      resolveMetrics(metrics)
      await firstMetrics
    })
    await act(async () => {
      await vi.advanceTimersByTimeAsync(TIMEOUTS.MONITORING_POLL_INTERVAL)
    })

    expect(getMetrics).toHaveBeenCalledTimes(2)
  })

  it('does not let an obsolete range request keep the page loading', async () => {
    let resolveOldMetrics: (value: MonitoringMetrics) => void = () => undefined
    const oldMetrics = new Promise<MonitoringMetrics>((resolve) => {
      resolveOldMetrics = resolve
    })
    vi.mocked(getMetrics).mockReturnValueOnce(oldMetrics).mockResolvedValue(metrics)

    const { result, rerender } = renderHook(({ range }) => useMonitoringData(range), {
      initialProps: { range: '24h' as MonitoringTimeRange },
    })
    await act(async () => {
      rerender({ range: '7d' })
      await Promise.resolve()
      await Promise.resolve()
    })

    expect(getMetrics).toHaveBeenCalledTimes(2)
    expect(result.current.loading).toBe(false)

    await act(async () => {
      resolveOldMetrics(metrics)
      await oldMetrics
    })
    expect(result.current.loading).toBe(false)
  })
})
