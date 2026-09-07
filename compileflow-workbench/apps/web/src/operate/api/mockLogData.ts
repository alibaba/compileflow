import type { ExecutionLog, LogFilterParams, LogListResponse } from '@/shared/contracts'

type MockExecutionLog = Omit<
  ExecutionLog,
  'invocationId' | 'callDepth' | 'traceId' | 'modelType' | 'namespace'
>

const MOCK_PROCESS_VERSIONS: Record<string, string> = {
  'order-approval-bpmn': '1.2.0',
  'payment-process-tbbpm': '2.0.1',
  'user-registration-bpmn': '1.0.5',
  'refund-workflow-tbbpm': '1.0.9',
}

const baseMockLogs: MockExecutionLog[] = [
  {
    id: 'log-001',
    processCode: 'order-approval-bpmn',
    status: 'success',
    startTime: '2026-02-10T09:15:00Z',
    endTime: '2026-02-10T09:15:32Z',
    duration: 32000,
  },
  {
    id: 'log-002',
    processCode: 'payment-process-tbbpm',
    status: 'success',
    startTime: '2026-02-10T09:20:15Z',
    endTime: '2026-02-10T09:20:18Z',
    duration: 3000,
  },
  {
    id: 'log-003',
    processCode: 'user-registration-bpmn',
    status: 'failed',
    startTime: '2026-02-10T09:25:00Z',
    endTime: '2026-02-10T09:25:05Z',
    duration: 5000,
    errorCode: 'EXECUTION_TIMEOUT',
    errorMessage: 'Email verification timeout',
  },
  {
    id: 'log-004',
    processCode: 'order-approval-bpmn',
    status: 'success',
    startTime: '2026-02-10T09:30:00Z',
    endTime: '2026-02-10T09:30:04Z',
    duration: 4000,
  },
  {
    id: 'log-005',
    processCode: 'refund-workflow-tbbpm',
    status: 'success',
    startTime: '2026-02-10T09:35:10Z',
    endTime: '2026-02-10T09:35:25Z',
    duration: 15000,
  },
  {
    id: 'log-006',
    processCode: 'inventory-check-bpmn',
    status: 'success',
    startTime: '2026-02-10T09:40:00Z',
    endTime: '2026-02-10T09:40:02Z',
    duration: 2000,
  },
  {
    id: 'log-007',
    processCode: 'notification-flow-tbbpm',
    status: 'failed',
    startTime: '2026-02-10T09:45:00Z',
    endTime: '2026-02-10T09:45:10Z',
    duration: 10000,
    errorCode: 'UPSTREAM_UNAVAILABLE',
    errorMessage: 'SMS service unavailable',
  },
  {
    id: 'log-008',
    processCode: 'coupon-distribute-tbbpm',
    status: 'success',
    startTime: '2026-02-10T09:50:00Z',
    endTime: '2026-02-10T09:50:45Z',
    duration: 45000,
  },
  {
    id: 'log-009',
    processCode: 'order-cancel-bpmn',
    status: 'success',
    startTime: '2026-02-10T10:00:00Z',
    endTime: '2026-02-10T10:00:18Z',
    duration: 18000,
  },
  {
    id: 'log-010',
    processCode: 'payment-process-tbbpm',
    status: 'failed',
    startTime: '2026-02-10T10:05:00Z',
    endTime: '2026-02-10T10:05:03Z',
    duration: 3000,
    errorCode: 'INSUFFICIENT_BALANCE',
    errorMessage: 'Insufficient balance',
  },
  {
    id: 'log-011',
    processCode: 'user-registration-bpmn',
    status: 'success',
    startTime: '2026-02-10T10:10:00Z',
    endTime: '2026-02-10T10:10:15Z',
    duration: 15000,
  },
  {
    id: 'log-012',
    processCode: 'inventory-check-bpmn',
    status: 'failed',
    startTime: '2026-02-10T10:15:00Z',
    endTime: '2026-02-10T10:15:01Z',
    duration: 1000,
    errorCode: 'INSUFFICIENT_INVENTORY',
    errorMessage: 'Insufficient inventory',
  },
  {
    id: 'log-013',
    processCode: 'order-approval-bpmn',
    status: 'success',
    startTime: '2026-02-10T10:20:00Z',
    endTime: '2026-02-10T10:21:20Z',
    duration: 80000,
  },
  {
    id: 'log-014',
    processCode: 'notification-flow-tbbpm',
    status: 'success',
    startTime: '2026-02-10T10:25:00Z',
    endTime: '2026-02-10T10:25:05Z',
    duration: 5000,
  },
  {
    id: 'log-015',
    processCode: 'refund-workflow-tbbpm',
    status: 'success',
    startTime: '2026-02-10T10:30:00Z',
    endTime: '2026-02-10T10:30:07Z',
    duration: 7000,
  },
  {
    id: 'log-016',
    processCode: 'payment-process-tbbpm',
    status: 'success',
    startTime: '2026-02-10T10:35:00Z',
    endTime: '2026-02-10T10:35:02Z',
    duration: 2000,
  },
  {
    id: 'log-017',
    processCode: 'coupon-distribute-tbbpm',
    status: 'success',
    startTime: '2026-02-09T14:00:00Z',
    endTime: '2026-02-09T14:02:30Z',
    duration: 150000,
  },
  {
    id: 'log-018',
    processCode: 'order-approval-bpmn',
    status: 'success',
    startTime: '2026-02-09T15:30:00Z',
    endTime: '2026-02-09T15:30:45Z',
    duration: 45000,
  },
  {
    id: 'log-019',
    processCode: 'user-registration-bpmn',
    status: 'failed',
    startTime: '2026-02-09T16:00:00Z',
    endTime: '2026-02-09T16:00:08Z',
    duration: 8000,
    errorCode: 'EMAIL_ALREADY_REGISTERED',
    errorMessage: 'Email already registered',
  },
  {
    id: 'log-020',
    processCode: 'inventory-check-bpmn',
    status: 'success',
    startTime: '2026-02-09T17:00:00Z',
    endTime: '2026-02-09T17:00:01Z',
    duration: 1000,
  },
]

