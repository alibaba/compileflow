import { beforeEach, describe, expect, it, vi } from 'vitest'

const mockApiClient = {
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
  patch: vi.fn(),
  getBlob: vi.fn(),
  postBlob: vi.fn(),
}

const mockIsOperateMockMode = vi.fn<() => boolean>()

vi.mock('@/shared/api/client', () => ({
  default: mockApiClient,
}))

vi.mock('@/shared/config/buildConfig', () => ({
  isOperateMockMode: mockIsOperateMockMode,
}))

describe('operate api truth strategy', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.clearAllMocks()
    mockIsOperateMockMode.mockReturnValue(false)
  })

  it('returns mock process data only in explicit operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const [{ getProcesses }, { getMockProcesses }] = await Promise.all([
      import('@/shared/api/processes'),
      import('@/shared/api/mockProcessData'),
    ])

    const params = { page: 1, pageSize: 2, keyword: '订单' }
    const result = await getProcesses(params)

    expect(result).toEqual(getMockProcesses(params))
    expect(mockApiClient.get).not.toHaveBeenCalled()
  })

  it('calls the real process list endpoint with supported query params only', async () => {
    const apiResponse = {
      data: [],
      total: 0,
      page: 1,
      pageSize: 20,
    }
    mockApiClient.get.mockResolvedValue(apiResponse)

    const { getProcesses } = await import('@/shared/api/processes')
    const params = {
      page: 1,
      pageSize: 20,
      type: 'TBBPM' as const,
      keyword: 'order',
      sortBy: 'updatedAt' as const,
      sortOrder: 'desc' as const,
    }

    await expect(getProcesses(params)).resolves.toEqual(apiResponse)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/processes', { params })
  })

  it('sends only authoritative process creation fields in real mode', async () => {
    const request = {
      code: 'order.approve',
      name: 'Order Approval',
      type: 'BPMN' as const,
      xml: '<definitions/>',
      description: 'Approval workflow',
      tags: ['order'],
    }
    const apiResponse = {
      ...request,
      createdBy: 'compileflow-bridge',
      createdAt: '2026-07-25T00:00:00Z',
      updatedAt: '2026-07-25T00:00:00Z',
      revision: 0,
    }
    mockApiClient.post.mockResolvedValue(apiResponse)

    const { createProcess } = await import('@/shared/api/processes')

    await expect(createProcess(request)).resolves.toEqual(apiResponse)
    expect(mockApiClient.post).toHaveBeenCalledWith('/api/processes', request)
  })

  it('keeps mock published versions separate from editable drafts', async () => {
    mockIsOperateMockMode.mockReturnValue(true)
    const { getProcessByCode, getProcessVersions, publishProcess } =
      await import('@/shared/api/processes')

    const draft = await getProcessByCode('order-approval-bpmn')
    const initial = await getProcessVersions('order-approval-bpmn')
    const published = await publishProcess(
      'order-approval-bpmn',
      draft.revision,
      'mock-publication-intent',
      'Mock release'
    )
    const retried = await publishProcess(
      'order-approval-bpmn',
      draft.revision,
      'mock-publication-intent',
      'Mock release'
    )
    const updated = await getProcessVersions('order-approval-bpmn')

    expect(draft).not.toHaveProperty('status')
    expect(draft).not.toHaveProperty('version')
    expect(initial.data.map((version) => version.version)).toContain('1.2.0')
    expect(published).toMatchObject({
      processCode: 'order-approval-bpmn',
      modelType: 'BPMN',
      changelog: 'Mock release',
    })
    expect(published).not.toHaveProperty('status')
    expect(retried).toEqual(published)
    expect(updated.data[0]).toEqual(published)
    expect(updated.data.filter((version) => version.version === published.version)).toHaveLength(1)
    expect(mockApiClient.get).not.toHaveBeenCalled()
    expect(mockApiClient.post).not.toHaveBeenCalled()
  })

  it('enforces draft revisions in mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)
    const { getProcessByCode, updateProcess } = await import('@/shared/api/processes')
    const draft = await getProcessByCode('payment-process-tbbpm')
    const request = {
      name: draft.name,
      xml: draft.xml,
      description: draft.description,
      tags: draft.tags ?? [],
      expectedRevision: draft.revision,
    }

    const updated = await updateProcess(draft.code, request)

    expect(updated.revision).toBe(draft.revision + 1)
    await expect(updateProcess(draft.code, request)).rejects.toThrow('Process revision mismatch')
  })

  it('detects BPMN imports from XML content in mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)
    const { importProcessXml } = await import('@/shared/api/processes')
    const file = new File([], 'misleading.tbbpm.xml', { type: 'application/xml' })
    Object.defineProperty(file, 'text', {
      value: async () =>
        [
          '<?xml version="1.0"?>',
          '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">',
          '<process id="imported.order" name="Imported Order"/>',
          '</definitions>',
        ].join(''),
    })

    const imported = await importProcessXml(file)

    expect(imported).toMatchObject({
      code: 'imported.order',
      name: 'Imported Order',
      type: 'BPMN',
      revision: 0,
    })
  })

  it('loads published versions from the real control-plane endpoint', async () => {
    const versions = {
      data: [
        {
          processCode: 'order.approve',
          version: 'r-1',
          modelType: 'TBBPM' as const,
          createdAt: '2026-07-25T00:00:00Z',
          publishedBy: 'release-service',
        },
      ],
      nextCursor: null,
      hasMore: false,
    }
    mockApiClient.get.mockResolvedValue(versions)
    const { getProcessVersions } = await import('@/shared/api/processes')

    const params = { limit: 20, versionPrefix: 'r-' }
    await expect(getProcessVersions('order.approve', params)).resolves.toEqual(versions)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/processes/order.approve/versions', {
      params,
    })
  })

  it('calls apiClient in real mode for deployments', async () => {
    const apiResponse = {
      deployments: [
        {
          id: 'deploy-real-1',
          processCode: 'real-flow',
          version: '1.0.0',
          alias: 'production',
          status: 'completed',
          createdAt: '2026-03-30T00:00:00.000Z',
          createdBy: 'tester',
        },
      ],
      nextCursor: 'next-deployment',
      hasMore: true,
    }
    mockApiClient.get.mockResolvedValue(apiResponse)

    const { getDeployments } = await import('../deployments')
    const params = { cursor: 'deployment-cursor', limit: 5, status: 'completed' as const }

    await expect(getDeployments(params)).resolves.toEqual(apiResponse)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/deployments', { params })
  })

  it('reads authoritative rollout events instead of synthesizing deployment logs', async () => {
    const events = [
      {
        id: 42,
        sequence: 1,
        type: 'CANARY_STARTED',
        fromPhase: null,
        toPhase: 'in_progress',
        actor: 'workbench-gateway',
        reason: 'Initial canary',
        timestamp: '2026-07-05T00:00:00Z',
      },
    ]
    mockApiClient.get.mockResolvedValue(events)

    const { getDeploymentEvents } = await import('../deployments')

    await expect(getDeploymentEvents('deploy-real-1')).resolves.toEqual(events)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/deployments/deploy-real-1/events')
  })

  it('calls real log list endpoint with supported execution-log filters only', async () => {
    const apiResponse = {
      data: [],
      total: 0,
      page: 1,
      pageSize: 20,
    }
    mockApiClient.get.mockResolvedValue(apiResponse)

    const { getLogs } = await import('../logs')
    const params = {
      processCode: 'order.approve',
      status: 'success' as const,
      keyword: 'tenant-a',
      startTime: '2026-07-05T00:00:00.000Z',
      endTime: '2026-07-05T01:00:00.000Z',
      invocationId: 'inv-1',
      namespace: 'default',
      requestedVersion: 'production',
      effectiveVersion: '1.0.0',
      routingSource: 'alias',
      routeAlias: 'production',
      routeRevision: 7,
      page: 1,
      pageSize: 20,
    }

    await expect(getLogs(params)).resolves.toEqual(apiResponse)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/execution-logs', { params })
  })

  it('calls real deployment lifecycle endpoints in real mode', async () => {
    const deployment = {
      id: 'deploy-real-1',
      processCode: 'real-flow',
      version: '1.0.0',
      alias: 'production',
      status: 'in_progress',
      strategy: 'canary',
      canaryWeightBps: 1_000,
      createdAt: '2026-03-30T00:00:00.000Z',
      createdBy: 'tester',
    }
    mockApiClient.post
      .mockResolvedValueOnce(deployment)
      .mockResolvedValueOnce({ ...deployment, status: 'completed' })
      .mockResolvedValueOnce({ ...deployment, status: 'aborted' })
      .mockResolvedValueOnce({ ...deployment, status: 'completed', canaryWeightBps: undefined })
    mockApiClient.put.mockResolvedValue({ ...deployment, canaryWeightBps: 3_000 })

    const {
      createDeployment,
      abortCanary,
      rollbackDeployment,
      updateCanaryWeightBps,
      promoteCanary,
    } = await import('../deployments')
    const request = {
      processCode: 'real-flow',
      version: '1.0.0',
      alias: 'production' as const,
      strategy: 'canary' as const,
      canaryWeightBps: 1_000,
      idempotencyKey: 'create-key',
      expectedRouteRevision: 4,
    }

    await expect(createDeployment(request)).resolves.toEqual(deployment)
    await expect(updateCanaryWeightBps('deploy-real-1', 3_000, 2)).resolves.toMatchObject({
      canaryWeightBps: 3_000,
    })
    await expect(rollbackDeployment('deploy-real-1', 5, 'rollback-key')).resolves.toMatchObject({
      status: 'completed',
    })
    await expect(
      abortCanary('deploy-real-1', 3, 'health threshold breached')
    ).resolves.toMatchObject({ status: 'aborted' })
    await expect(promoteCanary('deploy-real-1', 3)).resolves.toMatchObject({ status: 'completed' })
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      '/api/deployments',
      {
        processCode: 'real-flow',
        version: '1.0.0',
        alias: 'production',
        strategy: 'canary',
        canaryWeightBps: 1_000,
        expectedRouteRevision: 4,
      },
      { headers: { 'Idempotency-Key': 'create-key' } }
    )
    expect(mockApiClient.put).toHaveBeenCalledWith('/api/deployments/deploy-real-1/canary', {
      weightBps: 3_000,
      expectedRevision: 2,
    })
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      '/api/deployments/deploy-real-1/rollback',
      { expectedRouteRevision: 5 },
      { headers: { 'Idempotency-Key': 'rollback-key' } }
    )
    expect(mockApiClient.post).toHaveBeenNthCalledWith(3, '/api/deployments/deploy-real-1/abort', {
      expectedRevision: 3,
      reason: 'health threshold breached',
    })
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      4,
      '/api/deployments/deploy-real-1/promote',
      { expectedRevision: 3 }
    )
  })

  it('calls real canary health endpoint in real mode', async () => {
    const apiResponse = {
      deployment: {
        id: 'deploy-real-1',
        processCode: 'real-flow',
        version: '1.0.0',
        alias: 'production',
        status: 'in_progress',
        createdAt: '2026-03-30T00:00:00.000Z',
        createdBy: 'tester',
      },
      decision: 'healthy',
      reason: 'ok',
      canary: { version: '1.0.0', samples: 20, failures: 0, errorRate: 0, p95DurationMs: 42 },
      baseline: { version: '0.9.0', samples: 100, failures: 1, errorRate: 0.01, p95DurationMs: 50 },
      thresholds: {
        lookbackMs: 600000,
        minCanarySamples: 20,
        maxCanaryErrorRate: 0.05,
        maxCanaryP95Ms: 0,
      },
    }
    mockApiClient.post.mockResolvedValue(apiResponse)

    const { evaluateCanaryHealth } = await import('../deployments')
    const request = { minCanarySamples: 20 }

    await expect(evaluateCanaryHealth('deploy-real-1', request)).resolves.toEqual(apiResponse)
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/api/deployments/deploy-real-1/canary/evaluate',
      request
    )
  })

  it('returns mock canary health only in explicit operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const { evaluateCanaryHealth } = await import('../deployments')

    const result = await evaluateCanaryHealth('deploy-001', { minCanarySamples: 3 })
    expect(result.decision).toBe('healthy')
    expect(result.thresholds.minCanarySamples).toBe(3)
    expect(mockApiClient.post).not.toHaveBeenCalled()
  })

  it('calls real deployment control endpoints in real mode', async () => {
    const health = {
      status: 'UP',
      pendingCount: 0,
      processingCount: 0,
      expiredClaimCount: 0,
      failedCount: 0,
      dispatcherRunning: true,
      outboxStateAvailable: true,
      checkedAt: '2026-07-05T00:00:00.000Z',
    }
    mockApiClient.get.mockResolvedValue(health)
    mockApiClient.post.mockResolvedValueOnce({
      requeued: 2,
      requeuedAt: '2026-07-05T00:00:01.000Z',
      health,
    })

    const { getDeploymentControlHealth, requeueDeploymentDeadLetters } =
      await import('../deployments')

    await expect(getDeploymentControlHealth()).resolves.toEqual(health)
    await expect(requeueDeploymentDeadLetters()).resolves.toMatchObject({ requeued: 2 })
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/deployment-control/health')
    expect(mockApiClient.post).toHaveBeenCalledWith('/api/deployment-control/dead-letters/requeue')
  })

  it('returns mock deployment control only in explicit operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const { getDeploymentControlHealth, requeueDeploymentDeadLetters } =
      await import('../deployments')

    await expect(getDeploymentControlHealth()).resolves.toMatchObject({ status: 'UP' })
    await expect(requeueDeploymentDeadLetters()).resolves.toMatchObject({ requeued: 0 })
    expect(mockApiClient.get).not.toHaveBeenCalled()
    expect(mockApiClient.post).not.toHaveBeenCalled()
  })

  it('propagates real-mode log api errors instead of falling back to mock data', async () => {
    const apiError = new Error('logs request failed')
    mockApiClient.get.mockRejectedValue(apiError)

    const { getLogs } = await import('../logs')

    await expect(getLogs({ page: 1, pageSize: 10 })).rejects.toThrow('logs request failed')
  })

  it('uses the blob channel for process XML export in real mode', async () => {
    const blob = new Blob(['<xml />'], { type: 'application/xml' })
    mockApiClient.getBlob.mockResolvedValue(blob)

    const { exportProcessXml } = await import('@/shared/api/processes')

    await expect(exportProcessXml('flow-real-1')).resolves.toBe(blob)
    expect(mockApiClient.getBlob).toHaveBeenCalledWith('/api/processes/flow-real-1/export')
  })

  it('calls real async invocation endpoints in real mode', async () => {
    const task = {
      invocationId: 'inv-async-1',
      processCode: 'payment.approve',
      status: 'queued',
      currentAttemptCount: 0,
      totalAttemptCount: 0,
      redriveCount: 0,
      maxAttempts: 3,
      retryDelayMs: 1000,
      createdAt: '2026-07-04T00:00:00.000Z',
      updatedAt: '2026-07-04T00:00:00.000Z',
    }
    mockApiClient.post
      .mockResolvedValueOnce(task)
      .mockResolvedValueOnce({ ...task, status: 'queued' })
    mockApiClient.get.mockResolvedValue({ ...task, status: 'succeeded' })

    const { submitAsyncInvocation, getAsyncInvocation, requeueAsyncInvocation } =
      await import('@/shared/api/processes')
    const request = {
      invocationId: 'inv-async-1',
      params: { amount: 12 },
      routing: { alias: 'production', routingKey: 'tenant-a' },
      maxAttempts: 3,
    }

    await expect(submitAsyncInvocation('payment.approve', request)).resolves.toEqual(task)
    await expect(getAsyncInvocation('inv-async-1')).resolves.toMatchObject({ status: 'succeeded' })
    await expect(requeueAsyncInvocation('inv-async-1')).resolves.toMatchObject({ status: 'queued' })
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      1,
      '/api/processes/payment.approve/async-invocations',
      request
    )
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/async-invocations/inv-async-1')
    expect(mockApiClient.post).toHaveBeenNthCalledWith(
      2,
      '/api/async-invocations/inv-async-1/requeue'
    )
  })

  it('calls real async invocation list endpoint in real mode', async () => {
    const apiResponse = {
      data: [],
      total: 0,
      page: 1,
      pageSize: 10,
    }
    mockApiClient.get.mockResolvedValue(apiResponse)

    const { listAsyncInvocations } = await import('@/shared/api/processes')
    const params = {
      status: 'dead_letter' as const,
      processCode: 'payment.fail',
      page: 1,
      pageSize: 10,
    }

    await expect(listAsyncInvocations(params)).resolves.toEqual(apiResponse)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/async-invocations', { params })
  })

  it('calls the bounded async invocation attempt endpoint in real mode', async () => {
    const apiResponse = {
      data: [],
      hasMore: false,
    }
    mockApiClient.get.mockResolvedValue(apiResponse)

    const { listAsyncInvocationAttempts } = await import('@/shared/api/processes')
    const params = { afterSequence: 7, limit: 20 }

    await expect(listAsyncInvocationAttempts('inv-async-1', params)).resolves.toEqual(apiResponse)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/async-invocations/inv-async-1/attempts', {
      params,
    })
  })

  it('calls real async invocation ops endpoints in real mode', async () => {
    const health = {
      status: 'degraded',
      queuedCount: 1,
      readyQueuedCount: 1,
      oldestReadyAgeMs: 2500,
      delayedQueuedCount: 0,
      runningCount: 0,
      succeededCount: 10,
      deadLetterCount: 2,
      expiredRunningCount: 0,
      localRunningCount: 0,
      dispatchedCount: 0,
      workerId: 'worker-1',
      leaseDurationMs: 30000,
      dispatchBatchSize: 50,
      checkedAt: '2026-07-05T00:00:00.000Z',
    }
    const requeue = {
      requeued: 2,
      requeuedAt: '2026-07-05T00:01:00.000Z',
      processCode: 'payment.fail',
      limit: 50,
      invocationIds: ['inv-1', 'inv-2'],
      health,
    }
    mockApiClient.get.mockResolvedValueOnce(health)
    mockApiClient.post.mockResolvedValueOnce(requeue)

    const { getAsyncInvocationHealth, requeueAsyncInvocationDeadLetters } =
      await import('@/shared/api/processes')

    await expect(getAsyncInvocationHealth()).resolves.toEqual(health)
    await expect(
      requeueAsyncInvocationDeadLetters({ processCode: 'payment.fail', limit: 50 })
    ).resolves.toEqual(requeue)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/async-invocations/health')
    expect(mockApiClient.post).toHaveBeenCalledWith('/api/async-invocations/dead-letters/requeue', {
      processCode: 'payment.fail',
      limit: 50,
    })
  })

  it('calls real deploy runtime diagnostics endpoint in real mode', async () => {
    const diagnostics = {
      timestamp: '2026-07-05T00:00:00.000Z',
      available: true,
      started: true,
      topology: 'distributed',
      desiredAliasCount: 1,
      localReadyAliasCount: 1,
      pendingAliasCount: 0,
      failedAliasCount: 0,
      aliases: [
        {
          namespace: 'default',
          code: 'order-approval',
          alias: 'production',
          desiredRevision: 7,
          desiredDeleted: false,
          localReadyRevision: 7,
          localReadyDeleted: false,
          state: 'local_ready',
        },
      ],
      inflightCount: 0,
      inflightCapacity: 1000,
      inflightAvailablePermits: 1000,
      failureBackoffMs: 300000,
      retainedRuntimeCount: 1,
      inflightVersions: [],
      demandedVersions: [
        {
          namespace: 'default',
          code: 'order-approval',
          version: '1.0.9',
          id: 'default/order-approval@1.0.9',
        },
      ],
      pendingReleaseVersions: [],
      backedOffVersions: [],
      deployedVersions: [{ namespace: 'default', code: 'order-approval', versions: ['1.0.9'] }],
    }
    mockApiClient.get.mockResolvedValueOnce(diagnostics)

    const { getDeployRuntimeDiagnostics } = await import('../monitoring')

    await expect(getDeployRuntimeDiagnostics()).resolves.toEqual(diagnostics)
    expect(mockApiClient.get).toHaveBeenCalledWith('/api/monitoring/deploy-runtime')
  })

  it('applies one explicit time range to every durable monitoring aggregate', async () => {
    mockApiClient.get.mockResolvedValue([])
    const {
      getExecutionTrends,
      getMetrics,
      getRecentErrors,
      getTopProcesses,
      getVersionDistribution,
    } = await import('../monitoring')

    await getMetrics('7d')
    await getExecutionTrends({ timeRange: '7d', interval: '1h' })
    await getTopProcesses({ timeRange: '7d', limit: 5 })
    await getRecentErrors({ timeRange: '7d', limit: 6 })
    await getVersionDistribution({ processCode: 'order.approve', timeRange: '7d', limit: 7 })

    expect(mockApiClient.get).toHaveBeenNthCalledWith(1, '/api/monitoring/metrics', {
      params: { timeRange: '7d' },
    })
    expect(mockApiClient.get).toHaveBeenNthCalledWith(2, '/api/monitoring/trends', {
      params: { timeRange: '7d', interval: '1h' },
    })
    expect(mockApiClient.get).toHaveBeenNthCalledWith(3, '/api/monitoring/top-processes', {
      params: { timeRange: '7d', limit: 5 },
    })
    expect(mockApiClient.get).toHaveBeenNthCalledWith(4, '/api/monitoring/errors', {
      params: { timeRange: '7d', limit: 6 },
    })
    expect(mockApiClient.get).toHaveBeenNthCalledWith(5, '/api/monitoring/version-distribution', {
      params: { processCode: 'order.approve', timeRange: '7d', limit: 7 },
    })
  })

  it('returns mock deploy runtime diagnostics only in explicit operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const { getDeployRuntimeDiagnostics } = await import('../monitoring')

    const diagnostics = await getDeployRuntimeDiagnostics()
    expect(diagnostics.available).toBe(true)
    if (!diagnostics.available) throw new Error('expected available diagnostics')
    expect(diagnostics.demandedVersions.length).toBeGreaterThan(0)
    expect(diagnostics.backedOffVersions[0].reason).toBeTruthy()
    expect(diagnostics.aliases.some((alias) => alias.state === 'failed')).toBe(true)
    expect(mockApiClient.get).not.toHaveBeenCalled()
  })

  it('keeps async invocation usable in operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const {
      submitAsyncInvocation,
      getAsyncInvocation,
      listAsyncInvocationAttempts,
      listAsyncInvocations,
      requeueAsyncInvocationDeadLetters,
    } = await import('@/shared/api/processes')

    const submitted = await submitAsyncInvocation('payment-process-tbbpm', {
      invocationId: 'inv-mock-async-1',
      routing: { version: '2.0.1' },
    })
    const fetched = await getAsyncInvocation('inv-mock-async-1')

    expect(submitted.status).toBe('queued')
    expect(submitted.maxAttempts).toBe(1)
    expect(fetched.invocationId).toBe('inv-mock-async-1')
    expect(fetched.totalAttemptCount).toBe(1)
    expect(fetched.response?.routing?.effectiveVersion).toBe('2.0.1')
    await expect(listAsyncInvocationAttempts('inv-mock-async-1')).resolves.toMatchObject({
      data: [
        {
          invocationId: 'inv-mock-async-1',
          sequence: 1,
          attemptNumber: 1,
          outcome: 'succeeded',
          disposition: 'succeeded',
        },
      ],
      hasMore: false,
    })
    await expect(
      listAsyncInvocationAttempts('inv-mock-async-1', { afterSequence: -1 })
    ).rejects.toThrow('afterSequence must be a non-negative safe integer')
    await expect(
      listAsyncInvocations({ processCode: 'payment-process-tbbpm' })
    ).resolves.toMatchObject({
      total: 1,
      page: 1,
      pageSize: 20,
    })
    await expect(listAsyncInvocations({ page: 0 })).rejects.toThrow(
      'page must be greater than or equal to 1'
    )
    await expect(requeueAsyncInvocationDeadLetters({ limit: 501 })).rejects.toThrow(
      'limit must be between 1 and 500'
    )
    expect(mockApiClient.post).not.toHaveBeenCalled()
    expect(mockApiClient.get).not.toHaveBeenCalled()
  })

  it('returns a mock blob for process XML export in operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const { exportProcessXml } = await import('@/shared/api/processes')

    const result = await exportProcessXml('order-approval-bpmn')
    expect(result).toBeInstanceOf(Blob)
    expect(mockApiClient.getBlob).not.toHaveBeenCalled()
  })

  it('uses blob channel for log export in real mode', async () => {
    const blob = new Blob(['id,status'], { type: 'text/csv' })
    mockApiClient.postBlob.mockResolvedValue(blob)

    const { exportLogs } = await import('../logs')

    await expect(exportLogs({ keyword: 'order' })).resolves.toBe(blob)
    expect(mockApiClient.postBlob).toHaveBeenCalledWith('/api/execution-logs/export', {
      keyword: 'order',
    })
  })

  it('returns mock csv blob for log export in operate mock mode', async () => {
    mockIsOperateMockMode.mockReturnValue(true)

    const { exportLogs } = await import('../logs')

    const result = await exportLogs({ status: 'success' })
    expect(result).toBeInstanceOf(Blob)
    expect(mockApiClient.postBlob).not.toHaveBeenCalled()
  })

  it('neutralizes spreadsheet formulas in mock csv exports', async () => {
    mockIsOperateMockMode.mockReturnValue(true)
    const [{ exportLogs }, { mockLogs }] = await Promise.all([
      import('../logs'),
      import('../mockLogData'),
    ])
    const failedLog = mockLogs.find((log) => log.status === 'failed')
    if (!failedLog) throw new Error('expected a failed mock execution log')
    failedLog.errorMessage = ' \t=HYPERLINK("https://example.invalid")'

    const result = await exportLogs({ processCode: failedLog.processCode })
    const csv = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onerror = () => reject(reader.error)
      reader.onload = () => resolve(String(reader.result))
      reader.readAsText(result)
    })

    expect(csv).toContain('"\' \t=HYPERLINK(""https://example.invalid"")"')
  })

  it('returns one bounded purge result in real mode', async () => {
    const apiResponse = {
      deletedCount: 3,
      hasMore: true,
      purgedAt: '2026-07-01T12:00:00.000Z',
    }
    mockApiClient.post.mockResolvedValue(apiResponse)

    const { purgeLogs } = await import('../logs')

    await expect(purgeLogs({ before: '2026-01-01T00:00:00.000Z' })).resolves.toEqual(apiResponse)
    expect(mockApiClient.post).toHaveBeenCalledWith('/api/execution-logs/purge', {
      before: '2026-01-01T00:00:00.000Z',
    })
  })
})
