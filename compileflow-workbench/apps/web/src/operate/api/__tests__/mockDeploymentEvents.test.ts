import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

async function createCanary() {
  const api = await import('../deployments')
  const route = { processCode: 'events', alias: 'dev' as const }
  await api.createDeployment({
    ...route,
    version: '1',
    expectedRouteRevision: 0,
    idempotencyKey: 'base',
  })
  const request = {
    ...route,
    version: '2',
    strategy: 'canary' as const,
    canaryWeightBps: 1000,
    expectedRouteRevision: 1,
    idempotencyKey: 'canary',
  }
  const canary = await api.createDeployment(request)
  return { api, canary, request }
}

describe('mock deployment event history', () => {
  beforeEach(() => vi.resetModules())

  it('records the current canary phase and appends weight and promotion transitions', async () => {
    const { api, canary, request } = await createCanary()
    const started = await api.getDeploymentEvents(canary.id)
    expect(started).toEqual([
      expect.objectContaining({
        type: 'CANARY_STARTED',
        fromPhase: null,
        toPhase: 'in_progress',
        sequence: 1,
      }),
    ])
    await api.createDeployment(request)
    expect(await api.getDeploymentEvents(canary.id)).toEqual(started)
    const weighted = await api.updateCanaryWeightBps(canary.id, 3000, canary.revision)
    await api.promoteCanary(canary.id, weighted.revision)
    const events = await api.getDeploymentEvents(canary.id)
    expect(events[0]).toEqual(started[0])
    expect(events.slice(1)).toEqual([
      expect.objectContaining({
        type: 'CANARY_WEIGHT_UPDATED',
        fromPhase: 'in_progress',
        toPhase: 'in_progress',
        sequence: 2,
      }),
      expect.objectContaining({
        type: 'PROMOTED',
        fromPhase: 'in_progress',
        toPhase: 'completed',
        sequence: 3,
      }),
    ])
    expect(new Set(events.map((event) => event.id)).size).toBe(3)
  })

  it('appends abort reason and does not append for rejected stale mutations', async () => {
    const { api, canary } = await createCanary()
    const started = await api.getDeploymentEvents(canary.id)
    await expect(api.abortCanary(canary.id, 0, 'stale')).rejects.toThrow(/revision/i)
    expect(await api.getDeploymentEvents(canary.id)).toEqual(started)
    await api.abortCanary(canary.id, canary.revision, 'operator stopped canary')
    expect(await api.getDeploymentEvents(canary.id)).toEqual([
      ...started,
      expect.objectContaining({
        type: 'ABORTED',
        fromPhase: 'in_progress',
        toPhase: 'aborted',
        reason: 'operator stopped canary',
        sequence: 2,
      }),
    ])
  })

  it('returns isolated history copies and seeds existing fixture phase accurately', async () => {
    const { api, canary } = await createCanary()
    const first = await api.getDeploymentEvents(canary.id)
    first[0].reason = 'caller mutation'
    first.length = 0
    expect(await api.getDeploymentEvents(canary.id)).toEqual([
      expect.objectContaining({ type: 'CANARY_STARTED', reason: null }),
    ])
    const fixtureHistory = await api.getDeploymentEvents('deploy-002')
    expect(fixtureHistory[fixtureHistory.length - 1]?.toPhase).toBe('in_progress')
    expect(await api.getDeploymentEvents('missing')).toEqual([])
  })

  it('records rollback completion once and leaves source history untouched', async () => {
    const { api, canary } = await createCanary()
    const promoted = await api.promoteCanary(canary.id, canary.revision)
    const sourceHistory = await api.getDeploymentEvents(canary.id)
    const rollback = await api.rollbackDeployment(canary.id, promoted.routeRevision, 'rollback')
    const history = await api.getDeploymentEvents(rollback.id)
    expect(history).toEqual([
      expect.objectContaining({ type: 'COMPLETED', toPhase: 'completed', sequence: 1 }),
    ])
    await api.rollbackDeployment(canary.id, promoted.routeRevision, 'rollback')
    expect(await api.getDeploymentEvents(rollback.id)).toEqual(history)
    expect(await api.getDeploymentEvents(canary.id)).toEqual(sourceHistory)
  })
})