export const mockLogs: ExecutionLog[] = baseMockLogs.map((log) => {
  const sequence = Number.parseInt(log.id.slice(-3), 10)
  const effectiveVersion = MOCK_PROCESS_VERSIONS[log.processCode] ?? '1.0.0'
  const directVersion = sequence % 4 === 0

  return {
    ...log,
    invocationId: `inv-${log.id}`,
    callDepth: 0,
    traceId: `trace-${log.id}`,
    modelType: log.processCode.endsWith('-bpmn') ? 'BPMN' : 'TBBPM',
    namespace: 'default',
    requestedVersion: directVersion ? effectiveVersion : undefined,
    effectiveVersion,
    routingSource: directVersion ? 'version' : 'alias',
    routeAlias: directVersion ? undefined : 'production',
    routeRevision: directVersion ? undefined : (sequence % 12) + 1,
    sourceDigest: sequence.toString(16).padStart(64, '0'),
  }
})

function matchesKeyword(log: ExecutionLog, keyword: string): boolean {
  return [
    log.processCode,
    log.invocationId,
    log.parentInvocationId,
    log.traceId,
    log.sourceDigest,
    log.namespace,
    log.requestedVersion,
    log.effectiveVersion,
    log.routingSource,
    log.routeAlias,
    log.errorCode,
    log.errorMessage,
  ].some((value) => value?.toLowerCase().includes(keyword))
}

export function getMockLogs(params: LogFilterParams = {}): LogListResponse {
  const { page = 1, pageSize = 10 } = params
  const filtered = mockLogs
    .filter((log) => matchesFilters(log, params))
    .sort((a, b) => new Date(b.startTime).getTime() - new Date(a.startTime).getTime())

  const start = (page - 1) * pageSize
  return {
    data: filtered.slice(start, start + pageSize),
    total: filtered.length,
    page,
    pageSize,
  }
}

function matchesFilters(log: ExecutionLog, params: LogFilterParams): boolean {
  const startedAt = Date.parse(log.startTime)
  if (params.startTime && startedAt < Date.parse(params.startTime)) return false
  if (params.endTime && startedAt > Date.parse(params.endTime)) return false
  const exactMatches: Array<[unknown, unknown]> = [
    [params.processCode, log.processCode],
    [params.invocationId, log.invocationId],
    [params.parentInvocationId, log.parentInvocationId],
    [params.traceId, log.traceId],
    [params.callDepth, log.callDepth],
    [params.namespace, log.namespace],
    [params.requestedVersion, log.requestedVersion],
    [params.effectiveVersion, log.effectiveVersion],
    [params.routingSource, log.routingSource],
    [params.routeAlias, log.routeAlias],
    [params.routeRevision, log.routeRevision],
  ]
  if (exactMatches.some(([expected, actual]) => expected != null && expected !== actual)) {
    return false
  }
  if (params.status && params.status !== 'all' && params.status !== log.status) return false
  if (!params.keyword) return true
  return matchesKeyword(log, params.keyword.toLowerCase())
}
