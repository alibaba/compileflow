type TokenType = 'number' | 'string' | 'identifier' | 'operator' | 'punctuation' | 'eof'

interface Token {
  type: TokenType
  value: string
}

type Variables = ReadonlyMap<string, unknown> | Record<string, unknown>
type EvaluationMode = 'script-preview' | 'java-condition'

const MAX_EXPRESSION_LENGTH = 10_000
const MAX_TOKENS = 512
const THREE_CHAR_OPERATORS = new Set(['===', '!=='])
const TWO_CHAR_OPERATORS = new Set(['&&', '||', '>=', '<=', '==', '!='])
const ONE_CHAR_OPERATORS = new Set(['>', '<', '+', '-', '*', '/', '%', '!'])
const PUNCTUATION = new Set(['(', ')', '.', ','])
const DANGEROUS_MEMBER_NAMES = new Set(['__proto__', 'constructor', 'prototype'])
const FUNCTION_ARITIES = new Map<string, number>([
  ['contains', 2],
  ['isEmpty', 1],
  ['isNotEmpty', 1],
  ['length', 1],
  ['parseDouble', 1],
  ['parseFloat', 1],
  ['parseInt', 1],
  ['toLowerCase', 1],
  ['toUpperCase', 1],
])
const METHOD_ARITIES = new Map<string, number>([
  ['contains', 1],
  ['containsKey', 1],
  ['containsValue', 1],
  ['endsWith', 1],
  ['equals', 1],
  ['get', 1],
  ['isEmpty', 0],
  ['length', 0],
  ['size', 0],
  ['startsWith', 1],
  ['toLowerCase', 0],
  ['toUpperCase', 0],
])
const ALLOWED_FUNCTIONS = new Set(FUNCTION_ARITIES.keys())
const ALLOWED_METHODS = new Set(METHOD_ARITIES.keys())

export class SafeExpressionError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SafeExpressionError'
  }
}

function normalizeProcessExpression(expression: string): string {
  const trimmed = expression.trim()
  return trimmed.startsWith('${') && trimmed.endsWith('}') ? trimmed.slice(2, -1).trim() : trimmed
}

export function evaluateSafeExpression(expression: string, variables: Variables = {}): unknown {
  return evaluate(expression, variables, 'script-preview')
}

/**
 * Evaluates the deliberately small Java subset supported by browser simulation.
 *
 * Unsupported or type-ambiguous Java constructs fail closed. The generated
 * source compiled by javac remains authoritative for full Java semantics.
 */
export function evaluateSafeJavaCondition(expression: string, variables: Variables = {}): boolean {
  const value = evaluate(expression, variables, 'java-condition')
  if (typeof value !== 'boolean') {
    throw new SafeExpressionError('Java condition must evaluate to boolean')
  }
  return value
}

function evaluate(expression: string, variables: Variables, mode: EvaluationMode): unknown {
  if (typeof expression !== 'string' || expression.length > MAX_EXPRESSION_LENGTH) {
    throw new SafeExpressionError(
      `Expression must contain at most ${MAX_EXPRESSION_LENGTH} characters`
    )
  }
  const trimmed = expression.trim()
  if (mode === 'java-condition' && (trimmed.startsWith('${') || trimmed.startsWith('#{'))) {
    throw new SafeExpressionError(
      'Java conditions must use the raw expression body without ${...} or #{...} wrappers'
    )
  }
  const normalized = mode === 'script-preview' ? normalizeProcessExpression(trimmed) : trimmed
  if (!normalized) {
    throw new SafeExpressionError('Expression is empty')
  }
  return new Parser(tokenize(normalized), variables, mode).parse()
}

