import fs from 'node:fs'

import { expect, type Locator, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm&source=new'
const TIMEOUT = 20_000

const NODE_VISUALS: Record<string, { className: string; width: number; height: number }> = {
  开始: { className: 'tbbpm-node-start', width: 80, height: 80 },
  结束: { className: 'tbbpm-node-end', width: 80, height: 80 },
  自动任务: { className: 'tbbpm-node-autotask', width: 200, height: 100 },
  等待任务: { className: 'tbbpm-node-waittask', width: 200, height: 100 },
  等待事件: { className: 'tbbpm-node-waiteventtask', width: 200, height: 100 },
  定时任务: { className: 'tbbpm-node-waittask', width: 200, height: 100 },
  脚本任务: { className: 'tbbpm-node-scripttask', width: 200, height: 100 },
  排他网关: { className: 'tbbpm-node-exclusive', width: 100, height: 100 },
  并行网关: { className: 'tbbpm-node-parallel', width: 100, height: 100 },
  包容网关: { className: 'tbbpm-node-inclusive', width: 100, height: 100 },
  '内嵌 BPM': { className: 'tbbpm-node-subbpm', width: 220, height: 120 },
  'BPM 调用': { className: 'tbbpm-node-subbpm', width: 220, height: 120 },
  条件循环: { className: 'tbbpm-node-while', width: 220, height: 120 },
  集合遍历: { className: 'tbbpm-node-foreach', width: 220, height: 120 },
  继续循环: { className: 'tbbpm-node-continue', width: 120, height: 60 },
  中断循环: { className: 'tbbpm-node-break', width: 120, height: 60 },
  注释: { className: 'tbbpm-node-note', width: 180, height: 120 },
}

const COLLAPSED_CATEGORY_BY_LABEL: Record<string, string> = {
  '内嵌 BPM': '子流程',
  'BPM 调用': '子流程',
  条件循环: '循环',
  集合遍历: '循环',
  继续循环: '循环控制',
  中断循环: '循环控制',
  注释: '注释',
}

async function getPaletteItem(page: Page, label: string) {
  const item = page.locator('.drag-palette-item').filter({ hasText: label }).first()
  const category = COLLAPSED_CATEGORY_BY_LABEL[label]
  if (category && !(await item.isVisible())) {
    await page
      .locator('.palette-category-title')
      .filter({ hasText: new RegExp(`^${category}$`) })
      .click()
  }
  await expect(item).toBeVisible({ timeout: TIMEOUT })
  await item.scrollIntoViewIfNeeded()
  return item
}

async function dragToCanvas(page: Page, item: Locator) {
  const canvas = page.locator('.tbbpm-canvas, .x6-graph').first()
  const canvasBox = await canvas.boundingBox()
  expect(canvasBox).toBeTruthy()
  await item.hover()
  await page.mouse.down()
  await page.waitForTimeout(100)
  await page.mouse.move(
    canvasBox!.x + Math.min(canvasBox!.width * 0.4, 420),
    canvasBox!.y + canvasBox!.height * 0.55,
    { steps: 24 }
  )
  await page.waitForTimeout(100)
  await page.mouse.up()
}

async function gotoTbbpmDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await page.waitForLoadState('networkidle')
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
  await expect(page.locator('.x6-node')).toHaveCount(2, { timeout: TIMEOUT })
}

