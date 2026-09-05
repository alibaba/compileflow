import type { DevGatewayLogLevel } from './config.js'

type LogData = Record<string, unknown>
type LogMethod = 'error' | 'log' | 'warn'
type LogTransport = Pick<Console, LogMethod>

export interface DevGatewayLogger {
  debug: (message: string, data?: LogData) => void
  error: (message: string, error?: unknown, data?: LogData) => void
  info: (message: string, data?: LogData) => void
  warn: (message: string, data?: LogData) => void
}

const LEVEL_PRIORITY: Record<DevGatewayLogLevel, number> = {
  error: 0,
  warn: 1,
  info: 2,
  debug: 3,
}
const SENSITIVE_KEY = /(api[-_]?key|authorization|cookie|password|secret|token)/i
const INLINE_CREDENTIAL =
  /(["']?(?:api[-_ ]?key|authorization|cookie|password|secret|token)["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\r\n]*)/gi

function sanitizeString(value: string): string {
  return value.replace(INLINE_CREDENTIAL, '$1[REDACTED]')
}

function sanitize(value: unknown, seen: WeakSet<object>): unknown {
  if (typeof value === 'string') return sanitizeString(value)
  if (
    value === null ||
    value === undefined ||
    typeof value === 'number' ||
    typeof value === 'boolean'
  )
    return value
  if (typeof value === 'bigint') return value.toString()
  if (typeof value !== 'object') return `[${typeof value}]`
  if (seen.has(value)) return '[Circular]'
  seen.add(value)

  if (value instanceof Error) {
    const error: Record<string, unknown> = {
      name: value.name,
      message: sanitizeString(value.message),
    }
    if (value.stack) error.stack = sanitizeString(value.stack)
    if (value.cause !== undefined) error.cause = sanitize(value.cause, seen)
    for (const [key, entry] of Object.entries(value)) {
      error[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : sanitize(entry, seen)
    }
    return error
  }
  if (Array.isArray(value)) return value.map((entry) => sanitize(entry, seen))
  if (value instanceof Headers) {
    const headers: Record<string, unknown> = {}
    value.forEach((entry, key) => {
      headers[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : sanitizeString(entry)
    })
    return headers
  }

  const prototype = Object.getPrototypeOf(value)
  if (prototype !== Object.prototype && prototype !== null) {
    return `[${value.constructor?.name ?? 'Object'}]`
  }
  const result: Record<string, unknown> = {}
  for (const [key, entry] of Object.entries(value)) {
    result[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : sanitize(entry, seen)
  }
  return result
}

function write(
  transport: LogTransport,
  method: LogMethod,
  level: DevGatewayLogLevel,
  scope: string,
  message: string,
  error?: unknown,
  data?: LogData
): void {
  const event: Record<string, unknown> = {
    timestamp: new Date().toISOString(),
    level,
    scope,
    message: sanitizeString(message),
  }
  if (data !== undefined) event.data = sanitize(data, new WeakSet<object>())
  if (error !== undefined) event.error = sanitize(error, new WeakSet<object>())
  transport[method](JSON.stringify(event))
}

export function createLogger(
  scope: string,
  minimumLevel: DevGatewayLogLevel = 'info',
  transport: LogTransport = console
): DevGatewayLogger {
  const enabled = (level: DevGatewayLogLevel) =>
    LEVEL_PRIORITY[level] <= LEVEL_PRIORITY[minimumLevel]
  return {
    debug: (message, data) => {
      if (enabled('debug')) write(transport, 'log', 'debug', scope, message, undefined, data)
    },
    error: (message, error, data) => {
      if (enabled('error')) write(transport, 'error', 'error', scope, message, error, data)
    },
    info: (message, data) => {
      if (enabled('info')) write(transport, 'log', 'info', scope, message, undefined, data)
    },
    warn: (message, data) => {
      if (enabled('warn')) write(transport, 'warn', 'warn', scope, message, undefined, data)
    },
  }
}
