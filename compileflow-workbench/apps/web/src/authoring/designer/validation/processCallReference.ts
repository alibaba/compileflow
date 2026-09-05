export interface ProcessCallReferenceFields {
  code?: unknown
  classpath?: unknown
  version?: unknown
}

export type ProcessCallReferenceIssue = 'invalidReference' | 'targetRequired' | 'targetConflict'

const IDENTIFIER = /^[A-Za-z0-9][A-Za-z0-9._-]*$/

export function processCallReferenceIssue(
  fields: ProcessCallReferenceFields
): ProcessCallReferenceIssue | undefined {
  const code = normalized(fields.code)
  if (!validIdentifier(code, 128)) return 'invalidReference'
  const classpath = normalized(fields.classpath)
  const version = normalized(fields.version)
  if (!classpath && !version) return 'targetRequired'
  if (classpath && version) return 'targetConflict'
  if (version && !validIdentifier(version, 128)) return 'invalidReference'
  if (classpath && !validClasspath(classpath)) return 'invalidReference'
  return undefined
}

function normalized(value: unknown): string | undefined {
  return typeof value === 'string' && value.length > 0 ? value : undefined
}

function validClasspath(value: string): boolean {
  if (
    value !== value.trim() ||
    value.startsWith('/') ||
    value.includes('\\') ||
    value.includes('*') ||
    value.includes(':')
  ) {
    return false
  }
  return value.split('/').every((segment) => segment !== '' && segment !== '.' && segment !== '..')
}

function validIdentifier(value: string | undefined, maxLength: number): boolean {
  return (
    value !== undefined &&
    value.length <= maxLength &&
    value === value.trim() &&
    IDENTIFIER.test(value)
  )
}