function tokenize(input: string): Token[] {
  const tokens: Token[] = []
  let index = 0

  while (index < input.length) {
    const char = input[index]
    if (/\s/.test(char)) {
      index += 1
      continue
    }

    if (char === '"' || char === "'") {
      const parsed = readString(input, index)
      tokens.push({ type: 'string', value: parsed.value })
      index = parsed.nextIndex
      continue
    }

    if (/\d/.test(char) || (char === '.' && /\d/.test(input[index + 1] ?? ''))) {
      const parsed = readNumber(input, index)
      tokens.push({ type: 'number', value: parsed.value })
      index = parsed.nextIndex
      continue
    }

    if (/[A-Za-z_$]/.test(char)) {
      const parsed = readIdentifier(input, index)
      tokens.push({ type: 'identifier', value: parsed.value })
      index = parsed.nextIndex
      continue
    }

    const symbol = readSymbol(input, index)
    if (symbol) {
      tokens.push(symbol.token)
      index = symbol.nextIndex
      continue
    }

    throw new SafeExpressionError(`Unsupported expression character: ${char}`)
  }

  tokens.push({ type: 'eof', value: '' })
  if (tokens.length > MAX_TOKENS) {
    throw new SafeExpressionError(`Expression contains more than ${MAX_TOKENS} tokens`)
  }
  return tokens
}

function readSymbol(input: string, index: number): { token: Token; nextIndex: number } | null {
  const threeChar = input.slice(index, index + 3)
  if (THREE_CHAR_OPERATORS.has(threeChar)) {
    return { token: { type: 'operator', value: threeChar }, nextIndex: index + 3 }
  }

  const twoChar = input.slice(index, index + 2)
  if (TWO_CHAR_OPERATORS.has(twoChar)) {
    return { token: { type: 'operator', value: twoChar }, nextIndex: index + 2 }
  }

  const char = input[index]
  if (ONE_CHAR_OPERATORS.has(char)) {
    return { token: { type: 'operator', value: char }, nextIndex: index + 1 }
  }
  if (PUNCTUATION.has(char)) {
    return { token: { type: 'punctuation', value: char }, nextIndex: index + 1 }
  }
  return null
}

function readString(input: string, start: number): { value: string; nextIndex: number } {
  const quote = input[start]
  let value = ''
  let index = start + 1
  while (index < input.length) {
    const char = input[index]
    if (char === quote) {
      return { value, nextIndex: index + 1 }
    }
    if (char === '\\') {
      const escaped = input[index + 1]
      if (escaped === undefined) {
        break
      }
      value += readEscape(escaped)
      index += 2
      continue
    }
    value += char
    index += 1
  }
  throw new SafeExpressionError('Unterminated string literal')
}

function readEscape(char: string): string {
  if (char === 'n') return '\n'
  if (char === 'r') return '\r'
  if (char === 't') return '\t'
  return char
}

function readNumber(input: string, start: number): { value: string; nextIndex: number } {
  let index = start
  while (index < input.length && /[\d.]/.test(input[index])) {
    index += 1
  }
  const value = input.slice(start, index)
  if (!/^(?:\d+\.?\d*|\.\d+)$/.test(value)) {
    throw new SafeExpressionError(`Invalid numeric literal: ${value}`)
  }
  return { value, nextIndex: index }
}

function readIdentifier(input: string, start: number): { value: string; nextIndex: number } {
  let index = start
  while (index < input.length && /[A-Za-z0-9_$]/.test(input[index])) {
    index += 1
  }
  return { value: input.slice(start, index), nextIndex: index }
}

class Parser {
  private position = 0

  constructor(
    private readonly tokens: Token[],
    private readonly variables: Variables,
    private readonly mode: EvaluationMode
  ) {}

  parse(): unknown {
    const value = this.parseOr(true)
    this.expect('eof')
    return value
  }

  private parseOr(evaluate: boolean): unknown {
    let left = this.parseAnd(evaluate)
    while (this.matchOperator('||')) {
      const shortCircuited = evaluate && toBoolean(left)
      const right = this.parseAnd(evaluate && !shortCircuited)
      if (evaluate) {
        left = shortCircuited || toBoolean(right)
      }
    }
    return evaluate ? left : undefined
  }

  private parseAnd(evaluate: boolean): unknown {
    let left = this.parseEquality(evaluate)
    while (this.matchOperator('&&')) {
      const shortCircuited = evaluate && !toBoolean(left)
      const right = this.parseEquality(evaluate && !shortCircuited)
      if (evaluate) {
        left = !shortCircuited && toBoolean(right)
      }
    }
    return evaluate ? left : undefined
  }

