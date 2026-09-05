import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=bpmn'
const TIMEOUT = 20_000

async function gotoBpmnDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.bpmn-designer-left-sider')).toBeVisible({ timeout: TIMEOUT })
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

test.describe('BPMN palette node creation', () => {
  test.beforeEach(async ({ page }) => {
    await gotoBpmnDesigner(page)
  })

  test('palette items are keyboard-accessible node creation controls', async ({ page }) => {
    const serviceTask = page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first()
    await expect(serviceTask).toBeVisible()
    await expect(serviceTask).toBeEnabled({ timeout: TIMEOUT })
    await expect(serviceTask).toHaveJSProperty('tagName', 'BUTTON')
    expect(await serviceTask.evaluate((element) => element.tabIndex)).toBe(0)
    await expect(serviceTask).toHaveAttribute('aria-label', /服务任务/)
    const cursor = await serviceTask.evaluate((el) => window.getComputedStyle(el).cursor)
    expect(cursor).toBe('grab')
  })

  test('pressing Enter on a palette item creates a persisted BPMN node', async ({ page }) => {
    const serviceTask = page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first()
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()

    await expect(serviceTask).toBeEnabled({ timeout: TIMEOUT })
    await serviceTask.focus()
    await page.keyboard.press('Enter')

    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: '服务任务' }).first()).toBeVisible()
    await expect(page.getByRole('tab', { name: /通用|General/i })).toBeVisible({
      timeout: TIMEOUT,
    })
  })

  test('pointer drag from palette places a node on the canvas', async ({ page }) => {
    const serviceTask = page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first()
    const canvas = page.locator('.bpmn-canvas-wrapper, .x6-graph').first()
    await expect(serviceTask).toBeVisible()
    await expect(serviceTask).toBeEnabled({ timeout: TIMEOUT })
    await expect(canvas).toBeVisible()

    const beforeCount = await page.locator('.x6-node').count()
    const itemBox = await serviceTask.boundingBox()
    const canvasBox = await canvas.boundingBox()
    expect(itemBox).toBeTruthy()
    expect(canvasBox).toBeTruthy()

    const startX = itemBox!.x + itemBox!.width / 2
    const startY = itemBox!.y + itemBox!.height / 2
    const endX = canvasBox!.x + canvasBox!.width * 0.55
    const endY = canvasBox!.y + canvasBox!.height * 0.45

    await page.mouse.move(startX, startY)
    await page.mouse.down()
    await page.waitForTimeout(100)
    await page.mouse.move(endX, endY, { steps: 24 })
    await page.waitForTimeout(100)
    await page.mouse.up()

    await expect
      .poll(async () => page.locator('.x6-node').count(), { timeout: TIMEOUT })
      .toBeGreaterThan(beforeCount)
  })

  test('canvas renders the X6 grid layer used as the placement guide', async ({ page }) => {
    await expect(page.locator('.bpmn-canvas-wrapper, .bpmn-canvas').first()).toBeVisible()
    await expect(page.locator('.x6-graph-svg').first()).toBeVisible()
    await expect(page.locator('.x6-graph-grid').first()).toBeVisible()
  })
})
