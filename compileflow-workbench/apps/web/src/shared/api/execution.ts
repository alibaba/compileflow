import apiClient from '@/shared/api/client'
import { handleApiError, isTransientApiError } from '@/shared/api/errorHandler'
import type {
  ExecutionResponse,
  ExecutionStatusResponse,
  PreviewExecutionRequest,
} from '@/shared/contracts/executionContract'

const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_.-]{0,127}$/

function isObjectRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isExecutionStatusResponse(value: unknown): value is ExecutionStatusResponse {
  return (
    isObjectRecord(value) &&
    typeof value.engineAvailable === 'boolean' &&
    isNonBlankString(value.message)
  )
}

function isOptionalNonBlankString(value: unknown): boolean {
  return value === undefined || isNonBlankString(value)
}

function hasValidExecutionMetadata(value: Record<string, unknown>): boolean {
  return (
    isNonBlankString(value.traceId) &&
    isNonBlankString(value.invocationId) &&
    isNonBlankString(value.processCode)
  )
}

function hasValidDuration(value: Record<string, unknown>): boolean {
  return (
    typeof value.durationMs === 'number' &&
    Number.isFinite(value.durationMs) &&
    value.durationMs >= 0
  )
}

function hasValidRouting(value: Record<string, unknown>): boolean {
  if (!isObjectRecord(value.routing)) {
    return false
  }
  const routing = value.routing
  return (
    isNonBlankString(routing.namespace) &&
    isOptionalNonBlankString(routing.requestedVersion) &&
    isOptionalNonBlankString(routing.requestedAlias) &&
    isOptionalNonBlankString(routing.effectiveVersion) &&
    isOptionalNonBlankString(routing.alias) &&
    (routing.routeRevision === undefined ||
      (typeof routing.routeRevision === 'number' &&
        Number.isSafeInteger(routing.routeRevision) &&
        routing.routeRevision > 0)) &&
    (routing.target === undefined || routing.target === 'STABLE' || routing.target === 'CANDIDATE')
  )
}

function hasValidExecutionOutcome(value: Record<string, unknown>): boolean {
  if (value.success === true) {
    return (
      value.errorCode === undefined &&
      value.error === undefined &&
      (value.result === undefined || isObjectRecord(value.result))
    )
  }
  return (
    value.success === false &&
    typeof value.errorCode === 'string' &&
    ERROR_CODE_PATTERN.test(value.errorCode) &&
    isNonBlankString(value.error) &&
    value.result === undefined
  )
}

function isExecutionResponse(value: unknown): value is ExecutionResponse {
  return (
    isObjectRecord(value) &&
    isNonBlankString(value.message) &&
    hasValidExecutionMetadata(value) &&
    hasValidDuration(value) &&
    hasValidRouting(value) &&
    hasValidExecutionOutcome(value)
  )
}

/** Query availability from either the development mock or Workbench Server. */
export async function getEngineStatus(): Promise<ExecutionStatusResponse> {
  for (let attempt = 0; ; attempt++) {
    try {
      const body = await apiClient.get('/api/status')
      if (!isExecutionStatusResponse(body)) {
        throw new Error('Status check failed: backend returned an invalid response')
      }
      return body
    } catch (error) {
      const failure = handleApiError(error)
      if (attempt >= 2 || !isTransientApiError(failure)) {
        throw failure
      }
      const delay = 1000 * 2 ** attempt * (0.5 + Math.random() * 0.5)
      await new Promise((resolve) => setTimeout(resolve, delay))
    }
  }
}

/** Execute the supplied draft XML without publishing or published-version routing. */
export async function executePreview(request: PreviewExecutionRequest): Promise<ExecutionResponse> {
  const body = await apiClient.post('/api/executions/preview', request).catch((error: unknown) => {
    throw handleApiError(error)
  })
  if (!isExecutionResponse(body)) {
    throw new Error('Execution request failed: backend returned an invalid response')
  }
  return body
}
