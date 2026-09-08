import { expect, type Locator, type Page, test } from '@playwright/test'

const TIMEOUT = 20_000
type BrowserName = 'chromium' | 'firefox' | 'webkit'

async function openPalette(page: Page) {
  const expand = page.getByRole('button', { name: /展开节点面板|Expand palette/i })
  if (await expand.isVisible()) await expand.click()
}

async function dispatchTouchDrag(
  page: Page,
  browserName: BrowserName,
  item: Locator,
  source: { x: number; y: number },
  target: { x: number; y: number }
) {
  await item.evaluate((element) => {
    element.setAttribute('data-observed-touch-events', '')
    for (const type of ['pointerdown', 'pointermove', 'pointerup', 'pointercancel']) {
      element.addEventListener(type, (event) => {
        const pointer = event as PointerEvent
        const previous = element.getAttribute('data-observed-touch-events') ?? ''
        element.setAttribute(
          'data-observed-touch-events',
          `${previous}${type}:${pointer.pointerType}:${pointer.clientX},${pointer.clientY};`
        )
      })
    }
  })
  if (browserName === 'chromium') {
    const session = await page.context().newCDPSession(page)
    const point = (x: number, y: number) => [{ x, y, id: 1, radiusX: 4, radiusY: 4 }]
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchStart',
      touchPoints: point(source.x, source.y),
    })
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: point((source.x + target.x) / 2, (source.y + target.y) / 2),
    })
    await session.send('Input.dispatchTouchEvent', {
      type: 'touchMove',
      touchPoints: point(target.x, target.y),
    })
    await session.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    await session.detach()
    return item.getAttribute('data-observed-touch-events')
  }

  const pointer = { pointerId: 7, pointerType: 'touch', isPrimary: true, button: 0 }
  await item.dispatchEvent('pointerdown', { ...pointer, clientX: source.x, clientY: source.y })
  await item.dispatchEvent('pointermove', {
    ...pointer,
    clientX: (source.x + target.x) / 2,
    clientY: (source.y + target.y) / 2,
  })
  await item.dispatchEvent('pointermove', { ...pointer, clientX: target.x, clientY: target.y })
  await item.dispatchEvent('pointerup', { ...pointer, clientX: target.x, clientY: target.y })
  await item.dispatchEvent('click', { detail: 1 })
  return item.getAttribute('data-observed-touch-events')
}

async function findVisiblePoint(locator: Locator) {
  return locator.evaluate((element) => {
    const bounds = element.getBoundingClientRect()
    for (let y = Math.max(0, bounds.top + 4); y < Math.min(innerHeight, bounds.bottom); y += 4) {
      for (let x = Math.max(0, bounds.left + 4); x < Math.min(innerWidth, bounds.right); x += 4) {
        if (element.contains(document.elementFromPoint(x, y))) return { x, y }
      }
    }
    return null
  })
}

async function ensureDarkTheme(page: Page) {
  if ((await page.locator('html').getAttribute('data-theme')) !== 'dark') {
    await page.getByRole('button', { name: /切换到深色模式|Switch to dark mode/i }).click()
  }
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
}

function relativeLuminance(hex: string) {
  const rgb = hex
    .replace('#', '')
    .match(/.{2}/g)!
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) => (channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
  return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]
}

function contrastRatio(a: string, b: string) {
  const [light, dark] = [relativeLuminance(a), relativeLuminance(b)].sort((x, y) => y - x)
  return (light + 0.05) / (dark + 0.05)
}

