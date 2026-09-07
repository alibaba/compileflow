import { describe, expect, it } from 'vitest'

import en from '../en/authoring'
import zh from '../zh/authoring'

import { evaluateSafeJavaCondition } from '@/authoring/designer/simulation/safeExpressionEvaluator'

describe.each([
  ['en', en],
  ['zh', zh],
] as const)('expression examples (%s)', (_language, catalog) => {
  it('offers a runnable guard in the expression placeholder', () => {
    const text = catalog['designer.expr.placeholder']
    const expression = text.slice(text.indexOf('variable >'))
    expect(evaluateSafeJavaCondition(expression, { variable: 101, status: 'active' })).toBe(true)
    expect(evaluateSafeJavaCondition(expression, { variable: 101, status: null })).toBe(false)
  })
})
