import { ROUTES } from '@/shared/constants'

const HUB_ROOTS = new Set<string>([ROUTES.LEARN, ROUTES.BUILD, ROUTES.OPERATE])

export function isHubRootPath(pathname: string): boolean {
  return HUB_ROOTS.has(pathname)
}
