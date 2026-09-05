export const TIMEOUTS = {
  DESIGNER_LOAD: 10000,
  CODE_SYNC_DEBOUNCE: 500,
  API_REQUEST: 30000,
  // Polling interval for the monitoring dashboard (ms)
  MONITORING_POLL_INTERVAL: 30000,
} as const

export const LIMITS = {
  MAX_UNDO_HISTORY: 20,
} as const

export const ROUTES = {
  HOME: '/',
  LEARN: '/learn',
  LEARN_EXAMPLES: '/learn/examples',
  LEARN_EXAMPLE_DETAIL: '/learn/examples/:id',
  BUILD: '/build',
  BUILD_DESIGNER: '/build/designer',
  OPERATE: '/operate',
  OPERATE_PROCESSES: '/operate/processes',
  OPERATE_DEPLOYMENTS: '/operate/deployments',
  OPERATE_DEPLOYMENT_DETAIL: '/operate/deployments/:id',
  OPERATE_DEPLOY_WIZARD: '/operate/deploy-wizard',
  OPERATE_MONITORING: '/operate/monitoring',
  OPERATE_LOGS: '/operate/logs',
  SETTINGS: '/settings',
  SERVER_ERROR: '/500',
} as const

export function isRouteWithin(pathname: string, root: string): boolean {
  return pathname === root || pathname.startsWith(`${root}/`)
}

// 'system' key covers the /settings route; uses a neutral color since settings is not a primary module.
export const MODULE_META = {
  learn: { labelKey: 'nav.learn', color: 'var(--color-learn)' },
  build: { labelKey: 'nav.workspace', color: 'var(--color-build)' },
  operate: { labelKey: 'nav.ops', color: 'var(--color-operate)' },
  system: { labelKey: 'settings.title', color: 'var(--color-text-secondary)' },
} as const

function appendQuery(path: string, params: Record<string, string | undefined>): string {
  const searchParams = new URLSearchParams()

  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') {
      searchParams.set(key, value)
    }
  })

  const query = searchParams.toString()
  return query ? `${path}?${query}` : path
}

export function createLearnExamplesPath(
  params: {
    category?: string
    level?: string
    modelType?: string
    search?: string
    sortBy?: string
  } = {}
): string {
  return appendQuery(ROUTES.LEARN_EXAMPLES, params)
}

export function createLearnExampleDetailPath(exampleId: string): string {
  return `${ROUTES.LEARN_EXAMPLES}/${encodeURIComponent(exampleId)}`
}

export function buildDeploymentDetailPath(id: string): string {
  return `${ROUTES.OPERATE_DEPLOYMENTS}/${encodeURIComponent(id)}`
}

export function createOperateDeployWizardPath(
  params: {
    processCode?: string
    source?: string
  } = {}
): string {
  return appendQuery(ROUTES.OPERATE_DEPLOY_WIZARD, params)
}

interface DeploymentAliasPreset {
  value: string
  labelKey: string
}

export const DEPLOYMENT_ALIAS_PRESETS: readonly DeploymentAliasPreset[] = [
  {
    value: 'dev',
    labelKey: 'deployment.alias.dev',
  },
  {
    value: 'staging',
    labelKey: 'deployment.alias.staging',
  },
  {
    value: 'production',
    labelKey: 'deployment.alias.production',
  },
] as const

export function getDeploymentAliasPreset(alias: string): DeploymentAliasPreset | undefined {
  return DEPLOYMENT_ALIAS_PRESETS.find(({ value }) => alias === value)
}

export type DesignerRouteProcessType = 'bpmn' | 'tbbpm'
export type DesignerRouteSource =
  | 'new'
  | 'workspaceProcess'
  | 'template'
  | 'example'
  | 'operateProcessCode'

export function normalizeDesignerRouteProcessType(raw?: string | null): DesignerRouteProcessType {
  if (raw == null || raw.trim() === '') return 'tbbpm'
  const normalized = raw.trim().toLowerCase()
  if (normalized === 'bpmn' || normalized === 'tbbpm') return normalized
  throw new Error(`Unsupported designer process type: ${raw}`)
}

export function buildDesignerRoute(
  options: {
    modelType?: string | null
    processId?: string | null
    templateId?: string | null
    exampleId?: string | null
    processCode?: string | null
    source?: DesignerRouteSource | null
  } = {}
): string {
  const path = ROUTES.BUILD_DESIGNER
  const params = new URLSearchParams()
  params.set('modelType', normalizeDesignerRouteProcessType(options.modelType))
  if (options.processId) params.set('processId', options.processId)
  if (options.templateId) params.set('templateId', options.templateId)
  if (options.exampleId) params.set('exampleId', options.exampleId)
  if (options.processCode) params.set('processCode', options.processCode)
  if (options.source) params.set('source', options.source)
  return `${path}?${params.toString()}`
}

export const LEARN_CATEGORIES = {
  BEGINNER: 'basics',
  BUSINESS: 'business',
  ADVANCED: 'advanced',
} as const
