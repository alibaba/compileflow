import { act, renderHook, waitFor } from '@testing-library/react'
import type { MessageInstance } from 'antd/es/message/interface'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import {
  evaluateCanaryHealth,
  getDeployment,
  getDeploymentEvents,
  getDeploymentRoute,
  promoteCanary,
  rollbackDeployment,
} from '@/operate/api/deployments'
import { useDeploymentDetailData } from '@/operate/hooks/useDeploymentDetailData'
import type {
  CanaryHealthEvaluationResponse,
  Deployment,
  DeploymentEvent,
  DeploymentRoute,
} from '@/shared/contracts'
import i18n from '@/shared/i18n'

vi.mock('@/operate/api/deployments', () => ({
  abortCanary: vi.fn(),
  evaluateCanaryHealth: vi.fn(),
  getDeployment: vi.fn(),
  getDeploymentEvents: vi.fn(),
  getDeploymentRoute: vi.fn(),
  promoteCanary: vi.fn(),
  rollbackDeployment: vi.fn(),
  updateCanaryWeightBps: vi.fn(),
}))

const message: MessageInstance = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  info: vi.fn(),
  loading: vi.fn(),
  open: vi.fn(),
  destroy: vi.fn(),
  context: null as unknown,
} as unknown as MessageInstance

vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>()
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({ message }),
    },
  }
})

const canary: Deployment = {
  id: 'rollout-2',
  processCode: 'order.approve',
  version: 'v2',
  baselineVersion: 'v1',
  alias: 'production',
  operation: 'deploy',
  status: 'in_progress',
  strategy: 'canary',
  canaryWeightBps: 5_000,
  revision: 1,
  baseRouteRevision: 1,
  routeRevision: 2,
  createdAt: '2026-08-02T00:00:00Z',
  createdBy: 'operator',
}

const route: DeploymentRoute = {
  processCode: canary.processCode,
  alias: canary.alias,
  stableVersion: 'v1',
  candidateVersion: 'v2',
  candidateWeightBps: 5_000,
  revision: 2,
  updatedBy: 'operator',
  updatedAt: Date.parse(canary.createdAt),
}

const started: DeploymentEvent = {
  id: 1,
  sequence: 1,
  type: 'CANARY_STARTED',
  fromPhase: null,
  toPhase: 'in_progress',
  actor: 'operator',
  reason: null,
  timestamp: canary.createdAt,
}

const translate = i18n.t.bind(i18n)

function renderDetail() {
  return renderHook(() => useDeploymentDetailData(canary.id, translate))
}

