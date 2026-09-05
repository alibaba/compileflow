import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/shared/config/buildConfig', () => ({
  isOperateMockMode: () => true,
}))

describe('mock createDeployment detail follow-up', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('wizard success navigation target must be readable via getDeployment', async () => {
    const { createDeployment, getDeployment } = await import('../deployments')
    const created = await createDeployment({
      processCode: 'order-approval-bpmn',
      version: '1.2.0',
      alias: 'dev',
      strategy: 'all_at_once',
      expectedRouteRevision: 0,
      idempotencyKey: 'idem-detail-1',
    })

    await expect(getDeployment(created.id)).resolves.toMatchObject({
      id: created.id,
      processCode: 'order-approval-bpmn',
    })
  })
})
