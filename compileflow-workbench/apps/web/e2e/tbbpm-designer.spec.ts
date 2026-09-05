import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm'
const TIMEOUT = 15000

async function gotoDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await expect(page.locator('.tbbpm-designer')).toBeVisible({ timeout: TIMEOUT })
}

async function waitForCanvas(page: Page) {
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

test.describe('TBBPM designer smoke', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
  })

  test('renders layout and canvas toolbar', async ({ page }) => {
    await expect(page.locator('.tbbpm-designer')).toBeVisible()
    await expect(page.locator('.x6-canvas-toolbar')).toBeVisible()
    await expect(page.locator('.x6-graph')).toBeVisible()
  })

  test('header shows workspace and TBBPM local-source tag', async ({ page }) => {
    await expect(page.getByRole('button', { name: '返回构建' })).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: 'TBBPM' }).first()).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: '本地' }).first()).toBeVisible()
  })

  test('view tabs are present', async ({ page }) => {
    await expect(page.getByTestId('designer-tab-visual')).toBeVisible()
    await expect(page.getByTestId('designer-tab-xml')).toBeVisible()
    await expect(page.getByTestId('designer-tab-split')).toBeVisible()
  })

  test('save shows success toast (Ctrl+S)', async ({ page }) => {
    await page.keyboard.press('Control+S')
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 5000 })
  })

  test('node search modal opens (Ctrl+F)', async ({ page }) => {
    await page.keyboard.press('Control+F')
    await expect(page.getByText('搜索节点')).toBeVisible({ timeout: 5000 })
  })

  test('validate opens right panel tab', async ({ page }) => {
    await page.locator('.anticon-check-square').first().click()
    await expect(page.getByText('流程验证')).toBeVisible({ timeout: 5000 })
  })

  test('export XML via header more menu', async ({ page }) => {
    await page.locator('.header-more-btn').click()
    await page.getByText('导出 XML').click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 5000 })
  })

  test('load example from canvas toolbar', async ({ page }) => {
    await page.getByRole('button', { name: '加载示例' }).click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 5000 })
    const nodeCount = await page.locator('.x6-node').count()
    expect(nodeCount).toBeGreaterThan(2)
  })
})
