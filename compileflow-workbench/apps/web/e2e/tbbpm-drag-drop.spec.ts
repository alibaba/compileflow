import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm&source=new'
const TIMEOUT = 20_000

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
  const tempPath = path.join(os.tmpdir(), `tbbpm-export-${Date.now()}.xml`)
  await download.saveAs(tempPath)
  const xml = fs.readFileSync(tempPath, 'utf-8')
  fs.unlinkSync(tempPath)
  return xml
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

  test('canvas renders the X6 grid layer used as the placement guide', async ({ page }) => {
    await expect(page.locator('.tbbpm-canvas')).toBeVisible()
    await expect(page.locator('.x6-graph-svg')).toBeVisible()
    await expect(page.locator('.x6-graph-grid')).toBeVisible()
  })
})