async function exportXmlText(page: Page): Promise<string> {
  const downloadPromise = page.waitForEvent('download')
  await page.locator('.header-more-btn').click()
  await page.getByText('导出 XML').click()
  const download = await downloadPromise
  const tempPath = test.info().outputPath(`tbbpm-export-${Date.now()}.xml`)
  await download.saveAs(tempPath)
  const xml = fs.readFileSync(tempPath, 'utf-8')
  return xml
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
  if (['继续循环', '中断循环'].includes(label)) {
    await page.getByRole('textbox', { name: '执行条件（可选）' }).fill('shouldStop')
    await expect(page.getByRole('textbox', { name: '执行条件（可选）' })).toHaveValue('shouldStop')
    return
  }

  switch (label) {
    case '自动任务':
      await page.getByRole('textbox', { name: 'Java 类名' }).fill('com.example.OrderService')
      await expect(page.getByRole('textbox', { name: 'Java 类名' })).toHaveValue(
        'com.example.OrderService'
      )
      break
    case '等待任务':
      await page.getByRole('textbox', { name: '超时' }).fill('PT30M')
      await expect(page.getByRole('textbox', { name: '超时' })).toHaveValue('PT30M')
      break
    case '等待事件':
      await page.getByRole('textbox', { name: '事件名称' }).fill('approval.completed')
      await page.getByRole('textbox', { name: '超时' }).fill('PT1H')
      await expect(page.getByRole('textbox', { name: '事件名称' })).toHaveValue(
        'approval.completed'
      )
      break
    case '定时任务':
      await page.getByRole('textbox', { name: '固定时长' }).fill('PT45S')
      await selectOption(page, '定时方式', '时长表达式')
      await page.getByRole('textbox', { name: '时长表达式' }).fill('delay')
      await expect(page.getByRole('textbox', { name: '时长表达式' })).toHaveValue('delay')
      break
    case '脚本任务':
      await page.getByRole('combobox', { name: '语言' }).fill('qlexpress')
      await fillScriptSource(page, 'result = amount + 1;')
      break
    case '排他网关':
    case '包容网关':
      await expect(page.getByText('暂无出边')).toBeVisible()
      break
    case '并行网关':
      await expect(page.getByText('无需额外配置')).toBeVisible()
      break
    case '内嵌 BPM':
      await expect(page.getByRole('combobox', { name: '内部节点' })).toBeVisible()
      break
    case 'BPM 调用':
      await page.getByRole('textbox', { name: 'BPM 代码' }).fill('payment.flow')
      await selectOption(page, '目标类型', '精确版本')
      await page.getByRole('textbox', { name: '子流程固定版本' }).fill('2.0.0')
      await expect(page.getByRole('textbox', { name: '子流程固定版本' })).toHaveValue('2.0.0')
      break
    case '条件循环':
      await page.getByRole('textbox', { name: '循环条件表达式' }).fill('counter < 10')
      await page.getByRole('spinbutton', { name: '最大迭代次数' }).fill('10')
      await page.getByRole('textbox', { name: '索引变量名' }).fill('iteration')
      await expect(page.getByRole('textbox', { name: '循环条件表达式' })).toHaveValue(
        'counter < 10'
      )
      break
    case '集合遍历':
      await selectOption(page, '执行方式', '并行执行')
      await page.getByRole('textbox', { name: '集合变量名' }).fill('orders')
      await page.getByRole('textbox', { name: '元素变量名' }).fill('order')
      await page.getByRole('textbox', { name: '元素类型' }).fill('com.example.Order')
      await page.getByRole('textbox', { name: '索引变量名' }).fill('index')
      await expect(page.getByRole('textbox', { name: '集合变量名' })).toHaveValue('orders')
      break
    case '注释':
      await page.getByRole('textbox', { name: '注释内容' }).fill('完整属性验收')
      await expect(page.locator('.x6-node').last()).toContainText('完整属性验收')
      break
  }
}

async function exerciseNodeGroup(page: Page, labels: readonly string[]) {
  const graphNodes = page.locator('.x6-node')
  const initialCount = await graphNodes.count()

  for (const [index, label] of labels.entries()) {
    const paletteItem = await getPaletteItem(page, label)
    await expect(paletteItem).toBeEnabled({ timeout: TIMEOUT })
    await paletteItem.focus()
    await page.keyboard.press('Enter')
    await expect(graphNodes).toHaveCount(initialCount + index + 1, { timeout: TIMEOUT })

    const createdNode = graphNodes.last()
    await expect(createdNode.locator('.tbbpm-node')).toBeVisible({ timeout: TIMEOUT })
    const expectedVisual = NODE_VISUALS[label]
    await expect(createdNode.locator(`.${expectedVisual.className}`)).toBeVisible()
    const visualCoverage = await createdNode.evaluate((cell) => {
      const surface = cell.querySelector<HTMLElement>('.tbbpm-node')
      const foreignObject = cell.querySelector<SVGForeignObjectElement>('foreignObject')
      if (!surface || !foreignObject) return { width: 0, height: 0 }
      const cellBox = foreignObject.getBoundingClientRect()
      const surfaceBox = surface.getBoundingClientRect()
      return {
        width: surfaceBox.width / cellBox.width,
        height: surfaceBox.height / cellBox.height,
      }
    })
    expect(visualCoverage.width).toBeGreaterThan(0.9)
    expect(visualCoverage.height).toBeGreaterThan(0.9)
    const cellSize = await createdNode.locator('foreignObject').evaluate((foreignObject) => ({
      width: Number(foreignObject.getAttribute('width')),
      height: Number(foreignObject.getAttribute('height')),
    }))
    expect(cellSize).toEqual({ width: expectedVisual.width, height: expectedVisual.height })

    const tabs = page.locator('.tbbpm-designer-right-sider .ant-tabs-tab')
    const hasTypeProperties = label !== '开始' && label !== '结束'
    await expect(tabs).toHaveCount(hasTypeProperties ? 2 : 1, { timeout: TIMEOUT })
    if (hasTypeProperties) {
      await tabs.nth(1).click()
      await expect(
        page.locator('.tbbpm-designer-right-sider .ant-tabs-tabpane-active')
      ).not.toBeEmpty()
      await exerciseSpecificProperties(page, label)
      await tabs.first().click()
    }
    const editedName = `${label}已编辑`
    await page.getByRole('textbox', { name: '节点名称' }).fill(editedName)
    await expect(graphNodes.last()).toContainText(editedName)
  }
}

