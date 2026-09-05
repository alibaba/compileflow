const MAX_SYMBOLIC_IDENTIFIER_LENGTH = 256
const SYMBOLIC_IDENTIFIER_PATTERN = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/

export function isSymbolicIdentifier(value: string): boolean {
  return value.length <= MAX_SYMBOLIC_IDENTIFIER_LENGTH && SYMBOLIC_IDENTIFIER_PATTERN.test(value)
}

export function validateSymbolicIdentifier(value: string, fieldName: string): void {
  if (value !== value.trim()) {
    throw new Error(`${fieldName} must not contain surrounding whitespace`)
  }
  if (value.length === 0) {
    throw new Error(`${fieldName} must not be blank`)
  }
  if (value.length > MAX_SYMBOLIC_IDENTIFIER_LENGTH) {
    throw new Error(`${fieldName} must not exceed ${MAX_SYMBOLIC_IDENTIFIER_LENGTH} characters`)
  }
  if (Array.from(value).some(isIsoControl)) {
    throw new Error(`${fieldName} must not contain control characters`)
  }
  if (!SYMBOLIC_IDENTIFIER_PATTERN.test(value)) {
    throw new Error(`${fieldName} must use lowercase kebab-case`)
  }
}

function isIsoControl(character: string): boolean {
  const codePoint = character.codePointAt(0)!
  return codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f)
}
