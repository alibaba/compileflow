import { expect, type Locator, type Page, test } from '@playwright/test'

const TIMEOUT = 20_000

async function center(locator: Locator) {
  const box = await locator.boundingBox()
  expect(box).toBeTruthy()
  return { x: box!.x + box!.width / 2, y: box!.y + box!.height / 2 }
}

async function movePointer(page: Page, from: Locator, to: Locator) {
  const source = await center(from)
  const target = await center(to)
  await page.mouse.move(source.x, source.y)
  await page.mouse.down()
  await page.mouse.move((source.x + target.x) / 2, (source.y + target.y) / 2, { steps: 8 })
  return target
}

async function dragBy(page: Page, locator: Locator, dx: number, dy: number) {
  const start = await center(locator)
  await page.mouse.move(start.x, start.y)
  await page.mouse.down()
  await page.mouse.move(start.x + dx, start.y + dy, { steps: 8 })
  await page.mouse.up()
}

async function graphPosition(locator: Locator) {
  return locator.evaluate((element) => {
    const transform = (element as SVGGraphicsElement).transform.baseVal.consolidate()?.matrix
    return { x: transform?.e ?? 0, y: transform?.f ?? 0 }
  })
}

async function graphSurfaceCoversViewport(page: Page) {
  return page.evaluate(() => {
    const surface = document.querySelector('.x6-graph-svg')?.getBoundingClientRect()
    const viewport = document.querySelector('.x6-graph-scroller')?.getBoundingClientRect()
    return Boolean(
      surface &&
      viewport &&
      surface.width >= viewport.width - 1 &&
      surface.height >= viewport.height - 1
    )
  })
}

async function readDesignerXml(page: Page) {
  return page.evaluate(() => {
    const monaco = (
      globalThis as typeof globalThis & {
        monaco?: { editor: { getModels(): Array<{ getValue(): string }> } }
      }
    ).monaco
    return monaco?.editor.getModels()[0]?.getValue() ?? ''
  })
}

async function createConnectionWithoutDragging(page: Page, source: string, target: string) {
  await page.getByRole('button', { name: '连接节点（无需拖拽）' }).click()
  const dialog = page.getByRole('dialog', { name: '创建连接' })
  const visibleDropdown = page.locator('.ant-select-dropdown:visible')
  await expect(dialog).toBeVisible()
  await expect(visibleDropdown).toHaveCount(0)
  const sourceInput = dialog.getByRole('combobox').nth(0)
  await sourceInput.fill(source)
  await sourceInput.press('Enter')
  await expect(visibleDropdown).toHaveCount(0)
  const targetInput = dialog.getByRole('combobox').nth(1)
  await targetInput.fill(target)
  await targetInput.press('Enter')
  await dialog.getByRole('button', { name: /创\s*建/ }).click()
  await expect(dialog).toBeHidden()
}

