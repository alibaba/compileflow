import { expect, test } from '@playwright/test'

const TIMEOUT = 20_000

test.use({ hasTouch: true, viewport: { width: 390, height: 844 } })

for (const { modelType, label, initialCount } of [
  { modelType: 'tbbpm', label: '自动任务', initialCount: 2 },
  { modelType: 'bpmn', label: '服务任务', initialCount: 0 },
] as const) {
  test(`touching ${modelType} palette adds exactly one node`, async ({ page }) => {
    await page.goto(`/build/designer?modelType=${modelType}&source=new`)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })

    const expandPalette = page.getByRole('button', {
      name: /展开节点面板|Expand palette/i,
    })
    if (await expandPalette.isVisible()) await expandPalette.click()

    const nodes = page.locator('.x6-node')
    await expect(nodes).toHaveCount(initialCount, { timeout: TIMEOUT })
    await page.locator('.drag-palette-item').filter({ hasText: label }).first().tap()
    await expect(nodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })
    await expect(nodes.last()).toContainText(label)
  })
}
