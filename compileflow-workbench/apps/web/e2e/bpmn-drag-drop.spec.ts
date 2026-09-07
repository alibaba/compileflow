import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=bpmn'
const TIMEOUT = 20_000

async function gotoBpmnDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await page.waitForLoadState('networkidle')
  await expect(page.locator('.bpmn-designer-left-sider')).toBeVisible({ timeout: TIMEOUT })
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

async function selectOption(page: Page, label: string, option: string) {
  await page.getByRole('combobox', { name: label }).click()
  await page
    .locator('.ant-select-dropdown:visible .ant-select-item-option-content')
    .filter({ hasText: option })
    .click()
}

async function fillScriptSource(page: Page, source: string) {
  const editor = page
    .locator('.monaco-editor')
    .filter({ has: page.getByRole('textbox', { name: '源码' }) })
  await editor.locator('.view-lines').click()
  await page.keyboard.press('ControlOrMeta+A')
  await page.keyboard.insertText(source)
  await expect(page.locator('.monaco-editor .view-lines')).toContainText(source)
}

async function exerciseSpecificProperties(page: Page, label: string) {
  switch (label) {
    case '服务任务':
      await page.getByRole('textbox', { name: 'Java 类名' }).fill('com.example.PaymentService')
      await expect(page.getByRole('textbox', { name: 'Java 类名' })).toHaveValue(
        'com.example.PaymentService'
      )
      break
    case '脚本任务':
      await page.getByRole('combobox', { name: '语言' }).fill('qlexpress')
      await fillScriptSource(page, 'result = amount + 1;')
      break
    case '接收任务':
      await page.getByRole('combobox', { name: '消息 ID（messageRef）' }).fill('payment_received')
      await page.getByRole('combobox', { name: '消息 ID（messageRef）' }).press('Tab')
      await page.getByRole('textbox', { name: '运行时事件名' }).fill('payment.received')
      await expect(page.getByRole('textbox', { name: '运行时事件名' })).toHaveValue(
        'payment.received'
      )
      break
    case '排他网关':
    case '包容网关':
      await expect(page.getByRole('combobox', { name: '默认出向流' })).toBeVisible()
      break
    case '并行网关':
      await expect(page.getByText(/无需配置额外属性/)).toBeVisible()
      break
    case '调用活动':
      await page.getByRole('textbox', { name: '被调用元素（calledElement）' }).fill('child.flow')
      await selectOption(page, '目标类型', '精确版本')
      await page.getByRole('textbox', { name: '子流程固定版本' }).fill('3.0.0')
      await expect(page.getByRole('textbox', { name: '子流程固定版本' })).toHaveValue('3.0.0')
      break
    case '嵌入式子流程':
      await expect(page.getByRole('combobox', { name: '直属子节点' })).toBeVisible()
      await selectOption(page, '循环模式', '标准 while/until 循环')
      await page.getByRole('textbox', { name: '循环条件表达式' }).fill('remaining > 0')
      await page.getByRole('spinbutton', { name: '最大迭代次数' }).fill('5')
      await expect(page.getByRole('textbox', { name: '循环条件表达式' })).toHaveValue(
        'remaining > 0'
      )
      break
  }
}

async function exerciseBpmnNode(page: Page, label: string) {
  const paletteItem = page.locator('.drag-palette-item').filter({ hasText: label }).first()
  if (!(await paletteItem.isVisible())) {
    await page.locator('.ant-collapse-header').filter({ hasText: '组合' }).click()
  }

  const graphNodes = page.locator('.x6-node')
  const initialCount = await graphNodes.count()
  await expect(paletteItem).toBeEnabled({ timeout: TIMEOUT })
  await paletteItem.focus()
  await page.keyboard.press('Enter')
  await expect(graphNodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })

  const createdNode = graphNodes.last()
  const surface = createdNode.locator('.bpmn-node')
  await expect(surface).toBeVisible({ timeout: TIMEOUT })
  await expect(createdNode.locator('.tbbpm-node')).toHaveCount(0)
  await expect(surface.locator('svg')).toBeVisible()
  const visualCoverage = await createdNode.evaluate((cell) => {
    const nodeSurface = cell.querySelector<HTMLElement>('.bpmn-node')
    const foreignObject = cell.querySelector<SVGForeignObjectElement>('foreignObject')
    if (!nodeSurface || !foreignObject) return { width: 0, height: 0 }
    const cellBox = foreignObject.getBoundingClientRect()
    const surfaceBox = nodeSurface.getBoundingClientRect()
    return {
      width: surfaceBox.width / cellBox.width,
      height: surfaceBox.height / cellBox.height,
    }
  })
  expect(visualCoverage.width).toBeGreaterThan(0.9)
  expect(visualCoverage.height).toBeGreaterThan(0.9)

  const tabs = page.locator('.bpmn-designer-right-sider .ant-tabs-tab')
  const hasTypeProperties = label !== '流程开始' && label !== '流程结束'
  await expect(tabs).toHaveCount(hasTypeProperties ? 2 : 1, { timeout: TIMEOUT })
  if (hasTypeProperties) {
    await tabs.nth(1).click()
    await expect(
      page.locator('.bpmn-designer-right-sider .ant-tabs-tabpane-active')
    ).not.toBeEmpty()
    await exerciseSpecificProperties(page, label)
    await tabs.first().click()
  }

  const editedName = `${label}已编辑`
  await page.getByRole('textbox', { name: '节点名称' }).fill(editedName)
  await expect(createdNode).toContainText(editedName)
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

  test('clicking a palette button creates exactly one BPMN node', async ({ page }) => {
    const serviceTask = page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first()
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()

    await serviceTask.click()

    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(graphNodes.last()).toContainText('服务任务')
  })

  const nodeLabels = [
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

  for (const label of nodeLabels) {
    test(`${label}节点渲染、选中并支持属性编辑`, async ({ page }) => {
      await exerciseBpmnNode(page, label)
    })
  }

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

    await expect(page.locator('.x6-node')).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
  })

  test('canvas renders the X6 grid layer used as the placement guide', async ({ page }) => {
    await expect(page.locator('.bpmn-canvas-wrapper, .bpmn-canvas').first()).toBeVisible()
    await expect(page.locator('.x6-graph-svg').first()).toBeVisible()
    await expect(page.locator('.x6-graph-grid').first()).toBeVisible()
  })
})
