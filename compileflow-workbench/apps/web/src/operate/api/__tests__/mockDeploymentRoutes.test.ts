import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

describe('mock deployment route persistence', () => {
  beforeEach(() => vi.resetModules())

  it('rejects stale route revisions without changing the route', async () => {
    const api = await import('../deployments')
    const before = await api.getDeploymentRoute('order-approval-bpmn', 'production')
    await expect(
      api.createDeployment({
        processCode: 'order-approval-bpmn',
        version: '1.3.0',
        alias: 'production',
        expectedRouteRevision: 0,
        idempotencyKey: 'stale',
      })
    ).rejects.toThrow(/revision/i)
    expect(await api.getDeploymentRoute('order-approval-bpmn', 'production')).toEqual(before)
  })

  it('replays a deployment intent once and rejects reuse with different content', async () => {
    const api = await import('../deployments')
    const request = {
      processCode: 'order-approval-bpmn',
      version: '1.3.0',
      alias: 'production',
      expectedRouteRevision: 1,
      idempotencyKey: 'replay',
    }
    const first = await api.createDeployment(request)
    expect(await api.createDeployment(request)).toEqual(first)
    await expect(api.createDeployment({ ...request, version: '1.4.0' })).rejects.toThrow(
      /idempotency/i
    )
    expect(
      (await api.getDeployments()).deployments.filter((item) => item.id === first.id)
    ).toHaveLength(1)
  })

  it.each(['abortCanary', 'promoteCanary', 'updateCanaryWeightBps'] as const)(
    'rejects stale deployment revision for %s',
    async (operation) => {
      const api = await import('../deployments')
      const before = await api.getDeploymentRoute('payment-process-tbbpm', 'production')
      const result =
        operation === 'updateCanaryWeightBps'
          ? api[operation]('deploy-002', 3000, 1)
          : api[operation]('deploy-002', 1)
      await expect(result).rejects.toThrow(/revision/i)
      expect(await api.getDeploymentRoute('payment-process-tbbpm', 'production')).toEqual(before)
    }
  )

  it('rejects canary mutation after another release replaces its route', async () => {
    const api = await import('../deployments')
    await api.createDeployment({
      processCode: 'payment-process-tbbpm',
      version: '3.0',
      alias: 'production',
      expectedRouteRevision: 8,
      idempotencyKey: 'replacement',
    })
    await expect(api.promoteCanary('deploy-002', 2)).rejects.toThrow(/revision|route/i)
    expect(await api.getDeploymentRoute('payment-process-tbbpm', 'production')).toMatchObject({
      stableVersion: '3.0',
    })
  })

  it('replays rollback once and rejects stale rollback revisions', async () => {
    const api = await import('../deployments')
    await expect(api.rollbackDeployment('deploy-003', 3, 'stale')).rejects.toThrow(/revision/i)
    const first = await api.rollbackDeployment('deploy-003', 4, 'rollback-once')
    expect(await api.rollbackDeployment('deploy-003', 4, 'rollback-once')).toEqual(first)
  })

  it('keeps the baseline route after abort, including the next deployment baseline', async () => {
    const api = await import('../deployments')
    await api.abortCanary('deploy-002', 2)
    const route = await api.getDeploymentRoute('payment-process-tbbpm', 'production')
    expect(route).toMatchObject({ stableVersion: '2.0.0', revision: 9 })
    expect(route?.candidateVersion).toBeUndefined()
    const next = await api.createDeployment({
      processCode: 'payment-process-tbbpm',
      version: '2.0.2',
      alias: 'production',
      strategy: 'canary',
      expectedRouteRevision: 9,
      idempotencyKey: 'next',
    })
    expect(next.baselineVersion).toBe('2.0.0')
  })

  it('retains an all-at-once baseline so the newly deployed release can be rolled back', async () => {
    const api = await import('../deployments')
    const deployed = await api.createDeployment({
      processCode: 'order-approval-bpmn',
      version: '1.3.0',
      alias: 'production',
      strategy: 'all_at_once',
      expectedRouteRevision: 1,
      idempotencyKey: 'release',
    })
    expect(deployed.baselineVersion).toBe('1.2.0')
    await api.rollbackDeployment(deployed.id, deployed.routeRevision, 'rollback')
    expect(await api.getDeploymentRoute('order-approval-bpmn', 'production')).toMatchObject({
      stableVersion: '1.2.0',
    })
  })

  it('does not reuse deployment IDs for operations in the same millisecond', async () => {
    const api = await import('../deployments')
    const now = vi.spyOn(Date, 'now').mockReturnValue(42)
    try {
      const request = {
        processCode: 'new',
        version: '1',
        alias: 'dev' as const,
        expectedRouteRevision: 0,
        idempotencyKey: 'one',
      }
      const first = await api.createDeployment(request)
      const second = await api.createDeployment({
        ...request,
        version: '2',
        expectedRouteRevision: 1,
        idempotencyKey: 'two',
      })
      expect(second.id).not.toBe(first.id)
      expect((await api.getDeployment(first.id)).version).toBe('1')
    } finally {
      now.mockRestore()
    }
  })
})
