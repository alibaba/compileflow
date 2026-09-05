import { expect, test } from '@playwright/test'

const TIMEOUT = 15_000

test.describe('Settings smoke', () => {
  test('settings page shows preferences and build information', async ({ page }) => {
    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: /Settings|设置/ }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText(/Preferences|偏好设置/).first()).toBeVisible()
    await expect(page.getByText(/Build Information|构建信息/).first()).toBeVisible()
    await expect(
      page
        .locator('code')
        .filter({ hasText: /mock|real/ })
        .first()
    ).toBeVisible()
  })
})
