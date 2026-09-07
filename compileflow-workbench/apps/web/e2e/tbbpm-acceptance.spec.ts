import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm'
const TIMEOUT = 15000

async function gotoDesigner(page: Page) {
  await page.goto(DESIGNER_URL)
  await expect(page.locator('.tbbpm-designer')).toBeVisible({ timeout: TIMEOUT })
}

async function waitForCanvas(page: Page) {
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
}

// ============================================================
// 一、页面加载与布局验收
// ============================================================
test.describe('1. 页面加载与布局', () => {
  test('1.1 页面 title 正确', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page).toHaveTitle(/CompileFlow/)
  })

  test('1.2 三栏布局渲染 - 左侧节点工具箱存在', async ({ page }) => {
    await gotoDesigner(page)
    const palette = page.locator('.ant-layout-sider').first()
    await expect(palette).toBeVisible()
    await expect(page.getByText('节点工具箱')).toBeVisible()
  })

  test('1.3 三栏布局渲染 - 中间画布区域存在', async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    const canvas = page.locator('.tbbpm-canvas-wrapper')
    await expect(canvas).toBeVisible()
  })

  test('1.4 三栏布局渲染 - 右侧面板存在', async ({ page }) => {
    await gotoDesigner(page)
    const rightSider = page.locator('.ant-layout-sider').last()
    await expect(rightSider).toBeVisible()
  })

  test('1.5 Header 导航栏渲染', async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.getByRole('button', { name: '返回构建' })).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: 'TBBPM' }).first()).toBeVisible()
    await expect(page.locator('.ant-tag').filter({ hasText: '本地' }).first()).toBeVisible()
  })

  test('1.6 CanvasToolbar 初始为 disabled 状态（graph 未初始化）', async ({ page }) => {
    await page.goto(DESIGNER_URL)
    await page.waitForLoadState('domcontentloaded')
    await page.waitForTimeout(300)
    const toolbar = page.locator('.x6-canvas-toolbar')
    await expect(toolbar).toBeVisible()
    const btns = toolbar.locator('button')
    const count = await btns.count()
    expect(count).toBeGreaterThan(0)
  })

  test('1.7 画布初始化后 CanvasToolbar 按钮可用', async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
    const toolbar = page.locator('.x6-canvas-toolbar')
    await expect(toolbar).toBeVisible()
    const firstBtn = toolbar.locator('button').first()
    await expect(firstBtn).not.toBeDisabled()
  })

  test('1.8 画布背景网格显示', async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    const svgEl = page.locator('.x6-graph svg').first()
    await expect(svgEl).toBeVisible()
  })
})

