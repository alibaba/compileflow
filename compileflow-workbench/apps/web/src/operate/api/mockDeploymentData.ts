import type { Deployment, DeploymentListParams, DeploymentListResponse } from '@/shared/contracts'

export const mockDeployments: Deployment[] = [
  {
    id: 'deploy-001',
    processCode: 'order-approval-bpmn',
    version: '1.2.0',
    alias: 'production',
    operation: 'deploy',
    status: 'completed',
    strategy: 'all_at_once',
    revision: 2,
    baseRouteRevision: 0,
    routeRevision: 1,
    deployedAt: '2026-02-08T14:30:00Z',
    createdAt: '2026-02-08T14:25:00Z',
    createdBy: 'admin',
    notes: 'Initial production route',
  },
  {
    id: 'deploy-002',
    processCode: 'payment-process-tbbpm',
    version: '2.0.1',
    baselineVersion: '2.0.0',
    alias: 'production',
    operation: 'deploy',
    status: 'in_progress',
    strategy: 'canary',
    canaryWeightBps: 2_000,
    revision: 2,
    baseRouteRevision: 7,
    routeRevision: 8,
    createdAt: '2026-02-09T16:40:00Z',
    createdBy: 'operator',
    notes: '20 percent canary',
  },
  {
    id: 'deploy-003',
    processCode: 'user-registration-bpmn',
    version: '1.0.5',
    baselineVersion: '1.0.4',
    alias: 'staging',
    operation: 'deploy',
    status: 'completed',
    strategy: 'all_at_once',
    revision: 2,
    baseRouteRevision: 3,
    routeRevision: 4,
    deployedAt: '2026-02-01T11:00:00Z',
    createdAt: '2026-02-01T10:55:00Z',
    createdBy: 'operator',
  },
  {
    id: 'deploy-004',
    processCode: 'refund-workflow-tbbpm',
    version: '1.0.9',
    baselineVersion: '1.1.0',
    alias: 'production',
    operation: 'rollback',
    status: 'completed',
    strategy: 'all_at_once',
    revision: 2,
    baseRouteRevision: 12,
    routeRevision: 13,
    deployedAt: '2026-02-10T16:00:00Z',
    createdAt: '2026-02-10T15:59:00Z',
    createdBy: 'operator',
    notes: 'Rollback of deploy-previous',
  },
]

export function getMockDeployments(params?: DeploymentListParams): DeploymentListResponse {
  const { processCode, keyword, alias, status, cursor, limit = 20 } = params ?? {}
  if (!Number.isInteger(limit) || limit < 1 || limit > 100) {
    throw new Error('limit must be between 1 and 100')
  }
  const normalizedKeyword = keyword?.trim().toLowerCase()
  if (normalizedKeyword && normalizedKeyword.length > 128) {
    throw new Error('keyword must not exceed 128 characters')
  }
  const filtered = mockDeployments
    .filter((item) => !processCode || item.processCode === processCode)
    .filter(
      (item) =>
        !normalizedKeyword ||
        item.processCode.toLowerCase().includes(normalizedKeyword) ||
        item.id.toLowerCase().includes(normalizedKeyword)
    )
    .filter((item) => !alias || item.alias === alias)
    .filter((item) => !status || item.status === status)
    .sort((left, right) => Date.parse(right.createdAt) - Date.parse(left.createdAt))
  const cursorIndex = cursor ? filtered.findIndex((item) => item.id === cursor) : -1
  if (cursor && cursorIndex < 0) throw new Error('Invalid deployment cursor')
  const start = cursorIndex + 1
  const deployments = filtered.slice(start, start + limit)
  const hasMore = start + deployments.length < filtered.length
  return {
    deployments,
    nextCursor: hasMore ? (deployments[deployments.length - 1]?.id ?? null) : null,
    hasMore,
  }
}
