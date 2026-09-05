import { expect, test } from '@playwright/test'

import { installOperateApiMocks } from './helpers/operateApiMock'

const OPERATE_PROCESSES_URL = '/operate/processes'
const TIMEOUT = 20000

test.describe('运维流程 → 设计器桥接', () => {
  test.beforeEach(async ({ page }) => {
    await installOperateApiMocks(page)
  })
  test('从流程管理打开设计器并显示 processCode', async ({ page }) => {
    await page.goto(OPERATE_PROCESSES_URL)
    await expect(page.getByText('order-approval-bpmn')).toBeVisible({ timeout: TIMEOUT })

    const row = page.locator('tr').filter({ hasText: 'order-approval-bpmn' })
    await expect(row).toBeVisible({ timeout: TIMEOUT })

    const editButton = row.getByRole('button', { name: '编辑' })
    await expect(editButton).toBeEnabled()

    await editButton.click()
    await page.waitForURL(/\/build\/designer.*processCode=order-approval-bpmn/, {
      timeout: TIMEOUT,
    })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(
      page.locator('.ant-tag').filter({ hasText: 'order-approval-bpmn' }).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
  })

  test('运维来源保存显示成功提示', async ({ page }) => {
    await page.goto(
      '/build/designer?source=operateProcessCode&processCode=order-approval-bpmn&modelType=bpmn'
    )
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(
      page.locator('.ant-tag').filter({ hasText: 'order-approval-bpmn' }).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })

    await page
      .getByRole('button', { name: /保存|已保存/ })
      .first()
      .click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: '已保存到运维流程库' }).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
  })
})