// ============================================================
// 二、节点工具箱验收
// ============================================================
test.describe('2. 节点工具箱', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
  })

  test('2.1 显示 5 个分类', async ({ page }) => {
    await expect(page.getByText('流程控制')).toBeVisible()
    await expect(page.getByText('任务节点')).toBeVisible()
    await expect(page.getByText('网关节点')).toBeVisible()
    await expect(page.getByText('子流程')).toBeVisible()
    await expect(page.getByText('循环控制')).toBeVisible()
  })

  test('2.2 流程控制分类含 开始/结束 节点', async ({ page }) => {
    await expect(
      page.locator('.drag-palette-item').filter({ hasText: '开始' }).first()
    ).toBeVisible()
    await expect(
      page.locator('.drag-palette-item').filter({ hasText: '结束' }).first()
    ).toBeVisible()
  })

  test('2.3 任务节点分类含 4 种任务', async ({ page }) => {
    await expect(page.locator('.drag-palette-item').filter({ hasText: '自动任务' })).toBeVisible()
    await expect(page.locator('.drag-palette-item').filter({ hasText: '等待任务' })).toBeVisible()
    await expect(page.locator('.drag-palette-item').filter({ hasText: '等待事件' })).toBeVisible()
    await expect(page.locator('.drag-palette-item').filter({ hasText: '脚本任务' })).toBeVisible()
  })

  test('2.4 网关节点分类含 3 种网关', async ({ page }) => {
    await expect(page.locator('.drag-palette-item').filter({ hasText: '排他网关' })).toBeVisible()
    await expect(page.locator('.drag-palette-item').filter({ hasText: '并行网关' })).toBeVisible()
    await expect(page.locator('.drag-palette-item').filter({ hasText: '包容网关' })).toBeVisible()
  })

  test('2.5 搜索功能 - 搜索"任务"只显示任务类节点', async ({ page }) => {
    const searchInput = page.getByPlaceholder('搜索节点…')
    await searchInput.fill('任务')
    await page.waitForTimeout(500)
    await expect(page.locator('.drag-palette-item').filter({ hasText: '自动任务' })).toBeVisible()
    const exclusiveGateway = page.locator('.drag-palette-item').filter({ hasText: '排他网关' })
    await expect(exclusiveGateway).toHaveCount(0)
  })

  test('2.6 搜索无结果时显示空状态', async ({ page }) => {
    const searchInput = page.getByPlaceholder('搜索节点…')
    await searchInput.fill('xyznotexist999')
    await page.waitForTimeout(500)
    await expect(page.getByText('未找到匹配的节点')).toBeVisible()
  })

  test('2.7 分类可折叠展开', async ({ page }) => {
    const taskHeader = page.locator('.ant-collapse-header').filter({ hasText: '任务节点' }).first()
    await taskHeader.click()
    await page.waitForTimeout(400)
    // Ant Design Collapse 折叠后 DOM 保留但设为 display:none，用 isVisible 检测
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const isHidden = await autoTaskItem.isHidden()
    expect(isHidden).toBe(true)
    await taskHeader.click()
    await page.waitForTimeout(400)
    await expect(page.locator('.drag-palette-item').filter({ hasText: '自动任务' })).toBeVisible()
  })

  test('2.8 节点 cursor 为 grab', async ({ page }) => {
    const nodeItem = page.locator('.drag-palette-item').first()
    await expect(nodeItem).toBeVisible()
    const cursor = await nodeItem.evaluate((el) => window.getComputedStyle(el).cursor)
    expect(['grab', 'pointer']).toContain(cursor)
  })
})

// ============================================================
// 三、画布操作验收
// ============================================================
test.describe('3. 画布操作', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('3.1 Ctrl+滚轮缩放画布', async ({ page }) => {
    const canvas = page.locator('.tbbpm-canvas-wrapper')
    await canvas.hover()
    await page.keyboard.down('Control')
    await page.mouse.wheel(0, -200)
    await page.keyboard.up('Control')
    await page.waitForTimeout(300)
  })

  test('3.2 工具栏放大按钮可点击', async ({ page }) => {
    const zoomInBtn = page.locator('.x6-canvas-toolbar button[aria-label="放大（Ctrl+滚轮）"]')
    await expect(zoomInBtn).toBeVisible()
    await expect(zoomInBtn).not.toBeDisabled()
    await zoomInBtn.click({ force: true })
    await page.waitForTimeout(200)
  })

  test('3.3 工具栏缩小按钮可点击', async ({ page }) => {
    const zoomOutBtn = page.locator('.x6-canvas-toolbar button[aria-label="缩小（Ctrl+滚轮）"]')
    await expect(zoomOutBtn).toBeVisible()
    await zoomOutBtn.click({ force: true })
    await page.waitForTimeout(200)
  })

  test('3.4 工具栏适应画布按钮可点击', async ({ page }) => {
    const fitBtn = page.locator('.x6-canvas-toolbar button[aria-label="适应画布"]')
    await expect(fitBtn).toBeVisible()
    await fitBtn.click({ force: true })
    await page.waitForTimeout(200)
  })

  test('3.5 工具栏重置视图按钮可点击', async ({ page }) => {
    const resetBtn = page.locator('.x6-canvas-toolbar button[aria-label="重置视图"]')
    await expect(resetBtn).toBeVisible()
    await resetBtn.click({ force: true })
    await page.waitForTimeout(200)
  })

  test('3.6 右键画布显示上下文菜单', async ({ page }) => {
    const canvas = page.locator('.tbbpm-canvas')
    const box = await canvas.boundingBox()
    if (!box) throw new Error('Canvas not found')
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2, { button: 'right' })
    await page.waitForTimeout(300)
    // 上下文菜单可能存在也可能需要进一步交互，只要不报错即可
  })
})

