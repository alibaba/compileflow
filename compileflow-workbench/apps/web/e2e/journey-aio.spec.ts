import { expect, test } from '@playwright/test'

import { markerProcessXml } from './integration/support/markerProcess'
import { assertNoPageErrors, shot, TIMEOUT, trackErrors } from './journey-helpers'

const SERVER = process.env.COMPILEFLOW_E2E_SERVER_URL ?? 'http://127.0.0.1:8083'

/**
 * All-in-one JAR smoke: bundled UI + API in one process (auth DISABLED via dev profile).
 * Complements Vite→thin-server real suite and API_KEY integration suite.
 */
test.describe('All-in-one Workbench smoke', () => {
  test('bundled UI loads; Operate draft round-trips through embedded API', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.aio.smoke.${crypto.randomUUID()}`

    const health = await request.get(`${SERVER}/actuator/health`)
    expect(health.ok(), await health.text()).toBeTruthy()

    const status = await request.get(`${SERVER}/api/status`)
    expect(status.ok(), await status.text()).toBeTruthy()
    const statusBody = (await status.json()) as { engineAvailable?: boolean }
    expect(statusBody.engineAvailable).toBe(true)

    await page.goto('/settings')
    await expect(page.locator('main').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '250-aio-settings')

    const create = await request.post(`${SERVER}/api/processes`, {
      data: {
        code,
        name: 'All-in-one Smoke Draft',
        type: 'TBBPM',
        xml: markerProcessXml(code, 'aio'),
        tags: ['aio-smoke'],
      },
      headers: { 'Content-Type': 'application/json' },
    })
    const createText = await create.text()
    expect(create.ok(), createText).toBeTruthy()
    expect(typeof (JSON.parse(createText) as { revision: number }).revision).toBe('number')

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: /^Marker$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '251-aio-designer')

    // Preview execute against bundled engine (no publish required).
    const preview = await request.post(`${SERVER}/api/executions/preview`, {
      data: {
        code,
        modelType: 'TBBPM',
        xml: markerProcessXml(code, 'aio-preview'),
        params: {},
      },
      headers: { 'Content-Type': 'application/json' },
    })
    const previewText = await preview.text()
    expect(preview.ok(), previewText).toBeTruthy()
    const previewBody = JSON.parse(previewText) as {
      success?: boolean
      result?: Record<string, unknown>
    }
    expect(previewBody.success).toBe(true)
    expect(previewBody.result?.version_marker).toBe('aio-preview')
    await shot(page, '253-aio-preview')

    await page.getByRole('button', { name: /验证流程|Validate/i }).click()
    await expect(page.getByText(/流程验证|Validation|验证/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '254-aio-validate')

    const saveResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        new URL(response.url()).pathname === `/api/processes/${code}`
    )
    await page
      .getByRole('button', { name: /保存|已保存|Save/i })
      .first()
      .click()
    expect((await saveResponse).ok()).toBeTruthy()

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByText(code).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '252-aio-flows')

    await page.goto('/operate/monitoring')
    await expect(page.locator('main').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '255-aio-monitoring')

    await page.goto('/operate/logs')
    await expect(page.locator('main').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '257-aio-logs')

    await page.goto('/operate/deployments')
    await expect(page.locator('main').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '258-aio-deployments')

    // Re-open designer and run local simulation (bundled UI, no Vite).
    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await page.getByRole('button', { name: /调试流程|Debug/i }).click()
    await expect(page.locator('.flow-debugger-panel')).toBeVisible({ timeout: TIMEOUT })
    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /开始|Start/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已完成|Completed|错误|Error/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '259-aio-sim-complete')

    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: /示例库|Examples/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '256-aio-learn')

    const latest = await request.get(`${SERVER}/api/processes/${encodeURIComponent(code)}`)
    const latestText = await latest.text()
    expect(latest.ok(), latestText).toBeTruthy()
    const latestBody = JSON.parse(latestText) as { revision: number }
    const del = await request.delete(
      `${SERVER}/api/processes/${encodeURIComponent(code)}?expectedRevision=${latestBody.revision}`
    )
    expect(del.ok() || del.status() === 204, await del.text()).toBeTruthy()
    await assertNoPageErrors(errors)
  })
})
