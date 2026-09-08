import { expect, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm&source=new'
const TIMEOUT = 20_000

test.describe('canonical graph actions', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(DESIGNER_URL)
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.x6-node')).toHaveCount(2, { timeout: TIMEOUT })
  })

  test('Ctrl+D preserves configured properties and undo/redo treats the clone atomically', async ({
    page,
  }) => {
    const nodes = page.locator('.x6-node')
    const taskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    await taskItem.focus()
    await page.keyboard.press('Enter')
    await expect(nodes).toHaveCount(3, { timeout: TIMEOUT })

    await page.getByRole('tab', { name: /任务属性/ }).click()
    await page.getByRole('textbox', { name: 'Java 类名' }).fill('com.example.CloneSource')
    await page.locator('.x6-canvas-toolbar button[aria-label^="放大"]').focus()
    await page.keyboard.press('Control+d')

    await expect(nodes).toHaveCount(4, { timeout: TIMEOUT })
    await expect(nodes.last()).toContainText('自动任务_副本')
    await nodes.last().getByText('自动任务_副本', { exact: true }).click({ force: true })
    await page.getByRole('tab', { name: /任务属性/ }).click()
    await expect(page.getByRole('textbox', { name: 'Java 类名' })).toHaveValue(
      'com.example.CloneSource'
    )

    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(nodes).toHaveCount(3, { timeout: TIMEOUT })
    await page.getByRole('button', { name: /重做/ }).click()
    await expect(nodes).toHaveCount(4, { timeout: TIMEOUT })
  })

  test('multi-selection delete and edge-only delete each undo in one step', async ({ page }) => {
    const nodes = page.locator('.x6-node')
    const edges = page.locator('.x6-edge')
    const initialEdges = await edges.count()

    await page.keyboard.press('ControlOrMeta+A')
    await page.keyboard.press('Delete')
    await expect(nodes).toHaveCount(0, { timeout: TIMEOUT })
    await expect(edges).toHaveCount(0, { timeout: TIMEOUT })
    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(nodes).toHaveCount(2, { timeout: TIMEOUT })
    await expect(edges).toHaveCount(initialEdges, { timeout: TIMEOUT })

    await edges.first().locator('path').first().click({ force: true })
    await expect(page.getByText('连接线属性')).toBeVisible({ timeout: TIMEOUT })
    await page.keyboard.press('Delete')
    await expect(edges).toHaveCount(initialEdges - 1, { timeout: TIMEOUT })
    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(edges).toHaveCount(initialEdges, { timeout: TIMEOUT })
  })
})