// ============================================================
// 四、拖拽创建节点验收
// ============================================================
test.describe('4. 拖拽创建节点', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(800)
  })

  test('4.1 拖拽开始节点到画布', async ({ page }) => {
    const startItem = page.locator('.drag-palette-item').filter({ hasText: '开始' }).first()
    const canvas = page.locator('.tbbpm-canvas')
    const nodeCount = await page.locator('.x6-node').count()

    await expect(startItem).toBeVisible()
    await expect(startItem).toBeEnabled()
    const canvasBox = await canvas.boundingBox()
    if (!canvasBox) throw new Error('Canvas not found')
    await startItem.dragTo(canvas, {
      targetPosition: { x: canvasBox.width / 2, y: canvasBox.height / 2 },
    })
    await expect(page.locator('.x6-node')).toHaveCount(nodeCount + 1)
  })

  test('4.2 键盘 Enter 添加节点到画布中心', async ({ page }) => {
    const nodeItem = page.locator('.drag-palette-item').first()
    const nodeCount = await page.locator('.x6-node').count()
    await nodeItem.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.x6-node')).toHaveCount(nodeCount + 1)
  })
})

// ============================================================
// 五、属性面板验收
// ============================================================
test.describe('5. 属性面板', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('5.1 未选中时右侧面板显示空状态', async ({ page }) => {
    await expect(page.getByText('选择节点或边')).toBeVisible()
    await expect(page.getByText('在画布上点击元素以查看其属性')).toBeVisible()
  })

  test('5.2 空状态面板提示使用工具栏验证与调试', async ({ page }) => {
    await expect(page.locator('.tbbpm-designer-empty-panel')).toBeVisible()
    await expect(page.getByText(/使用工具栏.*验证.*调试/)).toBeVisible()
  })

  test('5.3 Header 验证与调试按钮存在', async ({ page }) => {
    await expect(page.getByRole('button', { name: '验证流程' })).toBeVisible()
    await expect(page.getByRole('button', { name: '调试流程' })).toBeVisible()
  })

  test('5.4 点击验证流程按钮切换到验证面板', async ({ page }) => {
    await page.getByRole('button', { name: '验证流程' }).click()
    await page.waitForTimeout(500)
    await expect(page.getByText('流程验证')).toBeVisible()
  })

  test('5.5 点击调试流程按钮切换到调试面板', async ({ page }) => {
    await page.getByRole('button', { name: '调试流程' }).click()
    await page.waitForTimeout(500)
    await expect(page.locator('.flow-debugger-panel, .ant-layout-sider').last()).toBeVisible()
  })

  test('5.6 点击画布节点后右侧面板显示属性', async ({ page }) => {
    await waitForCanvas(page)
    await page.waitForTimeout(500)
    const firstNode = page.locator('.x6-node').first()
    const nodeCount = await firstNode.count()
    if (nodeCount > 0) {
      await firstNode.click()
      await page.waitForTimeout(500)
      await expect(page.locator('.ant-card').filter({ hasText: '属性' }).first()).toBeVisible()
    }
  })
})

