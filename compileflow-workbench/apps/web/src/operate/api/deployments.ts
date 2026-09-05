import axios from 'axios'

import { getMockDeployments, mockDeployments } from './mockDeploymentData'

import apiClient from '@/shared/api/client'
import { isOperateMockMode } from '@/shared/config/buildConfig'
import type {
  CanaryHealthEvaluationRequest,
  CanaryHealthEvaluationResponse,
  Deployment,
  DeploymentControlHealth,
  DeploymentEvent,
  DeploymentListParams,
  DeploymentListResponse,
  DeploymentRequest,
  DeploymentRoute,
  RequeueDeploymentDeadLettersResponse,
} from '@/shared/contracts'

export async function getDeployments(
  params?: DeploymentListParams
): Promise<DeploymentListResponse> {
  if (isOperateMockMode()) return Promise.resolve(getMockDeployments(params))
  return apiClient.get<DeploymentListResponse>('/api/deployments', { params })
}

export async function getDeployment(id: string): Promise<Deployment> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    return found
      ? Promise.resolve(found)
      : Promise.reject(new Error(`Deployment ${id} not found in mock data`))
  }
  return apiClient.get<Deployment>(`/api/deployments/${id}`)
}

export async function getDeploymentRoute(
  processCode: string,
  alias: Deployment['alias']
): Promise<DeploymentRoute | undefined> {
  if (isOperateMockMode()) {
    const latest = mockDeployments
      .filter((item) => item.processCode === processCode && item.alias === alias)
      .sort((left, right) => right.routeRevision - left.routeRevision)[0]
    if (!latest) return undefined
    return {
      processCode,
      alias,
      stableVersion:
        latest.status === 'in_progress'
          ? (latest.baselineVersion ?? latest.version)
          : latest.version,
      candidateVersion: latest.status === 'in_progress' ? latest.version : undefined,
      candidateWeightBps: latest.status === 'in_progress' ? latest.canaryWeightBps : undefined,
      revision: latest.routeRevision,
      updatedBy: latest.createdBy,
      updatedAt: Date.parse(latest.deployedAt ?? latest.createdAt),
    }
  }
  try {
    return await apiClient.get<DeploymentRoute>('/api/deployment-routes', {
      params: { processCode, alias },
    })
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 404) return undefined
    throw error
  }
}

export async function createDeployment(request: DeploymentRequest): Promise<Deployment> {
  if (isOperateMockMode()) {
    const prior = mockDeployments
      .filter((item) => item.processCode === request.processCode && item.alias === request.alias)
      .sort((left, right) => right.routeRevision - left.routeRevision)[0]
    const isCanary = request.strategy === 'canary'
    const baselineVersion = isCanary
      ? prior?.status === 'in_progress'
        ? (prior.baselineVersion ?? prior.version)
        : prior?.version
      : undefined
    if (isCanary && !baselineVersion) {
      return Promise.reject(
        new Error(
          `Canary deploy for ${request.processCode}@${request.alias} requires an existing baseline route`
        )
      )
    }
    const mock: Deployment = {
      id: `deploy-mock-${Date.now()}`,
      processCode: request.processCode,
      version: request.version,
      baselineVersion,
      alias: request.alias,
      operation: 'deploy',
      status: isCanary ? 'in_progress' : 'completed',
      strategy: request.strategy ?? 'all_at_once',
      canaryWeightBps: isCanary ? (request.canaryWeightBps ?? 1_000) : undefined,
      createdAt: new Date().toISOString(),
      deployedAt: new Date().toISOString(),
      createdBy: 'mock-user',
      notes: request.notes,
      revision: 1,
      baseRouteRevision: request.expectedRouteRevision,
      routeRevision: request.expectedRouteRevision + 1,
    }
    mockDeployments.unshift(mock)
    return Promise.resolve(mock)
  }
  const { idempotencyKey, ...body } = request
  return apiClient.post<Deployment>('/api/deployments', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  })
}

export async function rollbackDeployment(
  id: string,
  expectedRouteRevision: number,
  idempotencyKey: string
): Promise<Deployment> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    if (!found) return Promise.reject(new Error(`Deployment ${id} not found`))
    if (!found.baselineVersion) {
      return Promise.reject(new Error(`Deployment ${id} has no rollback baseline`))
    }
    const now = new Date().toISOString()
    const rollback: Deployment = {
      ...found,
      id: `rollback-mock-${Date.now()}`,
      version: found.baselineVersion,
      baselineVersion: found.version,
      operation: 'rollback',
      status: 'completed',
      strategy: 'all_at_once',
      canaryWeightBps: undefined,
      revision: 1,
      baseRouteRevision: expectedRouteRevision,
      routeRevision: expectedRouteRevision + 1,
      createdAt: now,
      deployedAt: now,
      notes: `Rollback of ${id}`,
    }
    // Persist so subsequent route/list reads stay consistent with detail mutations.
    mockDeployments.unshift(rollback)
    return Promise.resolve(rollback)
  }
  return apiClient.post<Deployment>(
    `/api/deployments/${id}/rollback`,
    { expectedRouteRevision },
    { headers: { 'Idempotency-Key': idempotencyKey } }
  )
}

