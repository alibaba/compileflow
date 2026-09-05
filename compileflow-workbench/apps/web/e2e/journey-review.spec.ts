import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, type Page, test } from '@playwright/test'

/**
 * Browser journey review against the live mock Workbench.
 * Selectors follow the actual Chinese UI snapshot and semantic card structure.
 */

const TIMEOUT = 25_000
const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SHOT_DIR = path.join(ROOT, '../test-results/journey-review')

async function shot(page: Page, name: string) {
  await page.screenshot({ path: path.join(SHOT_DIR, `${name}.png`), fullPage: true })
}

function trackErrors(page: Page): Error[] {
  const errors: Error[] = []
  page.on('pageerror', (error) => errors.push(error))
  return errors
}

test.describe('Workbench browser journey review', () => {
  test.describe.configure({ timeout: 90_000 })
  test('01 Learn: home → list → filter → detail → execute → designer', async ({ page }) => {
    const errors = trackErrors(page)

    await page.goto('/learn')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '01-learn-home')

    await page.getByRole('button', { name: '浏览示例', exact: true }).first().click()
    await page.waitForURL(/\/learn\/examples/, { timeout: TIMEOUT })
    await expect(page.getByRole('heading', { name: '示例库' })).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByText('示例总数')).toBeVisible()
    // Cards are clickable articles/buttons with example titles.
    const cards = page.locator('main article')
    await expect(cards.first()).toBeVisible({ timeout: TIMEOUT })
    const cardCount = await cards.count()
    expect(cardCount).toBeGreaterThan(0)
    await shot(page, '02-example-list')

    // Difficulty filter (first select in filter bar).
    const selects = page.locator('main .ant-select')
    await selects.first().click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .first()
      .click()
    await expect(page).toHaveURL(/level=/, { timeout: TIMEOUT })
    await shot(page, '03-example-list-filtered')
    // Must not wipe the whole catalog due to string/number coercion.
    const filteredCount = await cards.count()
    expect(filteredCount).toBeGreaterThan(0)

    const clear = page.getByRole('button', { name: '清除' })
    if (await clear.count()) await clear.first().click()

    await cards.first().click()
    await page.waitForURL(/\/learn\/examples\/.+/, { timeout: TIMEOUT })
    await expect(page.getByRole('button', { name: /在设计器中打开|打开设计器/ })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '04-example-detail')

    await page.getByRole('tab', { name: '代码' }).click()
    const codePanel = page.locator('.ant-tabs-tabpane-active pre').first()
    await expect(codePanel).toBeAttached({ timeout: TIMEOUT })
    await expect(codePanel).toContainText(/cf:|bpmn|definitions|<bpm/i)
    await shot(page, '05-example-code')

    await page.getByRole('tab', { name: '执行' }).click()
    await page.locator('.ant-tabs-tabpane-active').getByRole('button', { name: /执行/ }).click()
    await expect(
      page.locator('.ant-message-notice').or(page.locator('[class*="execResult"]')).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '06-example-execute')

    await page.getByRole('button', { name: /在设计器中打开|打开设计器/ }).click()
    await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await shot(page, '07-designer-from-example')
    expect(errors, errors.map((e) => e.message).join('\n')).toEqual([])
  })

  test('02 Build: workspace → BPMN → save → TBBPM → bare URL defaults TBBPM', async ({ page }) => {
    const errors = trackErrors(page)

    await page.goto('/build')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '08-workspace')

    await page
      .getByRole('button', { name: /新建流程|新建 BPMN|New BPMN/i })
      .first()
      .click()
    await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
    await expect(page).toHaveURL(/modelType=bpmn/)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: 'BPMN' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '09-designer-new-bpmn')

    await page.keyboard.press('Control+s')
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })
    await shot(page, '10-designer-saved')

    await page.goto('/build')
    await page.getByRole('button', { name: /TBBPM/ }).first().click()
    await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
    await expect(page).toHaveURL(/modelType=tbbpm/)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await shot(page, '11-designer-new-tbbpm')

    await page.goto('/build/designer')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page).toHaveURL(/modelType=tbbpm/)
    await expect(page.locator('.ant-tag').filter({ hasText: 'TBBPM' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '12-designer-bare-default-bpmn')
    expect(errors).toEqual([])
  })

  test('03 Operate: home → flows → create menu → deploy wizard → list → monitoring → logs', async ({
    page,
  }) => {
    const errors = trackErrors(page)

    await page.goto('/operate')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByText(/^operate\.[a-z]/)).toHaveCount(0)
    await shot(page, '13-operate-home')

    await page
      .getByRole('button', { name: /流程管理/ })
      .first()
      .click()
    await page.waitForURL(/\/operate\/processes/, { timeout: TIMEOUT })
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '14-flow-management')

    await page.getByRole('button', { name: /创建流程/ }).click()
    await expect(page.getByRole('menuitem', { name: /BPMN/ })).toBeVisible({ timeout: 5000 })
    await expect(page.getByRole('menuitem', { name: /TBBPM/ })).toBeVisible({ timeout: 5000 })
    await shot(page, '15-flow-create-menu')
    await page.keyboard.press('Escape')

    // Prefer a known mock flow with published versions.
    const orderRow = page.locator('tbody tr').filter({ hasText: 'order-approval-bpmn' }).first()
    if (await orderRow.count()) {
      await orderRow.getByRole('button', { name: '部署' }).click()
    } else {
      await page.getByRole('button', { name: '部署' }).first().click()
    }
    await page.waitForURL(/\/operate\/deploy-wizard/, { timeout: TIMEOUT })
    await expect(page.getByRole('heading', { name: /部署向导/ })).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '16-deploy-wizard')

    // Version select (required).
    await page.getByRole('combobox', { name: /版本/ }).click()
    const versionOption = page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .first()
    await expect(versionOption).toBeVisible({ timeout: TIMEOUT })
    await versionOption.click()
    const nextButton = page.getByRole('button', { name: '下一步' })
    await expect(nextButton).toBeEnabled({ timeout: TIMEOUT })
    await nextButton.click()

    const aliasInput = page.getByRole('combobox', { name: /别名/ })
    await expect(aliasInput).toBeVisible({ timeout: TIMEOUT })
    await aliasInput.click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /开发别名|DEV/ })
      .first()
      .click()
    await shot(page, '17-deploy-env')
    await expect(nextButton).toBeEnabled({ timeout: TIMEOUT })
    await nextButton.click()
    const deployButton = page
      .locator('main')
      .getByRole('button', { name: /^(rocket\s*)?(立即部署|部署|Deploy now|Deploy)$/i })
    await expect(deployButton).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '18-deploy-confirm')
    await deployButton.click()
    await expect(page.getByText(/部署成功/)).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '19-deploy-success')

    await page.getByRole('button', { name: /查看部署/ }).click()
    await page.waitForURL(/\/operate\/deployments\//, { timeout: TIMEOUT })
    await expect(page.locator('main')).toContainText(/版本|别名|状态|order-approval|route|路由/i)
    await shot(page, '20-deployment-detail')

    await page.goto('/operate/deployments')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '21-deployment-list')

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/监控|执行|Operations|控制/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '22-monitoring')

    await page.goto('/operate/logs')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '23-logs')
    const detail = page.getByRole('button', { name: '详情' }).first()
    if (await detail.count()) {
      await detail.click()
      await expect(page.getByRole('dialog')).toBeVisible({ timeout: 8000 })
      await shot(page, '24-log-detail')
      await page.keyboard.press('Escape')
    }

    expect(errors).toEqual([])
  })

  test('04 Shell: settings, top nav, 404', async ({ page }) => {
    const errors = trackErrors(page)

    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '25-settings')

    await page.getByRole('menuitem', { name: /学习门户|学习|Learn/i }).click()
    await page.waitForURL(/\/learn/, { timeout: TIMEOUT })
    await page.getByRole('menuitem', { name: /开发工作区|构建|Workspace|Build/i }).click()
    await page.waitForURL(/\/build/, { timeout: TIMEOUT })
    await page.getByRole('menuitem', { name: /运维控制台|运维|Operate|Operations/i }).click()
    await page.waitForURL(/\/operate/, { timeout: TIMEOUT })
    await shot(page, '26-nav-cycle')

    await page.goto('/no-such-route-journey-review')
    await expect(page.getByText(/404|找不到|不存在|Not Found/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '27-not-found')
    expect(errors).toEqual([])
  })

  test('05 Designer duplicate copies content (workspace flow)', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    // Load example nodes so duplicate has non-default content.
    const loadExample = page.getByRole('button', { name: '加载示例' })
    if (await loadExample.count()) {
      await loadExample.click()
      await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })
    }
    await page.keyboard.press('Control+s')
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })

    await page.locator('.header-more-btn').click()
    await page
      .getByText('创建副本')
      .or(page.getByText('复制流程'))
      .or(page.getByText('Duplicate'))
      .click()
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /副本|复制|Duplicate|成功/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.waitForURL(/processId=/, { timeout: TIMEOUT })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '28-designer-duplicate')
    expect(errors).toEqual([])
  })
})
