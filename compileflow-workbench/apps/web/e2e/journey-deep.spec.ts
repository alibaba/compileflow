import fs from 'node:fs'

import { expect, test } from '@playwright/test'

import {
  assertNoPageErrors,
  confirmModalOk,
  selectFirstDropdownOption,
  shot,
  TIMEOUT,
  trackErrors,
} from './journey-helpers'

/**
 * Exhaustive browser coverage for surfaces not fully exercised by journey-review.
 * Runs against mock Operate + built-in examples on localhost:5173.
 */

test.describe('Deep Learn coverage', () => {
  test('sidebar categories, search, docs, download', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: '示例库' })).toBeVisible({ timeout: TIMEOUT })

    // Contextual sidebar categories.
    const basics = page.getByRole('menuitem', { name: /基础|Basics/i }).first()
    if (await basics.count()) {
      await basics.click()
      await page.waitForTimeout(400)
      await shot(page, '29-learn-sidebar-basics')
    }

    await page.goto('/learn/examples')
    const search = page.getByPlaceholder(/搜索|Search/i)
    await search.fill('bpmn')
    await page.waitForTimeout(500)
    const cards = page.locator('main article')
    const cardCount = await cards.count()
    if (cardCount === 0) {
      await search.fill('')
      await page.waitForTimeout(400)
    }
    await expect(cards.first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '30-learn-search')

    await cards.first().click()
    await page.waitForURL(/\/learn\/examples\/.+/, { timeout: TIMEOUT })

    await page.getByRole('tab', { name: /说明|文档|Docs|Documentation/i }).click()
    await expect(page.locator('.ant-tabs-tabpane-active')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '31-learn-docs')

    // Feedback + favorite are part of the detail sidebar.
    const helpful = page.getByRole('button', { name: /有帮助|Helpful/i })
    if (await helpful.count()) {
      await helpful.first().click()
      await shot(page, '31b-learn-feedback')
    }

    const download = page.getByRole('button', { name: /下载|Download/i })
    await expect(download.first()).toBeVisible()
    {
      const [downloadEvent] = await Promise.all([
        page.waitForEvent('download', { timeout: 8000 }),
        download.first().click(),
      ])
      expect(downloadEvent.suggestedFilename()).toMatch(/\.(bpmn|bpm|xml)$/i)
      await shot(page, '32-learn-download')
    }

    const related = page.getByText(/相关示例|Related/i)
    if (await related.count()) {
      await expect(related.first()).toBeVisible()
      await shot(page, '33-learn-related')
    }

    await assertNoPageErrors(errors)
  })
})

