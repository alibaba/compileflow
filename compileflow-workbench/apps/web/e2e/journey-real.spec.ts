import { createHash } from 'node:crypto'
import fs from 'node:fs'

import type { APIRequestContext, Page } from '@playwright/test'
import { expect, test } from '@playwright/test'

import {
  failingProcessXml,
  markerBpmnProcessXml,
  markerProcessXml,
} from './integration/support/markerProcess'
import { assertNoPageErrors, confirmModalOk, shot, TIMEOUT, trackErrors } from './journey-helpers'

const SERVER = process.env.COMPILEFLOW_E2E_SERVER_URL ?? 'http://127.0.0.1:8080'

interface ProcessDraft {
  revision: number
  xml?: string
}

interface PublishedVersion {
  version: string
}

interface DeploymentResult {
  baselineVersion?: string
  id: string
  operation?: 'deploy' | 'rollback'
  routeRevision: number
  status: 'in_progress' | 'completed' | 'aborted'
  version: string
}

interface ExecutionResult {
  result?: Record<string, unknown>
  routing: {
    effectiveVersion?: string
    target?: 'STABLE' | 'CANDIDATE'
  }
  success: boolean
}

interface AsyncInvocation {
  invocationId: string
  status: 'queued' | 'running' | 'succeeded' | 'dead_letter'
}

async function postJson<T>(
  request: APIRequestContext,
  apiPath: string,
  data: unknown,
  headers: Record<string, string> = {}
): Promise<T> {
  const response = await request.post(`${SERVER}${apiPath}`, {
    data,
    headers: { 'Content-Type': 'application/json', ...headers },
  })
  const body = await response.text()
  expect(response.ok(), `POST ${apiPath} → ${response.status()}: ${body}`).toBeTruthy()
  return JSON.parse(body) as T
}

async function putJson<T>(request: APIRequestContext, apiPath: string, data: unknown): Promise<T> {
  const response = await request.put(`${SERVER}${apiPath}`, {
    data,
    headers: { 'Content-Type': 'application/json' },
  })
  const body = await response.text()
  expect(response.ok(), `PUT ${apiPath} → ${response.status()}: ${body}`).toBeTruthy()
  return JSON.parse(body) as T
}

async function getJson<T>(request: APIRequestContext, apiPath: string): Promise<T> {
  const response = await request.get(`${SERVER}${apiPath}`)
  const body = await response.text()
  expect(response.ok(), `GET ${apiPath} → ${response.status()}: ${body}`).toBeTruthy()
  return JSON.parse(body) as T
}

async function chooseOpenSelectOption(page: Page, optionText: string | RegExp): Promise<void> {
  const option = page
    .locator('.ant-select-dropdown:visible')
    .locator('.ant-select-item-option-content')
    .filter({ hasText: optionText })
  await expect(option.first()).toBeVisible({ timeout: TIMEOUT })
  await option.first().click()
}

async function connectNodesViaGraph(
  page: Page,
  sourceLabel: string,
  sourcePort: string,
  targetLabel: string,
  targetPort: string
): Promise<void> {
  // X6 connecting gestures are unreliable under Playwright (8px magnets + foreignObject
  // hit-testing). Drive the same product path the UI uses: addEdge → edge:connected → Redux.
  await page.evaluate(
    ({ sourceLabel, sourcePort, targetLabel, targetPort }) => {
      type GraphNode = { id: string; getData: () => { label?: string } }
      type GraphEdge = { id: string }
      type DebugGraph = {
        getNodes: () => GraphNode[]
        addEdge: (metadata: Record<string, unknown>) => GraphEdge
        trigger: (name: string, args: { edge: GraphEdge }) => void
      }
      const graph = (window as unknown as { __x6Graph?: DebugGraph }).__x6Graph
      if (!graph) throw new Error('__x6Graph is not exposed')

      const source = graph.getNodes().find((node) => node.getData()?.label === sourceLabel)
      const target = graph.getNodes().find((node) => node.getData()?.label === targetLabel)
      if (!source || !target) {
        throw new Error(`Missing nodes for ${sourceLabel} → ${targetLabel}`)
      }

      const edge = graph.addEdge({
        source: { cell: source.id, port: sourcePort },
        target: { cell: target.id, port: targetPort },
        data: {},
        attrs: {
          line: {
            stroke: '#8f8f8f',
            strokeWidth: 2,
            targetMarker: { name: 'block', width: 12, height: 8 },
          },
          wrap: { stroke: 'transparent', strokeWidth: 16 },
        },
        zIndex: -1,
      })
      graph.trigger('edge:connected', { edge })
    },
    { sourceLabel, sourcePort, targetLabel, targetPort }
  )
}

async function waitForXmlMonaco(page: Page): Promise<void> {
  await expect(page.locator('.xml-code-editor-panel')).toBeVisible({ timeout: TIMEOUT })
  await expect(page.locator('.xml-code-editor-panel .monaco-editor')).toBeVisible({
    timeout: 60_000,
  })
  await expect(page.locator('.xml-code-editor-panel .ant-spin')).toHaveCount(0, {
    timeout: TIMEOUT,
  })
}

