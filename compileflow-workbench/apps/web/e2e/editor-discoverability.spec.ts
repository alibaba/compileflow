import { expect, test } from '@playwright/test'

for (const modelType of ['tbbpm', 'bpmn']) {
  test(`${modelType} deleting a right-clicked edge preserves the previously selected node`, async ({
    page,
  }) => {
    await page.goto(
      `/build/designer?modelType=${modelType}&source=template&templateId=${modelType === 'tbbpm' ? 'tpl-4' : 'tpl-1'}`
    )
    const nodes = page.locator('.x6-node')
    const edges = page.locator('.x6-edge')
    await expect(nodes).toHaveCount(2)
    await page.getByRole('button', { name: '适应画布', exact: true }).click()
    await nodes.first().click()
    const point = await edges
      .first()
      .locator('path')
      .first()
      .evaluate((element) => {
        const path = element as SVGPathElement
        const point = path
          .getPointAtLength(path.getTotalLength() / 2)
          .matrixTransform(path.getScreenCTM()!)
        return { x: point.x, y: point.y }
      })
    await page.mouse.click(point.x, point.y, { button: 'right' })
    await page.getByRole('menuitem', { name: /删除连接/ }).click()
    await expect(nodes).toHaveCount(2)
    await expect(edges).toHaveCount(0)
    await page.getByRole('button', { name: /撤销/ }).click()
    await expect(edges).toHaveCount(1)
  })

  test(`${modelType} context menu stays inside the viewport and supports cancel and select all`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 1280, height: 720 })
    await page.goto(
      `/build/designer?modelType=${modelType}&source=template&templateId=${modelType === 'tbbpm' ? 'tpl-4' : 'tpl-1'}`
    )
    await expect(page.locator('.x6-node')).toHaveCount(2)
    const canvas = await page.locator('.x6-graph-scroller').boundingBox()
    expect(canvas).not.toBeNull()
    const openMenu = () =>
      page.mouse.click(canvas!.x + canvas!.width - 24, canvas!.y + canvas!.height - 24, {
        button: 'right',
      })
    await openMenu()
    const menu = page.getByRole('menu')
    await expect(menu).toBeVisible()
    const bounds = await menu.boundingBox()
    expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(720)
    expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(1280)
    await page.keyboard.press('Escape')
    await expect(menu).toBeHidden()
    await openMenu()
    await menu.getByRole('menuitem', { name: /全选/ }).click()
    await expect(page.locator('.x6-node-selected')).toHaveCount(2)
    await expect(menu).toBeHidden()
  })
}

test('operate home identifies demo metrics before users act on them', async ({ page }) => {
  await page.goto('/operate')
  await expect(page.getByRole('status').filter({ hasText: '演示模式' })).toBeVisible()
})

for (const modelType of ['tbbpm', 'bpmn']) {
  for (const width of [1024, 1280, 1440]) {
    test(`${modelType} ${width}px side panels and split mode keep canvas actions reachable`, async ({
      page,
    }) => {
      await page.setViewportSize({ width, height: 800 })
      await page.goto(
        `/build/designer?modelType=${modelType}&source=template&templateId=${modelType === 'tbbpm' ? 'tpl-4' : 'tpl-1'}`
      )
      await expect(page.locator('.x6-node')).toHaveCount(2)
      const toolbar = page.getByRole('toolbar', { name: '画布工具栏' })
      const assertActionsReachable = async () => {
        await expect
          .poll(() =>
            toolbar.evaluate((element) => {
              const bounds = element.getBoundingClientRect()
              return [...element.querySelectorAll('button')].every((button) => {
                const box = button.getBoundingClientRect()
                return box.x >= bounds.x && box.right <= bounds.right
              })
            })
          )
          .toBe(true)
        await toolbar.getByRole('button', { name: '适应画布', exact: true }).click()
      }
      await assertActionsReachable()
      await page.getByRole('button', { name: '收起节点面板' }).click()
      await page.getByRole('button', { name: '收起属性面板' }).click()
      await assertActionsReachable()
      await page.getByRole('button', { name: '展开节点面板' }).click()
      await page.getByRole('button', { name: '展开属性面板' }).click()
      await page.getByRole('tab', { name: '分栏', exact: true }).click()
      await assertActionsReachable()
    })
  }
}

