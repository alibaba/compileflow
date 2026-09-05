import { describe, expect, test } from 'vitest'

import { parseProtocolDuration } from '../protocolDuration'

describe('ProtocolDuration contract', () => {
  test.each([
    ['PT0S', 0n],
    ['PT1S', 1_000n],
    ['PT60S', 60_000n],
    ['PT1.001S', 1_001n],
    ['PT1.000S', 1_000n],
    ['P0DT1S', 1_000n],
    ['P1D', 86_400_000n],
  ])('parses %s', (value, expectedMilliseconds) => {
    expect(parseProtocolDuration(value, 'duration')).toBe(expectedMilliseconds)
  })

  test.each([
    'P',
    'PT',
    'PT+1S',
    'PT-1S',
    '-PT1S',
    'P1Y',
    'P1M',
    'P1W',
    'pt1s',
    'PT1.0000000000S',
    'PT0.000000001S',
  ])('rejects %s', (value) => {
    expect(() => parseProtocolDuration(value, 'duration')).toThrow()
  })
})
