import { expect, test } from '@playwright/test'

import { assertNoPageErrors, shot, TIMEOUT, trackErrors } from './journey-helpers'

/**
 * Execute every built-in Learn example (mock catalog currently has a small fixed set).
 */
test.describe('Learn catalog full execute', () => {
  test('open and execute every example card', async ({ page }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: '示例库' })).toBeVisible({ timeout: TIMEOUT })

    const cards = page.locator('main article')
    await expect(cards.first()).toBeVisible({ timeout: TIMEOUT })
    const total = await cards.count()
    expect(total).toBeGreaterThan(0)

    const titles: string[] = []
    for (let i = 0; i < total; i += 1) {
      titles.push((await cards.nth(i).innerText()).split('\n')[0]?.trim() ?? `card-${i}`)
    }

    for (let i = 0; i < total; i += 1) {
      await page.goto('/learn/examples')
      await expect(cards.first()).toBeVisible({ timeout: TIMEOUT })
      await cards.nth(i).click()
      await page.waitForURL(/\/learn\/examples\/.+/, { timeout: TIMEOUT })

      await page.getByRole('tab', { name: /代码|Code/i }).click()
      await expect(page.locator('.ant-tabs-tabpane-active pre').first()).toBeAttached({
        timeout: TIMEOUT,
      })

      await page.getByRole('tab', { name: /执行|Execute/i }).click()
      await page.locator('.ant-tabs-tabpane-active').getByRole('button', { name: /执行/ }).click()
      await expect(
        page.locator('[class*="execResultBlock"]').getByText('执行成功', { exact: true })
      ).toBeVisible({ timeout: TIMEOUT })

      await page.screenshot({
        path: test.info().outputPath(`70-example-execute-${String(i + 1).padStart(2, '0')}.png`),
        fullPage: true,
      })

      // Open designer from each example once.
      await page.getByRole('button', { name: /在设计器中打开|打开设计器/ }).click()
      await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
      await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
      await page.screenshot({
        path: test.info().outputPath(`71-example-designer-${String(i + 1).padStart(2, '0')}.png`),
        fullPage: true,
      })
    }

    await shot(page, '72-learn-catalog-complete')
    // Persist titles into a soft assertion trail via page title count.
    expect(titles.length).toBe(total)
    await assertNoPageErrors(errors)
  })
})
