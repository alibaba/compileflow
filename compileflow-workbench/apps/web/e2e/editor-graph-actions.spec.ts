import { expect, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm&source=template&templateId=tpl-4'
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
    await page.getByRole('button', { name: '适应画布', exact: true }).focus()
    await page.keyboard.press('Control+d')

    await expect(nodes).toHaveCount(4, { timeout: TIMEOUT })
    await expect(nodes.last()).toContainText('自动任务_副本')
    await nodes.last().click()
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

    const edgeMidpoint = await edges
      .first()
      .locator('path')
      .first()
      .evaluate((element) => {
        const path = element as SVGPathElement
        const localPoint = path.getPointAtLength(path.getTotalLength() / 2)
        const screenMatrix = path.getScreenCTM()
        if (!screenMatrix) throw new Error('Edge path is not rendered')
        const screenPoint = localPoint.matrixTransform(screenMatrix)
        return { x: screenPoint.x, y: screenPoint.y }
      })
    await page.mouse.click(edgeMidpoint.x, edgeMidpoint.y)
    await expect(page.getByText('连接线属性')).toBeVisible({ timeout: TIMEOUT })
    await page.keyboard.press('Delete')
    await expect(edges).toHaveCount(initialEdges - 1, { timeout: TIMEOUT })
    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(edges).toHaveCount(initialEdges, { timeout: TIMEOUT })
  })

  test('node search replaces a stale canvas selection before keyboard delete', async ({ page }) => {
    const nodes = page.locator('.x6-node')
    await nodes.filter({ hasText: 'Start' }).click()

    await page.keyboard.press('ControlOrMeta+f')
    const searchDialog = page.getByRole('dialog', { name: /搜索节点/ })
    await searchDialog.getByRole('searchbox', { name: /搜索节点/ }).fill('End')
    await searchDialog.locator('.node-search-result-item').filter({ hasText: 'End' }).click()
    await page.keyboard.press('Delete')

    await expect(nodes.filter({ hasText: 'Start' })).toHaveCount(1)
    await expect(nodes.filter({ hasText: 'End' })).toHaveCount(0)
    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(nodes).toHaveCount(2, { timeout: TIMEOUT })
  })

  test('dragging one of multiple selected nodes persists and undoes the whole selection', async ({
    page,
  }) => {
    const start = page.locator('.x6-node').filter({ hasText: 'Start' })
    const end = page.locator('.x6-node').filter({ hasText: 'End' })
    await page.keyboard.press('ControlOrMeta+A')
    await expect(page.locator('.x6-widget-selection-box')).toHaveCount(2)

    const startBefore = await start.boundingBox()
    const endBefore = await end.boundingBox()
    if (!startBefore || !endBefore) throw new Error('Selected nodes are not rendered')
    await page.mouse.move(
      startBefore.x + startBefore.width / 2,
      startBefore.y + startBefore.height / 2
    )
    await page.mouse.down()
    await page.mouse.move(
      startBefore.x + startBefore.width / 2 + 80,
      startBefore.y + startBefore.height / 2 + 40,
      {
        steps: 8,
      }
    )
    await page.mouse.up()

    const startAfter = await start.boundingBox()
    const endAfter = await end.boundingBox()
    if (!startAfter || !endAfter) throw new Error('Moved nodes are not rendered')
    expect(startAfter.x - startBefore.x).toBeCloseTo(80, 0)
    expect(startAfter.y - startBefore.y).toBeCloseTo(40, 0)
    expect(endAfter.x - endBefore.x).toBeCloseTo(80, 0)
    expect(endAfter.y - endBefore.y).toBeCloseTo(40, 0)

    await page.getByRole('button', { name: /撤销/ }).click()
    const startUndone = await start.boundingBox()
    const endUndone = await end.boundingBox()
    expect(startUndone?.x).toBeCloseTo(startBefore.x, 0)
    expect(startUndone?.y).toBeCloseTo(startBefore.y, 0)
    expect(endUndone?.x).toBeCloseTo(endBefore.x, 0)
    expect(endUndone?.y).toBeCloseTo(endBefore.y, 0)
  })
})
