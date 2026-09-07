import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

describe('mock deployment idempotency scopes', () => {
  beforeEach(() => vi.resetModules())

  it('compares targeting request content and ignores parameter insertion order', async () => {
    const api = await import('../deployments')
    const route = { processCode: 'targeting', alias: 'dev' as const }
    await api.createDeployment({
      ...route,
      version: '1',
      expectedRouteRevision: 0,
      idempotencyKey: 'base',
    })
    const request = {
      ...route,
      version: '2',
      expectedRouteRevision: 1,
      idempotencyKey: 'target',
      strategy: 'canary' as const,
      canaryWeightBps: 1000,
      targetingPolicy: 'tenant',
      targetingParameters: { first: 'a', second: 'b' },
    }
    const first = await api.createDeployment(request)
    expect(
      await api.createDeployment({ ...request, targetingParameters: { second: 'b', first: 'a' } })
    ).toEqual(first)
    await expect(api.createDeployment({ ...request, targetingPolicy: 'other' })).rejects.toThrow(
      /idempotency/i
    )
    await expect(
      api.createDeployment({ ...request, targetingParameters: { first: 'changed', second: 'b' } })
    ).rejects.toThrow(/idempotency/i)
  })

  it('rejects a different rollback source under one route key before changing route/history', async () => {
    const api = await import('../deployments')
    const route = { processCode: 'scope', alias: 'dev' as const }
    await api.createDeployment({
      ...route,
      version: '1',
      expectedRouteRevision: 0,
      idempotencyKey: 'base',
    })
    const source = await api.createDeployment({
      ...route,
      version: '2',
      expectedRouteRevision: 1,
      idempotencyKey: 'next',
    })
    const rollback = await api.rollbackDeployment(source.id, 2, 'rollback-key')
    const next = await api.createDeployment({
      ...route,
      version: '3',
      expectedRouteRevision: 3,
      idempotencyKey: 'third',
    })
    const beforeRoute = await api.getDeploymentRoute(route.processCode, route.alias)
    const beforeHistory = await api.getDeploymentEvents(next.id)
    await expect(api.rollbackDeployment(next.id, 4, 'rollback-key')).rejects.toThrow(/idempotency/i)
    expect(await api.getDeploymentRoute(route.processCode, route.alias)).toEqual(beforeRoute)
    expect(await api.getDeploymentEvents(next.id)).toEqual(beforeHistory)
    expect(await api.rollbackDeployment(source.id, 2, 'rollback-key')).toEqual(rollback)
    await expect(api.rollbackDeployment(source.id, 4, 'rollback-key')).rejects.toThrow(
      /idempotency/i
    )
  })

  it('keeps process, alias and operation scopes independent', async () => {
    const api = await import('../deployments')
    for (const [processCode, alias] of [
      ['scope-a', 'dev'],
      ['scope-a', 'staging'],
      ['scope-b', 'dev'],
    ] as const) {
      const route = { processCode, alias }
      await api.createDeployment({
        ...route,
        version: '1',
        expectedRouteRevision: 0,
        idempotencyKey: 'base',
      })
      const deployed = await api.createDeployment({
        ...route,
        version: '2',
        expectedRouteRevision: 1,
        idempotencyKey: 'shared',
      })
      const rollback = await api.rollbackDeployment(deployed.id, 2, 'shared')
      expect(rollback).toMatchObject({ processCode, alias, version: '1', operation: 'rollback' })
    }
  })
})
