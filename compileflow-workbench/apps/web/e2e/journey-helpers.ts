import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, type Page } from '@playwright/test'

export const TIMEOUT = 25_000
const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SHOT_DIR = path.join(ROOT, '../test-results/journey-review')

export async function shot(page: Page, name: string) {
  await page.screenshot({ path: path.join(SHOT_DIR, `${name}.png`), fullPage: true })
}

export function trackErrors(page: Page): Error[] {
  const errors: Error[] = []
  page.on('pageerror', (error) => errors.push(error))
  return errors
}

export async function assertNoPageErrors(errors: Error[]) {
  const actionableErrors = errors.filter(
    (error) =>
      !/^ResizeObserver loop (?:limit exceeded|completed with undelivered notifications)\.?$/.test(
        error.message
      )
  )
  expect(actionableErrors, actionableErrors.map((error) => error.message).join('\n')).toEqual([])
}

export async function selectFirstDropdownOption(page: Page, comboboxName: RegExp | string) {
  await page.getByRole('combobox', { name: comboboxName }).click()
  const option = page
    .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
    .first()
  await expect(option).toBeVisible({ timeout: TIMEOUT })
  await option.click()
}

export async function confirmModalOk(page: Page, okName?: RegExp | string) {
  const dialog = page.getByRole('dialog').last()
  await expect(dialog).toBeVisible({ timeout: TIMEOUT })
  // Ant Design mounts the confirm buttons before its zoom-in transition has
  // finished. WebKit can report them as actionable during that transition but
  // drop the resulting pointer click, so wait for the dialog to become stable.
  await expect(dialog).not.toHaveClass(/ant-zoom-appear/, { timeout: TIMEOUT })
  const namedOk = okName
    ? dialog.getByRole('button', { name: okName }).last()
    : dialog.getByRole('button', { name: /确定|确认|OK|提升|中止|回滚/ }).last()
  const footerOk = dialog.locator('.ant-modal-confirm-btns button.ant-btn-primary').last()
  const okButton = (await namedOk.count()) > 0 ? namedOk : footerOk

  await expect(okButton).toBeVisible({ timeout: TIMEOUT })
  await expect(okButton).toBeEnabled({ timeout: TIMEOUT })
  await okButton.click()
  await expect(dialog).toBeHidden({ timeout: TIMEOUT })
}
