import { expect, test } from '@playwright/test'

test('hub metrics keep readable semantic colors in both themes', async ({ page }) => {
  for (const route of ['/build', '/operate']) {
    await page.goto(route)
    const values = page.locator('[data-hub-surface="true"] [class*="metricValue"]')
    await expect(values.first()).toBeVisible()

    const lightColors = await values.evaluateAll((elements) =>
      elements.map((element) => getComputedStyle(element).color)
    )
    expect(lightColors).not.toContain('rgb(248, 250, 252)')
  }

  await page.getByRole('button', { name: /切换到深色模式|Switch to dark mode/i }).click()
  const darkValues = page.locator('[data-hub-surface="true"] [class*="metricValue"]')
  await expect(darkValues.first()).toBeVisible()
  const darkColors = await darkValues.evaluateAll((elements) =>
    elements.map((element) => getComputedStyle(element).color)
  )
  expect(darkColors).not.toContain('rgb(15, 23, 42)')
})
