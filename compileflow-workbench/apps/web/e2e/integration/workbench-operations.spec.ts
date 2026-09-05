import { expect, test } from '@playwright/test'

import { browserUrl } from './support/integrationRuntime'

test('bundled Workbench loads and operates through the trusted edge', async ({ page }) => {
  test.skip(!browserUrl, 'COMPILEFLOW_E2E_BROWSER_URL is required for browser integration')

  const apiPaths = new Set<string>()
  const browserCredentialLeaks: string[] = []
  const failedApiRequests: string[] = []
  const failedApiResponses: string[] = []
  let pendingApiRequests = 0
  let deploymentHealthResponses = 0
  let asyncHealthResponses = 0

  page.on('request', (request) => {
    const url = new URL(request.url())
    if (!url.pathname.startsWith('/api/')) return
    pendingApiRequests += 1
    apiPaths.add(url.pathname)
    if (request.headers()['x-api-key']) browserCredentialLeaks.push(url.pathname)
  })
  page.on('requestfinished', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/')) pendingApiRequests -= 1
  })
  page.on('requestfailed', (request) => {
    const path = new URL(request.url()).pathname
    if (!path.startsWith('/api/')) return
    pendingApiRequests -= 1
    failedApiRequests.push(`${request.failure()?.errorText ?? 'request failed'} ${path}`)
  })
  page.on('response', (response) => {
    const url = new URL(response.url())
    if (!url.pathname.startsWith('/api/')) return
    if (url.pathname === '/api/deployment-control/health' && response.ok()) {
      deploymentHealthResponses += 1
    }
    if (url.pathname === '/api/async-invocations/health' && response.ok()) {
      asyncHealthResponses += 1
    }
    if (!response.ok()) failedApiResponses.push(`${response.status()} ${url.pathname}`)
  })

  const documentResponse = await page.goto(`${browserUrl}/operate/monitoring`)
  expect(documentResponse?.ok()).toBeTruthy()
  await expect(page.getByText('运维控制面')).toBeVisible()
  await expect(page.getByText('路由投递中')).toBeVisible()
  await expect(page.getByText('持久化调用重试管道')).toBeVisible()

  const initialHealthResponses = deploymentHealthResponses
  const deploymentRequeue = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/deployment-control/dead-letters/requeue'
  )
  await page.getByRole('button', { name: '重新入队部署死信' }).click()
  expect((await deploymentRequeue).ok()).toBeTruthy()
  await expect(page.getByText('部署死信已重新入队')).toBeVisible()
  await expect.poll(() => deploymentHealthResponses).toBeGreaterThan(initialHealthResponses)

  const healthResponsesBeforeAsyncRequeue = {
    deployment: deploymentHealthResponses,
    async: asyncHealthResponses,
  }
  const asyncRequeue = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/async-invocations/dead-letters/requeue'
  )
  await page.getByRole('button', { name: '重新入队异步调用死信' }).click()
  expect((await asyncRequeue).ok()).toBeTruthy()
  await expect(page.getByText('异步调用死信已重新入队')).toBeVisible()
  await expect
    .poll(() => deploymentHealthResponses)
    .toBeGreaterThan(healthResponsesBeforeAsyncRequeue.deployment)
  await expect
    .poll(() => asyncHealthResponses)
    .toBeGreaterThan(healthResponsesBeforeAsyncRequeue.async)
  await expect.poll(() => pendingApiRequests).toBe(0)

  expect(browserCredentialLeaks).toEqual([])
  expect(failedApiRequests).toEqual([])
  expect(failedApiResponses).toEqual([])
  expect([...apiPaths]).toEqual(
    expect.arrayContaining([
      '/api/deployment-control/health',
      '/api/async-invocations/dead-letters/requeue',
      '/api/async-invocations/health',
      '/api/monitoring/deploy-runtime',
      '/api/monitoring/errors',
      '/api/monitoring/metrics',
      '/api/monitoring/top-processes',
      '/api/monitoring/trends',
      '/api/monitoring/version-distribution',
    ])
  )
})
