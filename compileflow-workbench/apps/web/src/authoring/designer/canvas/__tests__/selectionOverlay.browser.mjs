import console from 'node:console'
import { mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import process from 'node:process'

import { chromium, expect } from '@playwright/test'

const baseUrl = process.argv[2] ?? 'http://127.0.0.1:5175'
const screenshotDir = join(tmpdir(), 'compileflow-workbench-browser-tests')
await mkdir(screenshotDir, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  for (const modelType of ['tbbpm', 'bpmn']) {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 }, hasTouch: true })
    await page.goto(`${baseUrl}/build/designer?source=new&modelType=${modelType}`)
    await page.getByRole('button', { name: 'Expand node palette', exact: true }).click()
    const label = modelType === 'tbbpm' ? 'Auto task' : 'Service task'
    await page.locator('.drag-palette-item').filter({ hasText: label }).first().tap()
    await expect(page.locator('.x6-node').last()).toContainText(label)
    await expect(page.locator('.x6-widget-selection-box')).toHaveCSS(
      'background-color',
      'rgba(0, 0, 0, 0)'
    )
    await page.getByRole('button', { name: 'Collapse node palette', exact: true }).click()
    await expect
      .poll(async () => (await page.locator('.x6-graph-scroller').boundingBox())?.width)
      .toBe(390)
    await page.screenshot({
      path: join(screenshotDir, `authoring-selection-${modelType}.png`),
    })
    console.log(`${modelType}: touch insertion, transparent selection, full-width canvas passed`)
    await page.close()
  }
} finally {
  await browser.close()
}
