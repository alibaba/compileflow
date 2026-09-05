import type { SemanticTagKind } from '@/shared/components/page'
import type {
  AsyncInvocationAttempt,
  AsyncInvocationResponse,
  AsyncInvocationStatus,
} from '@/shared/contracts'
import { formatDateTime } from '@/shared/i18n/dateTime'

export function statusTagKind(status: AsyncInvocationStatus): SemanticTagKind {
  if (status === 'succeeded') return 'status-success'
  if (status === 'dead_letter') return 'status-error'
  if (status === 'running') return 'status-info'
  return 'status-warning'
}

export function attemptTagKind(attempt: AsyncInvocationAttempt): SemanticTagKind {
  if (attempt.outcome === 'succeeded') return 'status-success'
  if (attempt.outcome === 'running') return 'status-info'
  return 'status-error'
}

export function formatTimestamp(value?: string): string {
  return formatDateTime(value)
}

export function routeLabel(invocation: AsyncInvocationResponse): string {
  if (invocation.routing?.effectiveVersion) return invocation.routing.effectiveVersion
  if (invocation.routing?.requestedVersion) return invocation.routing.requestedVersion
  if (invocation.routing?.requestedAlias) return `@${invocation.routing.requestedAlias}`
  return '-'
}
