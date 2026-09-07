import type { APIRequestContext } from '@playwright/test'
import { expect, test } from '@playwright/test'

import { apiHeaders, serverUrl } from './support/integrationRuntime'
import { failingProcessXml, markerProcessXml } from './support/markerProcess'
import { expectJson, postJson } from './support/workbenchApi'

interface ProcessDraft {
  revision: number
}

interface PublishedVersion {
  version: string
}

interface DeploymentResult {
  routeRevision: number
  status: string
}

interface AsyncInvocation {
  currentAttemptCount: number
  totalAttemptCount: number
  error?: string
  errorCode?: string
  processCode: string
  invocationId: string
  maxAttempts: number
  routing?: {
    effectiveVersion?: string
    requestedAlias?: string
    requestedVersion?: string
    routeRevision?: number
    target?: 'STABLE' | 'CANDIDATE'
  }
  response?: {
    invocationId: string
    result?: Record<string, unknown>
    routing: {
      effectiveVersion?: string
      routeRevision?: number
    }
    success: boolean
    traceId: string
  }
  status: 'queued' | 'running' | 'succeeded' | 'dead_letter'
  traceId?: string
}

interface DeploymentRuntimeDiagnostics {
  available: boolean
  deployedVersions?: Array<{
    code: string
    namespace: string
    versions: string[]
  }>
}

interface AsyncInvocationList {
  data: AsyncInvocation[]
  total: number
}

interface AsyncInvocationHealth {
  deadLetterCount: number
  status: 'healthy' | 'degraded'
}

interface ExecutionLog {
  effectiveVersion?: string
  processCode: string
  invocationId: string
  routeAlias?: string
  routeRevision?: number
  routingSource?: string
  status: string
  traceId: string
}

interface ExecutionLogList {
  data: ExecutionLog[]
  total: number
}

interface VersionDistribution {
  effectiveVersion: string
  executions: number
  failed: number
  processCode: string
  routeAlias: string
  routingSource: string
  success: number
}

async function publishStableMarkerProcess(
  request: APIRequestContext,
  code: string,
  xml = markerProcessXml(code, 'async-v1')
): Promise<{ draftRevision: number; routeRevision: number; version: string }> {
  const draft = await postJson<ProcessDraft>(request, '/api/processes', {
    code,
    name: 'Workbench Runtime Chain Evidence',
    type: 'TBBPM',
    xml,
    tags: ['integration'],
  })
  const published = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Async runtime baseline', expectedRevision: draft.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  const deployment = await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: published.version,
      alias: 'production',
      expectedRouteRevision: 0,
      strategy: 'all_at_once',
      notes: 'Async runtime integration',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  expect(deployment.status).toBe('completed')
  return {
    draftRevision: draft.revision,
    routeRevision: deployment.routeRevision,
    version: published.version,
  }
}

async function waitForAsyncInvocation(
  request: APIRequestContext,
  invocationId: string,
  status: AsyncInvocation['status'] = 'succeeded'
): Promise<AsyncInvocation> {
  await expect
    .poll(
      async () =>
        expectJson<AsyncInvocation>(
          await request.get(`${serverUrl}/api/async-invocations/${invocationId}`, {
            headers: apiHeaders(),
          })
        ),
      { intervals: [50, 100, 250, 500], timeout: 10_000 }
    )
    .toMatchObject({ status })
  return expectJson<AsyncInvocation>(
    await request.get(`${serverUrl}/api/async-invocations/${invocationId}`, {
      headers: apiHeaders(),
    })
  )
}

async function locallyInstalledVersions(
  request: APIRequestContext,
  code: string
): Promise<string[]> {
  const diagnostics = await expectJson<DeploymentRuntimeDiagnostics>(
    await request.get(`${serverUrl}/api/monitoring/deploy-runtime`, { headers: apiHeaders() })
  )
  expect(diagnostics.available).toBe(true)
  return (
    diagnostics.deployedVersions?.find(
      (process) => process.namespace === 'default' && process.code === code
    )?.versions ?? []
  )
}

