import { describe, expect, it, vi } from 'vitest'

import { getExecutionTrends, getVersionDistribution } from '../monitoring'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

describe('mock monitoring query contract', () => {
  it('uses the requested time range and bucket interval with consistent counts', async () => {
    const now = Date.now()
    const trends = await getExecutionTrends({ timeRange: '1h', interval: '5m' })
    expect(trends.length).toBeGreaterThan(1)
    for (const point of trends) {
      expect(Date.parse(point.time)).toBeGreaterThanOrEqual(now - 3600_000)
      expect(point.executions).toBe(point.success + point.failed)
    }
    expect(Date.parse(trends[1].time) - Date.parse(trends[0].time)).toBe(300_000)
  })

  it('filters distribution by the requested current process', async () => {
    const distribution = await getVersionDistribution({ processCode: 'payment-process-tbbpm' })
    expect(distribution.every((item) => item.processCode === 'payment-process-tbbpm')).toBe(true)
  })
})