async function setXmlEditorContent(page: Page, xml: string): Promise<void> {
  await waitForXmlMonaco(page)
  await page.locator('.xml-code-editor-panel .monaco-editor').first().click()

  // Prefer the Monaco model API; fall back to the accessible textarea so React onChange fires.
  const applied = await page.evaluate((content) => {
    const monaco = (
      window as unknown as {
        monaco?: {
          editor: {
            getModels: () => Array<{ setValue: (value: string) => void }>
            getEditors: () => Array<{
              getModel: () => { setValue: (value: string) => void } | null
              setValue?: (value: string) => void
            }>
          }
        }
      }
    ).monaco
    if (monaco?.editor) {
      const editors = monaco.editor.getEditors?.() ?? []
      for (const editor of editors) {
        if (typeof editor.setValue === 'function') {
          editor.setValue(content)
          return 'editor'
        }
        const model = editor.getModel?.()
        if (model) {
          model.setValue(content)
          return 'model'
        }
      }
      const models = monaco.editor.getModels?.() ?? []
      if (models[0]) {
        models[0].setValue(content)
        return 'models'
      }
    }
    return null
  }, xml)

  if (!applied) {
    const textarea = page.locator('.xml-code-editor-panel textarea').first()
    await expect(textarea).toBeAttached({ timeout: TIMEOUT })
    await textarea.fill(xml)
  }

  await expect(page.getByRole('button', { name: /应用到画布|Apply to canvas/i })).toBeEnabled({
    timeout: TIMEOUT,
  })
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

async function seedBaselineAndCandidate(
  request: APIRequestContext,
  code: string,
  flowName: string
): Promise<{ v1: PublishedVersion; v2: PublishedVersion }> {
  const draftV1 = await postJson<ProcessDraft>(request, '/api/processes', {
    code,
    name: flowName,
    type: 'TBBPM',
    xml: markerProcessXml(code, 'v1'),
    tags: ['real-vite'],
  })
  const v1 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Baseline', expectedRevision: draftV1.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: v1.version,
      alias: 'production',
      expectedRouteRevision: 0,
      strategy: 'all_at_once',
      notes: 'Real vite baseline',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  const draftV2 = await putJson<ProcessDraft>(request, `/api/processes/${code}`, {
    name: flowName,
    xml: markerProcessXml(code, 'v2'),
    tags: ['real-vite'],
    expectedRevision: draftV1.revision,
  })
  const v2 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Candidate', expectedRevision: draftV2.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  return { v1, v2 }
}

async function seedDeployedMarkerProcess(
  request: APIRequestContext,
  code: string,
  flowName: string,
  marker = 'seed'
): Promise<{ version: string }> {
  const draft = await postJson<ProcessDraft>(request, '/api/processes', {
    code,
    name: flowName,
    type: 'TBBPM',
    xml: markerProcessXml(code, marker),
    tags: ['real-vite'],
  })
  const published = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Seed', expectedRevision: draft.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: published.version,
      alias: 'production',
      expectedRouteRevision: 0,
      strategy: 'all_at_once',
      notes: 'Seed for observability',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  return { version: published.version }
}

async function waitForAsyncStatus(
  request: APIRequestContext,
  invocationId: string,
  status: AsyncInvocation['status']
): Promise<AsyncInvocation> {
  await expect
    .poll(async () => getJson<AsyncInvocation>(request, `/api/async-invocations/${invocationId}`), {
      intervals: [50, 100, 250, 500, 1000],
      timeout: 20_000,
    })
    .toMatchObject({ status })
  return getJson<AsyncInvocation>(request, `/api/async-invocations/${invocationId}`)
}

/**
 * Browser tour against Vite real mode → Workbench Server :8080 (dev, auth DISABLED).
 */
test.describe('Real-mode Operate UI tour', () => {
  test('settings shows real mode; operate pages load from server APIs', async ({ page }) => {
    const errors = trackErrors(page)
    const apiHits: string[] = []
    page.on('response', (response) => {
      const url = response.url()
      if (url.includes('/api/')) {
        apiHits.push(`${response.status()} ${new URL(url).pathname}`)
      }
    })

    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: /设置|Settings/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText('real', { exact: true })).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({ path: test.info().outputPath('110-real-settings.png'), fullPage: true })

    await page.goto('/operate')
    await expect(page.getByRole('banner', { name: /主导航|Main/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.screenshot({
      path: test.info().outputPath('111-real-operate-home.png'),
      fullPage: true,
    })

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({ path: test.info().outputPath('112-real-flows.png'), fullPage: true })

    await page.goto('/operate/deployments')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({
      path: test.info().outputPath('113-real-deployments.png'),
      fullPage: true,
    })

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/监控|运维|Operations|控制/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.screenshot({
      path: test.info().outputPath('114-real-monitoring.png'),
      fullPage: true,
    })

    await page.goto('/operate/logs')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await page.screenshot({ path: test.info().outputPath('115-real-logs.png'), fullPage: true })

    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: /示例库|Examples/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.screenshot({ path: test.info().outputPath('116-real-examples.png'), fullPage: true })

    expect(apiHits.some((hit) => hit.startsWith('2'))).toBeTruthy()
    await shot(page, '117-real-tour-done')
    await assertNoPageErrors(errors)
  })

  test('deploy wizard canary → evaluate health → abort via Vite proxy', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.abort.${crypto.randomUUID()}`
    const { v1, v2 } = await seedBaselineAndCandidate(request, code, 'Real Vite Canary Abort')

    const versionsLoaded = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === `/api/processes/${code}/versions`
    )
    await page.goto(`/operate/deploy-wizard?processCode=${encodeURIComponent(code)}`)
    expect((await versionsLoaded).ok()).toBeTruthy()
    await expect(page.getByRole('heading', { level: 1, name: /^部署$|Deploy/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '120-real-wizard')

    const versionSelect = page.getByRole('combobox', { name: /版本|Version/i })
    await versionSelect.fill(v2.version)
    await chooseOpenSelectOption(page, v2.version)
    await page.getByRole('button', { name: /下一步|Next/i }).click()

    await page.getByLabel(/别名|Alias/i).click()
    await chooseOpenSelectOption(page, /生产别名|PRODUCTION/i)
    await page.getByLabel(/策略|Strategy/i).click()
    await chooseOpenSelectOption(page, /灰度发布|Canary/i)
    await expect(page.getByText(/灰度流量权重|Canary traffic weight/i)).toBeVisible({
      timeout: 5000,
    })
    await page.getByRole('spinbutton', { name: /灰度流量权重|Canary traffic weight/i }).fill('5000')
    await shot(page, '121-real-wizard-canary')
    await page.getByRole('button', { name: /下一步|Next/i }).click()

    const createCanary = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/deployments'
    )
    await page
      .locator('main')
      .getByRole('button', { name: /^(rocket\s*)?(立即部署|部署|Deploy now|Deploy)$/i })
      .click()
    const canaryBody = await createCanary
    expect(canaryBody.ok(), await canaryBody.text()).toBeTruthy()
    const canary = (await canaryBody.json()) as DeploymentResult
    expect(canary).toEqual(
      expect.objectContaining({
        baselineVersion: v1.version,
        status: 'in_progress',
        version: v2.version,
      })
    )
    await expect(page.getByText(/部署成功|Deployed/i)).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '122-real-canary-success')

    await page.getByRole('button', { name: /查看部署/i }).click()
    await expect(page).toHaveURL(new RegExp(`/operate/deployments/${canary.id}$`))
    await expect(page.getByRole('heading', { name: /灰度发布|Canary deployment/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '123-real-canary-detail')

    const healthResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/canary/evaluate`
    )
    await page.getByRole('button', { name: /健康评估|Evaluate Health/i }).click()
    expect((await healthResponse).ok()).toBeTruthy()
    await expect(
      page
        .getByText(
          /灰度版本健康|灰度样本不足|灰度版本不健康|More canary samples|unhealthy|healthy/i
        )
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '124-real-canary-health')

    await page.getByRole('button', { name: /中止灰度|Abort/i }).click()
    const abortResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/abort`
    )
    await confirmModalOk(page, /中止灰度|Abort|确认|确\s*认/i)
    const abortedBody = await abortResponse
    expect(abortedBody.ok(), await abortedBody.text()).toBeTruthy()
    const aborted = (await abortedBody.json()) as DeploymentResult
    expect(aborted.status).toBe('aborted')
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /灰度已中止|Aborted|中止/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '125-real-canary-aborted')
    await assertNoPageErrors(errors)
  })

  test('canary traffic → healthy evaluate → promote → rollback via Vite proxy', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.promote.${crypto.randomUUID()}`
    const { v1, v2 } = await seedBaselineAndCandidate(request, code, 'Real Vite Canary Promote')

    const canary = await postJson<DeploymentResult>(
      request,
      '/api/deployments',
      {
        processCode: code,
        version: v2.version,
        alias: 'production',
        expectedRouteRevision: 1,
        strategy: 'canary',
        canaryWeightBps: 5_000,
        notes: 'Real vite canary for promote',
      },
      { 'Idempotency-Key': crypto.randomUUID() }
    )
    expect(canary.status).toBe('in_progress')
    expect(canary.baselineVersion).toBe(v1.version)

    const candidateKey = routingKeyFor(code, v2.version, true)
    const stableKey = routingKeyFor(code, v2.version, false)
    // Default minCanarySamples is 20 — seed candidate cohort + a few stable hits.
    for (let offset = 0; offset < 20; offset += 5) {
      const batch = Array.from({ length: 5 }, (_, index) =>
        executeAlias(request, code, `promote-candidate-${offset + index}`, candidateKey)
      )
      const executions = await Promise.all(batch)
      expect(
        executions.every((execution) => execution.routing.effectiveVersion === v2.version)
      ).toBe(true)
    }
    await executeAlias(request, code, 'promote-stable-0', stableKey)

    await page.goto(`/operate/deployments/${canary.id}`)
    await expect(page.getByRole('heading', { name: /灰度发布|Canary deployment/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '126-real-promote-detail')

    const healthResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/canary/evaluate`
    )
    await page.getByRole('button', { name: /健康评估|Evaluate Health/i }).click()
    expect((await healthResponse).ok()).toBeTruthy()
    await expect(page.getByText('灰度版本健康')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '127-real-promote-healthy')

    await page.getByRole('button', { name: /全量发布|Promote/i }).click()
    const promoteDialog = page.getByRole('dialog', {
      name: /确认全量发布|Confirm full rollout/i,
    })
    await expect(promoteDialog).toBeVisible({ timeout: TIMEOUT })
    const promoteResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/promote`
    )
    await promoteDialog.getByRole('button', { name: /全量发布|Promote/i }).click()
    const promotedBody = await promoteResponse
    expect(promotedBody.ok(), await promotedBody.text()).toBeTruthy()
    const promoted = (await promotedBody.json()) as DeploymentResult
    expect(promoted.status).toBe('completed')
    await expect(page.getByText(/已全量发布|Promoted/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '128-real-promoted')

    const afterPromote = await executeAlias(request, code, 'promote-after')
    expect(afterPromote.routing.effectiveVersion).toBe(v2.version)

    await page.getByRole('button', { name: /^回滚$|Rollback/i }).click()
    const rollbackDialog = page.getByRole('dialog', { name: /确认回滚|Confirm rollback/i })
    await expect(rollbackDialog).toBeVisible({ timeout: TIMEOUT })
    const rollbackResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/rollback`
    )
    await rollbackDialog.getByRole('button', { name: /回\s*滚|Rollback/i }).click()
    const rollbackBody = await rollbackResponse
    expect(rollbackBody.ok(), await rollbackBody.text()).toBeTruthy()
    const rollback = (await rollbackBody.json()) as DeploymentResult
    expect(rollback).toEqual(
      expect.objectContaining({
        baselineVersion: v2.version,
        operation: 'rollback',
        status: 'completed',
        version: v1.version,
      })
    )
    await expect(page).toHaveURL(new RegExp(`/operate/deployments/${rollback.id}$`))
    await shot(page, '129-real-rolled-back')

    const afterRollback = await executeAlias(request, code, 'rollback-after')
    expect(afterRollback.routing.effectiveVersion).toBe(v1.version)
    await assertNoPageErrors(errors)
  })

  test('async dead-letter → UI requeue via Vite proxy', async ({ page, request }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.async.${crypto.randomUUID()}`
    const invocationId = `real-async-${crypto.randomUUID()}`

    const draft = await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Async DLQ',
      type: 'TBBPM',
      xml: failingProcessXml(code),
      tags: ['real-vite'],
    })
    const published = await postJson<PublishedVersion>(
      request,
      `/api/processes/${code}/publish`,
      { changelog: 'Failing async baseline', expectedRevision: draft.revision },
      { 'Idempotency-Key': crypto.randomUUID() }
    )
    await postJson<DeploymentResult>(
      request,
      '/api/deployments',
      {
        processCode: code,
        version: published.version,
        alias: 'production',
        expectedRouteRevision: 0,
        strategy: 'all_at_once',
        notes: 'Async DLQ seed',
      },
      { 'Idempotency-Key': crypto.randomUUID() }
    )

    await postJson<AsyncInvocation>(request, `/api/processes/${code}/async-invocations`, {
      invocationId,
      params: { fail: true },
      routing: { alias: 'production' },
      maxAttempts: 2,
      retryDelayMs: 10,
    })
    await waitForAsyncStatus(request, invocationId, 'dead_letter')

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/异步|Async/i).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '130-real-async-monitoring')

    const statusFilter = page
      .getByLabel(/Invocation status|调用状态|状态/i)
      .or(page.locator('main .ant-select').filter({ hasText: /状态|Status|dead|死信/i }))
      .first()
    if (await statusFilter.count()) {
      await statusFilter.click()
    } else {
      await page.locator('main .ant-select').last().click()
    }
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /死信|dead_letter|Dead letter/i })
      .first()
      .click()

    await expect(page.getByText(invocationId)).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '131-real-async-dead-letter-row')

    await page
      .locator('tr')
      .filter({ hasText: invocationId })
      .getByRole('button', { name: /查看|View/i })
      .click()
    await expect(page.getByRole('dialog').or(page.locator('.ant-drawer')).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '132-real-async-dead-letter-detail')

    const requeueResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/async-invocations/${invocationId}/requeue`
    )
    await page.getByRole('button', { name: '重新入队', exact: true }).click()
    const confirmDialog = page.getByRole('dialog', {
      name: /重新入队这条死信|Requeue this dead-letter/i,
    })
    await expect(confirmDialog).toBeVisible({ timeout: TIMEOUT })
    const footerOk = confirmDialog.locator(
      '.ant-modal-footer button.ant-btn-primary, .ant-modal-confirm-btns button.ant-btn-primary'
    )
    if (await footerOk.count()) {
      await footerOk.click()
    } else {
      await confirmDialog.getByRole('button', { name: /确\s*认|Confirm|OK/i }).click()
    }
    expect((await requeueResponse).ok()).toBeTruthy()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已重新入队|requeued/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '133-real-async-requeued')
    await page.keyboard.press('Escape')
    await shot(page, '134-real-async-requeue-done')
    await assertNoPageErrors(errors)
  })

  test('batch async dead-letter requeue from Monitoring via Vite proxy', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.batch.${crypto.randomUUID()}`
    const invocationIds = [
      `real-batch-a-${crypto.randomUUID()}`,
      `real-batch-b-${crypto.randomUUID()}`,
    ]

    const draft = await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Batch Async DLQ',
      type: 'TBBPM',
      xml: failingProcessXml(code),
      tags: ['real-vite'],
    })
    const published = await postJson<PublishedVersion>(
      request,
      `/api/processes/${code}/publish`,
      { changelog: 'Batch failing async baseline', expectedRevision: draft.revision },
      { 'Idempotency-Key': crypto.randomUUID() }
    )
    await postJson<DeploymentResult>(
      request,
      '/api/deployments',
      {
        processCode: code,
        version: published.version,
        alias: 'production',
        expectedRouteRevision: 0,
        strategy: 'all_at_once',
        notes: 'Batch async DLQ seed',
      },
      { 'Idempotency-Key': crypto.randomUUID() }
    )

    for (const invocationId of invocationIds) {
      await postJson<AsyncInvocation>(request, `/api/processes/${code}/async-invocations`, {
        invocationId,
        params: { fail: true },
        routing: { alias: 'production' },
        maxAttempts: 2,
        retryDelayMs: 10,
      })
      await waitForAsyncStatus(request, invocationId, 'dead_letter')
    }

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/异步|Async/i).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '135-real-batch-monitoring')

    const batchRequeue = page.getByRole('button', {
      name: /重新入队异步调用死信|Requeue async dead/i,
    })
    await expect(batchRequeue.first()).toBeVisible({ timeout: TIMEOUT })
    const batchResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/async-invocations/dead-letters/requeue'
    )
    await batchRequeue.first().click()
    const batchBody = await batchResponse
    expect(batchBody.ok(), await batchBody.text()).toBeTruthy()
    const batchJson = (await batchBody.json()) as { requeued?: number }
    expect((batchJson.requeued ?? 0) >= 2).toBeTruthy()
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /重新入队|requeued|死信/i })
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '136-real-batch-async-requeued')
    // Failing processes may dead-letter again quickly; API requeued count is the contract proof.
    await shot(page, '137-real-batch-async-done')
    await assertNoPageErrors(errors)
  })

  test('canary weight slider update via Vite proxy', async ({ page, request }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.pct.${crypto.randomUUID()}`
    const { v1, v2 } = await seedBaselineAndCandidate(request, code, 'Real Vite Canary Percent')

    const canary = await postJson<DeploymentResult>(
      request,
      '/api/deployments',
      {
        processCode: code,
        version: v2.version,
        alias: 'production',
        expectedRouteRevision: 1,
        strategy: 'canary',
        canaryWeightBps: 5_000,
        notes: 'Real vite canary percent update',
      },
      { 'Idempotency-Key': crypto.randomUUID() }
    )
    expect(canary.status).toBe('in_progress')
    expect(canary.baselineVersion).toBe(v1.version)

    await page.goto(`/operate/deployments/${canary.id}`)
    await expect(page.getByRole('heading', { name: /灰度发布|Canary deployment/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText('50%').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '140-real-canary-pct-before')

    const slider = page.locator('.ant-slider-handle').first()
    await slider.focus()
    for (let i = 0; i < 5; i += 1) {
      await page.keyboard.press('ArrowRight')
    }

    const updateResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'PUT' &&
        new URL(response.url()).pathname === `/api/deployments/${canary.id}/canary`
    )
    await page.getByRole('button', { name: /调整权重|Update weight/i }).click()
    const updatedBody = await updateResponse
    expect(updatedBody.ok(), await updatedBody.text()).toBeTruthy()
    const updated = (await updatedBody.json()) as DeploymentResult & { canaryWeightBps?: number }
    if (updated.canaryWeightBps == null)
      throw new Error('Updated canary response omitted canaryWeightBps')
    expect(updated.canaryWeightBps).toBeGreaterThan(5_000)
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /灰度权重已更新|灰度比例已更新|updated/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(
      page.getByText(`${updated.canaryWeightBps / 100}%`, { exact: false }).first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '141-real-canary-pct-updated')
    await assertNoPageErrors(errors)
  })

  test('flow publish and duplicate via Vite proxy', async ({ page, request }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.flow.${crypto.randomUUID()}`
    const flowName = 'Real Vite Process Ops'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: flowName,
      type: 'TBBPM',
      xml: markerProcessXml(code, 'draft'),
      tags: ['real-vite'],
    })

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })

    const search = page.getByPlaceholder(/搜索|Search|流程|code|名称/i).first()
    const filteredProcesses = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        response.request().method() === 'GET' &&
        url.pathname === '/api/processes' &&
        url.searchParams.get('keyword') === code &&
        response.ok()
      )
    })
    await search.fill(code)
    await filteredProcesses
    await expect(page.locator('tbody tr').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '142-real-flow-listed')

    const row = page.locator('tbody tr').filter({ hasText: code }).first()
    const more = row.getByRole('button', { name: /更多操作|更多|More actions/i })
    await expect(more).toBeVisible({ timeout: TIMEOUT })

    const publishResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/processes/${code}/publish`
    )
    const publishedProcesses = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        response.request().method() === 'GET' &&
        url.pathname === '/api/processes' &&
        url.searchParams.get('keyword') === code &&
        response.ok()
      )
    })
    await more.click()
    await page.getByRole('menuitem', { name: /发布|Publish/i }).click()
    expect((await publishResponse).ok()).toBeTruthy()
    await publishedProcesses
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /流程已发布|Process published|发布成功/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '143-real-flow-published')

    const duplicateResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === `/api/processes/${code}/duplicate`
    )
    await more.click()
    await page.getByRole('menuitem', { name: /复制|Duplicate/i }).click()
    const dupBody = await duplicateResponse
    expect(dupBody.ok(), await dupBody.text()).toBeTruthy()
    const duplicated = (await dupBody.json()) as { code?: string }
    expect(duplicated.code).toBeTruthy()
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /复制成功|Duplicated Successfully|复制/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '144-real-flow-duplicated')

    if (duplicated.code) {
      await search.fill(duplicated.code)
      await expect(
        page.locator('tbody tr').filter({ hasText: duplicated.code }).first()
      ).toBeVisible({ timeout: TIMEOUT })
    }
    await shot(page, '145-real-flow-ops-done')
    await assertNoPageErrors(errors)
  })

  test('deployment-control dead-letter batch requeue via Vite proxy', async ({ page }) => {
    test.setTimeout(90_000)
    const errors = trackErrors(page)

    await page.goto('/operate/monitoring')
    await expect(page.getByText(/运维状态|Operations/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '150-real-deploy-dlq-monitoring')

    const requeueDeploy = page.getByRole('button', {
      name: /重新入队部署死信任务|Requeue deployment dead/i,
    })
    await expect(requeueDeploy.first()).toBeVisible({ timeout: TIMEOUT })
    const requeueResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/deployment-control/dead-letters/requeue'
    )
    await requeueDeploy.first().click()
    const body = await requeueResponse
    expect(body.ok(), await body.text()).toBeTruthy()
    const json = (await body.json()) as { requeued?: number }
    expect(typeof json.requeued).toBe('number')
    await expect(
      page.getByText(/部署死信任务已重新入队|Dead-lettered deployment tasks requeued/i)
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '151-real-deploy-dlq-requeued')
    await assertNoPageErrors(errors)
  })

  test('flow delete confirm commits via Vite proxy', async ({ page, request }) => {
    test.setTimeout(90_000)
    const errors = trackErrors(page)
    const code = `workbench.real.delete.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Delete Me',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'delete'),
      tags: ['real-vite'],
    })

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    const search = page.getByPlaceholder(/搜索|Search|流程|code|名称/i).first()
    const filteredProcesses = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        response.request().method() === 'GET' &&
        url.pathname === '/api/processes' &&
        url.searchParams.get('keyword') === code &&
        response.ok()
      )
    })
    await search.fill(code)
    await filteredProcesses
    const row = page.locator('tbody tr').filter({ hasText: code }).first()
    await expect(row).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '152-real-flow-delete-listed')

    await row.getByRole('button', { name: /更多操作|更多|More actions/i }).click()
    await page.getByRole('menuitem', { name: /删除|Delete/i }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: TIMEOUT })
    // Ant Design mirrors the title in a hidden ant-modal-title; assert the visible confirm title.
    await expect(dialog.locator('.ant-modal-confirm-title')).toContainText(
      /确定删除该流程|确认删除该流程|Are you sure/i
    )
    await shot(page, '153-real-flow-delete-confirm')

    const deleteResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'DELETE' &&
        new URL(response.url()).pathname === `/api/processes/${code}`
    )
    await dialog
      .locator(
        '.ant-modal-confirm-btns button.ant-btn-dangerous, .ant-modal-confirm-btns button.ant-btn-primary'
      )
      .last()
      .click()
    expect((await deleteResponse).ok()).toBeTruthy()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已删除|删除成功|Deleted/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('tbody tr').filter({ hasText: code })).toHaveCount(0, {
      timeout: TIMEOUT,
    })
    await shot(page, '154-real-flow-deleted')
    await assertNoPageErrors(errors)
  })

  test('create flow from Operate opens designer via Vite proxy', async ({ page }) => {
    test.setTimeout(90_000)
    const errors = trackErrors(page)

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '155-real-create-flows')

    const createResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/processes' &&
        response.ok()
    )
    await page.getByRole('button', { name: /创建流程|Create/i }).click()
    await expect(page.getByRole('menuitem', { name: /TBBPM/i })).toBeVisible({ timeout: 5000 })
    await page.getByRole('menuitem', { name: /新建 TBBPM|New TBBPM/i }).click()
    const createdBody = await createResponse
    const created = (await createdBody.json()) as { code?: string }
    expect(created.code).toBeTruthy()

    await page.waitForURL(new RegExp(`/build/designer.*processCode=${created.code}`), {
      timeout: TIMEOUT,
    })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: created.code! }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '156-real-create-designer')
    await assertNoPageErrors(errors)
  })

  test('logs filter, detail, and export via Vite proxy', async ({ page, request }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.logs.${crypto.randomUUID()}`
    await seedDeployedMarkerProcess(request, code, 'Real Vite Logs')
    for (let index = 0; index < 3; index += 1) {
      const result = await executeAlias(request, code, `logs-seed-${index}`)
      expect(result.success).toBe(true)
    }

    await page.goto('/operate/logs')
    await expect(page.locator('.ant-table').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '160-real-logs-home')

    const keyword = page.getByPlaceholder(/搜索日志|搜索关键词|Search logs/i)
    const logsResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/execution-logs' &&
        new URL(response.url()).searchParams.get('keyword') === code
    )
    await keyword.fill(code)
    expect((await logsResponse).ok()).toBeTruthy()
    await expect(page.locator('tbody tr').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '161-real-logs-filtered')

    const statusFilter = page.getByRole('combobox', { name: /状态|Status|选择状态/i }).or(
      page
        .locator('.ant-select')
        .filter({ hasText: /状态|选择状态|Success|成功/i })
        .first()
    )
    // Prefer the dedicated status select by placeholder text when present.
    const statusPlaceholder = page.locator('.ant-select-selection-placeholder').filter({
      hasText: /状态|Status/i,
    })
    if (await statusPlaceholder.count()) {
      await statusPlaceholder.first().click()
    } else if (await statusFilter.count()) {
      await statusFilter.first().click()
    } else {
      await page.locator('main .ant-select').nth(0).click()
    }
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /^成功$|^Success$/i })
      .first()
      .click()
    await expect(page.locator('tbody tr').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    const detailBtn = page
      .locator('tbody tr')
      .filter({ hasText: code })
      .first()
      .getByRole('button', { name: /详情|View|Detail/i })
    await expect(detailBtn).toBeVisible({ timeout: TIMEOUT })
    await detailBtn.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toBeVisible({ timeout: TIMEOUT })
    await expect(dialog.getByText(code).first()).toBeVisible()
    await expect(dialog.getByText(/耗时|Duration/i).first()).toBeVisible()
    await shot(page, '162-real-logs-detail')
    await page.keyboard.press('Escape')

    const exportBtn = page.getByRole('button', { name: /导出|Export/i })
    await expect(exportBtn.first()).toBeVisible({ timeout: TIMEOUT })
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: TIMEOUT }),
      exportBtn.first().click(),
    ])
    expect(download.suggestedFilename()).toMatch(/logs_.*\.csv/i)
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /导出成功|Exported Successfully/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '163-real-logs-export')
    await assertNoPageErrors(errors)
  })

  test('monitoring metrics and trends refresh with seeded executions', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.mon.${crypto.randomUUID()}`
    await seedDeployedMarkerProcess(request, code, 'Real Vite Monitoring')
    for (let index = 0; index < 5; index += 1) {
      const result = await executeAlias(request, code, `mon-seed-${index}`)
      expect(result.success).toBe(true)
    }

    const metricsHit = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/monitoring/metrics' &&
        response.ok()
    )
    const trendsHit = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/monitoring/trends' &&
        response.ok()
    )
    await page.goto('/operate/monitoring')
    await expect(page.getByRole('heading', { name: /监控|Monitoring/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    expect((await metricsHit).ok()).toBeTruthy()
    expect((await trendsHit).ok()).toBeTruthy()
    await expect(page.getByText(/总执行次数|Total Executions/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '164-real-monitoring-seeded')

    const rangeSelect = page.locator('main .ant-select').first()
    await rangeSelect.click()
    const trends1h = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return (
        response.request().method() === 'GET' &&
        url.pathname === '/api/monitoring/trends' &&
        url.searchParams.get('timeRange') === '1h' &&
        response.ok()
      )
    })
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /1\s*小时|1h/i })
      .first()
      .click()
    const trendsBody = await trends1h
    const trends = (await trendsBody.json()) as unknown[]
    expect(Array.isArray(trends)).toBeTruthy()
    expect(trends.length).toBeGreaterThan(0)
    await shot(page, '165-real-monitoring-1h')
    await assertNoPageErrors(errors)
  })

  test('Learn catalog loads from server examples API (no built-ins)', async ({ page }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)

    const catalogResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/examples' &&
        response.ok()
    )
    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: /示例库|Examples/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    const catalogBody = await catalogResponse
    const catalog = (await catalogBody.json()) as Array<{ id?: string; modelType?: string }>
    expect(Array.isArray(catalog)).toBeTruthy()
    expect(catalog.length).toBeGreaterThan(0)
    // Built-ins disabled: cards come from server, not mock-only local list.
    await expect(page.locator('main article').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '170-real-learn-catalog')

    const search = page.getByPlaceholder(/搜索|Search/i).first()
    if (await search.count()) {
      const needle = catalog[0]?.id?.slice(0, 8) ?? 'bpmn'
      await search.fill(needle)
      await page.waitForTimeout(400)
      await shot(page, '171-real-learn-search')
      await search.fill('')
      await page.waitForTimeout(300)
    }

    const firstCard = page.locator('main article').first()
    const detailResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        /\/api\/examples\/[^/]+$/.test(new URL(response.url()).pathname) &&
        response.ok()
    )
    await firstCard.click()
    await page.waitForURL(/\/learn\/examples\/.+/, { timeout: TIMEOUT })
    expect((await detailResponse).ok()).toBeTruthy()
    await page.getByRole('tab', { name: /代码|Code/i }).click()
    await expect(page.locator('.ant-tabs-tabpane-active pre').first()).toBeAttached({
      timeout: TIMEOUT,
    })
    await shot(page, '172-real-learn-detail-code')

    await page.getByRole('tab', { name: /执行|Execute/i }).click()
    const previewResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/executions/preview'
    )
    await page
      .locator('.ant-tabs-tabpane-active')
      .getByRole('button', { name: /执行|Execute/i })
      .click()
    const preview = await previewResponse
    expect(preview.ok(), await preview.text()).toBeTruthy()
    expect((await preview.json()).success).toBe(true)
    await expect(page.locator('[class*="execResult"]').first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '173-real-learn-execute')

    await page.getByRole('button', { name: /在设计器中打开|打开设计器|Open in designer/i }).click()
    await page.waitForURL(/\/build\/designer/, { timeout: TIMEOUT })
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await shot(page, '174-real-learn-designer')
    await assertNoPageErrors(errors)
  })

  test('Settings shows real operate mode and language switch persists', async ({ page }) => {
    test.setTimeout(60_000)
    const errors = trackErrors(page)

    await page.goto('/settings')
    await expect(page.getByRole('heading', { name: /设置|Settings/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText(/偏好设置|Preferences/i).first()).toBeVisible()
    await expect(page.getByText(/构建信息|Build Information/i).first()).toBeVisible()
    await expect(
      page
        .locator('code')
        .filter({ hasText: /^real$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText(/运维模式|Operate mode/i).first()).toBeVisible()
    await expect(page.getByText(/构建模式|Build mode/i).first()).toBeVisible()
    await expect(page.getByText(/应用版本|App version|Version/i).first()).toBeVisible()
    await shot(page, '175-real-settings-build')

    const language = page.getByRole('combobox').or(page.locator('.ant-select').first())
    await language.first().click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /^English$/ })
      .first()
      .click()
    await expect(page.getByRole('heading', { name: /Settings/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page
        .locator('code')
        .filter({ hasText: /^real$/ })
        .first()
    ).toBeVisible()
    await shot(page, '176-real-settings-en')

    await language.first().click()
    await page
      .locator('.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option')
      .filter({ hasText: /^中文$/ })
      .first()
      .click()
    await expect(page.getByRole('heading', { name: /设置/i }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    const github = page.locator('main').getByRole('link', { name: /GitHub 仓库|GitHub/i })
    await expect(github).toBeVisible()
    await expect(github).toHaveAttribute('href', /github\.com\/alibaba\/compileflow/)
    await shot(page, '177-real-settings-done')
    await assertNoPageErrors(errors)
  })

  test('Cmd+K finds server catalog example and quick-link navigates', async ({ page, request }) => {
    test.setTimeout(90_000)
    const errors = trackErrors(page)
    const catalog = await getJson<Array<{ id: string; name: string }>>(request, '/api/examples')
    expect(catalog.length).toBeGreaterThan(0)
    const target = catalog[0]!
    const searchNeedle = target.name.slice(0, Math.min(8, target.name.length))

    await page.goto('/learn')
    await expect(page.getByRole('banner', { name: /主导航|Main/i })).toBeVisible({
      timeout: TIMEOUT,
    })

    // Prefer keyboard — AppBar click can race with layout paint in full suite runs.
    const examplesLoaded = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        new URL(response.url()).pathname === '/api/examples' &&
        response.ok()
    )
    await page.keyboard.press('Control+k')
    const searchInput = page.getByPlaceholder(/搜索示例|Search examples/i)
    await expect(searchInput).toBeVisible({ timeout: TIMEOUT })
    await examplesLoaded
    await shot(page, '180-real-cmdk-open')

    await searchInput.fill(searchNeedle)
    const match = page
      .getByRole('dialog')
      .getByRole('button')
      .filter({ hasText: /TBBPM|BPMN/i })
    await expect(match.first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '181-real-cmdk-match')
    await match.first().click()
    await page.waitForURL(new RegExp(`/learn/examples/${target.id}`), { timeout: TIMEOUT })
    await shot(page, '182-real-cmdk-example')

    await page.keyboard.press('Control+k')
    await expect(searchInput).toBeVisible({ timeout: TIMEOUT })
    await searchInput.fill('')
    await page
      .getByRole('dialog')
      .getByText(/流程管理|Processes/i)
      .first()
      .click()
    await page.waitForURL(/\/operate\/processes/, { timeout: TIMEOUT })
    await shot(page, '183-real-cmdk-quicklink')
    await assertNoPageErrors(errors)
  })

  test('browser proxy never sends X-API-Key; theme toggle works in real mode', async ({ page }) => {
    test.setTimeout(60_000)
    const errors = trackErrors(page)
    const apiKeyLeaks: string[] = []
    const apiStatuses: string[] = []
    page.on('request', (req) => {
      const path = new URL(req.url()).pathname
      if (path.startsWith('/api/') && req.headers()['x-api-key']) {
        apiKeyLeaks.push(path)
      }
    })
    page.on('response', (response) => {
      const path = new URL(response.url()).pathname
      if (path.startsWith('/api/')) {
        apiStatuses.push(`${response.status()} ${path}`)
      }
    })

    await page.goto('/operate/processes')
    await expect(page.locator('.ant-table, main').first()).toBeVisible({ timeout: TIMEOUT })
    await page.goto('/learn/examples')
    await expect(page.getByRole('heading', { name: /示例库|Examples/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.goto('/settings')
    await expect(
      page
        .locator('code')
        .filter({ hasText: /^real$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })

    expect(apiKeyLeaks).toEqual([])
    expect(apiStatuses.some((hit) => hit.startsWith('2'))).toBeTruthy()
    // Dev profile uses authentication.mode=DISABLED — unauthenticated proxy is expected green.
    expect(apiStatuses.some((hit) => hit.startsWith('401'))).toBeFalsy()
    await shot(page, '184-real-auth-hygiene')

    const themeBtn = page.getByRole('button', {
      name: /切换到深色模式|切换到浅色模式|切换到亮色模式|Switch to Dark|Switch to Light/i,
    })
    await expect(themeBtn).toBeVisible({ timeout: TIMEOUT })
    const before = await page.locator('html').getAttribute('data-theme')
    await themeBtn.click()
    await expect.poll(async () => page.locator('html').getAttribute('data-theme')).not.toBe(before)
    await shot(page, '185-real-theme-toggled')
    await themeBtn.click()
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: validate, save to server, export XML', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.designer.${crypto.randomUUID()}`
    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Designer Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'designer'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '200-real-designer-open')

    // Validate panel.
    const validateBtn = page.getByRole('button', { name: /验证流程|Validate/i }).or(
      page
        .locator('button')
        .filter({ has: page.locator('.anticon-check-square') })
        .first()
    )
    if (await page.getByRole('button', { name: /验证流程|Validate/i }).count()) {
      await page.getByRole('button', { name: /验证流程|Validate/i }).click()
    } else {
      await page.locator('.anticon-check-square').first().click()
    }
    await expect(page.getByText(/流程验证|Validation|验证/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '201-real-designer-validate')
    void validateBtn

    // XML tab is present; content proof comes from export below (Monaco may load slowly).
    await page.getByTestId('designer-tab-xml').click()
    await expect(page.locator('.xml-code-editor-panel')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '202-real-designer-xml')
    await page.getByTestId('designer-tab-visual').click()
    await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })

    // Save persists to Operate (PUT /api/processes/:code).
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
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已保存到流程管理|saved/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '203-real-designer-saved')

    // Export XML download contains flow code / TBBPM markers.
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: TIMEOUT }),
      (async () => {
        await page.locator('.header-more-btn').click()
        await page.getByText(/导出 XML|Export XML/i).click()
      })(),
    ])
    const exportPath = test
      .info()
      .outputPath(download.suggestedFilename() || `real-export-${Date.now()}.xml`)
    await download.saveAs(exportPath)
    const xml = fs.readFileSync(exportPath, 'utf8')
    expect(xml).toMatch(/<bpm[\s>]/)
    expect(xml).toContain(code)
    await shot(page, '204-real-designer-exported')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: palette add autoTask + Action edit persists', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.palette.${crypto.randomUUID()}`
    const taskName = 'RealPaletteTask'
    const className = 'com.alibaba.compileflow.demo.RealPaletteAction'
    const method = 'run'
    const springBean = 'realPaletteAction'
    const springClass = 'com.alibaba.compileflow.demo.RealPaletteSpringAction'
    const springMethod = 'apply'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Palette Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'palette'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    // Palette Enter creates a persisted autoTask on the Operate draft canvas.
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    await expect(autoTaskItem).toBeVisible({ timeout: TIMEOUT })
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()
    await autoTaskItem.focus()
    await page.keyboard.press('Enter')
    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    const newTask = page.locator('.x6-node').filter({ hasText: '自动任务' }).first()
    await expect(newTask).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '210-real-palette-added')

    // Palette creation selects the new node and opens its properties for immediate editing.
    await expect(
      page
        .locator('.ant-card')
        .filter({ hasText: /属性|Properties/i })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.getByRole('tab', { name: /通用|General/i }).click()
    const nameInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /节点名称|Node name/i })
      .locator('input')
    await expect(nameInput.first()).toBeVisible({ timeout: TIMEOUT })
    await nameInput.first().fill(taskName)
    await expect(page.locator('.x6-node').filter({ hasText: taskName }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '211-real-palette-renamed')

    // Java action class + method on the new autoTask.
    await page.getByRole('tab', { name: /任务|Task/i }).click()
    await expect(
      page
        .locator('.tbbpm-designer-right-sider')
        .getByText(/自动任务|Auto task/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    const classInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /类名|Class name/i })
      .locator('input')
    const methodInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /方法名|Method name/i })
      .locator('input')
    await expect(classInput.first()).toBeVisible({ timeout: TIMEOUT })
    await classInput.first().fill(className)
    await methodInput.first().fill(method)
    await shot(page, '212-real-action-java')

    // Auto Task remains application-owned when switching implementation type.
    await page.locator('.tbbpm-canvas').click({ position: { x: 12, y: 12 } })
    await page
      .locator('.x6-node')
      .filter({ hasText: taskName })
      .first()
      .click({ position: { x: 8, y: 8 } })
    const selectedNameInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /节点名称|Node name/i })
      .locator('input')
      .first()
    await expect(selectedNameInput).toHaveValue(taskName, { timeout: TIMEOUT })
    await page.getByRole('tab', { name: /任务|Task/i }).click()
    await page.getByRole('combobox', { name: /^动作类型$|^Action type$/i }).click()
    await chooseOpenSelectOption(page, /Spring Bean/)
    const beanInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /Bean 名称|Bean name/i })
      .locator('input')
    const springClassInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /类名|Class name/i })
      .locator('input')
    const springMethodInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /方法名|Method name/i })
      .locator('input')
    await expect(beanInput.first()).toBeVisible({ timeout: TIMEOUT })
    await beanInput.first().fill(springBean)
    await springClassInput.first().fill(springClass)
    await springMethodInput.first().fill(springMethod)
    await shot(page, '213-real-action-switched')

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
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已保存到流程管理|saved/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '214-real-palette-saved')

    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: TIMEOUT }),
      (async () => {
        await page.locator('.header-more-btn').click()
        await page.getByText(/导出 XML|Export XML/i).click()
      })(),
    ])
    const exportPath = test
      .info()
      .outputPath(download.suggestedFilename() || `real-palette-${Date.now()}.xml`)
    await download.saveAs(exportPath)
    const xml = fs.readFileSync(exportPath, 'utf8')
    expect(xml).toMatch(/<bpm[\s>]/)
    expect(xml).toContain(taskName)
    expect(xml).toContain(`bean="${springBean}"`)
    expect(xml).toContain(`class="${springClass}"`)
    expect(xml).toContain(`method="${springMethod}"`)
    expect(xml).toMatch(/<action type="spring-bean"[^>]*>/)
    expect(xml).toMatch(/<scriptTask[^>]*id="marker"/)

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toBeTruthy()
    expect(saved.xml).toContain(taskName)
    expect(saved.xml).toContain(`bean="${springBean}"`)
    expect(saved.xml).toContain(`class="${springClass}"`)
    await shot(page, '215-real-palette-exported')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: pointer drag from palette places autoTask', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.drag.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Drag Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'drag'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })

    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const canvas = page.locator('.tbbpm-canvas').first()
    await expect(autoTaskItem).toBeVisible({ timeout: TIMEOUT })
    await expect(canvas).toBeVisible({ timeout: TIMEOUT })

    const beforeCount = await page.locator('.x6-node').count()
    const canvasBox = await canvas.boundingBox()
    expect(canvasBox).toBeTruthy()

    // X6 Dnd listens for native mousedown on the palette item; Playwright dragTo
    // reliably delivers the pointer sequence that creates a drop node.
    await autoTaskItem.dragTo(canvas, {
      targetPosition: {
        x: Math.floor(canvasBox!.width * 0.55),
        y: Math.floor(canvasBox!.height * 0.45),
      },
    })

    await expect
      .poll(async () => page.locator('.x6-node').count(), { timeout: TIMEOUT })
      .toBeGreaterThan(beforeCount)
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '220-real-pointer-drag')

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toMatch(/<autoTask\b/)
    expect((saved.xml?.match(/<autoTask\b/g) || []).length).toBe(1)
    await shot(page, '221-real-pointer-drag-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: XML tab apply-to-canvas updates visual + server', async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000)
    const errors = trackErrors(page)
    const code = `workbench.real.xmlapply.${crypto.randomUUID()}`
    const appliedName = 'XmlAppliedMarker'
    const appliedMarker = 'xml-apply-ok'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite XML Apply Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'before-apply'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: 'Marker' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    await page.getByTestId('designer-tab-xml').click()
    await waitForXmlMonaco(page)
    await shot(page, '230-real-xml-monaco')

    const nextXml = markerProcessXml(code, appliedMarker).replace(
      'name="Marker"',
      `name="${appliedName}"`
    )
    await setXmlEditorContent(page, nextXml)
    await shot(page, '231-real-xml-dirty')

    await page.getByRole('button', { name: /应用到画布|Apply to canvas/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /XML 已应用到画布|applied to canvas/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '232-real-xml-applied')

    await page.getByTestId('designer-tab-visual').click()
    await expect(page.locator('.x6-graph')).toBeVisible({ timeout: TIMEOUT })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: new RegExp(`^${appliedName}$`) })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.locator('.x6-node').filter({ hasText: /^Marker$/ })).toHaveCount(0)
    await shot(page, '233-real-xml-visual')

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toContain(appliedName)
    expect(saved.xml).toContain(appliedMarker)
    expect(saved.xml).not.toContain('before-apply')
    await shot(page, '234-real-xml-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: shortcuts, help, variables, node search, grid; history hidden', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.tools.${crypto.randomUUID()}`
    const varName = 'realToolsExtra'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Designer Tools',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'tools'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    // Operate-bound drafts intentionally hide browser-local snapshots.
    await expect(page.getByRole('button', { name: /本地快照|Local snapshots/i })).toHaveCount(0)
    await shot(page, '240-real-tools-open')

    await page.getByRole('button', { name: /快捷键|Keyboard shortcuts/i }).click()
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: TIMEOUT })
    await expect(page.getByText(/保存|Save|撤销|Undo/i).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '241-real-shortcuts')
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)

    await page.keyboard.press('Control+/')
    await expect(page.getByRole('dialog')).toBeVisible({ timeout: TIMEOUT })
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: /帮助文档|Help/i }).click()
    await expect(page.locator('.help-documentation, .ant-drawer, .ant-modal').first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '242-real-help')
    await page.keyboard.press('Escape')

    await page.getByRole('button', { name: /变量管理|Variable/i }).click()
    const varsDialog = page.getByRole('dialog').filter({ hasText: /流程变量|Process variables/i })
    await expect(varsDialog).toBeVisible({ timeout: TIMEOUT })
    await expect(varsDialog.getByText('version_marker')).toBeVisible({ timeout: TIMEOUT })
    await varsDialog.getByRole('button', { name: /添加变量|Add variable/i }).click()

    const editDialog = page.getByRole('dialog', { name: /添加变量|Add variable/i })
    await expect(editDialog).toBeVisible({ timeout: TIMEOUT })
    await editDialog.getByRole('textbox', { name: /变量名|Variable name/i }).fill(varName)
    await editDialog
      .getByRole('textbox', { name: /数据类型（Java 类名）|Data type \(Java class\)/i })
      .fill('java.lang.Integer')
    // Ant Design often inserts spaces into CJK button labels ("保 存").
    await editDialog.getByRole('button', { name: /保\s*存|Save/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /变量已添加|Variable added|已添加/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(varsDialog.getByText(varName)).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '243-real-variables')
    await varsDialog.getByRole('button', { name: /Close|关闭/i }).click()
    await expect(page.getByRole('dialog', { name: /流程变量|Process variables/i })).toHaveCount(0)

    await page.getByRole('button', { name: /搜索节点|Search nodes/i }).click()
    const searchDialog = page.getByRole('dialog').filter({ hasText: /搜索节点|Search nodes/i })
    await expect(searchDialog).toBeVisible({ timeout: TIMEOUT })
    await searchDialog.getByPlaceholder(/节点名称|name|ID|类型/i).fill('Marker')
    await expect(searchDialog.getByText(/Marker/i).first()).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '244-real-node-search')
    await searchDialog
      .getByText(/Marker/i)
      .first()
      .click()
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: /^Marker$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })

    await page.getByRole('button', { name: /切换网格|Toggle grid/i }).click()
    await shot(page, '245-real-grid')

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
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已保存到流程管理|saved/i })
    ).toBeVisible({ timeout: TIMEOUT })

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toMatch(new RegExp(`<var[^>]*name="${varName}"`))
    expect(saved.xml).toContain('java.lang.Integer')
    await shot(page, '246-real-tools-saved')
    await assertNoPageErrors(errors)
  })

  test('workspace designer shows local snapshots when not Operate-bound', async ({ page }) => {
    test.setTimeout(60_000)
    const errors = trackErrors(page)
    await page.goto('/build/designer?modelType=tbbpm')
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    const history = page.getByRole('button', { name: /本地快照|Local snapshots/i })
    await expect(history).toBeVisible({ timeout: TIMEOUT })
    await history.click()
    await expect(page.getByRole('dialog').or(page.locator('.ant-modal')).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page.getByText(/本地快照|Local snapshots|暂无|No snapshots|快照|snapshot/i).first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '247-real-workspace-history')
    await page.keyboard.press('Escape')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: debug simulation + engine preview + export image', async ({
    page,
    request,
  }) => {
    test.setTimeout(180_000)
    const errors = trackErrors(page)
    const code = `workbench.real.debug.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Debug Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'debug-ok'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: code }).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    await page.getByRole('button', { name: /调试流程|Debug/i }).click()
    await expect(page.locator('.flow-debugger-panel')).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '260-real-debug-open')

    // Browser simulation: start should advance past ready.
    // Ant icon buttons expose names like "play-circle 开始".
    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /开始|Start/i })
      .click()
    await expect
      .poll(
        async () =>
          page
            .locator('.flow-debugger-panel .ant-tag')
            .filter({ hasText: /完成|completed|错误|error|运行|running|暂停|paused/i })
            .count(),
        { timeout: TIMEOUT }
      )
      .toBeGreaterThan(0)
    await shot(page, '261-real-debug-simulation')

    // Engine preview executes draft XML via /api/executions/preview (no publish required).
    await page
      .locator('.flow-debugger-mode-switch')
      .getByText(/服务器执行|Server execution/i)
      .click()
    await expect(page.locator('.engine-debug-section')).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.engine-debug-section').getByText(code).first()).toBeVisible({
      timeout: TIMEOUT,
    })

    const paramsBox = page.locator('.engine-debug-section .debugger-vars-textarea')
    await paramsBox.fill('{}')

    const previewResponse = page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' &&
        new URL(response.url()).pathname === '/api/executions/preview'
    )
    await page
      .locator('.engine-debug-section')
      .getByRole('button', { name: /在服务器运行草稿|Run draft on server/i })
      .click()
    const preview = await previewResponse
    expect(preview.ok(), await preview.text()).toBeTruthy()
    const previewBody = (await preview.json()) as {
      success?: boolean
      result?: Record<string, unknown>
    }
    expect(previewBody.success).toBe(true)
    expect(previewBody.result?.version_marker).toBe('debug-ok')
    await expect(
      page
        .locator('.ant-message-notice')
        .filter({ hasText: /服务器执行完成|server.*success|completed/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.engine-debug-result')).toContainText(/debug-ok|version_marker/i)
    await shot(page, '262-real-debug-engine')

    // Export image downloads PNG then SVG from the canvas.
    const downloads: string[] = []
    page.on('download', (download) => {
      downloads.push(download.suggestedFilename())
    })
    await page.locator('.header-more-btn').click()
    await page
      .getByRole('menuitem', { name: /导出图片|Export image/i })
      .or(page.getByText(/导出图片|Export image/i))
      .first()
      .click()
    await expect
      .poll(() => downloads.some((name) => /\.png$/i.test(name)), { timeout: TIMEOUT })
      .toBeTruthy()
    await expect
      .poll(() => downloads.some((name) => /\.svg$/i.test(name)), { timeout: TIMEOUT })
      .toBeTruthy()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已导出 PNG|PNG image exported|PNG/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '263-real-export-image')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: simulation breakpoint pauses then step/continue', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.bp.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Breakpoint Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'bp'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await page.getByRole('button', { name: /调试流程|Debug/i }).click()
    await expect(page.locator('.flow-debugger-panel')).toBeVisible({ timeout: TIMEOUT })

    // Add a breakpoint on the Marker autoTask (target the collapse extra link, not the header).
    await page
      .locator('.flow-debugger-panel .ant-collapse-extra')
      .getByRole('button', { name: /添加|Add/i })
      .click()
    const bpDialog = page.getByRole('dialog').filter({ hasText: /添加断点|Add breakpoint/i })
    await expect(bpDialog).toBeVisible({ timeout: TIMEOUT })
    await bpDialog.locator('.ant-select').click()
    await chooseOpenSelectOption(page, /Marker/)
    await bpDialog.getByRole('button', { name: /添\s*加|Add/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已在节点|Breakpoint added/i })
    ).toBeVisible({ timeout: TIMEOUT })
    // Collapse header badge reflects the new breakpoint (table body may stay collapsed).
    await expect(
      page.locator('.flow-debugger-panel').getByRole('button', { name: /断点\s*1|Breakpoints.*1/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '270-real-breakpoint-added')

    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /开始|Start/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已暂停|Paused/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '271-real-breakpoint-paused')

    const stepBtn = page.locator('.flow-debugger-panel').getByRole('button', { name: /单步|Step/i })
    const continueBtn = page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /继续|Continue/i })
    await expect(stepBtn).toBeEnabled({ timeout: TIMEOUT })
    await expect(continueBtn).toBeEnabled({ timeout: TIMEOUT })

    await stepBtn.click()
    await expect
      .poll(
        async () =>
          page
            .locator('.flow-debugger-panel')
            .getByText(/已暂停|Paused|已完成|Completed|错误|Error/i)
            .count(),
        { timeout: TIMEOUT }
      )
      .toBeGreaterThan(0)
    await shot(page, '272-real-breakpoint-stepped')

    if (
      await page
        .locator('.flow-debugger-panel')
        .getByText(/已暂停|Paused/i)
        .first()
        .isVisible()
        .catch(() => false)
    ) {
      await continueBtn.click()
    }

    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已完成|Completed|错误|Error/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '273-real-breakpoint-continued')

    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /重置|Reset/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/准备就绪|Ready/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '274-real-breakpoint-reset')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: conditional breakpoint skips then hits', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.bpcond.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Conditional BP Draft',
      type: 'TBBPM',
      // The local assignment preview deliberately rejects engine output mappings.
      xml: markerProcessXml(code, 'bpcond')
        .replace('<output target="version_marker" dataType="java.lang.String"/>', '')
        .replace(
          '<code><![CDATA["bpcond"]]></code>',
          '<code><![CDATA[version_marker = "bpcond"]]></code>'
        ),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await page.getByRole('button', { name: /调试流程|Debug/i }).click()
    await expect(page.locator('.flow-debugger-panel')).toBeVisible({ timeout: TIMEOUT })

    const varsInput = page.locator('.flow-debugger-panel .debugger-vars-textarea')
    await expect(varsInput).toBeVisible({ timeout: TIMEOUT })
    await varsInput.fill('{"hit":false}')

    await page
      .locator('.flow-debugger-panel .ant-collapse-extra')
      .getByRole('button', { name: /添加|Add/i })
      .click()
    const bpDialog = page.getByRole('dialog').filter({ hasText: /添加断点|Add breakpoint/i })
    await expect(bpDialog).toBeVisible({ timeout: TIMEOUT })
    await bpDialog.locator('.ant-select').click()
    await chooseOpenSelectOption(page, /Marker/)
    await bpDialog.getByTestId('breakpoint-condition-input').fill('hit == true')
    await bpDialog.getByRole('button', { name: /添\s*加|Add/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已在节点|Breakpoint added/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(
      page.locator('.flow-debugger-panel').getByRole('button', { name: /断点\s*1|Breakpoints.*1/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '275-real-cond-bp-added')

    // Condition false → run completes without pausing (proves condition was stored).
    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /开始|Start/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已完成|Completed/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.flow-debugger-panel').getByText(/已暂停|Paused/i)).toHaveCount(0)
    await shot(page, '276-real-cond-bp-skipped')

    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /重置|Reset/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/准备就绪|Ready/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await varsInput.fill('{"hit":true}')

    // Condition true → pause on Marker.
    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /开始|Start/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已暂停|Paused/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '277-real-cond-bp-hit')

    await page
      .locator('.flow-debugger-panel')
      .getByRole('button', { name: /继续|Continue/i })
      .click()
    await expect(
      page
        .locator('.flow-debugger-panel')
        .getByText(/已完成|Completed/i)
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '278-real-cond-bp-continued')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: undo/redo palette node and import XML persists', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.undo.${crypto.randomUUID()}`
    const importedName = 'ImportedViaMenu'
    const importedMarker = 'import-xml-ok'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Undo Import Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'before-import'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: /^Marker$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })

    // Palette add is a single undoable graph mutation (unlike per-keystroke renames).
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()
    await autoTaskItem.focus()
    await page.keyboard.press('Enter')
    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '280-real-undo-added')

    const undoBtn = page.getByRole('button', { name: /撤销|Undo/i })
    const redoBtn = page.getByRole('button', { name: /重做|Redo/i })
    await expect(undoBtn).toBeEnabled({ timeout: TIMEOUT })
    await undoBtn.click()
    await expect(graphNodes).toHaveCount(beforeCount, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' })).toHaveCount(0)
    await shot(page, '281-real-undo')

    await expect(redoBtn).toBeEnabled({ timeout: TIMEOUT })
    await redoBtn.click()
    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '282-real-redo')

    // Keyboard undo/redo round-trip.
    await page.keyboard.press('Control+z')
    await expect(graphNodes).toHaveCount(beforeCount, { timeout: TIMEOUT })
    await page.keyboard.press('Control+Shift+z')
    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await shot(page, '283-real-undo-redo-keys')

    // Import XML via more menu file chooser (replaces canvas content).
    const importXml = markerProcessXml(code, importedMarker).replace(
      'name="Marker"',
      `name="${importedName}"`
    )
    const [fileChooser] = await Promise.all([
      page.waitForEvent('filechooser', { timeout: TIMEOUT }),
      (async () => {
        await page.locator('.header-more-btn').click()
        await page.getByText(/导入 XML|Import XML/i).click()
      })(),
    ])
    await fileChooser.setFiles({
      name: `${code}.bpm`,
      mimeType: 'application/xml',
      buffer: Buffer.from(importXml, 'utf8'),
    })
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /XML 已导入|XML imported/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: new RegExp(`^${importedName}$`) })
        .first()
    ).toBeVisible({ timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: /^Marker$/ })).toHaveCount(0)
    await expect(page.locator('.x6-node').filter({ hasText: '自动任务' })).toHaveCount(0)
    await shot(page, '284-real-import-xml')

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toContain(importedName)
    expect(saved.xml).toContain(importedMarker)
    expect(saved.xml).not.toContain('before-import')
    await shot(page, '285-real-import-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: copy/paste node; menu hides workspace duplicate', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.copy.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Copy Paste Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'copy'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    const graphNodes = page.locator('.x6-node')
    const beforeCount = await graphNodes.count()

    await page
      .locator('.x6-node')
      .filter({ hasText: /^Marker$/ })
      .first()
      .click()
    await page.getByRole('button', { name: /复制（Ctrl\+C）|Copy \(Ctrl\+C\)/i }).click()
    await expect(
      page.locator('.ant-message-notice').filter({ hasText: /已复制|copied/i })
    ).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '290-real-copy')

    await page.getByRole('button', { name: /粘贴（Ctrl\+V）|Paste \(Ctrl\+V\)/i }).click()
    await expect(graphNodes).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    await expect(page.locator('.x6-node').filter({ hasText: /Marker/i })).toHaveCount(2, {
      timeout: TIMEOUT,
    })
    await shot(page, '291-real-paste')

    // Operate-bound drafts must not offer workspace "duplicate flow" in the more menu.
    await page.locator('.header-more-btn').click()
    await expect(page.getByText(/导入 XML|Import XML/i)).toBeVisible({ timeout: TIMEOUT })
    await expect(
      page.getByRole('menuitem', { name: /创建副本|复制流程|Duplicate flow|Duplicate$/i })
    ).toHaveCount(0)
    await page.keyboard.press('Escape')
    await shot(page, '292-real-no-workspace-duplicate')

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect((saved.xml?.match(/<scriptTask\b/g) || []).length).toBe(2)
    expect(saved.xml).toContain('Marker_副本')
    await shot(page, '293-real-copy-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: connect edges + persist transitions', async ({ page, request }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.edge.${crypto.randomUUID()}`
    const bridgeName = 'BridgeTask'

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Edge Connect Draft',
      type: 'TBBPM',
      xml: markerProcessXml(code, 'edge'),
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: /^Marker$/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })

    await expect
      .poll(
        async () =>
          page.evaluate(() => {
            const graph = (window as unknown as { __x6Graph?: { getEdges: () => unknown[] } })
              .__x6Graph
            return graph?.getEdges().length ?? 0
          }),
        { timeout: TIMEOUT }
      )
      .toBeGreaterThanOrEqual(2)
    const edgeCountBefore = await page.evaluate(() => {
      const graph = (window as unknown as { __x6Graph?: { getEdges: () => unknown[] } }).__x6Graph
      return graph?.getEdges().length ?? 0
    })

    // Add a bridge autoTask via palette Enter.
    const autoTaskItem = page.locator('.drag-palette-item').filter({ hasText: '自动任务' }).first()
    const beforeNodes = await page.locator('.x6-node').count()
    await autoTaskItem.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.x6-node')).toHaveCount(beforeNodes + 1, { timeout: TIMEOUT })
    const bridge = page.locator('.x6-node').filter({ hasText: '自动任务' }).first()
    await expect(bridge).toBeVisible({ timeout: TIMEOUT })
    await page.getByRole('tab', { name: /通用|General/i }).click()
    const nameInput = page
      .locator('.ant-form-item')
      .filter({ hasText: /节点名称|Node name/i })
      .locator('input')
    await nameInput.first().fill(bridgeName)
    await expect(page.locator('.x6-node').filter({ hasText: bridgeName }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '300-real-edge-bridge-added')

    // Assert magnets exist in the SVG stage (real editable ports), then connect via graph API.
    await expect(page.locator('.x6-port-body[magnet="true"]')).toHaveCount(6, { timeout: TIMEOUT })
    await connectNodesViaGraph(page, 'Marker', 'bottom', bridgeName, 'top')
    await expect
      .poll(
        async () =>
          page.evaluate(() => {
            const graph = (window as unknown as { __x6Graph?: { getEdges: () => unknown[] } })
              .__x6Graph
            return graph?.getEdges().length ?? 0
          }),
        { timeout: TIMEOUT }
      )
      .toBeGreaterThan(edgeCountBefore)
    await shot(page, '301-real-edge-marker-bridge')

    const afterMarkerBridge = await page.evaluate(() => {
      const graph = (window as unknown as { __x6Graph?: { getEdges: () => unknown[] } }).__x6Graph
      return graph?.getEdges().length ?? 0
    })
    await connectNodesViaGraph(page, bridgeName, 'bottom', 'End', 'top')
    await expect
      .poll(
        async () =>
          page.evaluate(() => {
            const graph = (window as unknown as { __x6Graph?: { getEdges: () => unknown[] } })
              .__x6Graph
            return graph?.getEdges().length ?? 0
          }),
        { timeout: TIMEOUT }
      )
      .toBeGreaterThan(afterMarkerBridge)
    await shot(page, '302-real-edge-bridge-end')

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    expect(saved.xml).toContain(bridgeName)
    expect(saved.xml).toMatch(/<transition[^>]*to="[^"]*"[^/]*\/?>|<transition[^>]*to="[^"]*">/)
    // Bridge must appear as a transition target and itself transition onward.
    expect(saved.xml).toMatch(new RegExp(`name="${bridgeName}"`))
    const bridgeBlock = saved.xml?.match(
      new RegExp(`<autoTask[^>]*name="${bridgeName}"[\\s\\S]*?</autoTask>`)
    )?.[0]
    expect(bridgeBlock, 'bridge autoTask block').toBeTruthy()
    expect(bridgeBlock).toMatch(/<transition\b/)
    await shot(page, '303-real-edge-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate draft designer: align toolbar persists node positions', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.align.${crypto.randomUUID()}`

    // Seed deliberately staggered X positions so Align left is observable.
    const staggeredXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpm code="${code}" name="${code}">
  <var name="version_marker" dataType="java.lang.String" inOutType="return"/>
  <start id="start" name="Start" g="40,40,80,80">
    <transition to="marker"/>
  </start>
  <scriptTask id="marker" name="Marker" g="220,180,200,100">
    <action type="script" language="qlexpress">
      <output target="version_marker" dataType="java.lang.String"/>
      <code><![CDATA["align"]]></code>
    </action>
    <transition to="end"/>
  </scriptTask>
  <end id="end" name="End" g="480,320,80,80"/>
</bpm>`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite Align Draft',
      type: 'TBBPM',
      xml: staggeredXml,
      tags: ['real-vite'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=tbbpm`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.x6-canvas-toolbar')).toBeVisible({ timeout: TIMEOUT })

    const xsBefore = await page.evaluate(() => {
      const graph = (
        window as unknown as {
          __x6Graph?: {
            getNodes: () => Array<{ position: () => { x: number; y: number } }>
          }
        }
      ).__x6Graph
      return (graph?.getNodes() ?? []).map((n) => Math.round(n.position().x))
    })
    expect(new Set(xsBefore).size).toBeGreaterThan(1)
    await shot(page, '310-real-align-before')

    await page.getByRole('button', { name: /全选|Select all/i }).click()
    await page.getByRole('button', { name: /左对齐|Align left/i }).click()

    await expect
      .poll(
        async () => {
          const xs = await page.evaluate(() => {
            const graph = (
              window as unknown as {
                __x6Graph?: {
                  getNodes: () => Array<{ position: () => { x: number; y: number } }>
                }
              }
            ).__x6Graph
            return (graph?.getNodes() ?? []).map((n) => Math.round(n.position().x))
          })
          return new Set(xs).size
        },
        { timeout: TIMEOUT }
      )
      .toBe(1)
    await shot(page, '311-real-align-left')

    await page
      .locator('.x6-canvas-toolbar')
      .getByRole('button', { name: /放大（Ctrl|Zoom in/i })
      .click()
    await page
      .locator('.x6-canvas-toolbar')
      .getByRole('button', { name: /缩小（Ctrl|Zoom out/i })
      .click()
    await page
      .locator('.x6-canvas-toolbar')
      .getByRole('button', { name: /适应画布|Fit to canvas/i })
      .click()
    await page
      .locator('.x6-canvas-toolbar')
      .getByRole('button', { name: /重置视图|Reset view/i })
      .click()
    await shot(page, '312-real-align-zoom')

    const alignedX = await page.evaluate(() => {
      const graph = (
        window as unknown as {
          __x6Graph?: {
            getNodes: () => Array<{ position: () => { x: number; y: number } }>
          }
        }
      ).__x6Graph
      return Math.round(graph!.getNodes()[0].position().x)
    })

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

    const saved = await getJson<ProcessDraft>(request, `/api/processes/${code}`)
    const gAttrs = [...(saved.xml?.matchAll(/\bg="(\d+),(\d+),/g) ?? [])].map((m) => Number(m[1]))
    expect(gAttrs.length).toBeGreaterThanOrEqual(3)
    expect(new Set(gAttrs).size, `expected aligned g=x=${alignedX}, got ${gAttrs}`).toBe(1)
    expect(gAttrs[0]).toBe(alignedX)
    await shot(page, '313-real-align-saved')
    await assertNoPageErrors(errors)
  })

  test('Operate BPMN draft: open, palette serviceTask, validate, save, preview', async ({
    page,
    request,
  }) => {
    test.setTimeout(120_000)
    const errors = trackErrors(page)
    const code = `workbench.real.bpmn.${crypto.randomUUID()}`

    await postJson<ProcessDraft>(request, '/api/processes', {
      code,
      name: 'Real Vite BPMN Draft',
      type: 'BPMN',
      xml: markerBpmnProcessXml(code, 'bpmn-seed'),
      tags: ['real-vite', 'bpmn'],
    })

    await page.goto(
      `/build/designer?source=operateProcessCode&processCode=${encodeURIComponent(code)}&modelType=bpmn`
    )
    await page.waitForSelector('.x6-graph', { timeout: TIMEOUT })
    await expect(page.locator('.ant-tag').filter({ hasText: 'BPMN' }).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(
      page
        .locator('.x6-node')
        .filter({ hasText: /Marker/ })
        .first()
    ).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.locator('.bpmn-designer-left-sider').first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '320-real-bpmn-open')

    // Seeded Marker BPMN should validate cleanly before we mutate the canvas.
    await page.getByRole('button', { name: /验证流程|Validate/i }).click()
    await expect(page.getByText(/流程验证|Validation|验证/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await expect(page.getByText(/0 个错误|0 errors|通过|passed|✓/i).first()).toBeVisible({
      timeout: TIMEOUT,
    })
    await shot(page, '321-real-bpmn-validate-seed')

    const serviceTaskItem = page
      .locator('.drag-palette-item')
      .filter({ hasText: '服务任务' })
      .first()
    await expect(serviceTaskItem).toBeVisible({ timeout: TIMEOUT })
    const beforeCount = await page.locator('.x6-node').count()
    await serviceTaskItem.focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.x6-node')).toHaveCount(beforeCount + 1, { timeout: TIMEOUT })
    const added = page
      .locator('.x6-node')
      .filter({ hasText: /服务任务/ })
      .first()
    await expect(added).toBeVisible({ timeout: TIMEOUT })
    await shot(page, '322-real-bpmn-palette')

    // Remove the incomplete palette node so save/XML round-trip stays valid.
    // Palette-created nodes become selected immediately, which opens their properties and
    // makes keyboard deletion deterministic even when SVG hit areas are smaller than the cell.
    await expect(page.getByRole('tab', { name: /通用|General/i })).toBeVisible({
      timeout: TIMEOUT,
    })
    await page.keyboard.press('Delete')
    await expect(page.locator('.x6-node')).toHaveCount(beforeCount, { timeout: TIMEOUT })

    // Dirty the Operate draft via the header name editor (avoids edge hit-testing on nodes).
    await page.getByRole('button', { name: /重命名流程|Rename process/i }).click()
    const titleInput = page.locator('.designer-header input, header input').first()
    await expect(titleInput).toBeVisible({ timeout: TIMEOUT })
    await titleInput.fill('Real Vite BPMN Draft Saved')
    await titleInput.press('Enter')

    const [saveResponse] = await Promise.all([
      page.waitForResponse(
        (response) =>
          response.request().method() === 'PUT' &&
          new URL(response.url()).pathname === `/api/processes/${code}`,
        { timeout: 60_000 }
      ),
      page
        .getByRole('button', { name: /保存 \*|保存|已保存|Save/i })
        .first()
        .click(),
    ])
    expect(saveResponse.ok()).toBeTruthy()

    const saved = await getJson<ProcessDraft & { name?: string }>(request, `/api/processes/${code}`)
    expect(saved.xml).toMatch(/bpmn:definitions|definitions/)
    expect(saved.xml).not.toMatch(/serviceTask/i)
    expect(saved.xml).toMatch(/scriptTask/i)
    expect(saved.name || '').toMatch(/Saved|BPMN/)
    await shot(page, '323-real-bpmn-saved')

    const preview = await postJson<{
      success?: boolean
      result?: Record<string, unknown>
      error?: string
      errorCode?: string
      message?: string
    }>(request, '/api/executions/preview', {
      code,
      modelType: 'BPMN',
      xml: markerBpmnProcessXml(code, 'bpmn-preview'),
      params: {},
    })
    expect(
      preview.success,
      `BPMN preview failed: ${preview.errorCode || ''} ${preview.error || preview.message || JSON.stringify(preview)}`
    ).toBe(true)
    expect(preview.result?.version_marker).toBe('bpmn-preview')
    await shot(page, '324-real-bpmn-preview')
    await assertNoPageErrors(errors)
  })
})
