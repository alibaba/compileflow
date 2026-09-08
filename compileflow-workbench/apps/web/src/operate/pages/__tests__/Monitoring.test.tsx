import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import Monitoring from '../Monitoring'

import { getDeploymentControlHealth, requeueDeploymentDeadLetters } from '@/operate/api/deployments'
import {
  getDeploymentRuntimeDiagnostics,
  getExecutionTrends,
  getMetrics,
  getRecentErrors,
  getTopProcesses,
  getVersionDistribution,
} from '@/operate/api/monitoring'
import {
  getAsyncInvocation,
  getAsyncInvocationHealth,
  listAsyncInvocationAttempts,
  listAsyncInvocations,
  requeueAsyncInvocation,
  requeueAsyncInvocationDeadLetters,
} from '@/shared/api/processes'
import { ThemeProvider } from '@/shared/contexts/ThemeContext'
import type { AsyncInvocationResponse } from '@/shared/contracts'

vi.mock('@/operate/api/deployments', () => ({
  getDeploymentControlHealth: vi.fn(),
  requeueDeploymentDeadLetters: vi.fn(),
}))
vi.mock('@/shared/api/processes', () => ({
  getAsyncInvocation: vi.fn(),
  getAsyncInvocationHealth: vi.fn(),
  listAsyncInvocationAttempts: vi.fn(),
  listAsyncInvocations: vi.fn(),
  requeueAsyncInvocation: vi.fn(),
  requeueAsyncInvocationDeadLetters: vi.fn(),
}))
vi.mock('@/operate/api/monitoring', () => ({
  getDeploymentRuntimeDiagnostics: vi.fn(),
  getExecutionTrends: vi.fn(),
  getMetrics: vi.fn(),
  getRecentErrors: vi.fn(),
  getTopProcesses: vi.fn(),
  getVersionDistribution: vi.fn(),
}))

const deploymentHealth = {
  status: 'UP' as const,
  pendingCount: 7,
  processingCount: 1,
  expiredClaimCount: 0,
  failedCount: 2,
  dispatcherRunning: true,
  outboxStateAvailable: true,
  checkedAt: '2026-08-02T00:00:00Z',
}

const asyncHealth = {
  status: 'healthy' as const,
  queuedCount: 3,
  readyQueuedCount: 2,
  oldestReadyAgeMs: 2500,
  delayedQueuedCount: 1,
  runningCount: 1,
  succeededCount: 10,
  deadLetterCount: 0,
  expiredRunningCount: 0,
  localRunningCount: 1,
  dispatchedCount: 1,
  workerId: 'worker-test',
  leaseDurationMs: 30000,
  concurrency: 4,
  checkedAt: '2026-08-02T00:00:00Z',
}

const deadLetterInvocation: AsyncInvocationResponse = {
  invocationId: 'order-async-42',
  processCode: 'order.fulfill',
  status: 'dead_letter',
  currentAttemptCount: 1,
  totalAttemptCount: 1,
  redriveCount: 0,
  maxAttempts: 1,
  retryDelayMs: 1000,
  createdAt: '2026-08-02T00:00:00Z',
  updatedAt: '2026-08-02T00:01:00Z',
  startedAt: '2026-08-02T00:00:10Z',
  completedAt: '2026-08-02T00:01:00Z',
  routing: {
    namespace: 'default',
    requestedAlias: 'PROD',
    effectiveVersion: '17',
  },
  errorCode: 'LEASE_EXPIRED',
  error: 'Async invocation lease expired',
}

const queuedRedrive: AsyncInvocationResponse = {
  ...deadLetterInvocation,
  status: 'queued',
  currentAttemptCount: 0,
  redriveCount: 1,
  updatedAt: '2026-08-02T00:02:00Z',
  nextAttemptAt: '2026-08-02T00:02:00Z',
  startedAt: undefined,
  completedAt: undefined,
  errorCode: undefined,
  error: undefined,
}

function renderMonitoring() {
  return render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <MemoryRouter>
        <ThemeProvider>
          <Monitoring />
        </ThemeProvider>
      </MemoryRouter>
    </ConfigProvider>
  )
}

async function confirmOperation(title: string) {
  expect((await screen.findAllByText(title)).length).toBeGreaterThan(0)
  await act(async () => {
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /确\s*认/ }))
  })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((accept) => {
    resolve = accept
  })
  return { promise, resolve }
}

