import { createHash } from 'node:crypto'

import type { APIRequestContext, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

import { apiHeaders, browserUrl, serverUrl } from './support/integrationRuntime'
import { markerProcessXml } from './support/markerProcess'
import { expectJson, postJson } from './support/workbenchApi'

interface ProcessDraft {
  revision: number
}

interface PublishedVersion {
  version: string
}

interface DeploymentResult {
  baselineVersion?: string
  id: string
  operation: 'deploy' | 'rollback'
  revision: number
  routeRevision: number
  status: 'in_progress' | 'completed' | 'aborted'
  version: string
}

interface RouteResult {
  candidateWeightBps?: number
  candidateVersion?: string
  revision: number
  stableVersion: string
}

interface ExecutionResult {
  result?: Record<string, unknown>
  routing: {
    effectiveVersion?: string
    routeRevision?: number
    target?: 'STABLE' | 'CANDIDATE'
  }
  success: boolean
}

async function executeAlias(
  request: APIRequestContext,
  code: string,
  invocationId: string,
  routingKey?: string
): Promise<ExecutionResult> {
  return postJson<ExecutionResult>(request, `/api/processes/${code}/execute`, {
    invocationId,
    params: {},
    routing: { alias: 'production', routingKey },
  })
}

async function chooseOpenSelectOption(page: Page, optionText: string): Promise<void> {
  // Ant Design renders hidden ARIA mirror options alongside the visible popup items.
  const option = page
    .locator('.ant-select-dropdown:visible')
    .locator('.ant-select-item-option-content')
    .filter({ hasText: optionText })
  await expect(option).toHaveText(optionText)
  await expect(option).toBeVisible()
  await option.click()
}

function routeBucket(code: string, candidateVersion: string, routingKey: string): number {
  const hash = createHash('sha256')
  hash.update('CFROUTE1', 'ascii')
  for (const value of ['default', code, 'production', candidateVersion, routingKey]) {
    const bytes = Buffer.from(value, 'utf8')
    const length = Buffer.alloc(4)
    length.writeUInt32BE(bytes.length)
    hash.update(length)
    hash.update(bytes)
  }
  return Number(hash.digest().readBigUInt64BE() % 10_000n)
}

function routingKeyFor(
  code: string,
  candidateVersion: string,
  candidate: boolean,
  weightBps = 5_000
): string {
  for (let index = 0; index < 10_000; index += 1) {
    const key = `cohort-${index}`
    if (routeBucket(code, candidateVersion, key) < weightBps === candidate) return key
  }
  throw new Error(`No ${candidate ? 'candidate' : 'stable'} routing key found`)
}

function observeBrowserApi(page: Page) {
  const credentialLeaks: string[] = []
  const failedRequests: string[] = []
  const failedResponses: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/api/') && request.headers()['x-api-key']) credentialLeaks.push(path)
  })
  page.on('requestfailed', (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/api/')) {
      failedRequests.push(`${request.failure()?.errorText ?? 'request failed'} ${path}`)
    }
  })
  page.on('response', (response) => {
    const path = new URL(response.url()).pathname
    if (path.startsWith('/api/') && !response.ok()) {
      failedResponses.push(`${response.status()} ${path}`)
    }
  })
  return { credentialLeaks, failedRequests, failedResponses }
}

