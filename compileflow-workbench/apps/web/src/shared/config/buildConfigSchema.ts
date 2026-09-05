type BuildMode = 'development' | 'production' | 'test'
export type OperateMode = 'mock' | 'real'

const KNOWN_VITE_VARIABLES = new Set([
  'VITE_COMPILEFLOW_OPERATE_MODE',
  'VITE_COMPILEFLOW_DEBUG',
  'VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES',
])

export interface ViteBuildEnv {
  MODE?: string
  VITE_COMPILEFLOW_OPERATE_MODE?: string
  VITE_COMPILEFLOW_DEBUG?: string
  VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES?: string
}

export interface AppBuildConfig {
  readonly operateMode: OperateMode
  readonly enableDebug: boolean
  readonly buildMode: BuildMode
  readonly appVersion: string
  readonly useBuiltInExamples: boolean
}

export function resolveOperateMode(env: ViteBuildEnv): OperateMode {
  const explicitMode = env.VITE_COMPILEFLOW_OPERATE_MODE
  if (explicitMode !== undefined) {
    const normalized = explicitMode.trim().toLowerCase()
    if (normalized === 'mock' || normalized === 'real') {
      return normalized
    }
    throw new Error('VITE_COMPILEFLOW_OPERATE_MODE must be mock or real')
  }

  return resolveBuildMode(env.MODE) === 'development' ? 'mock' : 'real'
}

export function resolveBuildConfig(env: ViteBuildEnv, appVersion = 'unknown'): AppBuildConfig {
  rejectUnknownViteVariables(env)
  const buildMode = resolveBuildMode(env.MODE)
  const operateMode = resolveOperateMode(env)
  if (buildMode === 'production' && operateMode !== 'real') {
    throw new Error('VITE_COMPILEFLOW_OPERATE_MODE must be real in production builds')
  }
  const useBuiltInExamples = parseBoolean(
    env.VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES,
    'VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES',
    operateMode === 'mock'
  )
  if (operateMode === 'mock' && !useBuiltInExamples) {
    throw new Error(
      'VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES must be true when ' +
        'VITE_COMPILEFLOW_OPERATE_MODE is mock'
    )
  }

  return Object.freeze({
    operateMode,
    enableDebug: parseBoolean(env.VITE_COMPILEFLOW_DEBUG, 'VITE_COMPILEFLOW_DEBUG', false),
    buildMode,
    appVersion: parseNonBlank(appVersion, 'appVersion', 'unknown'),
    useBuiltInExamples,
  })
}

function rejectUnknownViteVariables(env: ViteBuildEnv): void {
  const unknown = Object.keys(env)
    .filter((name) => name.startsWith('VITE_COMPILEFLOW_') && !KNOWN_VITE_VARIABLES.has(name))
    .sort()
  if (unknown.length > 0) {
    throw new Error(`Unknown web build configuration variable(s): ${unknown.join(', ')}`)
  }
}

function resolveBuildMode(value: string | undefined): BuildMode {
  const normalized = value?.trim().toLowerCase() ?? 'development'
  if (normalized === 'development' || normalized === 'production' || normalized === 'test') {
    return normalized
  }
  throw new Error('MODE must be development, production, or test')
}

function parseBoolean(value: string | undefined, name: string, defaultValue: boolean): boolean {
  if (value === undefined) {
    return defaultValue
  }
  const normalized = value.trim().toLowerCase()
  if (normalized === 'true') {
    return true
  }
  if (normalized === 'false') {
    return false
  }
  throw new Error(`${name} must be true or false`)
}

function parseNonBlank(value: string | undefined, name: string, defaultValue: string): string {
  if (value === undefined) {
    return defaultValue
  }
  const normalized = value.trim()
  if (!normalized) {
    throw new Error(`${name} must not be blank`)
  }
  return normalized
}
