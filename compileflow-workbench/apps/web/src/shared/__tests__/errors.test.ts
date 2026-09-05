import { describe, expect, test } from 'vitest'

import { toError } from '../errors'

describe('toError', () => {
  test('preserves Error instances', () => {
    const error = new TypeError('invalid input')
    expect(toError(error)).toBe(error)
  })

  test('retains structured details from plain objects', () => {
    expect(toError({ code: 'INVALID', retryable: false }).message).toBe(
      '{"code":"INVALID","retryable":false}'
    )
  })

  test.each([null, undefined])('uses a stable message for an absent error value', (value) => {
    expect(toError(value).message).toBe('Unknown error')
  })

  test('accepts a contextual fallback', () => {
    expect(toError(undefined, 'Unable to load process').message).toBe('Unable to load process')
  })

  test('does not throw while normalizing a circular object', () => {
    const value: { self?: unknown } = {}
    value.self = value
    expect(toError(value).message).toBe('Unknown error')
  })
})