describe('useDeploymentDetailData', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getDeployment).mockResolvedValue(canary)
    vi.mocked(getDeploymentEvents).mockResolvedValue([started])
    vi.mocked(getDeploymentRoute).mockResolvedValue(route)
  })

  it('keeps an initial load failure visible and allows retrying it', async () => {
    vi.mocked(getDeployment).mockRejectedValueOnce(new Error('missing deployment'))
    const { result } = renderDetail()

    await waitFor(() => expect(result.current.loadError).toBe(true))
    expect(result.current.deployment).toBeNull()
    expect(message.error).toHaveBeenCalled()

    await act(async () => {
      await result.current.reload()
    })

    await waitFor(() => expect(result.current.loadError).toBe(false))
    expect(result.current.deployment).toEqual(canary)
  })

  it('ignores a slower response after navigating to another deployment', async () => {
    let resolveFirst: (deployment: Deployment) => void = () => undefined
    const first = new Promise<Deployment>((resolve) => {
      resolveFirst = resolve
    })
    const next = { ...canary, id: 'rollout-3', processCode: 'invoice.approve' }
    vi.mocked(getDeployment).mockImplementation((id) =>
      id === canary.id ? first : Promise.resolve(next)
    )
    vi.mocked(getDeploymentEvents).mockResolvedValue([])
    vi.mocked(getDeploymentRoute).mockImplementation((processCode) =>
      Promise.resolve({ ...route, processCode })
    )
    const { result, rerender } = renderHook(
      ({ id }: { id: string }) => useDeploymentDetailData(id, translate),
      { initialProps: { id: canary.id } }
    )

    rerender({ id: next.id })
    await waitFor(() => expect(result.current.deployment).toEqual(next))

    resolveFirst(canary)
    await act(async () => {
      await first
    })

    expect(result.current.deployment).toEqual(next)
  })

  it('does not apply a completed action after navigating to another deployment', async () => {
    let resolvePromotion: (deployment: Deployment) => void = () => undefined
    const promotion = new Promise<Deployment>((resolve) => {
      resolvePromotion = resolve
    })
    const promoted = { ...canary, status: 'completed' as const, revision: 2 }
    const next = { ...canary, id: 'rollout-3', processCode: 'invoice.approve' }
    vi.mocked(promoteCanary).mockReturnValue(promotion)
    vi.mocked(getDeployment).mockImplementation((id) =>
      Promise.resolve(id === next.id ? next : canary)
    )
    vi.mocked(getDeploymentEvents).mockResolvedValue([])
    vi.mocked(getDeploymentRoute).mockImplementation((processCode) =>
      Promise.resolve({ ...route, processCode })
    )
    const { result, rerender } = renderHook(
      ({ id }: { id: string }) => useDeploymentDetailData(id, translate),
      { initialProps: { id: canary.id } }
    )
    await waitFor(() => expect(result.current.deployment).toEqual(canary))

    let pending!: Promise<Deployment | undefined>
    act(() => {
      pending = result.current.promote()
    })
    rerender({ id: next.id })
    await waitFor(() => expect(result.current.deployment).toEqual(next))

    resolvePromotion(promoted)
    await act(async () => {
      await pending
    })

    expect(result.current.deployment).toEqual(next)
    expect(result.current.action).toBeUndefined()
    expect(message.success).not.toHaveBeenCalled()
  })

  it('does not report a completed action after the detail view unmounts', async () => {
    let resolvePromotion: (deployment: Deployment) => void = () => undefined
    const promotion = new Promise<Deployment>((resolve) => {
      resolvePromotion = resolve
    })
    vi.mocked(promoteCanary).mockReturnValue(promotion)
    const { result, unmount } = renderDetail()
    await waitFor(() => expect(result.current.deployment).toEqual(canary))

    let pending!: Promise<Deployment | undefined>
    act(() => {
      pending = result.current.promote()
    })
    unmount()
    resolvePromotion({ ...canary, status: 'completed', revision: 2 })
    await act(async () => {
      await pending
    })

    expect(message.success).not.toHaveBeenCalled()
  })

  it('refreshes the deployment, route, and audit events after promotion', async () => {
    const promoted: Deployment = {
      ...canary,
      status: 'completed',
      canaryWeightBps: undefined,
      revision: 2,
      routeRevision: 3,
      deployedAt: '2026-08-02T00:01:00Z',
    }
    const promotedEvent: DeploymentEvent = {
      ...started,
      id: 2,
      sequence: 2,
      type: 'PROMOTED',
      fromPhase: 'in_progress',
      toPhase: 'completed',
    }
    vi.mocked(promoteCanary).mockResolvedValue(promoted)
    vi.mocked(getDeploymentEvents)
      .mockResolvedValueOnce([started])
      .mockResolvedValueOnce([started, promotedEvent])
    vi.mocked(getDeploymentRoute)
      .mockResolvedValueOnce(route)
      .mockResolvedValueOnce({
        ...route,
        stableVersion: 'v2',
        candidateVersion: undefined,
        candidateWeightBps: undefined,
        revision: 3,
      })
    const { result } = renderDetail()
    await waitFor(() => expect(result.current.deployment).toEqual(canary))

    await act(async () => {
      await result.current.promote()
    })

    expect(promoteCanary).toHaveBeenCalledWith(canary.id, canary.revision)
    expect(result.current.deployment).toEqual(promoted)
    expect(result.current.events).toEqual([started, promotedEvent])
    expect(result.current.route?.stableVersion).toBe('v2')
    expect(message.success).toHaveBeenCalledOnce()
  })

  it('does not restore a baseline after a newer deployment takes ownership of the route', async () => {
    vi.mocked(getDeploymentRoute).mockResolvedValue({ ...route, revision: 3 })
    const { result } = renderDetail()
    await waitFor(() => expect(result.current.route?.revision).toBe(3))

    await act(async () => {
      await result.current.restoreBaseline()
    })

    expect(rollbackDeployment).not.toHaveBeenCalled()
    expect(message.error).toHaveBeenCalled()
  })

  it('keeps the evaluated health evidence with the current canary view', async () => {
    const health: CanaryHealthEvaluationResponse = {
      deployment: canary,
      metricsScope: 'workbench_server',
      metricsSource: 'execution_logs',
      decision: 'healthy',
      reason: 'Canary metrics are within configured thresholds.',
      canary: { version: 'v2', samples: 20, failures: 0, errorRate: 0, p95DurationMs: 15 },
      baseline: { version: 'v1', samples: 20, failures: 0, errorRate: 0, p95DurationMs: 16 },
      thresholds: {
        lookbackMs: 600_000,
        minCanarySamples: 20,
        maxCanaryErrorRate: 0.05,
        maxCanaryP95Ms: 0,
      },
    }
    vi.mocked(evaluateCanaryHealth).mockResolvedValue(health)
    const { result } = renderDetail()
    await waitFor(() => expect(result.current.deployment).toEqual(canary))

    await act(async () => {
      await result.current.evaluateHealth()
    })

    expect(result.current.health).toEqual(health)
  })
})
