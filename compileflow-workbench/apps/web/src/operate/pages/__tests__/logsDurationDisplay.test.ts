import { describe, expect, it } from 'vitest'

import { formatDuration } from '../Logs'

describe('Logs duration display', () => {
  it('renders zero-millisecond executions as 0ms', () => {
    expect(formatDuration(0)).toBe('0 ms')
  })

  it('renders positive durations', () => {
    expect(formatDuration(12)).toBe('12 ms')
  })
})
