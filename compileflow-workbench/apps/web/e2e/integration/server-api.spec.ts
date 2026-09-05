import { expect, test } from '@playwright/test'

import {
  apiHeaders,
  browserUrl,
  hasDirectApiKey,
  managedIntegration,
  serverUrl,
} from './support/integrationRuntime'

test.describe('compileflow-workbench-server integration', () => {
  test.beforeAll(async ({ request }) => {
    try {
      const response = await request.get(`${serverUrl}/actuator/health`, {
        headers: apiHeaders(),
        timeout: 5_000,
      })
      if (!response.ok()) {
        throw new Error(`health endpoint returned HTTP ${response.status()}`)
      }
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error)
      throw new Error(`compileflow-workbench-server is required at ${serverUrl}: ${reason}`, {
        cause: error,
      })
    }
  })

  test('health endpoint is UP', async ({ request }) => {
    const response = await request.get(`${serverUrl}/actuator/health`, { headers: apiHeaders() })
    expect(response.ok()).toBeTruthy()
    const body = (await response.json()) as { status?: string }
    expect(body.status).toBe('UP')
  })

  test('protected APIs reject requests without the configured key', async ({ request }) => {
    test.skip(!hasDirectApiKey(), 'A directly selected Server API key is required')

    const response = await request.get(`${serverUrl}/api/examples`)
    expect(response.status()).toBe(401)
    expect(response.headers()['content-type']).toContain('application/problem+json')
    const body = (await response.json()) as { code?: string; status?: number }
    expect(body).toEqual(expect.objectContaining({ code: 'UNAUTHENTICATED', status: 401 }))
  })

  test('managed edge replaces client-supplied upstream credentials', async ({ request }) => {
    test.skip(!managedIntegration, 'Credential replacement belongs to the managed integration edge')

    const response = await request.get(`${browserUrl}/api/examples`, {
      headers: {
        Authorization: 'Bearer untrusted-client-value',
        Cookie: 'session=untrusted-client-value',
        Forwarded: 'for=203.0.113.10;proto=https',
        'X-API-Key': 'untrusted-client-value',
        'X-Forwarded-For': '203.0.113.10',
      },
    })
    expect(response.ok()).toBeTruthy()
  })

  test('examples catalog is served', async ({ request }) => {
    const response = await request.get(`${serverUrl}/api/examples`, { headers: apiHeaders() })
    expect(response.ok()).toBeTruthy()
    const body: unknown = await response.json()
    expect(Array.isArray(body)).toBe(true)
    const examples = body as Array<Record<string, unknown>>
    expect(examples.length).toBeGreaterThan(0)

    const ids = examples.map((example) => {
      expect(typeof example.id).toBe('string')
      expect((example.id as string).trim()).not.toBe('')
      expect(['BPMN', 'TBBPM']).toContain(example.modelType)
      return example.id as string
    })
    expect(new Set(ids).size).toBe(ids.length)
  })

  test('monitoring trends honors interval=1m for 1h range', async ({ request }) => {
    const response = await request.get(
      `${serverUrl}/api/monitoring/trends?timeRange=1h&interval=1m`,
      { headers: apiHeaders() }
    )
    expect(response.ok()).toBeTruthy()
    const trends = (await response.json()) as unknown[]
    expect(trends.length).toBeGreaterThanOrEqual(30)
    expect(trends.length).toBeLessThanOrEqual(65)
  })

  test('operations health endpoints expose deployment, runtime, and async state', async ({
    request,
  }) => {
    const [deploymentResponse, runtimeResponse, asyncResponse] = await Promise.all([
      request.get(`${serverUrl}/api/deployment-control/health`, { headers: apiHeaders() }),
      request.get(`${serverUrl}/api/monitoring/deploy-runtime`, { headers: apiHeaders() }),
      request.get(`${serverUrl}/api/async-invocations/health`, { headers: apiHeaders() }),
    ])
    expect(deploymentResponse.ok()).toBeTruthy()
    expect(runtimeResponse.ok()).toBeTruthy()
    expect(asyncResponse.ok()).toBeTruthy()

    const deployment = (await deploymentResponse.json()) as Record<string, unknown>
    expect(['UP', 'DEGRADED', 'DOWN']).toContain(deployment.status)
    expect(typeof deployment.expiredClaimCount).toBe('number')
    expect(typeof deployment.dispatcherRunning).toBe('boolean')

    const runtime = (await runtimeResponse.json()) as Record<string, unknown>
    expect(typeof runtime.available).toBe('boolean')
    expect(typeof runtime.started).toBe('boolean')
    expect(typeof runtime.timestamp).toBe('string')

    const asyncHealth = (await asyncResponse.json()) as Record<string, unknown>
    expect(['healthy', 'degraded']).toContain(asyncHealth.status)
    expect(typeof asyncHealth.readyQueuedCount).toBe('number')
    expect(typeof asyncHealth.expiredRunningCount).toBe('number')
    expect(typeof asyncHealth.workerId).toBe('string')
  })

  test('dead-letter requeue operations return refreshed health snapshots', async ({ request }) => {
    const deploymentResponse = await request.post(
      `${serverUrl}/api/deployment-control/dead-letters/requeue`,
      { headers: apiHeaders() }
    )
    expect(deploymentResponse.ok()).toBeTruthy()
    const deployment = (await deploymentResponse.json()) as Record<string, unknown>
    expect(typeof deployment.requeued).toBe('number')
    expect(deployment.health).toEqual(expect.objectContaining({ status: expect.any(String) }))

    const asyncResponse = await request.post(
      `${serverUrl}/api/async-invocations/dead-letters/requeue`,
      {
        headers: apiHeaders(),
        data: { limit: 10 },
      }
    )
    expect(asyncResponse.ok()).toBeTruthy()
    const asyncResult = (await asyncResponse.json()) as Record<string, unknown>
    expect(typeof asyncResult.requeued).toBe('number')
    expect(asyncResult.limit).toBe(10)
    expect(asyncResult.health).toEqual(
      expect.objectContaining({ status: expect.stringMatching(/^(healthy|degraded)$/) })
    )
  })

  test('execution-log purge returns bounded progress', async ({ request }) => {
    const response = await request.post(`${serverUrl}/api/execution-logs/purge`, {
      headers: apiHeaders(),
      data: { before: '2099-01-01T00:00:00.000Z' },
    })
    expect(response.ok()).toBeTruthy()
    const body = (await response.json()) as {
      deletedCount?: number
      hasMore?: boolean
      purgedAt?: string
    }
    expect(typeof body.deletedCount).toBe('number')
    expect(typeof body.hasMore).toBe('boolean')
    expect(body.purgedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/)
  })
})
