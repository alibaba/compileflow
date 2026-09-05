import {
  AppError,
  ErrorSeverity,
  isTransientFetchError,
  parseProblemDetail,
  problemDetailToAppError,
  retryOperation,
} from '@/shared/api/errorHandler'
import { TIMEOUTS } from '@/shared/constants'
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

function fetchWithTimeout(
  url: string,
  options?: RequestInit,
  timeout = TIMEOUTS.API_REQUEST
): Promise<Response> {
  const controller = new AbortController()
  const id = setTimeout(() => controller.abort(), timeout)
  return fetch(url, {
    ...options,
    signal: controller.signal,
  }).finally(() => clearTimeout(id))
}

function responseError(response: Response, body: unknown, operation: string): AppError {
  const problem = parseProblemDetail(body)
  if (problem !== undefined) {
    return problemDetailToAppError(problem, response.status)
  }
  return new AppError(
    `${operation} failed (HTTP ${response.status}): ${response.statusText}`,
    `HTTP_${response.status}`,
    response.status >= 500 ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM,
    { statusCode: response.status }
  )
}

async function parseExecutionResponse(res: Response): Promise<ExecutionResponse> {
  const body = await res.json().catch(() => undefined)
  if (!res.ok) {
    throw responseError(res, body, 'Execution request')
  }
  if (!isExecutionResponse(body)) {
    throw new Error('Execution request failed: backend returned an invalid response')
  }
  return body
}

/** Query availability from either the development mock or Workbench Server. */
export async function getEngineStatus(): Promise<ExecutionStatusResponse> {
  return retryOperation(
    async () => {
      const res = await fetchWithTimeout('/api/status')
      if (!res.ok) {
        const body = await res.json().catch(() => undefined)
        throw responseError(res, body, 'Status check')
      }
      const body: unknown = await res.json()
      if (!isExecutionStatusResponse(body)) {
        throw new Error('Status check failed: backend returned an invalid response')
      }
      return body
    },
    { isRetryable: isTransientFetchError }
  )
}

/** Execute the supplied draft XML without publishing or published-version routing. */
export async function executePreview(request: PreviewExecutionRequest): Promise<ExecutionResponse> {
  const res = await fetchWithTimeout('/api/executions/preview', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return parseExecutionResponse(res)
}