test.describe('editor interaction feedback', () => {
  for (const modelType of ['tbbpm', 'bpmn'] as const) {
    test(`${modelType} reconnects both selected edge endpoints with undo and saved recovery`, async ({
      page,
    }) => {
      await page.goto(`/build/designer?modelType=${modelType}&source=new`)
      const label = modelType === 'tbbpm' ? '自动任务' : '服务任务'
      const ids: string[] = []
      for (const name of ['Task A', 'Task B', 'Task C']) {
        await page.locator('.drag-palette-item').filter({ hasText: label }).first().click()
        const input = page.getByRole('textbox', { name: '节点名称', exact: true })
        await input.fill(name)
        await input.press('Tab')
        ids.push((await page.locator('.x6-node').last().getAttribute('data-cell-id'))!)
      }
      await createConnectionWithoutDragging(page, 'Task A', 'Task B')
      const edgeCount = await page.locator('.x6-edge').count()
      const info = page.locator('.edge-info-block')
      for (const endpoint of ['target', 'source']) {
        const handle = page.locator(`.x6-edge-tool-${endpoint}-arrowhead`)
        await expect(handle).toBeVisible()
        const invalidNode = page.locator(
          `.x6-node[data-cell-id="${ids[endpoint === 'target' ? 0 : 1]}"]`
        )
        const invalid = await movePointer(page, handle, invalidNode)
        await page.mouse.move(invalid.x, invalid.y, { steps: 8 })
        await page.mouse.up()
        await expect(info.locator('div').nth(0)).toContainText(ids[0])
        await expect(info.locator('div').nth(1)).toContainText(ids[1])
        await expect(page.locator('.x6-edge')).toHaveCount(edgeCount)
        const destination = page.locator(`.x6-node[data-cell-id="${ids[2]}"]`)
        const target = await movePointer(page, handle, destination)
        await page.mouse.move(target.x, target.y, { steps: 12 })
        await page.mouse.up()
        await expect(info.locator('div').nth(endpoint === 'source' ? 0 : 1)).toContainText(ids[2])
        await expect(page.locator('.x6-edge')).toHaveCount(edgeCount)
        await page.getByRole('button', { name: /撤销/ }).click()
        await expect(info).toContainText(ids[0])
        await expect(info).toContainText(ids[1])
      }
      const target = await movePointer(
        page,
        page.locator('.x6-edge-tool-target-arrowhead'),
        page.locator(`.x6-node[data-cell-id="${ids[2]}"]`)
      )
      await page.mouse.move(target.x, target.y, { steps: 12 })
      await page.mouse.up()
      await page.getByRole('button', { name: /^保存/ }).click()
      await expect(page.getByRole('button', { name: '已保存', exact: true })).toBeVisible()
      await page.reload()
      const path = page.locator('.x6-edge').last().locator('path').first()
      await expect(path).toBeVisible()
      const point = await path.evaluate((element) => {
        const path = element as SVGPathElement
        const matrix = path.getScreenCTM()!
        for (let fraction = 0.1; fraction < 1; fraction += 0.1) {
          const point = path
            .getPointAtLength(path.getTotalLength() * fraction)
            .matrixTransform(matrix)
          if (document.elementFromPoint(point.x, point.y) === path)
            return { x: point.x, y: point.y }
        }
        return null
      })
      expect(point).not.toBeNull()
      await page.mouse.click(point!.x, point!.y)
      await expect(info).toContainText(ids[2])
      await expect(page.locator('.x6-edge-tool-target-arrowhead')).toBeVisible()
    })
  }
  test('narrow-screen creation keeps the canvas usable and properties open explicitly', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 489, height: 844 })
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await page.getByRole('button', { name: '展开节点面板' }).click()
    const nodes = page.locator('.x6-node')
    const initialCount = await nodes.count()
    await page.getByRole('button', { name: '更多画布操作' }).click()
    await expect(page.getByRole('menuitem', { name: /左对齐/ })).toHaveAttribute(
      'aria-disabled',
      'true'
    )
    await expect(page.getByRole('menuitem', { name: /水平分布/ })).toHaveAttribute(
      'aria-disabled',
      'true'
    )
    await page.keyboard.press('Escape')
    await expect(page.getByRole('button', { name: /创建选中节点的副本/ })).toBeDisabled()
    await expect(page.getByRole('button', { name: /删除选中节点/ })).toBeDisabled()
    await expect(page.getByRole('button', { name: /全选/ })).toBeDisabled()

    await page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first().click()

    await expect(nodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.tbbpm-designer-left-sider')).toHaveAttribute('aria-hidden', 'true')
    await expect(page.locator('.tbbpm-designer-right-sider')).toHaveAttribute('aria-hidden', 'true')
    const created = nodes.last()
    const position = await graphPosition(created)
    await dragBy(page, created, 40, 20)
    await expect.poll(() => graphPosition(created)).not.toEqual(position)
    await expect(page.locator('.tbbpm-designer-right-sider')).toHaveAttribute('aria-hidden', 'true')
    await page.getByRole('button', { name: '展开属性面板' }).click()
    await expect(page.getByRole('tab', { name: /任务属性/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /创建选中节点的副本/ })).toBeEnabled()
    await expect(page.getByRole('button', { name: /删除选中节点/ })).toBeEnabled()
    await page.getByRole('button', { name: '更多画布操作' }).click()
    await expect(page.getByRole('menuitem', { name: /左对齐/ })).toHaveAttribute(
      'aria-disabled',
      'true'
    )
  })

  for (const modelType of ['tbbpm', 'bpmn'] as const) {
    for (const [portIndex, sourceSide] of ['top', 'bottom', 'left', 'right'].entries()) {
      test(`${modelType} connection gesture ${sourceSide} snaps, saves and restores its selected port`, async ({
        page,
      }) => {
        await page.goto(
          `/build/designer?modelType=${modelType}&source=template&templateId=${modelType === 'tbbpm' ? 'tpl-4' : 'tpl-1'}`
        )
        await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
        const taskItem = page
          .locator('.drag-palette-item')
          .filter({ hasText: modelType === 'tbbpm' ? '自动任务' : '服务任务' })
          .first()
        await taskItem.click()
        await page.locator('.x6-graph').click({ position: { x: 20, y: 20 } })

        const sourceNode = page.locator('.x6-node').filter({
          has: page.locator(modelType === 'tbbpm' ? '.tbbpm-node-start' : '.bpmn-start-event'),
        })
        const sourcePort = sourceNode.locator('.x6-port-body').nth(portIndex)
        const targetPort = page
          .locator('.x6-node')
          .filter({
            has: page.locator(
              modelType === 'tbbpm' ? '.tbbpm-node-autotask' : '.bpmn-service-task'
            ),
          })
          .locator('.x6-port-body')
          .first()
        const edges = page.locator('.x6-edge')
        const initialEdges = await edges.count()
        const sourceCenter = await center(sourceNode)
        await page.mouse.click(sourceCenter.x, sourceCenter.y)
        await page.mouse.move(sourceCenter.x, sourceCenter.y)
        await expect(sourcePort).toBeVisible()
        const sourceIsTopHitTarget = await sourcePort.evaluate((element) => {
          const box = element.getBoundingClientRect()
          const hit = document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
          return hit === element
        })
        expect(sourceIsTopHitTarget).toBe(true)
        const target = await movePointer(page, sourcePort, targetPort)

        await expect(page.locator('.available-node')).not.toHaveCount(0)
        await expect(page.locator('.available-magnet')).not.toHaveCount(0)
        await page.mouse.move(target.x, target.y, { steps: 8 })
        await expect(page.locator('.adsorbed-magnet')).not.toHaveCount(0)
        await page.mouse.up()

        await expect(edges).toHaveCount(initialEdges + 1, { timeout: TIMEOUT })
        await page.getByRole('button', { name: /^保存/ }).click()
        await expect(page.getByRole('button', { name: '已保存', exact: true })).toBeVisible()
        await page.reload()
        await expect(edges).toHaveCount(initialEdges + 1, { timeout: TIMEOUT })
        await page.getByTestId('designer-tab-xml').click()
        await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
          timeout: TIMEOUT,
        })
        await expect.poll(() => readDesignerXml(page)).toContain(`"sourcePort":"${sourceSide}"`)
        expect(await readDesignerXml(page)).toContain('"targetPort":"top"')
      })
    }
  }

  test('non-drag connection creation and edge property history stay canonical through XML', async ({
    page,
  }) => {
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    for (const label of ['排他网关', '自动任务', '等待任务']) {
      await page.locator('.drag-palette-item').filter({ hasText: label }).first().click()
    }

    const edges = page.locator('.x6-edge')
    const initialEdges = await edges.count()
    await createConnectionWithoutDragging(page, '排他网关', '自动任务')
    await createConnectionWithoutDragging(page, '排他网关', '等待任务')
    await expect(edges).toHaveCount(initialEdges + 2, { timeout: TIMEOUT })

    const name = page.getByRole('textbox', { name: '连接名称' })
    const condition = page.getByRole('textbox', { name: '条件表达式' })
    await expect(condition).toBeVisible()
    await name.fill('通过')
    await name.press('Tab')
    await condition.fill('amount > 1000')
    await condition.press('Tab')

    const undo = page.getByRole('button', { name: /撤销/ })
    const redo = page.getByRole('button', { name: /重做/ })
    await undo.click()
    await expect(condition).toHaveValue('')
    await expect(name).toHaveValue('通过')
    await undo.click()
    await expect(name).toHaveValue('')
    await redo.click()
    await expect(name).toHaveValue('通过')
    await redo.click()
    await expect(condition).toHaveValue('amount > 1000')

    await page.getByTestId('designer-tab-xml').click()
    await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
      timeout: TIMEOUT,
    })
    const xml = await page.evaluate(() => {
      const monaco = (
        globalThis as typeof globalThis & {
          monaco?: { editor: { getModels(): Array<{ getValue(): string }> } }
        }
      ).monaco
      return monaco?.editor.getModels()[0]?.getValue() ?? ''
    })
    expect(xml).toContain('name="通过"')
    expect(xml).toContain('amount &gt; 1000')
  })

  test('click-add stays inside the current viewport after zoom and scroll', async ({ page }) => {
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    const viewport = page.locator('.x6-graph-scroller')
    await expect(viewport).toBeVisible({ timeout: TIMEOUT })
    const zoomIn = page.getByRole('button', { name: '放大', exact: true })
    for (let index = 0; index < 5; index += 1) await zoomIn.click()
    await viewport.evaluate((element) => element.scrollTo({ left: 180, top: 120 }))

    await page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first().click()
    const nodeBox = await page.locator('.x6-node').last().boundingBox()
    const viewportBox = await viewport.boundingBox()
    expect(nodeBox).toBeTruthy()
    expect(viewportBox).toBeTruthy()
    expect(nodeBox!.x).toBeGreaterThanOrEqual(viewportBox!.x)
    expect(nodeBox!.y).toBeGreaterThanOrEqual(viewportBox!.y)
    expect(nodeBox!.x + nodeBox!.width).toBeLessThanOrEqual(viewportBox!.x + viewportBox!.width)
    expect(nodeBox!.y + nodeBox!.height).toBeLessThanOrEqual(viewportBox!.y + viewportBox!.height)
  })

  for (const modelType of ['tbbpm', 'bpmn'] as const) {
    test(`${modelType} graph surface follows panel and window resizing`, async ({ page }) => {
      await page.goto(`/build/designer?modelType=${modelType}&source=new`)
      await expect(page.locator('.x6-graph-scroller')).toBeVisible({ timeout: TIMEOUT })
      await page
        .locator('.drag-palette-item')
        .filter({ hasText: modelType === 'tbbpm' ? '自动任务' : '服务任务' })
        .first()
        .click()
      await expect.poll(() => graphSurfaceCoversViewport(page)).toBe(true)

      await page.getByRole('button', { name: '收起节点面板' }).click()
      await page.getByRole('button', { name: '收起属性面板' }).click()
      await page.setViewportSize({ width: 1024, height: 720 })
      await expect.poll(() => graphSurfaceCoversViewport(page)).toBe(true)

      if (modelType === 'tbbpm') {
        await page.locator('.x6-node').first().click()
        await expect(page.locator('.x6-node-selected')).toHaveCount(1)
        const viewport = await page.locator('.x6-graph-scroller').boundingBox()
        expect(viewport).toBeTruthy()
        await page.mouse.click(
          viewport!.x + viewport!.width - 20,
          viewport!.y + viewport!.height - 20
        )
        await expect(page.locator('.x6-node-selected')).toHaveCount(0)
      }
    })
  }

  for (const { modelType, label, xmlRoot } of [
    { modelType: 'tbbpm', label: '自动任务', xmlRoot: '<bpm' },
    { modelType: 'bpmn', label: '服务任务', xmlRoot: '<bpmn:definitions' },
  ] as const) {
    test(`${modelType} newly added node opens a valid XML document`, async ({ page }) => {
      await page.goto(`/build/designer?modelType=${modelType}&source=new`)
      await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
      await page.locator('.drag-palette-item').filter({ hasText: label }).first().click()
      await page.getByTestId('designer-tab-xml').click()

      await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
        timeout: TIMEOUT,
      })
      await expect(page.locator('.ant-alert-error')).toHaveCount(0)
      expect(await readDesignerXml(page)).toContain(xmlRoot)
    })
  }

  for (const modelType of ['tbbpm', 'bpmn'] as const) {
    test(`${modelType} keeps two separate canvas node moves undoable and serialized`, async ({
      page,
    }) => {
      await page.goto(`/build/designer?modelType=${modelType}&source=new`)
      await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
      await page
        .locator('.drag-palette-item')
        .filter({ hasText: modelType === 'tbbpm' ? '自动任务' : '服务任务' })
        .first()
        .click()
      await expect.poll(() => graphSurfaceCoversViewport(page)).toBe(true)
      const node = page.locator('.x6-node').first()
      const initial = await graphPosition(node)

      await dragBy(page, node, 80, 60)
      const firstMove = await graphPosition(node)
      expect(Math.abs(firstMove.x - initial.x)).toBeGreaterThan(40)
      await dragBy(page, node, 70, -20)
      const secondMove = await graphPosition(node)
      expect(Math.abs(secondMove.x - firstMove.x)).toBeGreaterThan(40)

      const undo = page.getByRole('button', { name: /撤销/ })
      const redo = page.getByRole('button', { name: /重做/ })
      await undo.click()
      await expect.poll(() => graphPosition(node)).toEqual(firstMove)
      await undo.click()
      await expect.poll(() => graphPosition(node)).toEqual(initial)
      await redo.click()
      await expect.poll(() => graphPosition(node)).toEqual(firstMove)
      await redo.click()
      await expect.poll(() => graphPosition(node)).toEqual(secondMove)

      await page.getByTestId('designer-tab-xml').click()
      await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
        timeout: TIMEOUT,
      })
      const xml = await readDesignerXml(page)
      if (modelType === 'tbbpm') {
        expect(xml).toContain(`g="${secondMove.x},${secondMove.y},`)
      } else {
        expect(xml).toContain(`x="${secondMove.x}"`)
        expect(xml).toContain(`y="${secondMove.y}"`)
      }
    })
  }
})
