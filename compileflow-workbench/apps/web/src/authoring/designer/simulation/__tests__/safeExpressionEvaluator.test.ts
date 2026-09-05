import { describe, expect, it } from 'vitest'

import {
  evaluateSafeExpression,
  evaluateSafeJavaCondition,
  SafeExpressionError,
} from '../safeExpressionEvaluator'

describe('safeExpressionEvaluator', () => {
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
