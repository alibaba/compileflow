import { expect, test } from '@playwright/test'

const TIMEOUT = 20_000

test.use({ hasTouch: true, viewport: { width: 390, height: 844 } })

const TBBPM_LABELS = [
  '开始',
  '结束',
  '自动任务',
  '等待任务',
  '等待事件',
  '定时任务',
  '脚本任务',
  '排他网关',
  '并行网关',
  '包容网关',
  '内嵌 BPM',
  'BPM 调用',
  '条件循环',
  '集合遍历',
  '继续循环',
  '中断循环',
  '注释',
] as const

const BPMN_LABELS = [
  '流程开始',
  '流程结束',
  '服务任务',
  '脚本任务',
  '接收任务',
  '排他网关',
  '并行网关',
  '包容网关',
  '调用活动',
  '嵌入式子流程',
] as const

const TOUCH_CASES = [
  ...TBBPM_LABELS.map((label) => ({ modelType: 'tbbpm' as const, label, initialCount: 2 })),
  ...BPMN_LABELS.map((label) => ({ modelType: 'bpmn' as const, label, initialCount: 0 })),
]

for (const { modelType, label, initialCount } of TOUCH_CASES) {
  test(`touching ${modelType} ${label} palette item adds exactly one node`, async ({ page }) => {
    await page.goto(`/build/designer?modelType=${modelType}&source=new`)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })

    const expandPalette = page.getByRole('button', {
      name: /展开节点面板|Expand palette/i,
    })
    if (await expandPalette.isVisible()) await expandPalette.click()

    const search = page.getByRole('textbox', { name: /搜索节点|Search nodes/i })
    await search.fill(label)

    const nodes = page.locator('.x6-node')
    await expect(nodes).toHaveCount(initialCount, { timeout: TIMEOUT })
    const item = page.locator('.drag-palette-item').filter({ hasText: label }).first()
    await expect(item).toBeVisible({ timeout: TIMEOUT })
    await item.tap()
    await expect(nodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })
    await expect(nodes.last()).toContainText(label)
  })
}

for (const { modelType, nodeCount, visibleNodeCount } of [
  { modelType: 'tbbpm', nodeCount: TBBPM_LABELS.length, visibleNodeCount: 10 },
  { modelType: 'bpmn', nodeCount: BPMN_LABELS.length, visibleNodeCount: 8 },
] as const) {
  test(`${modelType} mobile palette has a recoverable empty search state`, async ({ page }) => {
    await page.goto(`/build/designer?modelType=${modelType}&source=new`)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    const expandPalette = page.getByRole('button', {
      name: /展开节点面板|Expand palette/i,
    })
    if (await expandPalette.isVisible()) await expandPalette.click()

    const search = page.getByRole('textbox', { name: /搜索节点|Search nodes/i })
    await search.fill('__no_such_node__')
    await expect(page.locator('.node-palette .ant-empty')).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.drag-palette-item')).toHaveCount(0)

    await search.clear()
    await expect(page.locator('.drag-palette-item')).toHaveCount(visibleNodeCount, {
      timeout: TIMEOUT,
    })
    await expect(page.locator('.node-palette .ant-badge-count').first()).toHaveText(
      String(nodeCount)
    )
    await expect(page.locator('body').first()).not.toContainText(
      /Application error|Something went wrong/i
    )
  })
}
