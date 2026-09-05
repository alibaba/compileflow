// Eliminated all local duplicate type definitions. MonitoringMetrics, ExecutionTrend,
// TopProcessStats, and ErrorSummary are now imported from the contract layer — the single
// source of truth in runtimeContract.ts.

import apiClient from '@/shared/api/client'
import { isOperateMockMode } from '@/shared/config/buildConfig'
import type {
  DeployRuntimeDiagnostics,
  ErrorSummary,
  ExecutionTrend,
  MonitoringMetrics,
  MonitoringTimeRange,
  TopProcessStats,
  VersionDistributionStats,
} from '@/shared/contracts'

const MONITORING_RANGE_MS: Record<MonitoringTimeRange, number> = {
  '1h': 60 * 60_000,
  '6h': 6 * 60 * 60_000,
  '24h': 24 * 60 * 60_000,
  '7d': 7 * 24 * 60 * 60_000,
  '30d': 30 * 24 * 60 * 60_000,
}

function getMockMetrics(timeRange: MonitoringTimeRange): MonitoringMetrics {
  const windowEnd = new Date()
  return {
    scope: 'workbench_server',
    totalExecutions: 1250,
    successExecutions: 1198,
    failedExecutions: 52,
    avgExecutionTime: 12,
    processesWithAliases: 8,
    timeRange,
    windowStart: new Date(windowEnd.getTime() - MONITORING_RANGE_MS[timeRange]).toISOString(),
    windowEnd: windowEnd.toISOString(),
    timestamp: windowEnd.toISOString(),
  }
}

function getMockTrends(): ExecutionTrend[] {
  return Array.from({ length: 12 }, (_, i) => ({
    time: new Date(Date.now() - (11 - i) * 3600_000).toISOString(),
    executions: 80 + ((i * 3) % 40),
    success: 75 + ((i * 2) % 35),
    failed: 1 + (i % 6),
  }))
}

function getMockTopProcesses(): TopProcessStats[] {
  return [
    { processCode: 'order-approval', executionCount: 420, avgDuration: 8, successRate: 98.5 },
    { processCode: 'payment-process', executionCount: 360, avgDuration: 15, successRate: 97.2 },
    { processCode: 'inventory-check', executionCount: 290, avgDuration: 5, successRate: 99.1 },
  ]
}

function getMockErrors(): ErrorSummary[] {
  return [
    {
      processCode: 'order-approval',
      errorCode: 'CF_COMPILE_002',
      errorMessage: 'Cannot find symbol: variable orderAmount',
      count: 12,
      lastOccurred: new Date(Date.now() - 3600_000).toISOString(),
    },
    {
      processCode: 'payment-process',
      errorCode: 'EXECUTION_FAILED',
      errorMessage: 'Connection timeout to payment gateway',
      count: 8,
      lastOccurred: new Date(Date.now() - 7200_000).toISOString(),
    },
  ]
}

function getMockVersionDistribution(): VersionDistributionStats[] {
  return [
    {
      processCode: 'order-approval',
      namespace: 'default',
      routeAlias: 'production',
      effectiveVersion: '1.0.8',
      routingSource: 'alias',
      executions: 950,
      success: 944,
      failed: 6,
      avgDuration: 9,
      successRate: 99.4,
    },
    {
      processCode: 'order-approval',
      namespace: 'default',
      routeAlias: 'production',
      effectiveVersion: '1.0.9',
      routingSource: 'alias',
      executions: 50,
      success: 48,
      failed: 2,
      avgDuration: 11,
      successRate: 96,
    },
  ]
}

