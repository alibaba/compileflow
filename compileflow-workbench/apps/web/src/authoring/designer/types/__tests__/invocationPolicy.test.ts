import { describe, expect, test } from 'vitest'

import type { InvocationPolicy } from '../invocationPolicy'
import { validateInvocationPolicy } from '../invocationPolicy'

describe('InvocationPolicy contract', () => {
  test('accepts the canonical policy boundaries', () => {
    expect(() =>
      validateInvocationPolicy({
        timeout: 'PT10S',
        attemptTimeout: 'PT1S',
        maxAttempts: 100,
        initialBackoff: 'PT0.2S',
        backoffMultiplier: 1,
        maxBackoff: 'P1D',
        jitter: 'full',
        retryOn: 'custom-retry',
        onFailure: 'custom-failure',
      })
    ).not.toThrow()
  })

  test.each([
    [{ attemptTimeout: '-PT1S' }, 'attemptTimeout'],
    [{ attemptTimeout: 'PT0S' }, 'positive'],
    [{ timeout: 'PT1S', attemptTimeout: 'PT2S' }, 'less than or equal'],
    [{ attemptTimeout: '-PT0.0001S' }, 'attemptTimeout'],
    [{ attemptTimeout: 'PT0.0001S' }, 'whole-millisecond precision'],
    [{ attemptTimeout: 'PT0.0015S' }, 'whole-millisecond precision'],
    [{ attemptTimeout: 'P1DT' }, 'attemptTimeout'],
    [{ maxAttempts: -1 }, 'maxAttempts'],
    [{ maxAttempts: 0 }, 'maxAttempts'],
    [{ maxAttempts: 101 }, 'maxAttempts'],
    [{ backoffMultiplier: Number.NaN }, 'backoffMultiplier'],
    [{ backoffMultiplier: 0.5 }, 'backoffMultiplier'],
    [{ initialBackoff: 'PT2S', maxBackoff: 'PT1S' }, 'maxBackoff'],
    [{ attemptTimeout: 'P106751991168D' }, 'representable as Java milliseconds'],
    [
      { initialBackoff: 'P1067519912D' },
      'default maxBackoff must be representable as Java milliseconds',
    ],
    [{ retryOn: '   ' }, 'retryOn'],
    [{ retryOn: 'customRetry' }, 'lowercase kebab-case'],
    [{ onFailure: `bad\u0000name` }, 'control characters'],
  ])('rejects invalid policy %o', (policy, expectedField) => {
    expect(() => validateInvocationPolicy(policy)).toThrow(expectedField)
  })

  test('rejects an unknown retry jitter at an untyped boundary', () => {
    const invalid = { jitter: 'decorrelated' } as unknown as InvocationPolicy
    expect(() => validateInvocationPolicy(invalid)).toThrow('jitter')
  })

  test('accepts the minimum positive duration precision', () => {
    expect(() =>
      validateInvocationPolicy({
        attemptTimeout: 'PT0.001S',
        initialBackoff: 'PT0.002S',
        maxBackoff: 'PT0.003S',
      })
    ).not.toThrow()
  })
})
