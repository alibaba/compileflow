import { describe, expect, it } from 'vitest'

/** Matches Logs column rendering after the duration===0 fix. */
function formatLogDuration(duration?: number): string {
  return duration != null ? `${duration}ms` : '-'
}

describe('Logs duration display', () => {
  it('renders zero-millisecond executions as 0ms', () => {
    expect(formatLogDuration(0)).toBe('0ms')
  })

  it('renders positive durations', () => {
    expect(formatLogDuration(12)).toBe('12ms')
  })

  it('renders missing duration as dash', () => {
    expect(formatLogDuration(undefined)).toBe('-')
  })
})
