import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

const workbenchRoot = resolve(process.cwd(), '../..')

describe('Docker web build inputs', () => {
  it.each([
    ['Dockerfile.web', ''],
    ['Dockerfile.all-in-one', 'compileflow-workbench/'],
  ])('%s includes the bundle checker required by the web build', (dockerfile, sourcePrefix) => {
    const packageJson = JSON.parse(readFileSync(resolve(process.cwd(), 'package.json'), 'utf8'))
    expect(packageJson.scripts.build).toContain('node ../../scripts/check-web-bundle-budget.mjs')

    const source = readFileSync(resolve(workbenchRoot, 'docker', dockerfile), 'utf8')
    const buildCommand = 'RUN pnpm --filter @compileflow/workbench-web build'
    const copyCommand = `COPY ${sourcePrefix}scripts/check-web-bundle-budget.mjs scripts/`
    const buildIndex = source.indexOf(buildCommand)
    expect(buildIndex).toBeGreaterThan(-1)
    expect(source.slice(0, buildIndex).split('\n')).toContain(copyCommand)
    expect(
      readFileSync(resolve(workbenchRoot, 'scripts/check-web-bundle-budget.mjs'), 'utf8')
    ).toContain("'../apps/web/dist'")
  })
})