  private parseEquality(evaluate: boolean): unknown {
    let left = this.parseRelational(evaluate)
    while (
      this.current().type === 'operator' &&
      ['==', '!=', '===', '!=='].includes(this.current().value)
    ) {
      const operator = this.advance().value
      const right = this.parseRelational(evaluate)
      if (evaluate) {
        if (this.mode === 'java-condition' && (operator === '===' || operator === '!==')) {
          throw new SafeExpressionError(`Operator ${operator} is not valid Java`)
        }
        const equal =
          this.mode === 'java-condition'
            ? javaConditionEqual(left, right)
            : operator === '===' || operator === '!=='
              ? Object.is(left, right)
              : looseEqual(left, right)
        left = operator === '!=' || operator === '!==' ? !equal : equal
      }
    }
    return evaluate ? left : undefined
  }

  private parseRelational(evaluate: boolean): unknown {
    let left = this.parseAdditive(evaluate)
    while (
      this.current().type === 'operator' &&
      ['>', '>=', '<', '<='].includes(this.current().value)
    ) {
      const operator = this.advance().value
      const right = this.parseAdditive(evaluate)
      if (evaluate) {
        left =
          this.mode === 'java-condition'
            ? compareJavaNumbers(left, right, operator)
            : compareValues(left, right, operator)
      }
    }
    return evaluate ? left : undefined
  }

  private parseAdditive(evaluate: boolean): unknown {
    let left = this.parseMultiplicative(evaluate)
    while (this.current().type === 'operator' && ['+', '-'].includes(this.current().value)) {
      const operator = this.advance().value
      const right = this.parseMultiplicative(evaluate)
      if (evaluate) {
        if (this.mode === 'java-condition') {
          throw new SafeExpressionError(
            `Java arithmetic operator ${operator} is type-dependent and cannot be simulated safely`
          )
        }
        if (operator === '+') {
          left =
            typeof left === 'string' || typeof right === 'string'
              ? `${toDisplayValue(left, this.mode)}${toDisplayValue(right, this.mode)}`
              : toFiniteNumber(left, this.mode) + toFiniteNumber(right, this.mode)
        } else {
          left = toFiniteNumber(left, this.mode) - toFiniteNumber(right, this.mode)
        }
      }
    }
    return evaluate ? left : undefined
  }

  private parseMultiplicative(evaluate: boolean): unknown {
    let left = this.parseUnary(evaluate)
    while (this.current().type === 'operator' && ['*', '/', '%'].includes(this.current().value)) {
      const operator = this.advance().value
      const rightValue = this.parseUnary(evaluate)
      if (evaluate) {
        if (this.mode === 'java-condition') {
          throw new SafeExpressionError(
            `Java arithmetic operator ${operator} is type-dependent and cannot be simulated safely`
          )
        }
        const right = toFiniteNumber(rightValue, this.mode)
        if ((operator === '/' || operator === '%') && right === 0) {
          throw new SafeExpressionError('Division by zero is not allowed')
        }
        if (operator === '*') left = toFiniteNumber(left, this.mode) * right
        if (operator === '/') left = toFiniteNumber(left, this.mode) / right
        if (operator === '%') left = toFiniteNumber(left, this.mode) % right
      }
    }
    return evaluate ? left : undefined
  }

  private parseUnary(evaluate: boolean): unknown {
    if (this.matchOperator('!')) {
      const value = this.parseUnary(evaluate)
      return evaluate ? !toBoolean(value) : undefined
    }
    if (this.matchOperator('-')) {
      const value = this.parseUnary(evaluate)
      return evaluate ? -toFiniteNumber(value, this.mode) : undefined
    }
    return this.parsePostfix(evaluate)
  }