export async function abortCanary(
  id: string,
  expectedRevision: number,
  reason?: string
): Promise<Deployment> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    if (!found) return Promise.reject(new Error(`Deployment ${id} not found`))
    found.status = 'aborted'
    found.canaryWeightBps = undefined
    found.revision = found.revision + 1
    found.routeRevision = found.routeRevision + 1
    found.deployedAt = new Date().toISOString()
    return Promise.resolve({ ...found })
  }
  return apiClient.post<Deployment>(`/api/deployments/${id}/abort`, {
    expectedRevision,
    reason,
  })
}

export async function updateCanaryWeightBps(
  id: string,
  weightBps: number,
  expectedRevision: number
): Promise<Deployment> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    if (!found) return Promise.reject(new Error(`Deployment ${id} not found`))
    found.canaryWeightBps = weightBps
    found.revision = found.revision + 1
    found.routeRevision = found.routeRevision + 1
    return Promise.resolve({ ...found })
  }
  return apiClient.put<Deployment>(`/api/deployments/${id}/canary`, {
    weightBps,
    expectedRevision,
  })
}

export async function promoteCanary(id: string, expectedRevision: number): Promise<Deployment> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    if (!found) return Promise.reject(new Error(`Deployment ${id} not found`))
    found.status = 'completed'
    found.canaryWeightBps = undefined
    found.revision = found.revision + 1
    found.routeRevision = found.routeRevision + 1
    return Promise.resolve({ ...found })
  }
  return apiClient.post<Deployment>(`/api/deployments/${id}/promote`, { expectedRevision })
}

export async function evaluateCanaryHealth(
  id: string,
  request: CanaryHealthEvaluationRequest = {}
): Promise<CanaryHealthEvaluationResponse> {
  if (isOperateMockMode()) {
    const found = mockDeployments.find((d) => d.id === id)
    if (!found) return Promise.reject(new Error(`Deployment ${id} not found`))
    const thresholds = {
      lookbackMs: request.lookbackMs ?? 600_000,
      minCanarySamples: request.minCanarySamples ?? 20,
      maxCanaryErrorRate: request.maxCanaryErrorRate ?? 0.05,
      maxCanaryP95Ms: request.maxCanaryP95Ms ?? 0,
    }
    return Promise.resolve({
      deployment: found,
      metricsScope: 'workbench_server',
      metricsSource: 'execution_logs',
      decision: 'healthy',
      reason: 'Canary metrics are within configured thresholds.',
      canary: {
        version: found.version,
        samples: Math.max(thresholds.minCanarySamples, 24),
        failures: 0,
        errorRate: 0,
        p95DurationMs: 42,
      },
      baseline: {
        version: found.baselineVersion ?? 'baseline',
        samples: 120,
        failures: 1,
        errorRate: 1 / 120,
        p95DurationMs: 48,
      },
      thresholds,
    })
  }
  return apiClient.post<CanaryHealthEvaluationResponse>(
    `/api/deployments/${id}/canary/evaluate`,
    request
  )
}

export async function getDeploymentControlHealth(): Promise<DeploymentControlHealth> {
  if (isOperateMockMode()) return Promise.resolve(mockDeploymentControlHealth())
  return apiClient.get<DeploymentControlHealth>('/api/deployment-control/health')
}

export async function requeueDeploymentDeadLetters(): Promise<RequeueDeploymentDeadLettersResponse> {
  if (isOperateMockMode()) {
    return Promise.resolve({
      requeued: 0,
      requeuedAt: new Date().toISOString(),
      health: mockDeploymentControlHealth(),
    })
  }
  return apiClient.post<RequeueDeploymentDeadLettersResponse>(
    '/api/deployment-control/dead-letters/requeue'
  )
}

export async function getDeploymentEvents(id: string): Promise<DeploymentEvent[]> {
  if (isOperateMockMode()) {
    const deployment = mockDeployments.find((item) => item.id === id)
    if (!deployment) return Promise.resolve([])
    return Promise.resolve([
      {
        id: 1,
        sequence: 1,
        type: 'COMPLETED',
        fromPhase: null,
        toPhase: 'completed',
        actor: 'mock-user',
        reason: null,
        timestamp: deployment.deployedAt ?? deployment.createdAt,
      },
    ])
  }
  return apiClient.get<DeploymentEvent[]>(`/api/deployments/${id}/events`)
}

function mockDeploymentControlHealth(): DeploymentControlHealth {
  return {
    status: 'UP',
    pendingCount: 0,
    processingCount: 0,
    expiredClaimCount: 0,
    failedCount: 0,
    dispatcherRunning: true,
    outboxStateAvailable: true,
    checkedAt: new Date().toISOString(),
  }
}