for (const modelType of ['tbbpm', 'bpmn']) {
  test(`${modelType} double-click opens existing connection properties`, async ({ page }) => {
    await page.setViewportSize({ width: 489, height: 605 })
    await page.goto(
      `/build/designer?modelType=${modelType}&source=template&templateId=${modelType === 'tbbpm' ? 'tpl-4' : 'tpl-1'}`
    )
    const edge = page.locator('.x6-edge').first().locator('path').first()
    await expect(edge).toHaveCount(1)
    await page.getByRole('button', { name: '适应画布', exact: true }).click()
    const hitPoint = () =>
      edge.evaluate((element) => {
        const path = element as SVGPathElement
        const matrix = path.getScreenCTM()
        if (!matrix) return null
        for (let fraction = 0.2; fraction < 0.9; fraction += 0.1) {
          const point = path
            .getPointAtLength(path.getTotalLength() * fraction)
            .matrixTransform(matrix)
          if (
            document.elementFromPoint(point.x, point.y)?.closest('.x6-edge') ===
            path.closest('.x6-edge')
          )
            return { x: point.x, y: point.y }
        }
        return null
      })
    await expect.poll(hitPoint).not.toBeNull()
    const point = (await hitPoint())!
    await page.mouse.dblclick(point.x, point.y)
    await expect(page.locator(`.${modelType}-designer-right-sider`)).toHaveAttribute(
      'aria-hidden',
      'false'
    )
    await expect(page.getByRole('textbox', { name: '连接名称', exact: true })).toBeVisible()
  })
  test(`${modelType} double-click edits a node without opening properties during selection`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: 489, height: 844 })
    await page.goto(`/build/designer?modelType=${modelType}&source=new`)
    await expect(page.locator('.x6-graph')).toBeVisible()
    await page.getByRole('button', { name: '展开节点面板' }).click()
    await page
      .locator('.drag-palette-item')
      .filter({ hasText: modelType === 'tbbpm' ? '自动任务' : '服务任务' })
      .first()
      .click()
    const panel = page.locator(`.${modelType}-designer-right-sider`)
    await expect(panel).toHaveAttribute('aria-hidden', 'true')
    await page.locator('.x6-node').first().dblclick()
    await expect(panel).toHaveAttribute('aria-hidden', 'false')
    const name = page.getByRole('textbox', { name: '节点名称', exact: true })
    await name.fill('双击编辑已保存')
    await name.press('Tab')
    await expect(page.locator('.x6-node').first()).toContainText('双击编辑已保存')
    await page.locator('.header-save-btn').click()
    await expect(page.getByRole('button', { name: '已保存' })).toBeVisible()
    await page.reload()
    await expect(page.locator('.x6-node').first()).toContainText('双击编辑已保存')
  })
}

test('short-screen menu keeps its last action reachable', async ({ page }) => {
  await page.setViewportSize({ width: 489, height: 605 })
  await page.goto('/build/designer?modelType=tbbpm&source=template&templateId=tpl-4')
  await expect(page.locator('.x6-node')).toHaveCount(2)
  await page.getByRole('button', { name: '更多画布操作' }).click()
  const menu = page.locator('.canvas-toolbar-menu')
  const bounds = await menu.boundingBox()
  expect(bounds).not.toBeNull()
  expect(bounds!.y).toBeGreaterThanOrEqual(0)
  expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(605)
  await page.getByRole('menuitem', { name: /重置视图/ }).click()
  await expect(menu).toBeHidden()
  await expect(page.getByRole('button', { name: '重置缩放', exact: true })).toContainText('100')
})

for (const width of [320, 390, 489]) {
  test(`canvas actions fit ${width}px without horizontal scrolling and layout remains operable`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 844 })
    await page.goto('/build/designer?modelType=tbbpm&source=template&templateId=tpl-4')
    await expect(page.locator('.x6-node')).toHaveCount(2)
    const toolbar = page.getByRole('toolbar', { name: '画布工具栏' })
    const buttons = toolbar.getByRole('button')
    await expect(buttons).toHaveCount(6)
    for (const button of await buttons.all()) {
      const box = await button.boundingBox()
      expect(box).not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(0)
      expect(box!.x + box!.width).toBeLessThanOrEqual(width)
      expect(box!.width).toBeGreaterThanOrEqual(44)
      expect(box!.height).toBeGreaterThanOrEqual(44)
    }
    await toolbar.getByRole('button', { name: /全选/ }).click()
    await toolbar.getByRole('button', { name: '更多画布操作' }).click()
    await expect(page.getByRole('menuitem', { name: /左对齐/ })).toBeEnabled()
    await page.getByRole('menuitem', { name: /左对齐/ }).click()
    await expect
      .poll(() =>
        page.locator('.x6-node').evaluateAll((nodes) => {
          const positions = nodes.map(
            (node) => (node as SVGGraphicsElement).transform.baseVal.consolidate()?.matrix.e
          )
          return typeof positions[0] === 'number' && positions[0] === positions[1]
        })
      )
      .toBe(true)
  })
}
