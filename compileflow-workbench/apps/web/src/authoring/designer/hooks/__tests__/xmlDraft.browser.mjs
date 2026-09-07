import console from 'node:console'
import { mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import process from 'node:process'

import { chromium, expect } from '@playwright/test'

const screenshotDir = join(tmpdir(), 'compileflow-workbench-browser-tests')
await mkdir(screenshotDir, { recursive: true })
const browser = await chromium.launch({ headless: true })
try {
  for (const width of [1440, 390]) {
    const page = await browser.newPage({ viewport: { width, height: width === 390 ? 844 : 1000 } })
    await page.goto(
      `${process.argv[2] ?? 'http://127.0.0.1:5175'}/build/designer?source=new&modelType=tbbpm`
    )
    await page.getByTestId('designer-tab-xml').click()
    await page.locator('.monaco-editor .view-lines').click()
    await page.keyboard.press('ControlOrMeta+A')
    await page.keyboard.press('Backspace')
    await expect(page.locator('.monaco-editor .view-lines')).toHaveText('')
    await page.keyboard.insertText(
      '<bpm code="draft"><start id="start" name="Retained XML input"><transition to="end"/></start><end id="end"/></bpm>'
    )
    await expect(page.getByRole('button', { name: 'Apply to canvas', exact: true })).toBeEnabled()
    await page.getByTestId('designer-tab-visual').click()
    await page.getByTestId('designer-tab-xml').click()
    await expect(page.locator('.monaco-editor .view-lines')).toContainText('Retained XML input')
    await expect(page.locator('.ant-notification-notice-error')).toHaveCount(0)
    await page.getByRole('button', { name: 'Back to Build', exact: true }).click()
    await expect(page.getByRole('dialog')).toContainText('Unsaved changes')
    await expect(page.getByRole('button', { name: 'Save and leave', exact: true })).toBeEnabled()
    await page.screenshot({
      path: join(screenshotDir, `xml-draft-${width}.png`),
    })
    await page.getByRole('button', { name: 'Save and leave', exact: true }).click()
    await expect(page).toHaveURL(/\/build$/)
    const definitions = await page.evaluate(async () => {
      const { exportAllData } = await import('/src/authoring/designer/api/processStorage.ts')
      return (await exportAllData()).processes.map((entry) => entry.definition)
    })
    expect(definitions.some((xml) => xml.includes('Retained XML input'))).toBe(true)
    console.log(
      `${width}px: real Monaco typing, view switching, leave guard and IndexedDB XML persistence passed`
    )
    await page.close()
  }
} finally {
  await browser.close()
}