test.describe('Deep Shell coverage', () => {
  test('Cmd+K search, theme, language, app menu, sidebar operate', async ({ page }) => {
    test.setTimeout(60_000)
    const errors = trackErrors(page)
    await page.goto('/learn')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })

    // Global search via keyboard.
    await page.keyboard.press('Control+k')
    const searchModal = page.getByRole('dialog')
    const searchInput = searchModal.getByRole('textbox', {
      name: /搜索示例和本地流程|Search examples and local processes/i,
    })
    await expect(searchInput).toBeVisible({ timeout: TIMEOUT })
    await searchInput.fill('订单')
    await page.waitForTimeout(500)
    const match = searchModal
      .locator('[role="button"]')
      .filter({ hasText: /订单|Order/i })
      .first()
    if (await match.count()) {
      await Promise.all([
        page.waitForURL(/\/learn\/examples\//, { timeout: TIMEOUT }),
        match.click(),
      ])
      await shot(page, '35-global-search-result')
    } else {
      // Quick link fallback.
      await Promise.all([
        page.waitForURL(/\/operate\/processes/, { timeout: TIMEOUT }),
        searchModal.getByRole('button', { name: /流程管理|Processes/i }).click(),
      ])
      await shot(page, '35-global-search-quicklink')
    }

    await page.goto('/learn')
    // Theme toggle (html[data-theme]).
    const themeBtn = page.getByRole('button', {
      name: /切换到深色模式|切换到亮色模式|切换到浅色模式|Switch to Dark|Switch to Light/i,
    })
    await expect(themeBtn).toBeVisible({ timeout: TIMEOUT })
    const before = await page.locator('html').getAttribute('data-theme')
    await themeBtn.click()
    await expect.poll(async () => page.locator('html').getAttribute('data-theme')).not.toBe(before)
    await shot(page, '36-theme-toggled')
    await themeBtn.click() // restore

    // Language toggle (AppBar EN / 中).
    const langBtn = page.getByRole('button', {
      name: /切换到英文|切换到中文|Switch to English|Switch to Chinese/i,
    })
    await expect(langBtn).toBeVisible({ timeout: TIMEOUT })
    const initialLanguage = await page.locator('html').getAttribute('lang')
    await langBtn.click()
    await expect.poll(() => page.locator('html').getAttribute('lang')).not.toBe(initialLanguage)
    await shot(page, '37-language-toggled')
    await langBtn.click() // restore zh
    await expect(page.locator('html')).toHaveAttribute('lang', initialLanguage ?? 'zh')

    // App menu → settings.
    await page.getByRole('button', { name: /应用菜单|Application menu/i }).click()
    const appMenu = page.locator('.ant-dropdown:visible')
    await expect(appMenu).toHaveCSS('opacity', '1')
    const settingsItem = appMenu.getByRole('menuitem', { name: /设置|Settings/i })
    await expect(settingsItem).toBeVisible({ timeout: TIMEOUT })
    await Promise.all([page.waitForURL(/\/settings/, { timeout: TIMEOUT }), settingsItem.click()])
    await shot(page, '38-appmenu-settings')

    // Operate contextual sidebar.
    await page.goto('/operate')
    for (const label of [/流程管理/, /部署/, /监控/, /日志/]) {
      const item = page.getByRole('menuitem', { name: label }).first()
      if (await item.count()) {
        await item.click()
        await page.waitForTimeout(400)
      }
    }
    await shot(page, '39-operate-sidebar-cycle')
    await assertNoPageErrors(errors)
  })
})

test.describe('Deep Build coverage', () => {
  test('workspace export and import', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/build')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })

    // Export all workspace JSON.
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: TIMEOUT }),
      page.getByRole('button', { name: /全部导出|导出所有数据|Export all/i }).click(),
    ])
    expect(download.suggestedFilename()).toMatch(/compileflow-workbench-.*\.json/)
    const exportPath = test.info().outputPath(download.suggestedFilename())
    await download.saveAs(exportPath)
    const exported = JSON.parse(fs.readFileSync(exportPath, 'utf8')) as { flows?: unknown[] }
    expect(exported).toBeTruthy()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /导出成功|exported/i })
    ).toBeVisible({
      timeout: 8000,
    })
    await shot(page, '40-workspace-export')

    // Import the same file back.
    const fileInput = page.locator('input[type="file"]')
    await expect(fileInput).toBeAttached()
    await page.getByRole('button', { name: /导入数据|导入|Import/i }).click()
    await fileInput.setInputFiles(exportPath)
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /成功.*跳过.*失败|imported.*skipped.*failed/i })
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '41-workspace-import')

    await page.getByRole('button', { name: /导入数据|导入|Import/i }).click()
    await fileInput.setInputFiles({
      name: 'invalid-workbench.json',
      mimeType: 'application/json',
      buffer: Buffer.from('{"formatVersion":2,"processes":"invalid"}'),
    })
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /导入失败|Import failed/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(fileInput).toHaveValue('')

    await assertNoPageErrors(errors)
  })

  test('template, designer views & tools', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/build')
    await expect(page.getByRole('banner', { name: '主导航' })).toBeVisible({ timeout: TIMEOUT })

    // Use a seeded template if listed.
    const useTemplate = page.getByRole('button', { name: /使用|Use/i }).first()
    if (await useTemplate.count()) {
      await useTemplate.click()
      await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
      await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
      await shot(page, '42-template-opened')
    } else {
      await page.goto('/build/designer?modelType=bpmn')
      await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    }

    // View tabs: XML + split.
    await page.getByTestId('designer-tab-xml').click()
    await expect(page.locator('.xml-code-editor-panel')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '43-designer-xml')
    await page.getByTestId('designer-tab-split').click()
    await expect(page.locator('.unified-designer-body--split')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '44-designer-split')
    await page.getByTestId('designer-tab-visual').click()

    // Debug panel.
    const bugBtn = page.locator('.designer-header button .anticon-bug').first()
    if (await bugBtn.count()) {
      await bugBtn.click()
      await expect(page.locator('.flow-debugger-panel')).toBeVisible({ timeout: TIMEOUT })
      await shot(page, '45-designer-debug')
      await page.keyboard.press('Escape')
    }

    // More menu: validate / shortcuts / export xml presence.
    await page.locator('.header-more-btn').click()
    await expect(
      page.getByText(/导入|导出|Import|Export|快捷键|Shortcuts|校验|Validate/i).first()
    ).toBeVisible({
      timeout: 5000,
    })
    await shot(page, '46-designer-more-menu')
    await page.keyboard.press('Escape')

    await assertNoPageErrors(errors)
  })
})