  private parsePostfix(evaluate: boolean): unknown {
    let value = this.parsePrimary(evaluate)
    while (this.matchPunctuation('.')) {
      const name = this.expect('identifier').value
      validateMemberName(name)
      if (this.matchPunctuation('(')) {
        const args = this.parseArguments(evaluate)
        validateCallSignature(name, args, ALLOWED_METHODS, METHOD_ARITIES, 'method')
        if (evaluate) {
          value = callMethod(name, value, args, this.mode)
        }
      } else if (evaluate) {
        value = readMember(value, name, this.mode)
      }
    }
    return evaluate ? value : undefined
  }

  private parsePrimary(evaluate: boolean): unknown {
    const token = this.current()
    if (token.type === 'number') {
      this.advance()
      return evaluate ? Number(token.value) : undefined
    }
    if (token.type === 'string') {
      this.advance()
      return evaluate ? token.value : undefined
    }
    if (token.type === 'identifier') {
      this.advance()
      if (this.matchPunctuation('(')) {
        const args = this.parseArguments(evaluate)
        if (this.mode === 'java-condition') {
          throw new SafeExpressionError(
            `Unqualified function calls are not supported in Java condition simulation: ${token.value}`
          )
        }
        validateCallSignature(token.value, args, ALLOWED_FUNCTIONS, FUNCTION_ARITIES, 'function')
        return evaluate ? callFunction(token.value, args) : undefined
      }
      validateMemberName(token.value)
      if (!evaluate && this.mode === 'java-condition') {
        this.assertIdentifierKnown(token.value)
      }
      return evaluate ? this.resolveIdentifier(token.value) : undefined
    }
    if (this.matchPunctuation('(')) {
      const value = this.parseOr(evaluate)
      this.expect('punctuation', ')')
      return evaluate ? value : undefined
    }
    throw new SafeExpressionError(`Unexpected token: ${token.value || token.type}`)
  }

  private parseArguments(evaluate: boolean): unknown[] {
    const args: unknown[] = []
    if (this.matchPunctuation(')')) {
      return args
    }
    do {
      args.push(this.parseOr(evaluate))
    } while (this.matchPunctuation(','))
    this.expect('punctuation', ')')
    return args
  }

  private resolveIdentifier(name: string): unknown {
    validateMemberName(name)
    if (name === 'true') return true
    if (name === 'false') return false
    if (name === 'null') return null
    if (name === 'undefined') {
      if (this.mode === 'java-condition') {
        throw new SafeExpressionError('undefined is not a Java literal')
      }
      return undefined
    }
    this.assertIdentifierKnown(name)
    if (this.variables instanceof Map) {
      return this.variables.get(name)
    }
    return (this.variables as Record<string, unknown>)[name]
  }

  private assertIdentifierKnown(name: string): void {
    if (name === 'true' || name === 'false' || name === 'null') return
    if (name === 'undefined' && this.mode !== 'java-condition') return
    const known =
      this.variables instanceof Map
        ? this.variables.has(name)
        : Object.prototype.hasOwnProperty.call(this.variables, name)
    if (!known && this.mode === 'java-condition') {
      throw new SafeExpressionError(`Unknown Java condition variable: ${name}`)
    }
  }

  private matchOperator(value: string): boolean {
    if (this.current().type === 'operator' && this.current().value === value) {
      this.position += 1
      return true
    }
    return false
  }

  private matchPunctuation(value: string): boolean {
    if (this.current().type === 'punctuation' && this.current().value === value) {
      this.position += 1
      return true
    }
    return false
  }

  private expect(type: TokenType, value?: string): Token {
    const token = this.current()
    if (token.type !== type || (value !== undefined && token.value !== value)) {
      throw new SafeExpressionError(`Expected ${value ?? type}`)
    }
    return this.advance()
  }

  private current(): Token {
    return this.tokens[this.position]
  }

  private advance(): Token {
    const token = this.tokens[this.position]
    this.position += 1
    return token
  }
}

