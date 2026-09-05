import { describe, expect, test } from 'vitest'

import {
  findDirectJavaMutation,
  normalizeJavaConditionExpression,
} from '../javaConditionExpression'

describe('findDirectJavaMutation', () => {
  test('accepts raw Java bodies and rejects removed wrapper syntax', () => {
    expect(() => normalizeJavaConditionExpression(' ${amount > 1000} ')).toThrow(
      'raw expression body'
    )
    expect(() => normalizeJavaConditionExpression(' #{ready} ')).toThrow('raw expression body')
    expect(normalizeJavaConditionExpression('amount > 1000')).toBe('amount > 1000')
  })

  test('accepts comparisons and ignores operators inside literals and comments', () => {
    expect(
      findDirectJavaMutation(
        'amount >= 1 && status != null && value == expected && "count++" != text' +
          ' /* total = 1 */ // remaining--\n && active'
      )
    ).toBeUndefined()
  })

  test.each([
    ['enabled = true', '='],
    ['count++ > 0', '++'],
    ['--remaining > 0', '--'],
    ['(flags |= mask) != 0', '|='],
    ['(bits >>>= 1) > 0', '>>>='],
    ['enabled \\u003d true', 'unicode escape'],
  ])('rejects direct mutation in %s', (expression, operator) => {
    expect(findDirectJavaMutation(expression)).toBe(operator)
  })
})