test.describe('Deep Operate canary & rollback', () => {
  test('canary evaluate → update % → promote', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/deployments/deploy-002')
    await expect(page.getByRole('heading', { name: /灰度发布|Canary release/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '47-canary-detail')

    await page.getByRole('button', { name: /健康评估|Evaluate Health/i }).click()
    await expect(page.getByText(/灰度版本健康|healthy|样本/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '48-canary-health')

    const slider = page.locator('.ant-slider-handle').first()
    await slider.focus()
    await page.keyboard.press('ArrowRight')
    await page.keyboard.press('ArrowRight')
    await page.getByRole('button', { name: /调整权重|Update (Canary|weight)/i }).click()
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /灰度比例已更新|灰度权重已更新|updated/i })
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page.getByRole('button', { name: /全量发布|Promote to 100%|Promote/i })
    ).toBeEnabled()
    await shot(page, '49-canary-updated')

    await page.getByRole('button', { name: /全量发布|Promote/i }).click()
    await confirmModalOk(page, /确认全量发布|Confirm promotion/i)
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /承接全部流量|promoted/i })
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '50-canary-promoted')
    await assertNoPageErrors(errors)
  })

  test('deploy wizard canary → abort', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const orderRow = page.locator('tbody tr').filter({ hasText: 'order-approval-bpmn' }).first()
    await orderRow.getByRole('button', { name: '部署' }).click()
    await page.waitForURL(/\/operate\/deploy-wizard/, { timeout: TIMEOUT })

    await selectFirstDropdownOption(page, /版本/)
    await page.getByRole('button', { name: '下一步' }).click()

    // Production already has a baseline route for this flow in mock data.
    await page.getByRole('combobox', { name: /别名/ }).click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /生产别名|PROD|production/i })
      .first()
      .click()

    await page.getByRole('combobox', { name: /策略|Strategy/i }).click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /灰度|Canary/i })
      .first()
      .click()
    await expect(page.getByText(/灰度流量权重|Canary traffic weight/i)).toBeVisible({
      timeout: 5000,
    })
    await shot(page, '51-wizard-canary')

    await page.getByRole('button', { name: '下一步' }).click()
    await page
      .locator('main')
      .getByRole('button', { name: /^(rocket\s*)?(立即部署|部署|Deploy now|Deploy)$/i })
      .click()
    await expect(page.getByText(/部署成功/)).toBeVisible({ timeout: TIMEOUT })
    await page.getByRole('button', { name: /查看部署/ }).click()
    await page.waitForURL(/\/operate\/deployments\//, { timeout: TIMEOUT })
    await expect(page.getByRole('heading', { name: /灰度发布|Canary release/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.locator('main')).toContainText('1.2.0')
    await shot(page, '52-new-canary-detail')

    await page.getByRole('button', { name: /中止灰度|Abort/i }).click()
    await confirmModalOk(page, /中止灰度|Abort/i)
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /灰度已中止|Aborted|中止/i })
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '53-canary-aborted')
    await assertNoPageErrors(errors)
  })

  test('rollback from completed baseline', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/deployments/deploy-003')
    await expect(page.getByRole('heading', { name: 'user-registration-bpmn' })).toBeVisible({
      timeout: TIMEOUT,
    })
    const rollback = page.getByRole('button', { name: /回滚|Rollback/i })
    await expect(rollback.first()).toBeVisible({ timeout: TIMEOUT })
    await rollback.first().click()
    await confirmModalOk(page, /回滚|Rollback/i)
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已回滚|回滚成功|Rolled/i })
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '54-rollback')
    await assertNoPageErrors(errors)
  })
})

