import { type AppBuildConfig, resolveBuildConfig, type ViteBuildEnv } from './buildConfigSchema'

export { resolveBuildConfig, resolveOperateMode } from './buildConfigSchema'
export type { AppBuildConfig }

type BuildLogTransport = Pick<Console, 'error' | 'info' | 'warn'>
const buildLogTransport: BuildLogTransport = globalThis.console
const SENSITIVE_KEY = /(api[-_]?key|authorization|cookie|password|secret|token)/i
const INLINE_CREDENTIAL =
  /(["']?(?:api[-_ ]?key|authorization|cookie|password|secret|token)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\r\n]*)/gi

const viteBuildEnv = import.meta.env as ViteBuildEnv
const appVersion =
  typeof __COMPILEFLOW_APP_VERSION__ === 'string' ? __COMPILEFLOW_APP_VERSION__ : 'unknown'

export const APP_BUILD_CONFIG: AppBuildConfig = resolveBuildConfig(viteBuildEnv, appVersion)

export function isOperateMockMode(): boolean {
  return APP_BUILD_CONFIG.operateMode === 'mock'
}

export function devLog(message: string, ...args: unknown[]): void {
  if (isDebugLoggingEnabled()) {
    buildLogTransport.info(`[CF-Workbench] ${redactString(message)}`, ...sanitizeArgs(args))
  }
}

export function devError(message: string, ...args: unknown[]): void {
  if (isDebugLoggingEnabled()) {
    buildLogTransport.error(`[CF-Workbench] ${redactString(message)}`, ...sanitizeArgs(args))
  }
}

function isDebugLoggingEnabled(): boolean {
  return APP_BUILD_CONFIG.enableDebug
}

function sanitizeArgs(args: unknown[]): unknown[] {
  return args.map(sanitizeDiagnosticValue)
}

export function sanitizeDiagnosticValue(value: unknown): unknown {
  return sanitizeLogValue(value, new WeakSet<object>())
}

function sanitizeLogValue(value: unknown, seen: WeakSet<object>): unknown {
  if (typeof value === 'string') {
    return redactString(value)
  }
  if (value instanceof Error) {
    return sanitizeError(value, seen)
  }
  if (Array.isArray(value)) {
    return sanitizeArray(value, seen)
  }
  if (value instanceof Headers) {
    return sanitizeHeaders(value, seen)
  }
  if (isHeaderLike(value)) {
    return sanitizeHeaderLike(value, seen)
  }
  if (isPlainObject(value)) {
    return sanitizeRecord(value, seen)
  }

  return describeUnsupportedObject(value)
}

function sanitizeError(value: Error, seen: WeakSet<object>): unknown {
  if (!trackObject(value, seen)) {
    return '[Circular]'
  }

  const sanitized: Record<string, unknown> = {
    name: value.name,
    message: redactString(value.message),
  }
  if (value.stack) {
    sanitized.stack = redactString(value.stack)
  }
  const cause = (value as Error & { cause?: unknown }).cause
  if (cause !== undefined) {
    sanitized.cause = sanitizeLogValue(cause, seen)
  }
  return sanitizeEntries(Object.entries(value), seen, sanitized)
}

function sanitizeArray(value: unknown[], seen: WeakSet<object>): unknown {
  if (!trackObject(value, seen)) {
    return '[Circular]'
  }
  return value.map((entry) => sanitizeLogValue(entry, seen))
}

function sanitizeHeaders(value: Headers, seen: WeakSet<object>): unknown {
  if (!trackObject(value, seen)) {
    return '[Circular]'
  }
  const sanitized: Record<string, unknown> = {}
  value.forEach((entry, key) => {
    sanitized[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : redactString(entry)
  })
  return sanitized
}

function sanitizeHeaderLike(value: { toJSON: () => unknown }, seen: WeakSet<object>): unknown {
  try {
    return sanitizeLogValue(value.toJSON(), seen)
  } catch {
    return '[Headers]'
  }
}

function sanitizeRecord(value: Record<string, unknown>, seen: WeakSet<object>): unknown {
  if (!trackObject(value, seen)) {
    return '[Circular]'
  }
  return sanitizeEntries(Object.entries(value), seen)
}

function sanitizeEntries(
  entries: [string, unknown][],
  seen: WeakSet<object>,
  sanitized: Record<string, unknown> = {}
): Record<string, unknown> {
  for (const [key, entry] of entries) {
    sanitized[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : sanitizeLogValue(entry, seen)
  }
  return sanitized
}

function trackObject(value: object, seen: WeakSet<object>): boolean {
  if (seen.has(value)) {
    return false
  }
  seen.add(value)
  return true
}

function describeUnsupportedObject(value: unknown): unknown {
  if (typeof value === 'object' && value !== null) {
    return `[${value.constructor?.name ?? 'Object'}]`
  }
  return value
}

function redactString(value: string): string {
  return value.replace(INLINE_CREDENTIAL, '$1[REDACTED]')
}

function isHeaderLike(value: unknown): value is { toJSON: () => unknown } {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const constructorName = value.constructor?.name ?? ''
  return (
    constructorName.endsWith('Headers') &&
    typeof (value as { toJSON?: unknown }).toJSON === 'function'
  )
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}