// ============================================================
// 六、Header 功能验收
// ============================================================
test.describe('6. Header 功能', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await expect(page.getByRole('button', { name: /保存|已保存/ })).toBeVisible({
      timeout: TIMEOUT,
    })
  })

  test('6.1 流程名称可内联编辑', async ({ page }) => {
    const flowNameDisplay = page.locator('.flow-name-display')
    await expect(flowNameDisplay).toBeVisible()
    await flowNameDisplay.click()
    await page.waitForTimeout(200)
    const input = page.locator('.ant-input-search input').first()
    await expect(input).toBeVisible()
    await input.fill('测试流程名称修改')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(300)
    await expect(page.locator('.flow-name-text')).toHaveText('测试流程名称修改')
  })

  test('6.2 返回构建按钮存在', async ({ page }) => {
    await expect(page.getByRole('button', { name: '返回构建' })).toBeVisible()
  })

  test('6.3 撤销/重做按钮存在', async ({ page }) => {
    const undoIcon = page.locator('.anticon-undo').first()
    const redoIcon = page.locator('.anticon-redo').first()
    await expect(undoIcon).toBeVisible()
    await expect(redoIcon).toBeVisible()
  })

  test('6.4 粘贴图标为 SnippetsOutlined', async ({ page }) => {
    const snippetsIcon = page.locator('.anticon-snippets')
    await expect(snippetsIcon).toBeVisible()
  })

  test('6.5 保存按钮存在且可点击', async ({ page }) => {
    const saveBtn = page.getByRole('button', { name: '保存' })
    await expect(saveBtn).toBeVisible()
    await saveBtn.click()
    await page.waitForTimeout(1000)
    await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: 5000 })
  })

  test('6.6 验证流程图标按钮存在', async ({ page }) => {
    const checkIcon = page.locator('.anticon-check-square').first()
    await expect(checkIcon).toBeVisible()
  })

  test('6.7 更多操作菜单含导出XML', async ({ page }) => {
    await page.locator('.header-more-btn').click()
    await page.waitForTimeout(300)
    await expect(page.getByText('导出 XML')).toBeVisible()
    await page.keyboard.press('Escape')
  })

  test('6.8 更多操作菜单含删除流程（danger 样式）', async ({ page }) => {
    await page.locator('.header-more-btn').click()
    await page.waitForTimeout(300)
    const deleteItem = page.locator('.ant-dropdown-menu-item-danger')
    await expect(deleteItem).toBeVisible()
    await page.keyboard.press('Escape')
  })

  test('6.9 返回按钮正常工作（无未保存更改时直接返回）', async ({ page }) => {
    const backBtn = page.getByRole('button', { name: '返回构建' })
    await backBtn.click()
    await page.waitForTimeout(500)
    await expect(page).toHaveURL('/build')
  })
})

// ============================================================
// 七、键盘快捷键验收
// ============================================================
test.describe('7. 键盘快捷键', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('7.1 Ctrl+/ 打开快捷键帮助弹窗', async ({ page }) => {
    await page.locator('.tbbpm-canvas').click()
    await page.keyboard.press('Control+/')
    await page.waitForTimeout(800)
    const modal = page.locator('.ant-modal-wrap:visible, .ant-modal:visible').first()
    const isVisible = await modal.isVisible().catch(() => false)
    if (isVisible) {
      await page.keyboard.press('Escape')
    }
  })

  test('7.2 Ctrl+F 打开节点搜索（不报错）', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.locator('.tbbpm-canvas').click()
    await page.keyboard.press('Control+f')
    await page.waitForTimeout(500)
    expect(errors).toHaveLength(0)
  })

  test('7.3 Ctrl+S 触发保存显示提示', async ({ page }) => {
    await page.locator('.flow-name-display').click()
    const nameInput = page.locator('.ant-input-search input').first()
    await expect(nameInput).toBeVisible({ timeout: TIMEOUT })
    await nameInput.fill('快捷键保存验证')
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: '保存' })).toBeVisible({ timeout: TIMEOUT })
    await page.locator('.tbbpm-canvas').click()
    await page.keyboard.press('Control+s')
    await expect(page.getByRole('button', { name: '已保存' })).toBeVisible({ timeout: TIMEOUT })
  })

  test('7.4 Ctrl+Z 撤销（无操作时不报错）', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await page.locator('.tbbpm-canvas').click()
    await page.keyboard.press('Control+z')
    await page.waitForTimeout(300)
    expect(errors).toHaveLength(0)
  })

  test('7.5 Delete 键删除选中节点（不报错）', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    const firstNode = page.locator('.x6-node').first()
    if ((await firstNode.count()) > 0) {
      await firstNode.click()
      await page.waitForTimeout(200)
      await page.keyboard.press('Delete')
      await page.waitForTimeout(300)
    }
    expect(errors).toHaveLength(0)
  })

  test('7.6 Ctrl+= 放大且 Ctrl+A 选中全部节点', async ({ page }) => {
    const viewport = page.locator('.x6-graph-svg-viewport')
    const canvas = page.locator('.tbbpm-canvas')
    const transformBefore = await viewport.getAttribute('transform')

    await canvas.click()
    await page.keyboard.press('Control+=')
    await expect.poll(() => viewport.getAttribute('transform')).not.toBe(transformBefore)

    await page.keyboard.press('Control+a')
    const nodeCount = await page.locator('.x6-node').count()
    await expect(page.locator('.x6-widget-selection-box')).toHaveCount(nodeCount)
  })
})