test.describe('Deep Operate flows / monitoring / logs', () => {
  test('flow publish, duplicate, edit→designer', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const row = page.locator('tbody tr').filter({ hasText: 'order-approval-bpmn' }).first()
    await expect(row).toBeVisible({ timeout: TIMEOUT })

    const more = row.getByRole('button', { name: /更多操作|更多|More actions/i })
    await expect(more).toBeVisible({ timeout: TIMEOUT })
    await more.click()
    await page.getByRole('menuitem', { name: /发布|Publish/i }).click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })
    await shot(page, '55-flow-publish')

    await more.click()
    await page.getByRole('menuitem', { name: /复制|Duplicate/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /复制|Duplicate/i })
    ).toBeVisible({
      timeout: 8000,
    })
    await shot(page, '56-flow-duplicate')

    await row.getByRole('button', { name: '编辑' }).first().click()
    await page.waitForURL(/\/build\/designer.*processCode=order-approval-bpmn/, {
      timeout: TIMEOUT,
    })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(
      page.locator('.ant-tag').filter({ hasText: 'order-approval-bpmn' }).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '57-operate-edit-designer')
    await assertNoPageErrors(errors)
  })

  test('monitoring requeue controls', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/monitoring')
    await expect(page.getByText(/监控|Operations|控制/i).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '58-monitoring-deep')

    const requeueDeploy = page.getByRole('button', {
      name: /重新入队部署死信任务|Requeue deployment dead/i,
    })
    await expect(requeueDeploy.first()).toBeVisible({ timeout: TIMEOUT })
    await requeueDeploy.first().click()
    await page
      .getByRole('dialog')
      .getByRole('button', { name: /确\s*认|Confirm/i })
      .click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })
    await shot(page, '59-monitoring-requeue-deploy')

    const requeueAsync = page.getByRole('button', {
      name: /重新入队异步调用死信|Requeue async dead/i,
    })
    await expect(requeueAsync.first()).toBeVisible({ timeout: TIMEOUT })
    await requeueAsync.first().click()
    await page
      .getByRole('dialog')
      .getByRole('button', { name: /确\s*认|Confirm/i })
      .click()
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 8000 })
    await shot(page, '60-monitoring-requeue-async')

    await assertNoPageErrors(errors)
  })

  test('logs filters, export, duration detail', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/logs')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const statusSelect = page.locator('main .ant-select').first()
    if (await statusSelect.count()) {
      await statusSelect.click()
      const failed = page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .filter({ hasText: /失败|Failed|成功|Success/i })
        .first()
      if (await failed.count()) await failed.click()
      await page.waitForTimeout(400)
      await shot(page, '61-logs-filtered')
    }

    const exportBtn = page.getByRole('button', { name: /导出|Export/i })
    await expect(exportBtn.first()).toBeVisible()
    {
      const [dl] = await Promise.all([
        page.waitForEvent('download', { timeout: 8000 }),
        exportBtn.first().click(),
      ])
      expect(dl.suggestedFilename()).toMatch(/\.csv$/i)
      await shot(page, '62-logs-export')
    }

    await page.getByRole('button', { name: /清理日志|Purge logs/i }).click()
    const purgeDialog = page.getByRole('dialog')
    await expect(purgeDialog).toBeVisible()
    await expect(purgeDialog).toContainText(/永久删除|permanently deleted/i)
    const purgeBefore = purgeDialog.getByRole('textbox', {
      name: /清理此时间之前的日志|Purge logs before this time/i,
    })
    await expect(purgeBefore).not.toHaveValue('')
    await purgeBefore.hover()
    const clearPurgeBefore = purgeDialog.locator('.ant-picker-clear')
    await expect(clearPurgeBefore).toBeVisible()
    await clearPurgeBefore.click()
    await expect(purgeDialog.getByRole('button', { name: /确认清理|Purge logs/i })).toBeDisabled()
    await purgeDialog.getByRole('button', { name: /取\s*消|Cancel/i }).click()

    await page.getByRole('button', { name: /清理日志|Purge logs/i }).click()
    await expect(purgeDialog).toBeVisible()
    await purgeDialog.getByRole('button', { name: /确认清理|Purge logs/i }).click()
    await expect(purgeDialog).not.toBeVisible()
    await expect(page.getByText(/已清理 20 条执行日志|Purged 20 execution logs/i)).toBeVisible()
    await expect(page.getByText(/暂无执行日志|No execution logs/i)).toBeVisible()

    // Range picker should be interactive (controlled).
    const range = page.locator('.ant-picker-range')
    if (await range.count()) {
      await range.first().click()
      await expect(
        page.locator('.ant-picker-dropdown:not(.ant-picker-dropdown-hidden)')
      ).toBeVisible({
        timeout: 5000,
      })
      await shot(page, '63-logs-range')
      await page.keyboard.press('Escape')
    }

    const detail = page.getByRole('button', { name: '详情' }).first()
    if (await detail.count()) {
      await detail.click()
      const dialog = page.getByRole('dialog')
      await expect(dialog).toBeVisible({ timeout: 8000 })
      // duration 0 must not render as bare "-" for missing — look for duration label.
      await expect(dialog.getByText(/耗时|Duration/i)).toBeVisible()
      await expect(dialog).toContainText(
        /别名路由|指定版本|当前流程定义|Alias route|Explicit version|Current definition/
      )
      await expect(dialog.getByText(/^(alias|version|definition)$/)).toHaveCount(0)
      await shot(page, '64-logs-detail-duration')
      await page.keyboard.press('Escape')
    }

    await assertNoPageErrors(errors)
  })
})

test.describe('Deep designer node authoring smoke', () => {
  test('BPMN palette search + node count; TBBPM starts blank', async ({ page }) => {
    const errors = trackErrors(page)

    await page.goto('/build/designer?modelType=bpmn')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    const palette = page.locator('.bpmn-designer-left-sider')
    await expect(palette).toBeVisible({ timeout: TIMEOUT })
    for (const label of ['事件', '任务', '网关', '组合']) {
      await expect(
        palette.locator('.ant-collapse-header').filter({ hasText: label }).first()
      ).toBeVisible()
    }
    const searchInput = palette.getByPlaceholder('搜索节点…')
    await searchInput.fill('网关')
    await page.waitForTimeout(400)
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '排他网关' })
    ).toBeVisible()
    await shot(page, '65-bpmn-palette-search')

    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.x6-node')).toHaveCount(0)
    await shot(page, '66-tbbpm-blank-flow')
    await assertNoPageErrors(errors)
  })
})
