export type DevGatewayLogLevel = 'error' | 'warn' | 'info' | 'debug'

export interface DevGatewayConfig {
  readonly logLevel: DevGatewayLogLevel
  readonly host: '127.0.0.1'
  readonly port: number
  readonly maxRequestBytes: number
}

const DEFAULT_PORT = 3001
const DEFAULT_LOG_LEVEL: DevGatewayLogLevel = 'info'
const MAX_PORT = 65_535
const MAX_REQUEST_BYTES = 10 * 1024 * 1024
const DEFAULT_MAX_REQUEST_BYTES = MAX_REQUEST_BYTES

const VARIABLE = {
  port: 'COMPILEFLOW_DEV_GATEWAY_PORT',
  logLevel: 'COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL',
  maxRequestBytes: 'COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES',
} as const

const KNOWN_VARIABLES = new Set<string>(Object.values(VARIABLE))

export function loadDevGatewayConfig(env: NodeJS.ProcessEnv = process.env): DevGatewayConfig {
  rejectUnknownVariables(env)
  validateEnvironment(env.NODE_ENV)

  return Object.freeze({
    logLevel: parseLogLevel(env[VARIABLE.logLevel]),
    host: '127.0.0.1',
    port: parseIntegerEnv(env[VARIABLE.port], VARIABLE.port, DEFAULT_PORT, 1, MAX_PORT),
    maxRequestBytes: parseIntegerEnv(
      env[VARIABLE.maxRequestBytes],
      VARIABLE.maxRequestBytes,
      DEFAULT_MAX_REQUEST_BYTES,
      1,
      MAX_REQUEST_BYTES
    ),
  })
}

function rejectUnknownVariables(env: NodeJS.ProcessEnv): void {
  const unknown = Object.keys(env)
    .filter((name) => name.startsWith('COMPILEFLOW_DEV_GATEWAY_') && !KNOWN_VARIABLES.has(name))
    .sort()
  if (unknown.length > 0) {
    throw new Error(`Unknown development gateway variable(s): ${unknown.join(', ')}`)
  }
}

function validateEnvironment(value: string | undefined): void {
  const normalized = value?.trim().toLowerCase() ?? 'development'
  if (normalized === 'development' || normalized === 'test') {
    return
  }
  if (normalized === 'production') {
    throw new Error('The Workbench development gateway must not run with NODE_ENV=production')
  }
  throw new Error('NODE_ENV must be development or test')
}

function parseLogLevel(value: string | undefined): DevGatewayLogLevel {
  const normalized = value?.trim().toLowerCase() ?? DEFAULT_LOG_LEVEL
  if (
    normalized === 'error' ||
    normalized === 'warn' ||
    normalized === 'info' ||
    normalized === 'debug'
  ) {
    return normalized
  }
  throw new Error(`${VARIABLE.logLevel} must be error, warn, info, or debug`)
}

function parseIntegerEnv(
  value: string | undefined,
  name: string,
  defaultValue: number,
  min: number,
  max: number
): number {
  if (value === undefined) {
    return defaultValue
  }
  const normalized = value.trim()
  if (!/^\d+$/.test(normalized)) {
    throw new Error(`${name} must be an integer between ${min} and ${max}`)
  }
  const parsed = Number(normalized)
  if (!Number.isSafeInteger(parsed) || parsed < min || parsed > max) {
    throw new Error(`${name} must be an integer between ${min} and ${max}`)
  }
  return parsed
}