function callFunction(name: string, args: unknown[]): unknown {
  validateCallable(name, ALLOWED_FUNCTIONS, 'function')
  switch (name) {
    case 'contains':
      requireArity(name, args, 2)
      return containsValue(args[0], args[1])
    case 'isEmpty':
      requireArity(name, args, 1)
      return isEmpty(args[0])
    case 'isNotEmpty':
      requireArity(name, args, 1)
      return !isEmpty(args[0])
    case 'length':
      requireArity(name, args, 1)
      return readLength(args[0])
    case 'toUpperCase':
      requireArity(name, args, 1)
      return String(args[0] ?? '').toUpperCase()
    case 'toLowerCase':
      requireArity(name, args, 1)
      return String(args[0] ?? '').toLowerCase()
    case 'parseInt':
      requireArity(name, args, 1)
      return parseInteger(args[0])
    case 'parseDouble':
    case 'parseFloat':
      requireArity(name, args, 1)
      return toFiniteNumber(args[0])
    default:
      throw new SafeExpressionError(`Unsupported function: ${name}`)
  }
}

type MethodHandler = (target: unknown, args: unknown[]) => unknown

const PREVIEW_METHOD_HANDLERS: Readonly<Record<string, MethodHandler>> = {
  contains: (target, args) => containsValue(target, args[0]),
  containsKey: (target, args) => hasOwnMember(target, args[0]),
  containsValue: (target, args) => objectValues(target).some((value) => looseEqual(value, args[0])),
  startsWith: (target, args) => String(target ?? '').startsWith(String(args[0] ?? '')),
  endsWith: (target, args) => String(target ?? '').endsWith(String(args[0] ?? '')),
  toUpperCase: (target) => String(target ?? '').toUpperCase(),
  toLowerCase: (target) => String(target ?? '').toLowerCase(),
  equals: (target, args) => looseEqual(target, args[0]),
  get: (target, args) => readObjectMember(target, args[0]),
  isEmpty: (target) => isEmpty(target),
  length: (target) => readLength(target),
  size: (target) => readLength(target),
}

const JAVA_METHOD_HANDLERS: Readonly<Record<string, MethodHandler>> = {
  equals: (target, args) => javaObjectEquals(target, args[0]),
  contains: (target, args) => javaContains(target, args[0]),
  startsWith: (target, args) => javaStringArgumentMethod('startsWith', target, args[0]),
  endsWith: (target, args) => javaStringArgumentMethod('endsWith', target, args[0]),
  toUpperCase: (target) => javaStringNoArgumentMethod('toUpperCase', target),
  toLowerCase: (target) => javaStringNoArgumentMethod('toLowerCase', target),
  length: (target) => javaStringNoArgumentMethod('length', target),
  size: (target) => javaSize(target),
  isEmpty: (target) => javaIsEmpty(target),
  containsKey: (target, args) => hasOwnMember(target, args[0]),
  containsValue: (target, args) =>
    objectValues(target).some((value) => javaObjectEquals(value, args[0])),
  get: (target, args) => readObjectMember(target, args[0]),
}

function callMethod(name: string, target: unknown, args: unknown[], mode: EvaluationMode): unknown {
  validateCallable(name, ALLOWED_METHODS, 'method')
  if (mode === 'java-condition') return callJavaConditionMethod(name, target, args)
  const handler = PREVIEW_METHOD_HANDLERS[name]
  if (!handler) throw new SafeExpressionError(`Unsupported method: ${name}`)
  return handler(target, args)
}

function callJavaConditionMethod(name: string, target: unknown, args: unknown[]): unknown {
  if (target == null) {
    throw new SafeExpressionError(`Java method ${name} cannot be invoked on null`)
  }
  const handler = JAVA_METHOD_HANDLERS[name]
  if (!handler) {
    throw new SafeExpressionError(`Unsupported Java condition method: ${name}`)
  }
  return handler(target, args)
}

function javaContains(target: unknown, argument: unknown): boolean {
  if (typeof target === 'string') {
    requireStringArgument('contains', argument)
    return target.includes(argument)
  }
  if (Array.isArray(target)) {
    return target.some((entry) => javaObjectEquals(entry, argument))
  }
  throw unsupportedTarget('contains', target)
}

function javaStringArgumentMethod(
  name: 'startsWith' | 'endsWith',
  target: unknown,
  argument: unknown
): boolean {
  requireStringTarget(name, target)
  requireStringArgument(name, argument)
  return name === 'startsWith' ? target.startsWith(argument) : target.endsWith(argument)
}

