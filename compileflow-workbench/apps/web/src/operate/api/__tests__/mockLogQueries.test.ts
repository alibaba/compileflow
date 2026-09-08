import { afterEach, describe, expect, it, vi } from 'vitest'

import { exportLogs, getLogById, getLogs, purgeLogs } from '../logs'
import { mockLogs } from '../mockLogData'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

describe('mock execution log query completeness', () => {
  const initialLogs = mockLogs.map((log) => ({ ...log }))

  afterEach(() => mockLogs.splice(0, mockLogs.length, ...initialLogs.map((log) => ({ ...log }))))

  it('loads a detail from outside the first list page', async () => {
    const secondPage = await getLogs({ page: 2 })
    await expect(getLogById(secondPage.data[0].id)).resolves.toEqual(secondPage.data[0])
  })

  it('exports every matching record rather than just the default page', async () => {
    const blob = await exportLogs({})
    const csv = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(reader.error)
      reader.readAsText(blob)
    })
    for (const log of mockLogs) expect(csv).toContain(log.id)
  })

  it('applies the requested inclusive start-time range', async () => {
    const startTime = '2026-02-10T09:20:15Z'
    const endTime = '2026-02-10T09:30:00Z'
    const logs = await getLogs({ startTime, endTime })
    expect(logs.data.map((log) => log.id)).toEqual(['log-004', 'log-003', 'log-002'])
    expect(logs.total).toBe(3)
  })

  it('purges matching records and persists the result for subsequent queries', async () => {
    const result = await purgeLogs({ before: '2026-02-10T09:20:15Z' })

    expect(result).toMatchObject({ deletedCount: 5, hasMore: false })
    const remaining = await getLogs({ pageSize: 100 })
    expect(remaining.total).toBe(15)
    expect(remaining.data[remaining.data.length - 1]?.id).toBe('log-002')
    expect(
      remaining.data.every((log) => Date.parse(log.startTime) >= Date.parse('2026-02-10T09:20:15Z'))
    ).toBe(true)
  })
})