describe('Monitoring operations control plane', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getMetrics).mockResolvedValue({
      scope: 'workbench_server',
      totalExecutions: 10,
      successExecutions: 9,
      failedExecutions: 1,
      avgExecutionTime: 12,
      processesWithAliases: 2,
      timeRange: '24h',
      windowStart: '2026-08-01T00:00:00Z',
      windowEnd: '2026-08-02T00:00:00Z',
      timestamp: '2026-08-02T00:00:00Z',
    })
    vi.mocked(getExecutionTrends).mockResolvedValue([])
    vi.mocked(getTopProcesses).mockResolvedValue([])
    vi.mocked(getRecentErrors).mockResolvedValue([])
    vi.mocked(getVersionDistribution).mockResolvedValue([])
    vi.mocked(getDeploymentRuntimeDiagnostics).mockResolvedValue({
      available: false,
      started: false,
      message: 'Deploy runtime is not configured',
      timestamp: '2026-08-02T00:00:00Z',
    })
    vi.mocked(getDeploymentControlHealth).mockResolvedValue(deploymentHealth)
    vi.mocked(getAsyncInvocationHealth).mockResolvedValue(asyncHealth)
    vi.mocked(listAsyncInvocations).mockResolvedValue({
      data: [deadLetterInvocation],
      total: 1,
      page: 1,
      pageSize: 10,
    })
    vi.mocked(getAsyncInvocation).mockResolvedValue(deadLetterInvocation)
    vi.mocked(listAsyncInvocationAttempts).mockResolvedValue({
      data: [
        {
          attemptId: 'attempt-1',
          invocationId: deadLetterInvocation.invocationId,
          sequence: 1,
          redriveCount: 0,
          attemptNumber: 1,
          workerId: 'worker-crashed',
          outcome: 'lease_expired',
          disposition: 'dead_lettered',
          startedAt: '2026-08-02T00:00:10Z',
          finishedAt: '2026-08-02T00:01:00Z',
          errorCode: 'LEASE_EXPIRED',
          error: 'Async invocation lease expired',
        },
      ],
      hasMore: false,
    })
    vi.mocked(requeueAsyncInvocation).mockResolvedValue(queuedRedrive)
    vi.mocked(requeueDeploymentDeadLetters).mockResolvedValue({
      requeued: 2,
      requeuedAt: '2026-08-02T00:00:01Z',
      health: deploymentHealth,
    })
    vi.mocked(requeueAsyncInvocationDeadLetters).mockResolvedValue({
      requeued: 0,
      requeuedAt: '2026-08-02T00:00:01Z',
      limit: 100,
      invocationIds: [],
      health: asyncHealth,
    })
  })

  it('keeps operations data available when an independent metrics source fails', async () => {
    vi.mocked(getMetrics).mockRejectedValueOnce(new Error('metrics unavailable'))

    renderMonitoring()

    expect(await screen.findByText('部分监控数据刷新失败')).toBeInTheDocument()
    expect(screen.getByText(/不可用的数据源：执行指标/)).toBeInTheDocument()
    expect(screen.getByText('部署任务投递中')).toBeInTheDocument()
    expect(screen.getByText('异步调用的持久化重试队列')).toBeInTheDocument()
    expect(screen.getByText('2.5 s')).toBeInTheDocument()
  })

  it('retries every failed monitoring source without waiting for the next poll', async () => {
    vi.mocked(getMetrics).mockRejectedValueOnce(new Error('metrics unavailable'))

    renderMonitoring()
    expect(await screen.findByText('部分监控数据刷新失败')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }))

    await waitFor(() => expect(getMetrics).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByText('部分监控数据刷新失败')).not.toBeInTheDocument())
  })

  it('refreshes deployment health after an operator requeues dead letters', async () => {
    vi.mocked(getDeploymentControlHealth)
      .mockResolvedValueOnce(deploymentHealth)
      .mockResolvedValue({
        ...deploymentHealth,
        pendingCount: 0,
        failedCount: 0,
        dispatcherRunning: false,
      })

    renderMonitoring()
    const requeue = await screen.findByRole('button', { name: '重新入队部署死信任务' })
    fireEvent.click(requeue)
    fireEvent.click(requeue)
    expect(requeueDeploymentDeadLetters).not.toHaveBeenCalled()
    expect(await screen.findAllByRole('dialog')).toHaveLength(1)
    await confirmOperation('确认重新入队全部部署死信任务？')

    await waitFor(() => expect(requeueDeploymentDeadLetters).toHaveBeenCalledOnce())
    await waitFor(() => expect(getDeploymentControlHealth).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('部署任务投递已停止')).toBeInTheDocument()
    expect(screen.getByText('否')).toBeInTheDocument()
  })

  it('localizes the deployment dispatcher state', async () => {
    renderMonitoring()

    expect(await screen.findByText('部署任务投递中')).toBeInTheDocument()
    expect(screen.getByText('是')).toBeInTheDocument()
    expect(screen.queryByText('on')).not.toBeInTheDocument()
  })

  it('localizes runtime status and topology values instead of exposing internal enums', async () => {
    vi.mocked(getDeploymentRuntimeDiagnostics).mockResolvedValue({
      available: true,
      started: true,
      topology: 'embedded',
      inflightCount: 0,
      inflightCapacity: 10,
      inflightAvailablePermits: 10,
      failureBackoffMs: 300_000,
      retainedRuntimeCount: 0,
      desiredAliasCount: 0,
      localReadyAliasCount: 0,
      pendingAliasCount: 0,
      failedAliasCount: 0,
      aliases: [],
      demandedVersions: [],
      backedOffVersions: [],
      deployedVersions: [],
      inflightVersions: [],
      pendingReleaseVersions: [],
      timestamp: '2026-08-02T00:00:00Z',
    })

    renderMonitoring()

    expect(await screen.findByText('内嵌')).toBeInTheDocument()
    expect(screen.getAllByText('健康').length).toBeGreaterThan(0)
    expect(screen.queryByText('embedded')).not.toBeInTheDocument()
  })

  it('refreshes the invocation ledger after a bounded dead-letter redrive', async () => {
    renderMonitoring()
    expect(await screen.findByText('order.fulfill')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '重新入队异步调用死信' }))
    expect(requeueAsyncInvocationDeadLetters).not.toHaveBeenCalled()
    await confirmOperation('确认重新入队全部异步调用死信？')

    await waitFor(() => expect(requeueAsyncInvocationDeadLetters).toHaveBeenCalledOnce())
    await waitFor(() => expect(listAsyncInvocations).toHaveBeenCalledTimes(2))
  })

  it('marks the last known deployment health as stale when post-operation refresh fails', async () => {
    vi.mocked(getDeploymentControlHealth)
      .mockResolvedValueOnce(deploymentHealth)
      .mockRejectedValue(new Error('health unavailable'))

    renderMonitoring()
    expect(await screen.findByText('部署任务投递中')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '重新入队部署死信任务' }))
    await confirmOperation('确认重新入队全部部署死信任务？')

    expect(await screen.findByText('无法刷新，当前显示最近可用数据')).toBeInTheDocument()
    expect(screen.getByText('部署任务投递中')).toBeInTheDocument()
    expect(screen.getByText(/不可用的数据源：部署任务队列/)).toBeInTheDocument()
  })

  it('cancels a batch dead-letter requeue without changing server state', async () => {
    renderMonitoring()
    fireEvent.click(await screen.findByRole('button', { name: '重新入队异步调用死信' }))
    expect((await screen.findAllByText('确认重新入队全部异步调用死信？')).length).toBeGreaterThan(0)
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: /取\s*消/ }))

    expect(requeueAsyncInvocationDeadLetters).not.toHaveBeenCalled()
  })

  it('does not refresh or notify after leaving during a batch requeue', async () => {
    const request = deferred<Awaited<ReturnType<typeof requeueDeploymentDeadLetters>>>()
    vi.mocked(requeueDeploymentDeadLetters).mockReturnValue(request.promise)
    const { unmount } = renderMonitoring()
    fireEvent.click(await screen.findByRole('button', { name: '重新入队部署死信任务' }))
    await confirmOperation('确认重新入队全部部署死信任务？')
    await waitFor(() => expect(requeueDeploymentDeadLetters).toHaveBeenCalledOnce())

    unmount()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    request.resolve({
      requeued: 2,
      requeuedAt: '2026-08-02T00:00:01Z',
      health: deploymentHealth,
    })
    await request.promise

    expect(getDeploymentControlHealth).toHaveBeenCalledOnce()
    expect(screen.queryByText('部署死信任务已重新入队')).not.toBeInTheDocument()
    expect(screen.queryByText('操作失败')).not.toBeInTheDocument()
    expect(screen.queryByText('操作已完成，但状态刷新失败')).not.toBeInTheDocument()
  })

  it('shows the physical attempt ledger even when an attempt produced no execution trace', async () => {
    renderMonitoring()

    expect(await screen.findByText('order.fulfill')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /查看/ }))

    expect(await screen.findByText(/worker-crashed/)).toBeInTheDocument()
    expect(screen.getAllByText('LEASE_EXPIRED').length).toBeGreaterThan(0)
    expect(screen.getByText('租约过期')).toBeInTheDocument()
    expect(screen.queryByText(/^trace:/)).not.toBeInTheDocument()
    expect(listAsyncInvocationAttempts).toHaveBeenCalledWith('order-async-42', { limit: 20 })
  })

  it('requires confirmation before redriving one dead-letter invocation', async () => {
    renderMonitoring()
    fireEvent.click(await screen.findByRole('button', { name: /查看/ }))
    fireEvent.click(await screen.findByRole('button', { name: '重新入队' }))
    expect(await screen.findByText('是否重新入队这条死信调用？')).toBeInTheDocument()
    const confirm = screen.getByRole('button', { name: /确\s*认/ })
    fireEvent.click(confirm)
    fireEvent.click(confirm)

    await waitFor(() => expect(requeueAsyncInvocation).toHaveBeenCalledWith('order-async-42'))
    expect(requeueAsyncInvocation).toHaveBeenCalledOnce()
    await waitFor(() => expect(getAsyncInvocationHealth).toHaveBeenCalledTimes(2))
  })

  it('keeps the last successful invocation page when a manual refresh fails', async () => {
    vi.mocked(listAsyncInvocations)
      .mockResolvedValueOnce({ data: [deadLetterInvocation], total: 1, page: 1, pageSize: 10 })
      .mockRejectedValue(new Error('ledger unavailable'))

    renderMonitoring()
    expect(await screen.findByText('order.fulfill')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /刷新/ }))

    expect(await screen.findByText('调用记录刷新失败，当前显示最近可用数据')).toBeInTheDocument()
    expect(screen.getByText('order.fulfill')).toBeInTheDocument()
  })
})