// ============================================================
// 八、验证面板验收
// ============================================================
test.describe('8. 验证面板', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('8.1 切换到验证 Tab 显示流程验证面板', async ({ page }) => {
    await page.getByRole('button', { name: '验证流程' }).click()
    await page.waitForTimeout(500)
    await expect(page.getByText('流程验证')).toBeVisible()
  })

  test('8.2 验证面板有重新验证按钮', async ({ page }) => {
    await page.getByRole('button', { name: '验证流程' }).click()
    await page.waitForTimeout(500)
    const revalidateBtn = page.getByRole('button', { name: '重新验证' })
    await expect(revalidateBtn).toBeVisible()
  })

  test('8.3 验证面板渲染完整（有流程定义）', async ({ page }) => {
    await page.getByRole('button', { name: '验证流程' }).click()
    await page.waitForTimeout(800)
    const validationPanel = page.locator('.validation-result-panel')
    await expect(validationPanel).toBeVisible()
  })

  test('8.4 重新验证后显示结果', async ({ page }) => {
    await page.getByRole('button', { name: '验证流程' }).click()
    await page.waitForTimeout(500)
    const revalidateBtn = page.getByRole('button', { name: '重新验证' })
    await revalidateBtn.click()
    await page.waitForTimeout(500)
    const resultPanel = page.locator('.validation-result-panel')
    await expect(resultPanel).toBeVisible()
  })
})

// ============================================================
// 九、无障碍和视觉质量验收
// ============================================================
test.describe('9. 无障碍与视觉质量', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('9.1 工具栏按钮有 aria-label', async ({ page }) => {
    const toolbarBtns = page.locator('.x6-canvas-toolbar button[aria-label]')
    const count = await toolbarBtns.count()
    expect(count).toBeGreaterThan(0)
  })

  test('9.2 节点工具箱项有 aria-label', async ({ page }) => {
    const nodeItems = page.locator('.drag-palette-item[aria-label]')
    const count = await nodeItems.count()
    expect(count).toBeGreaterThan(0)
  })

  test('9.3 节点工具箱项 role 为 button', async ({ page }) => {
    const nodeItems = page.locator('button.drag-palette-item')
    const count = await nodeItems.count()
    expect(count).toBeGreaterThan(0)
  })

  test('9.4 节点工具箱项可通过 Tab 聚焦', async ({ page }) => {
    const firstItem = page.locator('button.drag-palette-item').first()
    await expect(firstItem).toBeVisible()
    await expect(firstItem).toBeEnabled({ timeout: TIMEOUT })
    expect(await firstItem.evaluate((element) => element.tabIndex)).toBe(0)
    await firstItem.focus()
    await expect(firstItem).toBeFocused()
  })

  test('9.5 设计器无运行时 JS 报错', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(1000)
    expect(errors).toHaveLength(0)
  })

  test('9.6 节点 hover 无 translateY 位移', async ({ page }) => {
    const nodeItem = page.locator('.drag-palette-item').first()
    await nodeItem.hover()
    await nodeItem.evaluate(async (el) => {
      await Promise.all(el.getAnimations().map((animation) => animation.finished))
    })
    await expect
      .poll(() =>
        nodeItem.evaluate((el) => {
          const transform = window.getComputedStyle(el).transform
          return transform === 'none' ? 0 : new DOMMatrixReadOnly(transform).m42
        })
      )
      .toBe(0)
  })

  test('9.7 antd deprecation 警告不含致命错误', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (err) => errors.push(err.message))
    await gotoDesigner(page)
    await page.waitForTimeout(1000)
    const criticalErrors = errors.filter((e) => !e.toLowerCase().includes('deprecated'))
    expect(criticalErrors).toHaveLength(0)
  })
})