function javaStringNoArgumentMethod(
  name: 'toUpperCase' | 'toLowerCase' | 'length',
  target: unknown
): unknown {
  requireStringTarget(name, target)
  if (name === 'toUpperCase') return target.toUpperCase()
  if (name === 'toLowerCase') return target.toLowerCase()
  return target.length
}

function javaSize(target: unknown): number {
  if (Array.isArray(target)) return target.length
  if (isPlainObject(target)) return Object.keys(target).length
  throw unsupportedTarget('size', target)
}

function javaIsEmpty(target: unknown): boolean {
  if (typeof target === 'string' || Array.isArray(target)) return target.length === 0
  if (isPlainObject(target)) return Object.keys(target).length === 0
  throw unsupportedTarget('isEmpty', target)
}

function validateCallable(name: string, allowed: ReadonlySet<string>, kind: string): void {
  validateMemberName(name)
  if (!allowed.has(name)) {
    throw new SafeExpressionError(`Unsupported ${kind}: ${name}`)
  }
}

function validateCallSignature(
  name: string,
  args: unknown[],
  allowed: ReadonlySet<string>,
  arities: ReadonlyMap<string, number>,
  kind: string
): void {
  validateCallable(name, allowed, kind)
  const expected = arities.get(name)
  if (expected === undefined) {
    throw new SafeExpressionError(`Unsupported ${kind}: ${name}`)
  }
  requireArity(name, args, expected)
}

function validateMemberName(name: string): void {
  if (DANGEROUS_MEMBER_NAMES.has(name)) {
    throw new SafeExpressionError(`Unsafe member name: ${name}`)
  }
}

function requireArity(name: string, args: unknown[], expected: number): void {
  if (args.length !== expected) {
    throw new SafeExpressionError(`${name} expects ${expected} argument(s)`)
  }
}

function readMember(target: unknown, name: string, mode: EvaluationMode): unknown {
  if (target == null) {
    if (mode === 'java-condition') {
      throw new SafeExpressionError(`Java member ${name} cannot be read from null`)
    }
    return undefined
  }
  if (name === 'length') {
    if (mode === 'java-condition' && !Array.isArray(target)) {
      throw new SafeExpressionError('Java .length field is supported only for arrays')
    }
    return readLength(target)
  }
  if (mode === 'java-condition') {
    throw new SafeExpressionError(
      `Java object field access cannot be simulated safely in the browser: ${name}`
    )
  }
  if (typeof target === 'object' && Object.prototype.hasOwnProperty.call(target, name)) {
    return (target as Record<string, unknown>)[name]
  }
  return undefined
}

function containsValue(container: unknown, item: unknown): boolean {
  if (Array.isArray(container)) {
    return container.some((entry) => looseEqual(entry, item))
  }
  return String(container ?? '').includes(String(item ?? ''))
}

function isEmpty(value: unknown): boolean {
  return value == null || value === '' || (Array.isArray(value) && value.length === 0)
}

function readLength(value: unknown): number {
  if (typeof value === 'string' || Array.isArray(value)) {
    return value.length
  }
  return 0
}

function parseInteger(value: unknown): number {
  const parsed = Number.parseInt(String(value ?? ''), 10)
  if (!Number.isFinite(parsed)) {
    throw new SafeExpressionError(`Value is not an integer: ${String(value)}`)
  }
  return parsed
}

function toBoolean(value: unknown): boolean {
  return value === true
}

function toFiniteNumber(value: unknown, mode: EvaluationMode = 'script-preview'): number {
  if (mode === 'java-condition' && typeof value !== 'number') {
    throw new SafeExpressionError(`Java numeric operator received ${describeValue(value)}`)
  }
  const numberValue = typeof value === 'number' ? value : Number(String(value ?? '').trim())
  if (!Number.isFinite(numberValue)) {
    throw new SafeExpressionError(`Value is not numeric: ${String(value)}`)
  }
  if (
    mode === 'java-condition' &&
    Number.isInteger(numberValue) &&
    !Number.isSafeInteger(numberValue)
  ) {
    throw new SafeExpressionError('Java integer exceeds the browser safe-integer range')
  }
  return numberValue
}

