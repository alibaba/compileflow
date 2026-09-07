import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

describe('ListPanel theme contract', () => {
  it('uses the existing theme-aware hub ink for hub titles', () => {
    const css = readFileSync(
      resolve(process.cwd(), 'src/shared/components/page/ListPanel.module.css'),
      'utf8'
    )
    const rule = css.match(/:global\(\[data-hub-surface='true'\]\) \.title\s*\{([^}]+)\}/)?.[1]
    expect(rule).toBeDefined()
    expect(rule).toContain('color: var(--hub-ink, var(--text-primary))')
    expect(rule).not.toContain('#f8fafc')
  })
})
