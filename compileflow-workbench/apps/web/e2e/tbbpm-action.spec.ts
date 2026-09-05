import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'

import { expect, type Page, test } from '@playwright/test'

const DESIGNER_URL = '/build/designer?modelType=tbbpm'
const TIMEOUT = 20000

async function gotoDesignerWithExample(page: Page) {
  await page.goto(DESIGNER_URL)
  await page.waitForLoadState('networkidle')
  await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
  await page.getByRole('button', { name: '加载示例' }).click()
  await expect(page.locator('.ant-message-notice').first()).toBeVisible({ timeout: TIMEOUT })
  await page.waitForTimeout(500)
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

test.describe('TBBPM Action / XML', () => {
  test('loaded example XML contains TBBPM nodes', async ({ page }) => {
    await gotoDesignerWithExample(page)
    const xml = await exportXmlText(page)
    expect(xml.length).toBeGreaterThan(100)
    expect(xml).toMatch(/<bpm[\s>]/)
    expect(xml).toMatch(/<(autoTask|start|exclusive|end)\b/)
  })

  test('export XML from header more menu', async ({ page }) => {
    await gotoDesignerWithExample(page)
    const downloadPromise = page.waitForEvent('download')
    await page.locator('.header-more-btn').click()
    await page.getByText('导出 XML').click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toMatch(/\.(xml|bpm)$/i)
  })

  test('split view shows buffered XML editor after example load', async ({ page }) => {
    await gotoDesignerWithExample(page)
    await page.getByTestId('designer-tab-split').click()
    await expect(page.locator('.unified-designer-body--split')).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.xml-code-editor-panel')).toBeVisible()
    await expect(page.locator('.xml-code-editor-toolbar')).toBeVisible()
    await expect(page.locator('.x6-graph')).toBeVisible()
  })
})