function compareJavaNumbers(left: unknown, right: unknown, operator: string): boolean {
  const leftNumber = toFiniteNumber(left, 'java-condition')
  const rightNumber = toFiniteNumber(right, 'java-condition')
  if (operator === '>') return leftNumber > rightNumber
  if (operator === '>=') return leftNumber >= rightNumber
  if (operator === '<') return leftNumber < rightNumber
  return leftNumber <= rightNumber
}

function javaConditionEqual(left: unknown, right: unknown): boolean {
  if (left == null || right == null) return left === right
  if (typeof left === 'number' && typeof right === 'number') {
    return toFiniteNumber(left, 'java-condition') === toFiniteNumber(right, 'java-condition')
  }
  if (typeof left === 'boolean' && typeof right === 'boolean') return left === right
  throw new SafeExpressionError(
    'Java == and != are simulated only for numbers, booleans, and null; use .equals(...) for values'
  )
}

function javaObjectEquals(left: unknown, right: unknown): boolean {
  if (left == null) return right == null
  if (typeof left === 'string' || typeof left === 'number' || typeof left === 'boolean') {
    return typeof left === typeof right && Object.is(left, right)
  }
  if (Array.isArray(left)) {
    if (!Array.isArray(right) || left.length !== right.length) return false
    return left.every((entry, index) => javaObjectEquals(entry, right[index]))
  }
  throw new SafeExpressionError(
    'Object.equals semantics for custom Java types cannot be simulated safely in the browser'
  )
}

function compareValues(left: unknown, right: unknown, operator: string): boolean {
  const leftNumber = asOptionalNumber(left)
  const rightNumber = asOptionalNumber(right)
  const leftComparable = leftNumber ?? String(left ?? '')
  const rightComparable = rightNumber ?? String(right ?? '')
  if (operator === '>') return leftComparable > rightComparable
  if (operator === '>=') return leftComparable >= rightComparable
  if (operator === '<') return leftComparable < rightComparable
  return leftComparable <= rightComparable
}

function asOptionalNumber(value: unknown): number | null {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function looseEqual(left: unknown, right: unknown): boolean {
  if (left == null || right == null) {
    return left == null && right == null
  }
  const leftNumber = asOptionalNumber(left)
  const rightNumber = asOptionalNumber(right)
  if (leftNumber !== null && rightNumber !== null) {
    return leftNumber === rightNumber
  }
  return Object.is(left, right)
}

function toDisplayValue(value: unknown, mode: EvaluationMode): string {
  if (mode === 'java-condition' && value === null) return 'null'
  return value == null ? '' : String(value)
}

function requireStringTarget(name: string, target: unknown): asserts target is string {
  if (typeof target !== 'string') throw unsupportedTarget(name, target)
}

function requireStringArgument(name: string, argument: unknown): asserts argument is string {
  if (typeof argument !== 'string') {
    throw new SafeExpressionError(`Java method ${name} requires a string argument`)
  }
}

function unsupportedTarget(name: string, target: unknown): SafeExpressionError {
  return new SafeExpressionError(
    `Java method ${name} is unsupported for browser value ${describeValue(target)}`
  )
}

function describeValue(value: unknown): string {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  return typeof value
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function hasOwnMember(target: unknown, key: unknown): boolean {
  if (!isPlainObject(target) || typeof key !== 'string') {
    throw new SafeExpressionError('Map operation requires an object target and string key')
  }
  return Object.prototype.hasOwnProperty.call(target, key)
}

function objectValues(target: unknown): unknown[] {
  if (!isPlainObject(target)) {
    throw new SafeExpressionError('Map operation requires an object target')
  }
  return Object.values(target)
}

function readObjectMember(target: unknown, key: unknown): unknown {
  if (!isPlainObject(target) || typeof key !== 'string') {
    throw new SafeExpressionError('Map get requires an object target and string key')
  }
  return target[key]
}
