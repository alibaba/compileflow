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
    const name = `Keyboard save ${crypto.randomUUID()}`
    await page.getByRole('button', { name: '重命名流程' }).click()
    await page.getByRole('searchbox', { name: '重命名流程' }).fill(name)
    await page.getByRole('searchbox', { name: '重命名流程' }).press('Enter')
    await expect(page.locator('.modified-indicator')).toHaveCount(1)
    await page.locator('.tbbpm-canvas').click()

    // Observe the transient result before dispatching the shortcut.
    await Promise.all([
      expect(page.locator('.ant-message-success').filter({ hasText: /^已保存$/ })).toBeVisible({
        timeout: 5000,
      }),
      page.keyboard.press('Control+S'),
    ])
    await expect(page.locator('.header-save-btn')).toHaveText('已保存')
    await expect(page.locator('.modified-indicator')).toHaveCount(0)
    await page.reload()
    await expect(page.locator('.flow-name-display')).toHaveText(name)
  })

  test('node search modal opens (Ctrl+F)', async ({ page }) => {
    await page.locator('.tbbpm-canvas').click()
    await page.keyboard.press('Control+F')
    await expect(page.getByRole('dialog', { name: /搜索节点/ })).toBeVisible({ timeout: 5000 })
  })

  test('validate opens right panel tab', async ({ page }) => {
    await page.locator('.anticon-check-square').first().click()
    await expect(page.getByText('流程验证', { exact: true })).toBeVisible({ timeout: 5000 })
  })

  test('export XML via header more menu', async ({ page }) => {
    await page.locator('.header-more-btn').click()
    await page.getByText('导出 XML').click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 5000 })
  })
})
