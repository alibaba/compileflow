import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, test } from '@playwright/test'

import { assertNoPageErrors, shot, TIMEOUT, trackErrors } from './journey-helpers'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SHOT_DIR = path.join(ROOT, '../test-results/journey-review')

test.describe('Async dead-letter single requeue', () => {
  test('filter dead letters → inspect → confirm requeue', async ({ page }) => {
    const errors = trackErrors(page)
    await page.goto('/operate/monitoring')
    await expect(page.getByText(/异步|Async/i).first()).toBeVisible({ timeout: TIMEOUT })

    // Prefer status filter labeled for invocation status.
    const statusFilter = page
      .getByLabel(/Invocation status|调用状态|状态/i)
      .or(page.locator('main .ant-select').filter({ hasText: /状态|Status|dead|死信/i }))
      .first()
    if (await statusFilter.count()) {
      await statusFilter.click()
    } else {
      // Fall back to last select in monitoring filters area.
      await page.locator('main .ant-select').last().click()
    }
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /死信|dead_letter|Dead letter/i })
      .first()
      .click()

    await expect(page.getByText('order-async-42')).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({
      path: path.join(SHOT_DIR, '100-async-dead-letter-row.png'),
      fullPage: true,
    })

    await page
      .locator('tr')
      .filter({ hasText: 'order-async-42' })
      .getByRole('button', { name: /查看|View/i })
      .click()
    await expect(page.getByRole('dialog').or(page.locator('.ant-drawer')).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText(/LEASE_EXPIRED|lease expired|死信/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '101-async-dead-letter-detail')

    await page.getByRole('button', { name: '重新入队', exact: true }).click()
    const confirmDialog = page.getByRole('dialog', {
      name: /重新入队这条死信|Requeue this dead-letter/i,
    })
    await expect(confirmDialog).toBeVisible({ timeout: TIMEOUT })
    const confirmButton = confirmDialog.getByRole('button', { name: /确\s*认|Confirm/i })
    await expect(confirmButton).toBeEnabled()
    await confirmButton.click()
    await expect(confirmDialog).toBeHidden({ timeout: TIMEOUT })
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已重新入队|requeued/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '102-async-requeued')
    // Detail drawer still shows the invocation id after redrive; success toast is the contract proof.
    await page.keyboard.press('Escape')
    await shot(page, '103-async-requeue-done')

    await assertNoPageErrors(errors)
  })
})