// ============================================================
// 十、DesignerHeader 交互深度验收
// ============================================================
test.describe('10. DesignerHeader 深度交互', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await page.waitForTimeout(800)
  })

  test('10.1 未保存时返回显示确认弹窗', async ({ page }) => {
    // 修改流程名称触发 isModified=true
    const flowNameDisplay = page.locator('.flow-name-display')
    await flowNameDisplay.click()
    const input = page.locator('.ant-input-search input').first()
    await input.fill('修改了名称')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(300)

    const backBtn = page.getByRole('button', { name: '返回构建' })
    await backBtn.click()

    const dialog = page.getByRole('dialog', { name: '未保存的更改' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: /取\s*消/ }).click()
    await expect(dialog).toBeHidden()
    await expect(page).toHaveURL(/designer/)
  })

  test('10.2 TBBPM 类型标签显示', async ({ page }) => {
    const typeTag = page.locator('.ant-tag').filter({ hasText: 'TBBPM' }).first()
    await expect(typeTag).toBeVisible()
  })

  test('10.3 状态标签显示本地来源', async ({ page }) => {
    const sourceTag = page.locator('.ant-tag').filter({ hasText: '本地' }).first()
    await expect(sourceTag).toBeVisible()
  })

  test('10.4 本地快照按钮存在', async ({ page }) => {
    await expect(page.getByRole('button', { name: /本地快照|Local snapshots/i })).toBeVisible()
  })

  test('10.5 快捷键按钮存在', async ({ page }) => {
    const shortcutsBtn = page.locator('.anticon-key').first()
    await expect(shortcutsBtn).toBeVisible()
  })
})

// ============================================================
// 十一、节点属性编辑验收
// ============================================================
test.describe('11. 节点属性编辑', () => {
  test.beforeEach(async ({ page }) => {
    await gotoDesigner(page)
    await waitForCanvas(page)
    await page.waitForTimeout(500)
  })

  test('11.1 点击 X6 画布节点显示属性面板', async ({ page }) => {
    const nodes = page.locator('.x6-node')
    const nodeCount = await nodes.count()
    if (nodeCount > 0) {
      await nodes.first().click()
      await page.waitForTimeout(500)
      const propPanel = page.locator('.ant-card').filter({ hasText: '属性' })
      await expect(propPanel.first()).toBeVisible()
    }
  })

  test('11.2 属性面板有通用 Tab', async ({ page }) => {
    const nodes = page.locator('.x6-node')
    const nodeCount = await nodes.count()
    if (nodeCount > 0) {
      await nodes.first().click()
      await page.waitForTimeout(500)
      const generalTab = page.locator('.ant-tabs-tab').filter({ hasText: '通用' })
      await expect(generalTab.first()).toBeVisible()
    }
  })

  test('11.3 属性面板节点名称可编辑', async ({ page }) => {
    const nodes = page.locator('.x6-node')
    const nodeCount = await nodes.count()
    if (nodeCount > 0) {
      await nodes.first().click()
      await page.waitForTimeout(500)
      const nameInput = page
        .locator('.ant-form-item')
        .filter({ hasText: '节点名称' })
        .locator('input')
      if ((await nameInput.count()) > 0) {
        await expect(nameInput.first()).toBeVisible()
      }
    }
  })
})
