import { ROUTES } from '@/shared/constants'

export interface BreadcrumbItem {
  /** Translation key resolved when the breadcrumb is rendered. */
  labelKey: string
  path: string
}

type BreadcrumbTrail = BreadcrumbItem[]

const STATIC_TRAILS: Record<string, BreadcrumbTrail> = {
  [ROUTES.LEARN]: [{ labelKey: 'nav.learn', path: ROUTES.LEARN }],
  [ROUTES.LEARN_EXAMPLES]: [
    { labelKey: 'nav.learn', path: ROUTES.LEARN },
    { labelKey: 'nav.learn.examples', path: ROUTES.LEARN_EXAMPLES },
  ],
  [ROUTES.BUILD]: [{ labelKey: 'nav.workspace', path: ROUTES.BUILD }],
  [ROUTES.BUILD_DESIGNER]: [
    { labelKey: 'nav.workspace', path: ROUTES.BUILD },
    { labelKey: 'nav.workspace.designer', path: ROUTES.BUILD_DESIGNER },
  ],
  [ROUTES.OPERATE]: [{ labelKey: 'nav.ops', path: ROUTES.OPERATE }],
  [ROUTES.OPERATE_PROCESSES]: [
    { labelKey: 'nav.ops', path: ROUTES.OPERATE },
    { labelKey: 'nav.ops.processes', path: ROUTES.OPERATE_PROCESSES },
  ],
  [ROUTES.OPERATE_DEPLOYMENTS]: [
    { labelKey: 'nav.ops', path: ROUTES.OPERATE },
    { labelKey: 'nav.ops.deployment', path: ROUTES.OPERATE_DEPLOYMENTS },
  ],
  [ROUTES.OPERATE_DEPLOY_WIZARD]: [
    { labelKey: 'nav.ops', path: ROUTES.OPERATE },
    { labelKey: 'deployment.wizard.title', path: ROUTES.OPERATE_DEPLOY_WIZARD },
  ],
  [ROUTES.OPERATE_MONITORING]: [
    { labelKey: 'nav.ops', path: ROUTES.OPERATE },
    { labelKey: 'nav.ops.monitoring', path: ROUTES.OPERATE_MONITORING },
  ],
  [ROUTES.OPERATE_LOGS]: [
    { labelKey: 'nav.ops', path: ROUTES.OPERATE },
    { labelKey: 'nav.ops.logs', path: ROUTES.OPERATE_LOGS },
  ],
  [ROUTES.SETTINGS]: [{ labelKey: 'settings.title', path: ROUTES.SETTINGS }],
}

export function getBreadcrumbsForPath(pathname: string): BreadcrumbTrail {
  if (pathname.startsWith(`${ROUTES.LEARN_EXAMPLES}/`) && pathname.split('/').length === 4) {
    return [
      { labelKey: 'nav.learn', path: ROUTES.LEARN },
      { labelKey: 'nav.learn.examples', path: ROUTES.LEARN_EXAMPLES },
      { labelKey: 'nav.learn.exampleDetail', path: pathname },
    ]
  }

  if (pathname.startsWith(`${ROUTES.OPERATE_DEPLOYMENTS}/`)) {
    return [
      { labelKey: 'nav.ops', path: ROUTES.OPERATE },
      { labelKey: 'nav.ops.deployment', path: ROUTES.OPERATE_DEPLOYMENTS },
      { labelKey: 'nav.ops.deploymentDetail', path: pathname },
    ]
  }

  return STATIC_TRAILS[pathname] ?? []
}