test.describe('touch drag', () => {
  test.use({ hasTouch: true })

  for (const { modelType, label, initialCount } of [
    { modelType: 'tbbpm', label: '自动任务', initialCount: 0 },
    { modelType: 'bpmn', label: '服务任务', initialCount: 0 },
  ] as const) {
    test(`places one ${modelType} node at the drop point without a click duplicate`, async ({
      browserName,
      page,
    }) => {
      await page.setViewportSize({ width: 390, height: 844 })
      await page.goto(`/build/designer?modelType=${modelType}&source=new`)
      const canvas = page.locator('.x6-graph-scroller')
      await expect(canvas).toBeVisible({ timeout: TIMEOUT })
      await openPalette(page)
      await page.getByRole('textbox', { name: /搜索节点|Search nodes/i }).fill(label)
      await expect(page.locator('.drag-palette-item')).toHaveCount(1, { timeout: TIMEOUT })
      await page.evaluate(
        () =>
          new Promise<void>((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
          )
      )

      const item = page.locator('.drag-palette-item').filter({ hasText: label }).first()
      await expect(item).toBeVisible({ timeout: TIMEOUT })
      await item.evaluate((element) => element.scrollIntoView({ block: 'center' }))
      const canvasBox = await canvas.boundingBox()
      const source = await findVisiblePoint(item)
      expect(canvasBox).toBeTruthy()
      expect(source).toBeTruthy()
      const target = {
        x: canvasBox!.x + canvasBox!.width - 54,
        y: Math.min(canvasBox!.y + canvasBox!.height - 80, Math.max(canvasBox!.y + 80, source!.y)),
      }
      expect(Math.abs(target.x - source!.x)).toBeGreaterThan(50)
      expect(Math.abs(target.x - source!.x)).toBeGreaterThan(Math.abs(target.y - source!.y))
      await expect(item).toHaveCSS('touch-action', 'pan-y')

      const nodes = page.locator('.x6-node')
      await expect(nodes).toHaveCount(initialCount, { timeout: TIMEOUT })
      const observedEvents = await dispatchTouchDrag(page, browserName, item, source!, target)
      expect(observedEvents).toContain('pointerdown:touch')
      expect(observedEvents).toContain('pointermove:touch')
      expect(observedEvents).toContain('pointerup:touch')
      expect(observedEvents).not.toContain('pointercancel')
      await expect(nodes).toHaveCount(initialCount + 1, { timeout: TIMEOUT })
      await expect(nodes.last()).toContainText(label)
      await expect(page.locator(`.${modelType}-designer-left-sider`)).toHaveAttribute(
        'aria-hidden',
        'true'
      )
      await expect(page.locator(`.${modelType}-designer-right-sider`)).toHaveAttribute(
        'aria-hidden',
        'true'
      )
      await page.evaluate(
        () =>
          new Promise<void>((resolve) =>
            requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
          )
      )
      expect(await nodes.count()).toBe(initialCount + 1)

      const nodeBox = await nodes.last().boundingBox()
      expect(nodeBox).toBeTruthy()
      expect(
        await nodes.last().evaluate((element) => {
          const box = element.getBoundingClientRect()
          return element.contains(
            document.elementFromPoint(box.x + box.width / 2, box.y + box.height / 2)
          )
        })
      ).toBe(true)
      expect(Math.abs(nodeBox!.x + nodeBox!.width / 2 - target.x)).toBeLessThanOrEqual(12)
      expect(Math.abs(nodeBox!.y + nodeBox!.height / 2 - target.y)).toBeLessThanOrEqual(12)
    })
  }

  test('a real touch drag that remains over the palette does not create a node', async ({
    browserName,
    page,
  }) => {
    test.skip(browserName !== 'chromium', 'CDP provides the real touch stream for this boundary')
    await page.setViewportSize({ width: 390, height: 844 })
    await page.goto('/build/designer?modelType=tbbpm&source=new')
    await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
    await openPalette(page)
    await page.getByRole('textbox', { name: /搜索节点|Search nodes/i }).fill('自动任务')
    const item = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    await expect(item).toBeVisible({ timeout: TIMEOUT })
    await expect(item).toBeEnabled({ timeout: TIMEOUT })
    await item.evaluate((element) => element.scrollIntoView({ block: 'center' }))
    await page.evaluate(
      () =>
        new Promise<void>((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
        )
    )
    const source = await findVisiblePoint(item)
    const paletteBox = await page.locator('.node-palette').boundingBox()
    expect(source).toBeTruthy()
    expect(paletteBox).toBeTruthy()
    const target = {
      x: paletteBox!.x + paletteBox!.width - 12,
      y: source!.y,
    }
    const nodes = page.locator('.x6-node')
    await expect(nodes).toHaveCount(0, { timeout: TIMEOUT })
    const observedEvents = await dispatchTouchDrag(page, browserName, item, source!, target)
    expect(observedEvents).toContain('pointerup:touch')
    expect(observedEvents).not.toContain('pointercancel')
    await page.evaluate(
      () =>
        new Promise<void>((resolve) =>
          requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
        )
    )
    await expect(nodes).toHaveCount(0)
  })
})

test('dark theme updates the graph surface, TBBPM nodes, edge, and labels with readable contrast', async ({
  page,
}) => {
  await page.goto('/build/designer?modelType=tbbpm&source=template&templateId=tpl-4')
  await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
  await expect(page.locator('.x6-edge')).toHaveCount(1, { timeout: TIMEOUT })
  await page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first().click()
  await ensureDarkTheme(page)

  const paletteItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
  const paletteLabel = paletteItem.locator('.drag-palette-item-label')
  await expect(paletteLabel).toHaveCSS('color', 'rgba(255, 255, 255, 0.85)')

  const task = page.locator('.tbbpm-node-autotask')
  await expect(task).toBeVisible()
  await expect(task.locator('.node-label')).toHaveCSS('color', 'rgba(255, 255, 255, 0.85)')
  const backgroundImage = await task.evaluate(
    (element) => getComputedStyle(element).backgroundImage
  )
  expect(backgroundImage).toContain('linear-gradient')

  const paperAndGrid = await page.locator('html').evaluate((element) => {
    const style = getComputedStyle(element)
    return [
      style.getPropertyValue('--graph-paper').trim(),
      style.getPropertyValue('--graph-grid').trim(),
    ]
  })
  expect(paperAndGrid).toEqual(['#141a24', '#2a3344'])

  const edgeLine = page.locator('.x6-edge path[stroke]:not([stroke="transparent"])').first()
  await expect(edgeLine).toBeAttached()
  await expect(edgeLine).toHaveAttribute('stroke', '#8f8f8f')
  const edgeAppearance = await edgeLine.evaluate((element) => {
    const style = getComputedStyle(element)
    return {
      display: style.display,
      length: (element as unknown as SVGGeometryElement).getTotalLength(),
      opacity: Number(style.opacity),
      visibility: style.visibility,
    }
  })
  expect(edgeAppearance).toMatchObject({ display: 'inline', opacity: 1, visibility: 'visible' })
  expect(edgeAppearance.length).toBeGreaterThan(100)
  expect(contrastRatio('#8f8f8f', paperAndGrid[0])).toBeGreaterThanOrEqual(3)
})

test('dark theme applies BPMN material fills, strokes, and readable external labels', async ({
  page,
}) => {
  await page.goto('/build/designer?modelType=bpmn&source=new')
  await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
  await page.locator('.drag-palette-item').filter({ hasText: '服务任务' }).first().click()
  await ensureDarkTheme(page)

  const node = page.locator('.bpmn-service-task')
  const shape = node.locator('svg > rect')
  const label = node.locator('.bpmn-node-label')
  await expect(shape).toHaveCSS('fill', 'rgb(22, 32, 51)')
  await expect(shape).toHaveCSS('stroke', 'rgb(107, 87, 255)')
  const viewport = await page.locator('.x6-graph-scroller').boundingBox()
  expect(viewport).toBeTruthy()
  await page.mouse.click(viewport!.x + 20, viewport!.y + viewport!.height - 20)
  await expect(page.locator('.x6-node-selected')).toHaveCount(0)
  await expect(shape).toHaveCSS('stroke', 'rgb(96, 165, 250)')
  await expect(label).toHaveCSS('color', 'rgba(226, 232, 240, 0.92)')
  const labelBox = await label.boundingBox()
  expect(labelBox?.width).toBeGreaterThan(20)
  expect(labelBox?.height).toBeGreaterThan(8)
  expect(contrastRatio('#e2e8f0', '#141a24')).toBeGreaterThanOrEqual(4.5)
})

test('TBBPM nodes, edge, and text remain rendered and selectable at 50%, 100%, and 200%', async ({
  page,
}) => {
  await page.goto('/build/designer?modelType=tbbpm&source=template&templateId=tpl-4')
  await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
  await expect(page.locator('.x6-edge')).toHaveCount(1, { timeout: TIMEOUT })
  const node = page.locator('.x6-node').first()
  const label = node.locator('.node-label')
  const edgeLine = page.locator('.x6-edge path[stroke]:not([stroke="transparent"])').first()
  const status = page.locator('.status-bar-zoom-btn')
  const zoomOut = page.getByRole('button', { name: '缩小', exact: true })
  const zoomIn = page.getByRole('button', { name: '放大', exact: true })
  const reset = page.getByRole('button', { name: '重置缩放', exact: true })

  await reset.click()
  await expect(status).toContainText('100%')
  const baseWidth = (await node.boundingBox())!.width
  const baseEdgeWidth = await edgeLine.evaluate((element) => element.getBoundingClientRect().width)

  for (let index = 0; index < 5; index += 1) await zoomOut.click()
  await expect(status).toContainText('50%')
  expect((await node.boundingBox())!.width).toBeCloseTo(baseWidth * 0.5, 0)
  await expect(label).toBeVisible()
  await expect(edgeLine).toBeAttached()
  expect(await edgeLine.evaluate((element) => element.getBoundingClientRect().width)).toBeCloseTo(
    baseEdgeWidth * 0.5,
    0
  )
  await node.click()
  await expect(page.locator('.x6-widget-selection-box')).not.toHaveCount(0)

  await reset.click()
  await expect(status).toContainText('100%')
  expect((await node.boundingBox())!.width).toBeCloseTo(baseWidth, 0)

  for (let index = 0; index < 10; index += 1) await zoomIn.click()
  await expect(status).toContainText('200%')
  expect((await node.boundingBox())!.width).toBeCloseTo(baseWidth * 2, 0)
  await expect(label).toBeVisible()
  await expect(edgeLine).toBeAttached()
  expect(await edgeLine.evaluate((element) => element.getBoundingClientRect().width)).toBeCloseTo(
    baseEdgeWidth * 2,
    0
  )
})
