/**
 * Finds direct Java state mutation in a condition expression.
 *
 * Method purity cannot be proven in the browser, so methods used by
 * conditions must still be treated as read-only by model authors.
 */
export function normalizeJavaConditionExpression(expression: string | undefined): string {
  const value = expression?.trim() ?? ''
  if (value.startsWith('${') || value.startsWith('#{')) {
    throw new Error('Java conditions must use the raw expression body without ${...} or #{...}')
  }
  return value
}

export function findDirectJavaMutation(expression: string | undefined): string | undefined {
  if (!expression?.trim()) return undefined
  if (expression.includes('\\u')) return 'unicode escape'

  let index = 0
  while (index < expression.length) {
    const skipped = skipNonCode(expression, index)
    if (skipped !== index) {
      index = skipped
      continue
    }
    const operator = mutationOperatorAt(expression, index)
    if (operator) return operator
    index += 1
  }
  return undefined
}

function mutationOperatorAt(expression: string, index: number): string | undefined {
  const operators = [
    '>>>=',
    '<<=',
    '>>=',
    '++',
    '--',
    '+=',
    '-=',
    '*=',
    '/=',
    '%=',
    '&=',
    '|=',
    '^=',
  ]
  const compound = operators.find((operator) => expression.startsWith(operator, index))
  if (compound) return compound
  if (expression[index] !== '=') return undefined

  const previous = expression[index - 1]
  const next = expression[index + 1]
  return previous === '=' ||
    previous === '!' ||
    previous === '<' ||
    previous === '>' ||
    next === '='
    ? undefined
    : '='
}

function skipNonCode(expression: string, index: number): number {
  const current = expression[index]
  const next = expression[index + 1]
  if (current === '/' && next === '/') return skipLineComment(expression, index + 2)
  if (current === '/' && next === '*') return skipBlockComment(expression, index + 2)
  if (current === '"' || current === "'") return skipJavaLiteral(expression, index, current)
  return index
}

function skipLineComment(expression: string, index: number): number {
  while (index < expression.length && expression[index] !== '\n' && expression[index] !== '\r') {
    index += 1
  }
  return index
}

function skipBlockComment(expression: string, index: number): number {
  const end = expression.indexOf('*/', index)
  return end < 0 ? expression.length : end + 2
}

function skipJavaLiteral(expression: string, start: number, delimiter: string): number {
  const textBlock =
    delimiter === '"' && expression[start + 1] === '"' && expression[start + 2] === '"'
  let index = start + (textBlock ? 3 : 1)
  while (index < expression.length) {
    if (textBlock && expression.startsWith('"""', index) && !isEscaped(expression, index)) {
      return index + 3
    }
    if (!textBlock && expression[index] === delimiter && !isEscaped(expression, index)) {
      return index + 1
    }
    index += 1
  }
  return expression.length
}

function isEscaped(expression: string, index: number): boolean {
  let slashCount = 0
  for (let cursor = index - 1; cursor >= 0 && expression[cursor] === '\\'; cursor -= 1) {
    slashCount += 1
  }
  return slashCount % 2 !== 0
}