test('persisted async invocation is idempotent, version-pinned, and observable', async ({
  request,
}) => {
  test.setTimeout(60_000)

  const code = `workbench.runtime.${crypto.randomUUID()}`
  const invocationId = `runtime-${crypto.randomUUID()}`
  const deployed = await publishStableMarkerProcess(request, code)
  const submission = {
    invocationId,
    params: {},
    routing: { alias: 'production' },
    maxAttempts: 2,
    retryDelayMs: 10,
  }

  const acceptedResponse = await request.post(
    `${serverUrl}/api/processes/${code}/async-invocations`,
    {
      data: submission,
      headers: apiHeaders(),
    }
  )
  expect(acceptedResponse.status()).toBe(202)
  const accepted = await expectJson<AsyncInvocation>(acceptedResponse)
  expect(accepted).toEqual(
    expect.objectContaining({
      processCode: code,
      invocationId,
      maxAttempts: 2,
      status: 'queued',
    })
  )

  const replay = await postJson<AsyncInvocation>(
    request,
    `/api/processes/${code}/async-invocations`,
    submission
  )
  expect(replay.invocationId).toBe(invocationId)

  const conflict = await request.post(`${serverUrl}/api/processes/${code}/async-invocations`, {
    data: { ...submission, maxAttempts: 3 },
    headers: apiHeaders(),
  })
  expect(conflict.status()).toBe(409)
  await expect(conflict.json()).resolves.toEqual(
    expect.objectContaining({ code: 'CONFLICT', status: 409 })
  )

  const completed = await waitForAsyncInvocation(request, invocationId)
  expect(completed).toEqual(
    expect.objectContaining({
      currentAttemptCount: 1,
      processCode: code,
      invocationId,
      routing: expect.objectContaining({
        effectiveVersion: deployed.version,
        requestedAlias: 'production',
        routeRevision: deployed.routeRevision,
        target: 'STABLE',
      }),
      status: 'succeeded',
      traceId: expect.any(String),
      response: expect.objectContaining({
        invocationId,
        result: expect.objectContaining({ version_marker: 'async-v1' }),
        routing: expect.objectContaining({
          effectiveVersion: deployed.version,
          routeRevision: deployed.routeRevision,
        }),
        success: true,
      }),
    })
  )
  expect(completed.response?.traceId).toBe(completed.traceId)

  const invocations = await expectJson<AsyncInvocationList>(
    await request.get(
      `${serverUrl}/api/async-invocations?status=succeeded&processCode=${encodeURIComponent(code)}`,
      { headers: apiHeaders() }
    )
  )
  expect(invocations.total).toBe(1)
  expect(invocations.data.map((invocation) => invocation.invocationId)).toEqual([invocationId])

  const logs = await expectJson<ExecutionLogList>(
    await request.get(
      `${serverUrl}/api/execution-logs?invocationId=${encodeURIComponent(invocationId)}`,
      {
        headers: apiHeaders(),
      }
    )
  )
  expect(logs.total).toBe(1)
  expect(logs.data[0]).toEqual(
    expect.objectContaining({
      effectiveVersion: deployed.version,
      processCode: code,
      invocationId,
      routeAlias: 'production',
      routeRevision: deployed.routeRevision,
      routingSource: 'alias',
      status: 'success',
      traceId: completed.traceId,
    })
  )

  const distribution = await expectJson<VersionDistribution[]>(
    await request.get(
      `${serverUrl}/api/monitoring/version-distribution?processCode=${encodeURIComponent(code)}`,
      { headers: apiHeaders() }
    )
  )
  expect(distribution).toContainEqual(
    expect.objectContaining({
      effectiveVersion: deployed.version,
      executions: 1,
      failed: 0,
      processCode: code,
      routeAlias: 'production',
      routingSource: 'alias',
      success: 1,
    })
  )

  const draftV2 = await expectJson<ProcessDraft>(
    await request.put(`${serverUrl}/api/processes/${code}`, {
      headers: apiHeaders(),
      data: {
        name: 'Workbench Runtime Chain Evidence',
        xml: markerProcessXml(code, 'async-v2'),
        tags: ['integration'],
        expectedRevision: deployed.draftRevision,
      },
    })
  )
  const publishedV2 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Async runtime replacement', expectedRevision: draftV2.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  const replacement = await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: publishedV2.version,
      alias: 'production',
      expectedRouteRevision: deployed.routeRevision,
      strategy: 'all_at_once',
      notes: 'Replace async runtime baseline',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  expect(replacement.status).toBe('completed')
  await expect
    .poll(() => locallyInstalledVersions(request, code), { timeout: 10_000 })
    .toEqual([publishedV2.version])

  const oldVersionInvocationId = `runtime-old-${crypto.randomUUID()}`
  const oldVersionAccepted = await request.post(
    `${serverUrl}/api/processes/${code}/async-invocations`,
    {
      data: {
        invocationId: oldVersionInvocationId,
        params: {},
        routing: { version: deployed.version },
        maxAttempts: 2,
        retryDelayMs: 10,
      },
      headers: apiHeaders(),
    }
  )
  expect(oldVersionAccepted.status()).toBe(202)

  const oldVersionInvocation = await waitForAsyncInvocation(request, oldVersionInvocationId)
  expect(oldVersionInvocation).toEqual(
    expect.objectContaining({
      currentAttemptCount: 1,
      routing: expect.objectContaining({
        effectiveVersion: deployed.version,
        requestedVersion: deployed.version,
      }),
      status: 'succeeded',
      response: expect.objectContaining({
        result: expect.objectContaining({ version_marker: 'async-v1' }),
        routing: expect.objectContaining({ effectiveVersion: deployed.version }),
        success: true,
      }),
    })
  )
  expect(oldVersionInvocation.routing?.requestedAlias).toBeUndefined()

  const oldVersionLogs = await expectJson<ExecutionLogList>(
    await request.get(
      `${serverUrl}/api/execution-logs?invocationId=${encodeURIComponent(oldVersionInvocationId)}`,
      { headers: apiHeaders() }
    )
  )
  expect(oldVersionLogs.total).toBe(1)
  expect(oldVersionLogs.data[0]).toEqual(
    expect.objectContaining({
      effectiveVersion: deployed.version,
      processCode: code,
      invocationId: oldVersionInvocationId,
      routingSource: 'version',
      status: 'success',
    })
  )
  expect(oldVersionLogs.data[0].routeAlias).toBeUndefined()
  expect(oldVersionLogs.data[0].routeRevision).toBeUndefined()

  await expect
    .poll(() => locallyInstalledVersions(request, code), { timeout: 10_000 })
    .toEqual([publishedV2.version])
})

