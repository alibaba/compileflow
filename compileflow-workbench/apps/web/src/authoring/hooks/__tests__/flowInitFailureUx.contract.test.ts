import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

describe('useProcessInitialization failure UX contract', () => {
  const source = readFileSync(resolve(__dirname, '../useProcessInitialization.ts'), 'utf8')

  it('must keep the error Result reachable (no immediate navigate away on markFailed)', () => {
    const start = source.indexOf('const markFailed = useCallback')
    const end = source.indexOf('useEffect(() => {', start)
    const body = source.slice(start, end)

    // Setting error status is required for UnifiedDesigner error Result.
    expect(body).toContain("setStatus('error')")

    // Auto-navigation makes DesignerLoadingState retry UI unreachable.
    expect(body).not.toMatch(/navigate\(navigateTo\)/)
    expect(body).not.toMatch(/if \(navigateTo\)/)
  })

  it('must surface unexpected initialization failures instead of leaving the loader pending', () => {
    const start = source.indexOf('const run = async () => {')
    const end = source.indexOf('void run()', start)
    const body = source.slice(start, end)

    expect(body).toContain("t('designer.flowInit.errorGeneric')")
    expect(body).toContain('messageApi.error(message)')
    expect(body).toContain('markFailed(message, ROUTES.BUILD)')
  })
})
