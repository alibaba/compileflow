import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockIsOperateMockMode = vi.fn(() => true)

vi.mock('@/shared/config/buildConfig', () => ({
  isOperateMockMode: () => mockIsOperateMockMode(),
}))

describe('mock createDeployment persistence', () => {
  beforeEach(() => {
    vi.resetModules()
    mockIsOperateMockMode.mockReturnValue(true)
  })

  it('newly created mock deployment must appear in subsequent getDeployments', async () => {
    const { createDeployment, getDeployments } = await import('../deployments')

    const before = await getDeployments()
    const created = await createDeployment({
      processCode: 'order-approval-bpmn',
      version: 'v-test-persistence',
      alias: 'dev',
      strategy: 'all_at_once',
      expectedRouteRevision: 0,
      idempotencyKey: 'idem-test-1',
    })

    const after = await getDeployments()
    expect(after.deployments.some((d) => d.id === created.id)).toBe(true)
    expect(after.deployments.length).toBe(before.deployments.length + 1)
  })
})