function getMockDeployRuntimeDiagnostics(): DeployRuntimeDiagnostics {
  const now = new Date().toISOString()
  return {
    timestamp: now,
    available: true,
    started: true,
    topology: 'embedded',
    desiredAliasCount: 2,
    localReadyAliasCount: 2,
    pendingAliasCount: 0,
    failedAliasCount: 1,
    aliases: [
      {
        namespace: 'default',
        code: 'inventory-check',
        alias: 'production',
        desiredRevision: 4,
        desiredDeleted: false,
        localReadyRevision: 3,
        localReadyDeleted: false,
        state: 'failed',
        failureReason: 'Runtime installation failed',
      },
      {
        namespace: 'default',
        code: 'order-approval',
        alias: 'production',
        desiredRevision: 12,
        desiredDeleted: false,
        localReadyRevision: 12,
        localReadyDeleted: false,
        state: 'local_ready',
      },
    ],
    inflightCount: 1,
    inflightCapacity: 1000,
    inflightAvailablePermits: 999,
    failureBackoffMs: 300000,
    retainedRuntimeCount: 3,
    inflightVersions: [
      {
        namespace: 'default',
        code: 'payment-process',
        version: '1.1.0',
        id: 'default/payment-process@1.1.0',
      },
    ],
    demandedVersions: [
      {
        namespace: 'default',
        code: 'order-approval',
        version: '1.0.9',
        id: 'default/order-approval@1.0.9',
      },
      {
        namespace: 'default',
        code: 'order-approval',
        version: '1.0.8',
        id: 'default/order-approval@1.0.8',
      },
    ],
    pendingReleaseVersions: [],
    backedOffVersions: [
      {
        namespace: 'default',
        code: 'inventory-check',
        version: '2.0.1',
        id: 'default/inventory-check@2.0.1',
        reason: 'preflight failed',
        blockedUntil: new Date(Date.now() + 120000).toISOString(),
        remainingMs: 120000,
      },
    ],
    deployedVersions: [
      { namespace: 'default', code: 'order-approval', versions: ['1.0.8', '1.0.9'] },
      { namespace: 'default', code: 'payment-process', versions: ['1.0.0'] },
    ],
  }
}

export async function getMetrics(
  timeRange: MonitoringTimeRange = '24h'
): Promise<MonitoringMetrics> {
  if (isOperateMockMode()) return Promise.resolve(getMockMetrics(timeRange))
  return apiClient.get<MonitoringMetrics>('/api/monitoring/metrics', {
    params: { timeRange },
  })
}

export async function getExecutionTrends(params: {
  timeRange: MonitoringTimeRange
  interval?: '1m' | '5m' | '1h' | '1d'
}): Promise<ExecutionTrend[]> {
  if (isOperateMockMode()) return Promise.resolve(getMockTrends())
  return apiClient.get<ExecutionTrend[]>('/api/monitoring/trends', { params })
}

export async function getTopProcesses(
  params: { timeRange?: MonitoringTimeRange; limit?: number } = {}
): Promise<TopProcessStats[]> {
  const { timeRange = '24h', limit = 10 } = params
  if (isOperateMockMode()) return Promise.resolve(getMockTopProcesses().slice(0, limit))
  return apiClient.get<TopProcessStats[]>('/api/monitoring/top-processes', {
    params: { timeRange, limit },
  })
}

export async function getRecentErrors(
  params: { timeRange?: MonitoringTimeRange; limit?: number } = {}
): Promise<ErrorSummary[]> {
  const { timeRange = '24h', limit = 20 } = params
  if (isOperateMockMode()) return Promise.resolve(getMockErrors().slice(0, limit))
  return apiClient.get<ErrorSummary[]>('/api/monitoring/errors', {
    params: { timeRange, limit },
  })
}

export async function getVersionDistribution(
  params: {
    processCode?: string
    timeRange?: MonitoringTimeRange
    limit?: number
  } = {}
): Promise<VersionDistributionStats[]> {
  const resolvedParams = {
    ...params,
    timeRange: params.timeRange ?? '24h',
    limit: params.limit ?? 20,
  }
  if (isOperateMockMode())
    return Promise.resolve(getMockVersionDistribution().slice(0, resolvedParams.limit))
  return apiClient.get<VersionDistributionStats[]>('/api/monitoring/version-distribution', {
    params: resolvedParams,
  })
}

export async function getDeployRuntimeDiagnostics(): Promise<DeployRuntimeDiagnostics> {
  if (isOperateMockMode()) return Promise.resolve(getMockDeployRuntimeDiagnostics())
  return apiClient.get<DeployRuntimeDiagnostics>('/api/monitoring/deploy-runtime')
}
