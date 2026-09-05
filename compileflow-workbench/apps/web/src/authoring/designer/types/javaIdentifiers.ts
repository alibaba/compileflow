const JAVA_KEYWORDS = new Set([
  '_',
  'abstract',
  'assert',
  'boolean',
  'break',
  'byte',
  'case',
  'catch',
  'char',
  'class',
  'const',
  'continue',
  'default',
  'do',
  'double',
  'else',
  'enum',
  'extends',
  'false',
  'final',
  'finally',
  'float',
  'for',
  'goto',
  'if',
  'implements',
  'import',
  'instanceof',
  'int',
  'interface',
  'long',
  'native',
  'new',
  'null',
  'package',
  'private',
  'protected',
  'public',
  'record',
  'return',
  'sealed',
  'short',
  'static',
  'strictfp',
  'super',
  'switch',
  'synchronized',
  'this',
  'throw',
  'throws',
  'transient',
  'true',
  'try',
  'var',
  'void',
  'volatile',
  'while',
  'yield',
  'permits',
])

const JAVA_IDENTIFIER_START = /^[\p{L}\p{Sc}\p{Pc}]$/u
const JAVA_IDENTIFIER_PART = /^[\p{L}\p{Sc}\p{Pc}\p{Nd}\p{Nl}\p{Mc}\p{Mn}]$/u
const MAX_JAVA_CLASS_NAME_LENGTH = 500

export function isJavaIdentifier(value: string): boolean {
  const [firstCharacter, ...remainingCharacters] = Array.from(value)
  return (
    firstCharacter !== undefined &&
    !JAVA_KEYWORDS.has(value) &&
    JAVA_IDENTIFIER_START.test(firstCharacter) &&
    remainingCharacters.every((character) => JAVA_IDENTIFIER_PART.test(character))
  )
}

export function isJavaClassName(value: string): boolean {
  return (
    value.length <= MAX_JAVA_CLASS_NAME_LENGTH &&
    !value.includes('/') &&
    !value.includes('\\') &&
    value.split('.').every(isJavaIdentifier)
  )
}

export function requireGeneratedJavaIdentifier(value: string, attribute: string): void {
  if (!isJavaIdentifier(value)) {
    throw new Error(`${attribute} must be a valid Java identifier: ${value}`)
  }
  if (isReservedJavaIdentifier(value)) {
    throw new Error(`${attribute} uses the reserved CompileFlow identifier prefix: ${value}`)
  }
}

export function isReservedJavaIdentifier(value: string): boolean {
  return value.startsWith('_cf$') || value.startsWith('__cf_')
}
