import { describe, expect, it } from 'vitest'

import {
  evaluateSafeExpression,
  evaluateSafeJavaCondition,
  SafeExpressionError,
} from '../safeExpressionEvaluator'

describe('safeExpressionEvaluator', () => {
  it.each([
    'value != null && value.startsWith(1)',
    'value != null && value.endsWith(false)',
    'true || values.length > "wrong"',
    'true || values.length == "wrong"',
  ])('rejects known invalid types behind null guards and array lengths: %s', (expression) => {
    expect(() => evaluateSafeJavaCondition(expression, { value: null, values: [] })).toThrow(
      SafeExpressionError
    )
  })

  it.each([
    'true || count',
    'false && !count',
    'true || count.startsWith("x")',
    'true || count.length == 1',
    'true || count == "1"',
    'true || count.isEmpty()',
    'true || -true > 0',
    'false && -"1" > 0',
    'true || -1 > "x"',
  ])('rejects known invalid skipped operand types: %s', (expression) => {
    expect(() => evaluateSafeJavaCondition(expression, { count: 1 })).toThrow(SafeExpressionError)
  })

  it('keeps valid short-circuit null guards without invoking skipped getters', () => {
    let reads = 0
    const variables = {
      value: null,
      get skipped() {
        reads++
        throw new Error('getter executed')
      },
    }
    expect(evaluateSafeJavaCondition('value != null && value.startsWith("x")', variables)).toBe(
      false
    )
    expect(() => evaluateSafeJavaCondition('true || skipped', variables)).toThrow(
      SafeExpressionError
    )
    expect(reads).toBe(0)
  })

  it('rejects nonfinite computed preview results', () => {
    expect(() => evaluateSafeExpression('huge * 2', { huge: Number.MAX_VALUE })).toThrow(
      SafeExpressionError
    )
    expect(() => evaluateSafeExpression('huge * 2 > 0', { huge: Number.MAX_VALUE })).toThrow(
      SafeExpressionError
    )
  })

  it('rejects ambiguous skipped boolean values rather than guessing their type', () => {
    expect(() =>
      evaluateSafeJavaCondition('true || map.get("enabled")', { map: { enabled: 1 } })
    ).toThrow(SafeExpressionError)
  })

  it.each(['1 && true', '"false" || true', '!1', 'true && null'])(
    'rejects nonboolean logical operands: %s',
    (expression) => {
      expect(() => evaluateSafeJavaCondition(expression)).toThrow(SafeExpressionError)
      expect(() => evaluateSafeExpression(expression)).toThrow(SafeExpressionError)
    }
  )

  it.each([
    'true || (1 === 1)',
    'false && (1 + 2 > 0)',
    'true || object.field == null',
    'false && undefined == null',
  ])('validates unsupported Java syntax even in skipped branches: %s', (expression) => {
    expect(() => evaluateSafeJavaCondition(expression, { object: {} })).toThrow(SafeExpressionError)
  })

  it.each(['true || 1', 'false && "bad"', 'true || !1'])(
    'validates skipped nonboolean literals: %s',
    (expression) => {
      expect(() => evaluateSafeJavaCondition(expression)).toThrow(SafeExpressionError)
    }
  )

  it.each([
    '"\\q" == "q"',
    '"\\u0041".equals("u0041")',
    '\'word\'.equals("word")',
    '01 == 1',
    '--1 == 1',
    '"line\nbreak".equals("line\nbreak")',
  ])('does not silently reinterpret unsupported Java literals: %s', (expression) => {
    expect(() => evaluateSafeJavaCondition(expression)).toThrow(SafeExpressionError)
  })

  it('evaluates comparison, logic, and wrapped flow expressions', () => {
    const variables = new Map<string, unknown>([
      ['amount', 1200],
      ['status', 'approved'],
      ['approved', true],
    ])

    expect(evaluateSafeExpression('${amount > 1000 && status == "approved"}', variables)).toBe(true)
    expect(evaluateSafeExpression('amount <= 1000 || approved', variables)).toBe(true)
    expect(evaluateSafeExpression('amount < 1000 && approved', variables)).toBe(false)
  })

  it('returns arithmetic and string values for script simulation', () => {
    const variables = { amount: 250, status: 'approved' }

    expect(evaluateSafeExpression('amount * 0.1', variables)).toBe(25)
    expect(evaluateSafeExpression('"order-" + status', variables)).toBe('order-approved')
  })

  it('supports the expression builder string helpers', () => {
    const variables = { status: 'APPROVED', tags: ['vip', 'paid'] }

    expect(evaluateSafeExpression('status.toLowerCase() == "approved"', variables)).toBe(true)
    expect(evaluateSafeExpression('status.startsWith("APP")', variables)).toBe(true)
    expect(evaluateSafeExpression('contains(tags, "vip")', variables)).toBe(true)
    expect(evaluateSafeExpression('isNotEmpty(status) && length(status) > 3', variables)).toBe(true)
  })

  it('uses Java-compatible short-circuit semantics without skipping validation', () => {
    expect(evaluateSafeExpression('false && 1 / 0 > 1')).toBe(false)
    expect(evaluateSafeExpression('true || parseInt("not-a-number") > 1')).toBe(true)
    expect(() => evaluateSafeExpression('true || unsupported()')).toThrow(
      'Unsupported function: unsupported'
    )
    expect(() => evaluateSafeExpression('false && contains("value")')).toThrow(
      'contains expects 2 argument(s)'
    )
  })

  it('rejects code execution and prototype access without reading host globals', () => {
    expect(() => evaluateSafeExpression('Function("return 1")()', {})).toThrow(SafeExpressionError)
    expect(evaluateSafeExpression('window.location == null', {})).toBe(true)
    expect(
      evaluateSafeExpression('window.location == "/safe"', {
        window: { location: '/safe' },
      })
    ).toBe(true)
    expect(() => evaluateSafeExpression('user.constructor.name == "Object"', { user: {} })).toThrow(
      SafeExpressionError
    )
  })

  it('keeps map access on own properties and preserves mode-specific missing values', () => {
    const variables = { map: { present: 'value', prototype: 'owned' } }

    expect(evaluateSafeExpression('map.get("present")', variables)).toBe('value')
    expect(evaluateSafeExpression('map.get("prototype")', variables)).toBe('owned')
    expect(evaluateSafeExpression('map.get("missing")', variables)).toBeUndefined()
    expect(evaluateSafeExpression('map.get("__proto__")', variables)).toBeUndefined()
    expect(evaluateSafeExpression('map.get("constructor")', variables)).toBeUndefined()
    expect(evaluateSafeJavaCondition('map.get("missing") == null', variables)).toBe(true)
    expect(evaluateSafeJavaCondition('map.get("__proto__") == null', variables)).toBe(true)
    expect(evaluateSafeJavaCondition('map.get("constructor") == null', variables)).toBe(true)
    expect(evaluateSafeJavaCondition('map.get("prototype").equals("owned")', variables)).toBe(true)
  })

  it('uses the parser allow-list instead of rejecting harmless string content', () => {
    expect(evaluateSafeExpression('"window.location" == "window.location"')).toBe(true)
    expect(() => evaluateSafeExpression('eval("1")')).toThrow('Unsupported function: eval')
    expect(() => evaluateSafeExpression('a'.repeat(10_001))).toThrow(
      'Expression must contain at most 10000 characters'
    )
  })

  it('evaluates the fail-closed Java condition subset without JavaScript coercion', () => {
    const variables = {
      amount: 1200,
      status: 'APPROVED',
      tags: ['vip', 'paid'],
    }

    expect(evaluateSafeJavaCondition('amount > 1000', variables)).toBe(true)
    expect(evaluateSafeJavaCondition('"APPROVED".equals(status)', variables)).toBe(true)
    expect(evaluateSafeJavaCondition('tags != null && tags.size() == 2', variables)).toBe(true)
  })

  it.each([
    ['amount === 1200', { amount: 1200 }, 'not valid Java'],
    ['contains(tags, "vip")', { tags: ['vip'] }, 'Unqualified function calls'],
    ['status == "APPROVED"', { status: 'APPROVED' }, 'use .equals'],
    ['amount == "1200"', { amount: 1200 }, 'use .equals'],
    ['missing == null', {}, 'Unknown Java condition variable'],
    ['status == undefined', { status: 'APPROVED' }, 'undefined is not a Java literal'],
    ['amount + 1', { amount: 1 }, 'type-dependent'],
    ['amount / 2 > 1', { amount: 5 }, 'type-dependent'],
    ['${amount > 1}', { amount: 5 }, 'raw expression body'],
    ['#{amount > 1}', { amount: 5 }, 'raw expression body'],
  ])('rejects browser-only or type-ambiguous condition %s', (expression, variables, error) => {
    expect(() => evaluateSafeJavaCondition(expression, variables)).toThrow(error)
  })
})
