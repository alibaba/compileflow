import fs from 'node:fs'

import { expect, type Page, test } from '@playwright/test'

import { assertNoPageErrors, shot, TIMEOUT, trackErrors } from './journey-helpers'

const MINIMAL_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  id="Definitions_journey" targetNamespace="http://www.compileflow.org">
  <bpmn:process id="Process_journey_import" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始"/>
    <bpmn:endEvent id="EndEvent_1" name="结束"/>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1"/>
  </bpmn:process>
</bpmn:definitions>`

async function openDesignerTools(page: Page) {
  await page.goto('/build/designer?modelType=tbbpm&source=template&templateId=tpl-4')
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

test.describe('Remaining details — Settings / mobile / designer tools', () => {
  test.describe.configure({ timeout: 60_000 })
  test('settings language select + build info + mobile drawer', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByRole('heading', { name: '偏好设置', exact: true })).toBeVisible()
    await expect(page.getByText('mock', { exact: true })).toBeVisible()
    await shot(page, '80-settings-detail')

    await page.locator('.ant-select').first().click()
    await page.locator('.ant-select-item-option').filter({ hasText: 'English' }).click()
    await expect(page.getByRole('heading', { name: /Settings/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '81-settings-english')
    await page.locator('.ant-select').first().click()
    await page.locator('.ant-select-item-option').filter({ hasText: '中文' }).click()
    await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible({
      timeout: TIMEOUT,
    })

    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/learn')
    await page.getByRole('button', { name: /打开导航菜单|Open navigation menu/i }).click()
    await expect(
      page.locator('.ant-drawer-content-wrapper, .ant-drawer-content').first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByRole('menuitem', { name: /学习|Learn/i }).first()).toBeVisible()
    await page.getByRole('button', { name: /全局搜索|Global search/i }).click()
    await expect(page.getByRole('dialog', { name: /全局搜索|Global search/i })).toBeVisible()
    await page.keyboard.press('Escape')
    await page.getByRole('button', { name: /打开导航菜单|Open navigation menu/i }).click()
    await shot(page, '82-mobile-drawer')
    await page.setViewportSize({ width: 1024, height: 768 })
    await expect(page.locator('.ant-drawer-content-wrapper')).toBeHidden()

    await page.setViewportSize({ width: 390, height: 844 })
    await page.getByRole('button', { name: /打开导航菜单|Open navigation menu/i }).click()
    await page
      .getByRole('menuitem', { name: /构建|Build|Workspace/i })
      .first()
      .click()
    await page.waitForURL(/\/build/, { timeout: TIMEOUT })
    await expect(page.locator('.ant-drawer-content-wrapper')).toBeHidden()

    await page.getByRole('button', { name: /打开导航菜单|Open navigation menu/i }).click()
    await page.getByRole('button', { name: /应用菜单|Application menu/i }).click()
    await page
      .locator('.ant-dropdown:visible')
      .getByRole('menuitem', { name: /设置|Settings/i })
      .click()
    await page.waitForURL(/\/settings/, { timeout: TIMEOUT })
    await expect(page.locator('.ant-drawer-content-wrapper')).toBeHidden()
    await expect(page.locator('.ant-dropdown:visible')).toHaveCount(0)
    await assertNoPageErrors(errors)
  })

  test('mobile designer keeps the viewport usable and exposes mutually exclusive panels', async ({
    page,
  }) => {
    const errors = trackErrors(page)
    const consoleErrors: string[] = []
    page.on('console', (message) => {
      if (message.type() === 'error') consoleErrors.push(message.text())
    })
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await page.waitForSelector('.x6-graph-scroller', { timeout: TIMEOUT })

    await expect(
      page.getByRole('button', { name: /已保存|保存更改|Saved|Save changes/i })
    ).toBeVisible()
    await page.getByRole('button', { name: /更多操作|More actions/i }).click()
    await page.getByRole('menuitem', { name: /工具|Tools/i }).click()
    await expect(page.getByRole('menuitem', { name: /验证流程|Validate flow/i })).toBeVisible()
    await page.keyboard.press('Escape')
    await page.keyboard.press('Escape')

    await expect
      .poll(() =>
        page.evaluate(
          () => document.documentElement.scrollWidth - document.documentElement.clientWidth
        )
      )
      .toBe(0)
    await expect
      .poll(() =>
        page.evaluate(() => {
          const wrapper = document.querySelector('.tbbpm-canvas-wrapper')
          const scroller = document.querySelector('.x6-graph-scroller')
          return wrapper && scroller
            ? Math.abs(
                wrapper.getBoundingClientRect().width - scroller.getBoundingClientRect().width
              )
            : Number.POSITIVE_INFINITY
        })
      )
      .toBeLessThan(2)

    await page.getByRole('button', { name: /展开节点面板|Expand palette/i }).click()
    await expect(page.locator('.tbbpm-designer-left-sider')).toHaveAttribute('aria-hidden', 'false')
    await expect(page.locator('.tbbpm-designer-right-sider')).toHaveAttribute('aria-hidden', 'true')
    const nodeCount = await page.locator('.x6-node').count()
    const automaticTask = page
      .locator('.drag-palette-item')
      .filter({ hasText: /自动任务|Auto/i })
      .first()
    await automaticTask.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.x6-node')).toHaveCount(nodeCount + 1)

    await expect(page.locator('.tbbpm-designer-left-sider')).toHaveAttribute('aria-hidden', 'true')
    await expect(page.locator('.tbbpm-designer-right-sider')).toHaveAttribute('aria-hidden', 'true')
    await page.getByRole('button', { name: /展开属性面板|Expand properties/i }).click()
    await expect(page.locator('.tbbpm-designer-left-sider')).toHaveAttribute('aria-hidden', 'true')
    await expect(page.locator('.tbbpm-designer-right-sider')).toHaveAttribute(
      'aria-hidden',
      'false'
    )

    await page.goto('/operate')
    await expect(page.getByRole('heading', { name: /运维|Operate/i }).first()).toBeVisible()
    expect(consoleErrors.filter((message) => message.includes('synchronously unmount'))).toEqual([])
    await assertNoPageErrors(errors)
  })

  test('designer validate, shortcuts, help, variables, history, search, props', async ({
    page,
  }) => {
    const errors = trackErrors(page)
    await openDesignerTools(page)

    await page.getByRole('button', { name: /验证流程|Validate/i }).click()
    await expect(
      page
        .locator('.validation-result-panel, .ant-tabs-tab')
        .filter({ hasText: /验证|Validate/i })
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '83-designer-validate')

    await page.getByRole('button', { name: /快捷键|Keyboard shortcuts/i }).click()
    const shortcutsDialog = page.getByRole('dialog', { name: /快捷键|Keyboard shortcuts/i })
    await expect(shortcutsDialog).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '84-designer-shortcuts')
    await page.keyboard.press('Escape')
    await expect(shortcutsDialog).toBeHidden({ timeout: TIMEOUT })

    await page.getByRole('button', { name: /帮助文档|Help/i }).click()
    const helpDialog = page.getByRole('dialog', { name: /^帮助$|Help/i })
    await expect(helpDialog).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '85-designer-help')
    await page.keyboard.press('Escape')
    await expect(helpDialog).toBeHidden({ timeout: TIMEOUT })

    await page.getByRole('button', { name: /变量管理|Variable/i }).click()
    const variablesDialog = page.getByRole('dialog', { name: /流程变量|Process variables/i })
    await expect(variablesDialog).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '86-designer-variables')
    await page.keyboard.press('Escape')
    await expect(variablesDialog).toBeHidden({ timeout: TIMEOUT })

    const snapshots = page.getByRole('button', { name: /本地快照|Local snapshots/i })
    await expect(snapshots).toBeVisible({ timeout: TIMEOUT })
    await snapshots.click()
    const snapshotsDialog = page.getByRole('dialog', { name: /本地快照|Local snapshots/i })
    await expect(snapshotsDialog).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '87-designer-snapshots')
    await page.keyboard.press('Escape')
    await expect(snapshotsDialog).toBeHidden({ timeout: TIMEOUT })

    await page.getByRole('button', { name: /搜索节点|Search nodes/i }).click()
    const searchDialog = page.getByRole('dialog', { name: /搜索节点|Search nodes/i })
    await expect(searchDialog).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '88-designer-node-search')
    await page.keyboard.press('Escape')
    await expect(searchDialog).toBeHidden({ timeout: TIMEOUT })

    await page.getByRole('button', { name: /切换网格|Toggle grid/i }).click()
    await page.waitForTimeout(200)
    await shot(page, '89-designer-grid-toggled')

    const node = page.locator('.x6-node').first()
    if (await node.count()) {
      await node.click()
      await expect(page.locator('.ant-layout-sider').last()).toBeVisible()
      await shot(page, '90-designer-node-props')
    }

    await assertNoPageErrors(errors)
  })
})

test.describe('Remaining details — Operate filters / import / list actions', () => {
  test.describe.configure({ timeout: 60_000 })
  test('deployment filters, list rollback/abort affordance, flow import, async ledger', async ({
    page,
  }) => {
    const errors = trackErrors(page)

    await page.goto('/operate/deployments')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const statusSelect = page.locator('main .ant-select').first()
    await statusSelect.click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /进行中|in progress|已完成|completed/i })
      .first()
      .click()
    await page.waitForTimeout(300)
    await shot(page, '91-deployments-filtered')

    // List-level rollback/abort for rows with baseline.
    const listAction = page.getByRole('button', { name: /回滚|中止灰度|Rollback|Abort/i }).first()
    if (await listAction.count()) {
      await expect(listAction).toBeVisible()
      await shot(page, '92-deployments-list-actions')
    }

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const importPath = test.info().outputPath(`journey-import-${Date.now()}.bpmn`)
    fs.writeFileSync(importPath, MINIMAL_BPMN, 'utf8')
    const fileInput = page.locator('input[type="file"]')
    await expect(fileInput).toBeAttached()
    await fileInput.setInputFiles(importPath)
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /导入成功|已导入|imported|Import/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '93-flow-import')
    fs.unlinkSync(importPath)

    // Delete confirm opens then cancel — no destructive commit in review.
    const row = page.locator('tbody tr').first()
    const more = row.getByRole('button', { name: /更多操作|更多|More actions/i })
    if (await more.count()) {
      await more.click()
      await page.getByRole('menuitem', { name: /删除|Delete/i }).click()
      await expect(page.getByRole('dialog')).toBeVisible({ timeout: TIMEOUT })
      await shot(page, '94-flow-delete-confirm')
      await page.getByRole('button', { name: /否|No|取消|Cancel/i }).click()
    }

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/异步|Async/i).first()).toBeVisible({ timeout: TIMEOUT })
    const statusFilter = page.getByLabel(/Invocation status|调用状态|状态/i).first()
    if (await statusFilter.count()) {
      await statusFilter.click()
      const statusOption = page
        .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
        .filter({ hasText: /dead_letter|死信|succeeded|成功/i })
        .first()
      await expect(statusOption).toBeVisible({ timeout: TIMEOUT })
      await statusOption.click()
    }
    await shot(page, '95-async-ledger')
    await assertNoPageErrors(errors)
  })
})

test.describe('Remaining details — BPMN node props + OperateHome recent', () => {
  test('BPMN click node shows properties; operate home recent deploy navigates', async ({
    page,
  }) => {
    const errors = trackErrors(page)

    await page.goto('/build/designer?modelType=bpmn')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    const serviceTask = page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first()
    const graphNodes = page.locator('.x6-node')
    const nodeCount = await graphNodes.count()
    await expect(serviceTask).toBeEnabled({ timeout: TIMEOUT })
    await serviceTask.focus()
    await page.keyboard.press('Enter')
    await expect(graphNodes).toHaveCount(nodeCount + 1, { timeout: TIMEOUT })
    await expect(graphNodes.filter({ hasText: '服务任务' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    const renderedServiceTask = graphNodes.filter({ hasText: '服务任务' }).first()
    const serviceTaskBox = await renderedServiceTask.boundingBox()
    expect(serviceTaskBox).not.toBeNull()
    await page.mouse.click(
      serviceTaskBox!.x + serviceTaskBox!.width / 2,
      serviceTaskBox!.y + serviceTaskBox!.height / 2
    )
    const propertiesPanel = page.locator('.bpmn-designer-right-sider, .ant-layout-sider').last()
    await expect(propertiesPanel.getByRole('tab', { name: /通用|General/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '96-bpmn-node-props')

    await page.goto('/operate')
    const recent = page
      .locator('main')
      .getByText(/order-approval|payment-process|deploy-/i)
      .first()
    if (await recent.count()) {
      await recent.click()
      await page.waitForURL(/\/operate\/(deployments|flows)/, { timeout: TIMEOUT })
      await shot(page, '97-operate-home-recent')
    }

    await assertNoPageErrors(errors)
  })
})