test.describe('TBBPM palette node creation', () => {
  test.beforeEach(async ({ page }) => {
    await gotoTbbpmDesigner(page)
  })

  test('palette items are keyboard-accessible node creation controls', async ({ page }) => {
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()

    await expect(autoTaskItem).toBeVisible()
    await expect(autoTaskItem).toBeEnabled({ timeout: TIMEOUT })
    await expect(autoTaskItem).toHaveJSProperty('tagName', 'BUTTON')
    expect(await autoTaskItem.evaluate((element) => element.tabIndex)).toBe(0)
    await expect(autoTaskItem).toHaveAttribute('aria-label', /自动任务/)

    const cursor = await autoTaskItem.evaluate((el) => window.getComputedStyle(el).cursor)
    expect(cursor).toBe('grab')
  })

  test('pressing Enter on a palette item creates a persisted TBBPM node', async ({ page }) => {
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()

    await expect(autoTaskItem).toBeEnabled({ timeout: TIMEOUT })
    await autoTaskItem.focus()
    await page.keyboard.press('Enter')

    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' }).first()).toBeVisible()

    const xml = await exportXmlText(page)
    expect(xml).toMatch(/<autoTask\b/)
    expect(xml).toContain('自动任务')
  })

  test('select-all copy and paste duplicates every copyable selected node', async ({ page }) => {
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const graphNodes = page.locator('.x6-node')
    await autoTaskItem.click()
    await autoTaskItem.click()
    await expect(graphNodes).toHaveCount(4, { timeout: TIMEOUT })

    await page.keyboard.press('ControlOrMeta+A')
    await page.keyboard.press('ControlOrMeta+C')
    await page.keyboard.press('ControlOrMeta+V')

    await expect(graphNodes).toHaveCount(6, { timeout: TIMEOUT })
  })

  test('editing note content updates the canvas immediately', async ({ page }) => {
    await page
      .locator('.palette-category-title')
      .filter({ hasText: /^注释$/ })
      .click()
    await page.locator('.drag-palette-item').filter({ hasText: '注释' }).click()
    await page.getByRole('tab', { name: '注释' }).click()
    await page.getByRole('textbox', { name: '注释内容' }).fill('画布即时注释')

    await expect(page.locator('.x6-node').last()).toContainText('画布即时注释')
  })

  const nodeLabels = [
    '开始',
    '结束',
    '自动任务',
    '等待任务',
    '等待事件',
    '定时任务',
    '脚本任务',
    '排他网关',
    '并行网关',
    '包容网关',
    '内嵌 BPM',
    'BPM 调用',
    '条件循环',
    '集合遍历',
    '继续循环',
    '中断循环',
    '注释',
  ] as const

  for (const label of nodeLabels) {
    test(`${label}节点渲染、选中并支持属性编辑`, async ({ page }) => {
      await exerciseNodeGroup(page, [label])
    })

    test(`${label}节点鼠标点击与拖拽均只创建一次`, async ({ page }) => {
      const graphNodes = page.locator('.x6-node')
      const item = await getPaletteItem(page, label)
      const initialCount = await graphNodes.count()
      const matchingNodes = graphNodes.filter({ hasText: label })
      const initialMatchingCount = await matchingNodes.count()

      await dragToCanvas(page, item)
      await expect(graphNodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })
      await expect(matchingNodes).toHaveCount(initialMatchingCount + 1)

      await item.click()
      await expect(graphNodes).toHaveCount(initialCount + 2, { timeout: TIMEOUT })
      await expect(matchingNodes).toHaveCount(initialMatchingCount + 2)
    })
  }

  test('canvas renders the X6 grid layer used as the placement guide', async ({ page }) => {
    await expect(page.locator('.tbbpm-canvas')).toBeVisible()
    await expect(page.locator('.x6-graph-svg')).toBeVisible()
    await expect(page.locator('.x6-graph-grid')).toBeVisible()
  })
})
