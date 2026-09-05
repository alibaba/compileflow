import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { expect, test } from '@playwright/test'

import { markerProcessXml } from './integration/support/markerProcess'
import { assertNoPageErrors, shot, TIMEOUT, trackErrors } from './journey-helpers'

const ROOT = path.dirname(fileURLToPath(import.meta.url))
const SHOT_DIR = path.join(ROOT, '../test-results/journey-review')
const SERVER = 'http://127.0.0.1:8082'
const EDGE = 'http://127.0.0.1:4174'
const API_KEY = 'compileflow-e2e-test-api-key-32charsxx'

/**
 * Secured edge: Vite → trusted edge (injects X-API-Key) → API_KEY server.
 */
test.describe('Secured Vite → edge → API_KEY server', () => {
  test('direct server rejects missing key; accepts configured key', async ({ request }) => {
    const unauthorized = await request.get(`${SERVER}/api/examples`)
    expect(unauthorized.status()).toBe(401)
    const problem = (await unauthorized.json()) as { code?: string; status?: number }
    expect(problem).toEqual(expect.objectContaining({ code: 'UNAUTHENTICATED', status: 401 }))

    const authorized = await request.get(`${SERVER}/api/examples`, {
      headers: { 'X-API-Key': API_KEY },
    })
    expect(authorized.ok()).toBeTruthy()
    const catalog = (await authorized.json()) as unknown[]
    expect(Array.isArray(catalog)).toBeTruthy()
    expect(catalog.length).toBeGreaterThan(0)
  })

  test('trusted edge strips client key and still reaches server', async ({ request }) => {
    const forged = await request.get(`${EDGE}/api/examples`, {
      headers: { 'X-API-Key': 'untrusted-client-value-should-be-stripped' },
    })
    expect(forged.ok(), await forged.text()).toBeTruthy()

    const missing = await request.get(`${EDGE}/api/examples`)
    expect(missing.ok(), await missing.text()).toBeTruthy()
  })

  test('Vite UI works through secured edge without browser credentials', async ({ page }) => {
    test.setTimeout(90_000)
    const errors = trackErrors(page)
    const apiKeyLeaks: string[] = []
    const apiHits: string[] = []
    page.on('request', (req) => {
      const pathname = new URL(req.url()).pathname
      if (pathname.startsWith('/api/') && req.headers()['x-api-key']) {
        apiKeyLeaks.push(pathname)
      }
    })
    page.on('response', (response) => {
      const pathname = new URL(response.url()).pathname
      if (pathname.startsWith('/api/')) {
        apiHits.push(`${response.status()} ${pathname}`)
      }
    })

    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: /设置|Settings/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page
        .locator('code')
        .filter({ hasText: /^real$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.screenshot({ path: path.join(SHOT_DIR, '190-secured-settings.png'), fullPage: true })

    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: /示例库|Examples/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.locator('main article').first()).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({ path: path.join(SHOT_DIR, '191-secured-learn.png'), fullPage: true })

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({ path: path.join(SHOT_DIR, '192-secured-flows.png'), fullPage: true })

    expect(apiKeyLeaks).toEqual([])
    expect(apiHits.some((hit) => hit.startsWith('2'))).toBeTruthy()
    expect(apiHits.some((hit) => hit.startsWith('401'))).toBeFalsy()
    await shot(page, '193-secured-edge-done')
    await assertNoPageErrors(errors)
  })

  test('Vite designer create/save Operate draft through secured edge', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.secured.draft.${crypto.randomUUID()}`
    const apiKeyLeaks: string[] = []
    page.on('request', (req) => {
      const pathname = new URL(req.url()).pathname
      if (pathname.startsWith('/api/') && req.headers()['x-api-key']) {
        apiKeyLeaks.push(pathname)
      }
    })

    // Create via trusted edge (no client key) — proves write path is edge-authenticated.
    const create = await request.post(`${EDGE}/api/processes`, {
      data: {
        code,
        name: 'Secured Edge Draft',
        type: 'TBBPM',
        xml: markerProcessXml(code, 'secured'),
        tags: ['secured-e2e'],
      },
      headers: { 'Content-Type': 'application/json' },
    })
    const createText = await create.text()
    expect(create.ok(), createText).toBeTruthy()

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
    await shot(page, '194-secured-designer-open')

    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const before = await page.locator('.x6-node').count()
    await autoTaskItem.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.x6-node')).toHaveCount(before + 1, { timeout: TIMEOUT })
    await shot(page, '195-secured-palette')

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
    await shot(page, '196-secured-saved')

    // Read-back via edge (still no client key).
    const saved = await request.get(`${EDGE}/api/processes/${encodeURIComponent(code)}`)
    const savedText = await saved.text()
    expect(saved.ok(), savedText).toBeTruthy()
    const body = JSON.parse(savedText) as { xml?: string; revision: number }
    expect((body.xml?.match(/<autoTask\b/g) || []).length).toBeGreaterThanOrEqual(2)

    // Direct server still requires the key.
    const denied = await request.get(`${SERVER}/api/processes/${encodeURIComponent(code)}`)
    expect(denied.status()).toBe(401)
    const allowed = await request.get(`${SERVER}/api/processes/${encodeURIComponent(code)}`, {
      headers: { 'X-API-Key': API_KEY },
    })
    expect(allowed.ok()).toBeTruthy()

    expect(apiKeyLeaks).toEqual([])
    await shot(page, '197-secured-designer-done')
    await assertNoPageErrors(errors)
  })
})
