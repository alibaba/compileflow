import { describe, expect, it, vi } from 'vitest'

import { exportLogs, getLogById, getLogs } from '../logs'
import { mockLogs } from '../mockLogData'

vi.mock('@/shared/config/buildConfig', () => ({ isOperateMockMode: () => true }))

describe('mock execution log query completeness', () => {
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
})