test('persisted async invocation exhausts retries, dead-letters, and requeues the pinned version', async ({
  request,
}) => {
  test.setTimeout(60_000)

  const code = `workbench.retry.${crypto.randomUUID()}`
  const invocationId = `retry-${crypto.randomUUID()}`
  const deployed = await publishStableMarkerProcess(request, code, failingProcessXml(code))

  const accepted = await postJson<AsyncInvocation>(
    request,
    `/api/processes/${code}/async-invocations`,
    {
      invocationId,
      params: { fail: true },
      routing: { alias: 'production' },
      maxAttempts: 2,
      retryDelayMs: 10,
    }
  )
  expect(accepted).toEqual(
    expect.objectContaining({
      currentAttemptCount: 0,
      invocationId,
      maxAttempts: 2,
      status: 'queued',
    })
  )

  const firstDeadLetter = await waitForAsyncInvocation(request, invocationId, 'dead_letter')
  expect(firstDeadLetter).toEqual(
    expect.objectContaining({
      currentAttemptCount: 2,
      error: expect.any(String),
      errorCode: expect.any(String),
      routing: expect.objectContaining({
        effectiveVersion: deployed.version,
        requestedAlias: 'production',
        routeRevision: deployed.routeRevision,
        target: 'STABLE',
      }),
      status: 'dead_letter',
      response: expect.objectContaining({
        routing: expect.objectContaining({
          effectiveVersion: deployed.version,
          routeRevision: deployed.routeRevision,
        }),
        success: false,
      }),
    })
  )
  expect(firstDeadLetter.error).toBe('Script execution error')

  const degraded = await expectJson<AsyncInvocationHealth>(
    await request.get(`${serverUrl}/api/async-invocations/health`, { headers: apiHeaders() })
  )
  expect(degraded).toEqual(
    expect.objectContaining({ deadLetterCount: expect.any(Number), status: 'degraded' })
  )
  expect(degraded.deadLetterCount).toBeGreaterThanOrEqual(1)

  const draftV2 = await expectJson<ProcessDraft>(
    await request.put(`${serverUrl}/api/processes/${code}`, {
      headers: apiHeaders(),
      data: {
        name: 'Workbench Runtime Chain Evidence',
        xml: markerProcessXml(code, 'recovery-v2'),
        tags: ['integration'],
        expectedRevision: deployed.draftRevision,
      },
    })
  )
  const publishedV2 = await postJson<PublishedVersion>(
    request,
    `/api/processes/${code}/publish`,
    { changelog: 'Healthy replacement', expectedRevision: draftV2.revision },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  const replacement = await postJson<DeploymentResult>(
    request,
    '/api/deployments',
    {
      processCode: code,
      version: publishedV2.version,
      alias: 'production',
      expectedRouteRevision: deployed.routeRevision,
      strategy: 'all_at_once',
      notes: 'Move the Alias before dead-letter replay',
    },
    { 'Idempotency-Key': crypto.randomUUID() }
  )
  expect(replacement.status).toBe('completed')

  const requeued = await postJson<AsyncInvocation>(
    request,
    `/api/async-invocations/${invocationId}/requeue`,
    undefined
  )
  expect(requeued).toEqual(
    expect.objectContaining({
      currentAttemptCount: 0,
      invocationId,
      routing: expect.objectContaining({
        effectiveVersion: deployed.version,
        requestedAlias: 'production',
        routeRevision: deployed.routeRevision,
        target: 'STABLE',
      }),
      status: 'queued',
    })
  )

  const replayedDeadLetter = await waitForAsyncInvocation(request, invocationId, 'dead_letter')
  expect(replayedDeadLetter).toEqual(
    expect.objectContaining({
      currentAttemptCount: 2,
      routing: expect.objectContaining({
        effectiveVersion: deployed.version,
        requestedAlias: 'production',
        routeRevision: deployed.routeRevision,
        target: 'STABLE',
      }),
      status: 'dead_letter',
      response: expect.objectContaining({
        routing: expect.objectContaining({
          effectiveVersion: deployed.version,
          routeRevision: deployed.routeRevision,
        }),
        success: false,
      }),
    })
  )

  const logs = await expectJson<ExecutionLogList>(
    await request.get(
      `${serverUrl}/api/execution-logs?invocationId=${encodeURIComponent(invocationId)}`,
      {
        headers: apiHeaders(),
      }
    )
  )
  expect(logs.total).toBe(4)
  expect(logs.data).toHaveLength(4)
  expect(
    logs.data.every(
      (log) =>
        log.effectiveVersion === deployed.version &&
        log.routeAlias === 'production' &&
        log.routeRevision === deployed.routeRevision &&
        log.routingSource === 'alias' &&
        log.status === 'failed'
    )
  ).toBe(true)

  const activeExecution = await postJson<{
    result?: Record<string, unknown>
    routing: { effectiveVersion?: string }
    success: boolean
  }>(request, `/api/processes/${code}/execute`, {
    invocationId: `active-${crypto.randomUUID()}`,
    params: {},
    routing: { alias: 'production' },
  })
  expect(activeExecution).toEqual(
    expect.objectContaining({
      result: expect.objectContaining({ version_marker: 'recovery-v2' }),
      routing: expect.objectContaining({ effectiveVersion: publishedV2.version }),
      success: true,
    })
  )
})
