// ==================== HTTP API Contract ====================

import type { paths } from './generated/workbenchServerOpenApi'
import type { ServerSchema } from './serverSchema'

type PreviewOperation = paths['/api/executions/preview']['post']

/** Wire request generated from the Workbench Server preview operation. */
export type PreviewExecutionRequest = PreviewOperation['requestBody']['content']['application/json']

type ExecutionRouting = ServerSchema<'ExecutionRoutingRequest'>

type ExecutionRoutingContext = Omit<ExecutionRouting, 'version' | 'alias'>

/** Published execution route containing exactly one immutable version or logical Alias. */
export type PublishedExecutionRouting = ExecutionRoutingContext &
  ({ version: string; alias?: never } | { alias: string; version?: never })

export interface ExecutionRoutingResult {
  /** Runtime namespace used for the invocation. */
  namespace: string

  /** Exact version requested by the caller. */
  requestedVersion?: string

  /** Alias requested by the caller. */
  requestedAlias?: string

  /** Version that the backend actually selected for this invocation. */
  effectiveVersion?: string

  /** Effective Alias attribution when the invocation was Alias-selected. */
  alias?: string

  /** Authoritative Alias revision observed by the router. */
  routeRevision?: number

  /** Stable or candidate side selected by the Alias router. */
  target?: 'STABLE' | 'CANDIDATE'
}

interface ExecutionResponseBase {
  /** Descriptive message (present on both success and failure) */
  message: string

  /** Engine trace ID used to correlate one execution across services. */
  traceId: string

  /** Stable invocation identifier returned by the active execution backend. */
  invocationId: string

  /** Process code executed by the engine. */
  processCode: string

  /** Execution duration in milliseconds. */
  durationMs: number

  /** Runtime routing facts returned by the backend. */
  routing: ExecutionRoutingResult
}

interface ExecutionSuccessResponse extends ExecutionResponseBase {
  success: true
  result?: Record<string, unknown>
  errorCode?: never
  error?: never
}

interface ExecutionFailureResponse extends ExecutionResponseBase {
  success: false
  /** Stable machine-readable failure code. */
  errorCode: string
  /** Human-readable failure detail. */
  error: string
  result?: never
}

export type ExecutionResponse = ExecutionSuccessResponse | ExecutionFailureResponse

export interface ExecutionStatusResponse {
  /** Whether the engine is available */
  engineAvailable: boolean

  /** Status description */
  message: string
}
