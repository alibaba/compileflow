import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=bpmn'
const TIMEOUT = 15000

async function gotoDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await expect(page.locator('.bpmn-designer-left-sider')).toBeVisible({ timeout: TIMEOUT })
}

async function waitForCanvas(page: Page) {
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

test.describe('BPMN 1. 页面加载与布局', () => {
  test('1.1 页面 title 正确', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page).toHaveTitle(/CompileFlow/)
  })

  test('1.2 三栏布局 - 左侧 BPMN 节点面板', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.locator('.bpmn-designer-left-sider')).toBeVisible()
    await expect(page.getByText('BPMN 节点')).toBeVisible()
  })

  test('1.3 三栏布局 - 中间 BPMN 画布', async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await expect(page.locator('.bpmn-canvas-wrapper')).toBeVisible()
  })

  test('1.4 三栏布局 - 右侧面板', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.locator('.bpmn-designer-right-sider')).toBeVisible()
  })

  test('1.5 Header 显示 BPMN 类型与本地来源', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.getByRole('button', { name: '返回构建' })).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: 'BPMN' }).first()).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: '本地' }).first()).toBeVisible()
  })

  test('1.6 视图切换 Tab 存在', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.getByTestId('designer-tab-visual')).toBeVisible()
    await expect(page.getByTestId('designer-tab-xml')).toBeVisible()
    await expect(page.getByTestId('designer-tab-split')).toBeVisible()
  })

  test('1.7 分屏视图显示画布与 XML 编辑器', async ({ page }) => {
    await gotoDesigner(page)
    await page.getByTestId('designer-tab-split').click()
    await expect(page.locator('.unified-designer-body--split')).toBeVisible()
    await expect(page.locator('.xml-code-editor-panel')).toBeVisible()
    await waitForCanvas(page)
  })

  test('1.8 代码视图显示 XML 编辑器', async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.getByTestId('designer-tab-xml').click()
    await expect(page.locator('.xml-code-editor-panel')).toBeVisible()
    await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect
      .poll(() =>
        page.evaluate(() => {
          const monaco = (
            globalThis as typeof globalThis & {
              monaco?: {
                editor: {
                  getModels(): Array<{ getLanguageId(): string }>
                }
              }
            }
          ).monaco
          return (
            monaco?.editor.getModels().some((model) => model.getLanguageId() === 'xml') ?? false
          )
        })
      )
      .toBe(true)
    await expect(page.locator('.xml-code-editor-toolbar')).toBeVisible()
    await expect(page.locator('.unified-designer-canvas-pane--hidden')).toBeAttached()
    await expect(page.getByText('应用到画布')).toBeVisible()
  })
})

test.describe('BPMN 2. 节点工具箱', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
  })

  test('2.1 显示 4 个分类', async ({ page }) => {
    const palette = page.locator('.bpmn-designer-left-sider')
    for (const label of ['事件', '任务', '网关', '组合']) {
      await expect(
        palette.locator('.ant-collapse-header').filter({ hasText: label }).first()
      ).toBeVisible()
    }
  })

  test('2.2 事件分类含开始/结束', async ({ page }) => {
    const palette = page.locator('.bpmn-designer-left-sider')
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '流程开始' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '流程结束' })
    ).toBeVisible()
  })

  test('2.3 任务分类含 3 种受支持任务', async ({ page }) => {
    const palette = page.locator('.bpmn-designer-left-sider')
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '服务任务' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '脚本任务' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '接收任务' })
    ).toBeVisible()
  })

  test('2.4 网关分类含 3 种网关', async ({ page }) => {
    const palette = page.locator('.bpmn-designer-left-sider')
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '排他网关' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '并行网关' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '包容网关' })
    ).toBeVisible()
  })

  test('2.5 搜索网关过滤', async ({ page }) => {
    const palette = page.locator('.bpmn-designer-left-sider')
    const searchInput = palette.getByPlaceholder('搜索节点…')
    await searchInput.fill('网关')
    await page.waitForTimeout(400)
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '排他网关' })
    ).toBeVisible()
    await expect(
      palette.locator('.drag-palette-item-label').filter({ hasText: '服务任务' })
    ).toHaveCount(0)
  })
})

test.describe('BPMN 3. 调试面板', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
  })

  test('3.1 打开调试面板并切换引擎模式', async ({ page }) => {
    await page.locator('.designer-header button .anticon-bug').first().click()
    await expect(page.locator('.flow-debugger-panel')).toBeVisible()
    await page.locator('.flow-debugger-mode-switch').getByText('服务器执行').click()
    await expect(page.locator('.engine-debug-section')).toBeVisible()
  })
})