test('publishes, canaries, promotes, rolls back, and executes the effective versions', async ({
  page,
  request,
}) => {
  test.skip(!browserUrl, 'COMPILEFLOW_E2E_BROWSER_URL is required for browser integration')
  test.setTimeout(90_000)

  const browserApi = observeBrowserApi(page)
  const code = `workbench.lifecycle.${crypto.randomUUID()}`
  const flowName = 'Workbench Lifecycle Evidence'
  const v1PublishKey = crypto.randomUUID()

  const draftV1 = await postJson<ProcessDraft>(request, '/api/processes', {
    code,
    name: flowName,
    type: 'TBBPM',
    xml: markerProcessXml(code, 'v1'),
    tags: ['integration'],
  })
  const v1 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Baseline', expectedRevision: draftV1.revision },
    { 'Idempotency-Key': v1PublishKey }
  )
  const baseline = await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: v1.version,
      alias: 'production',
      expectedRouteRevision: 0,
      strategy: 'all_at_once',
      notes: 'Integration baseline',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  expect(baseline).toEqual(
    expect.objectContaining({ status: 'completed', version: v1.version, routeRevision: 1 })
  )
  expect(baseline.baselineVersion).toBeUndefined()

  const baselineExecution = await executeAlias(request, code, 'lifecycle-baseline')
  expect(baselineExecution).toEqual(
    expect.objectContaining({
      success: true,
      result: expect.objectContaining({ version_marker: 'v1' }),
      routing: expect.objectContaining({
        effectiveVersion: v1.version,
        routeRevision: baseline.routeRevision,
        target: 'STABLE',
      }),
    })
  )

  const draftV2 = await expectJson<ProcessDraft>(
    await request.put(`${serverUrl}/api/processes/${code}`, {
      headers: apiHeaders(),
      data: {
        name: flowName,
        xml: markerProcessXml(code, 'v2'),
        tags: ['integration'],
        expectedRevision: draftV1.revision,
      },
    })
  )
  const v2 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Candidate', expectedRevision: draftV2.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )

  const replayedV1 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Baseline', expectedRevision: draftV1.revision },
    { 'Idempotency-Key': v1PublishKey }
  )
  expect(replayedV1.version).toBe(v1.version)

  const versionResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'GET' &&
      new URL(response.url()).pathname === `/api/processes/${code}/versions`
  )
  expect(
    (
      await page.goto(`${browserUrl}/operate/deploy-wizard?processCode=${encodeURIComponent(code)}`)
    )?.ok()
  ).toBeTruthy()
  expect((await versionResponse).ok()).toBeTruthy()
  await expect(page.getByRole('heading', { level: 1, name: '部署' })).toBeVisible()

  const searchedVersions = page.waitForResponse((response) => {
    const url = new URL(response.url())
    return (
      response.request().method() === 'GET' &&
      url.pathname === `/api/processes/${code}/versions` &&
      url.searchParams.get('versionPrefix') === v2.version
    )
  })
  const versionSelect = page.getByRole('combobox', { name: /版本/ })
  await versionSelect.fill(v2.version)
  expect((await searchedVersions).ok()).toBeTruthy()
  await chooseOpenSelectOption(page, v2.version)
  await page.getByRole('button', { name: '下一步' }).click()
  await page.getByLabel('别名').click()
  await chooseOpenSelectOption(page, '生产（PRODUCTION）')
  await page.getByLabel('策略').click()
  await chooseOpenSelectOption(page, '灰度发布')
  await page.getByRole('spinbutton', { name: '灰度流量权重' }).fill('5000')
  await page.getByRole('button', { name: '下一步' }).click()

  const createCanaryResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/deployments'
  )
  await page
    .locator('main')
    .getByRole('button', { name: /^(rocket\s*)?(立即部署|部署|Deploy now|Deploy)$/i })
    .click()
  const canary = await expectJson<DeploymentResult>(await createCanaryResponse)
  expect(canary).toEqual(
    expect.objectContaining({
      baselineVersion: v1.version,
      status: 'in_progress',
      version: v2.version,
    })
  )
  await expect(page.getByRole('button', { name: /查看部署/ })).toBeVisible()
  await page.getByRole('button', { name: /查看部署/ }).click()
  await expect(page).toHaveURL(new RegExp(`/operate/deployments/${canary.id}$`))

  const canaryRoute = await expectJson<RouteResult>(
    await request.get(
      `${serverUrl}/api/deployment-routes?processCode=${encodeURIComponent(code)}&alias=production`,
      { headers: apiHeaders() }
    )
  )
  expect(canaryRoute).toEqual(
    expect.objectContaining({
      stableVersion: v1.version,
      candidateVersion: v2.version,
      candidateWeightBps: 5_000,
      revision: canary.routeRevision,
    })
  )

  const candidateKey = routingKeyFor(code, v2.version, true)
  const stableKey = routingKeyFor(code, v2.version, false)
  const stableExecution = await executeAlias(request, code, 'lifecycle-stable', stableKey)
  expect(stableExecution.routing).toEqual(
    expect.objectContaining({
      effectiveVersion: v1.version,
      routeRevision: canary.routeRevision,
      target: 'STABLE',
    })
  )
  const candidateExecution = await executeAlias(
    request,
    code,
    'lifecycle-candidate-0',
    candidateKey
  )
  expect(candidateExecution).toEqual(
    expect.objectContaining({
      success: true,
      result: expect.objectContaining({ version_marker: 'v2' }),
      routing: expect.objectContaining({
        effectiveVersion: v2.version,
        routeRevision: canary.routeRevision,
        target: 'CANDIDATE',
      }),
    })
  )
  for (let offset = 1; offset < 20; offset += 5) {
    const batch = Array.from({ length: Math.min(5, 20 - offset) }, (_, index) =>
      executeAlias(request, code, `lifecycle-candidate-${offset + index}`, candidateKey)
    )
    const executions = await Promise.all(batch)
    expect(executions.every((execution) => execution.routing.effectiveVersion === v2.version)).toBe(
      true
    )
  }

  const healthResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === `/api/deployments/${canary.id}/canary/evaluate`
  )
  await page.getByRole('button', { name: '健康评估' }).click()
  expect((await healthResponse).ok()).toBeTruthy()
  await expect(page.getByText('灰度版本健康')).toBeVisible()

  await page.getByRole('button', { name: /全量发布|Promote/i }).click()
  const promoteDialog = page.getByRole('dialog', {
    name: /确认全量发布|Promote candidate/i,
  })
  await expect(promoteDialog).toBeVisible()
  const promoteResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === `/api/deployments/${canary.id}/promote`
  )
  await promoteDialog.getByRole('button', { name: /全量发布|Promote/i }).click()
  const promoted = await expectJson<DeploymentResult>(await promoteResponse)
  expect(promoted.status).toBe('completed')
  await expect(page.getByText('候选版本已承接全部流量')).toBeVisible()

  const promotedExecution = await executeAlias(request, code, 'lifecycle-promoted')
  expect(promotedExecution).toEqual(
    expect.objectContaining({
      result: expect.objectContaining({ version_marker: 'v2' }),
      routing: expect.objectContaining({
        effectiveVersion: v2.version,
        routeRevision: promoted.routeRevision,
        target: 'STABLE',
      }),
    })
  )

  await page.getByRole('button', { name: /回滚|Rollback/i }).click()
  const rollbackDialog = page.getByRole('dialog', { name: /确认回滚|Confirm rollback/i })
  await expect(rollbackDialog).toBeVisible()
  const rollbackResponse = page.waitForResponse(
    (response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === `/api/deployments/${canary.id}/rollback`
  )
  await rollbackDialog.getByRole('button', { name: /回\s*滚/ }).click()
  const rollback = await expectJson<DeploymentResult>(await rollbackResponse)
  expect(rollback).toEqual(
    expect.objectContaining({
      baselineVersion: v2.version,
      operation: 'rollback',
      status: 'completed',
      version: v1.version,
    })
  )
  await expect(page).toHaveURL(new RegExp(`/operate/deployments/${rollback.id}$`))

  const rolledBackExecution = await executeAlias(request, code, 'lifecycle-rolled-back')
  expect(rolledBackExecution).toEqual(
    expect.objectContaining({
      result: expect.objectContaining({ version_marker: 'v1' }),
      routing: expect.objectContaining({
        effectiveVersion: v1.version,
        routeRevision: rollback.routeRevision,
        target: 'STABLE',
      }),
    })
  )

  expect(browserApi.credentialLeaks).toEqual([])
  expect(browserApi.failedRequests).toEqual([])
  expect(browserApi.failedResponses).toEqual([])
})
