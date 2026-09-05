import type {
  ExecutionResponse,
  ExecutionRoutingResult,
  PublishedExecutionRouting,
} from './executionContract'
import type { RefinedServerSchema, ServerOperationQuery, ServerSchema } from './serverSchema'

// ==================== 执行状态 ====================

export type ExecutionStatus = 'success' | 'failed'

// ==================== 执行日志 ====================

export type ExecutionLog = RefinedServerSchema<'ExecutionLogResponse', { status: ExecutionStatus }>

// ==================== 日志查询契约 ====================

/**
 * Execution-log filters include `invocationId`, `requestedVersion?: string`, `effectiveVersion?: string`,
 * and `routingSource?: string`.
 * The generated operation schema remains the source of truth for field types and bounds.
 */
export type LogFilterParams = ServerOperationQuery<'listExecutionLogs'>

export type LogListResponse = RefinedServerSchema<
  'ExecutionLogListResponse',
  { data: ExecutionLog[] }
>

// ==================== 监控指标契约 ====================

// MonitoringMetrics, ExecutionTrend, TopProcessStats, ErrorSummary are defined
// here as the single source of truth for monitoring contract types.

export type MonitoringTimeRange = '1h' | '6h' | '24h' | '7d' | '30d'

export type MonitoringMetrics = RefinedServerSchema<
  'MonitoringMetricsResponse',
  { scope: 'workbench_server'; timeRange: MonitoringTimeRange }
>

export type ExecutionTrend = ServerSchema<'ExecutionTrendResponse'>

export type TopProcessStats = ServerSchema<'TopProcessStatsResponse'>

export type ErrorSummary = ServerSchema<'ErrorSummaryResponse'>

export type VersionDistributionStats = ServerSchema<'VersionDistributionResponse'>

type DeployRuntimeVersionRef = ServerSchema<'DeployRuntimeVersionResponse'>

type DeployRuntimeBackedOffVersion = ServerSchema<'DeployRuntimeBackedOffVersionResponse'>

type DeployRuntimeDeployedProcess = ServerSchema<'DeployRuntimeDeployedProcessResponse'>

type DeployRuntimeAliasConvergenceState = 'local_ready' | 'pending' | 'failed'

type DeployRuntimeAliasState = RefinedServerSchema<
  'DeployRuntimeAliasResponse',
  { state: DeployRuntimeAliasConvergenceState }
>

interface DeployRuntimeDiagnosticsBase {
  timestamp: string
  available: boolean
  started: boolean
}

interface DeployRuntimeUnavailableDiagnostics extends DeployRuntimeDiagnosticsBase {
  available: false
  started: false
  message: string
}

export interface DeployRuntimeAvailableDiagnostics extends DeployRuntimeDiagnosticsBase {
  available: true
  topology: 'embedded' | 'distributed'
  desiredAliasCount: number
  localReadyAliasCount: number
  pendingAliasCount: number
  failedAliasCount: number
  aliases: DeployRuntimeAliasState[]
  inflightCount: number
  inflightCapacity: number
  inflightAvailablePermits: number
  failureBackoffMs: number
  retainedRuntimeCount: number
  inflightVersions: DeployRuntimeVersionRef[]
  demandedVersions: DeployRuntimeVersionRef[]
  pendingReleaseVersions: DeployRuntimeVersionRef[]
  backedOffVersions: DeployRuntimeBackedOffVersion[]
  deployedVersions: DeployRuntimeDeployedProcess[]
}

export type DeployRuntimeDiagnostics =
  | DeployRuntimeUnavailableDiagnostics
  | DeployRuntimeAvailableDiagnostics
// ==================== 异步调用契约 ====================

export type AsyncInvocationStatus = 'queued' | 'running' | 'succeeded' | 'dead_letter'

type AsyncInvocationAttemptOutcome = 'running' | 'succeeded' | 'failed' | 'lease_expired'

type AsyncInvocationAttemptDisposition = 'succeeded' | 'retry_scheduled' | 'dead_lettered'

export interface AsyncInvocationRequest {
  /** Stable invocation identifier used for idempotency, logs, metrics, and retry correlation. */
  invocationId?: string

  /** Execution parameters passed to the process engine. */
  params?: Record<string, unknown>

  /** Runtime routing controls containing exactly one of version or alias. */
  routing: PublishedExecutionRouting

  /** Maximum execution attempts before dead-lettering, from 1 through 100; defaults to one. */
  maxAttempts?: number

  /** Durable retry delay in milliseconds, from 0 through 604800000 (7 days). */
  retryDelayMs?: number
}

export type AsyncInvocationResponse = RefinedServerSchema<
  'AsyncInvocationResponse',
  {
    status: AsyncInvocationStatus
    routing?: ExecutionRoutingResult
    response?: ExecutionResponse
    payloadErrors?: {
      routingJson?: string
      resultJson?: string
    }
  }
>

export type AsyncInvocationAttempt = RefinedServerSchema<
  'AsyncInvocationAttemptResponse',
  {
    outcome: AsyncInvocationAttemptOutcome
    disposition?: AsyncInvocationAttemptDisposition
  }
>

export type AsyncInvocationAttemptListParams = ServerOperationQuery<'listAsyncInvocationAttempts'>

export type AsyncInvocationAttemptListResponse = RefinedServerSchema<
  'AsyncInvocationAttemptListResponse',
  { data: AsyncInvocationAttempt[] }
>

/** Async invocation list query. One-based page number. */
export type AsyncInvocationListParams = ServerOperationQuery<'listAsyncInvocations'>

export type AsyncInvocationListResponse = RefinedServerSchema<
  'AsyncInvocationListResponse',
  { data: AsyncInvocationResponse[] }
>

export type AsyncInvocationHealth = RefinedServerSchema<
  'AsyncInvocationHealthResponse',
  { status: 'healthy' | 'degraded' }
>

export type AsyncInvocationDeadLetterRequeueRequest = ServerSchema<'DeadLetterRequeueRequest'>

export type AsyncInvocationDeadLetterRequeueResponse = RefinedServerSchema<
  'AsyncInvocationDeadLetterRequeueResponse',
  { health: AsyncInvocationHealth }
>

// ==================== 日志操作契约 ====================

export type LogExportParams = Omit<LogFilterParams, 'page' | 'pageSize'>

export type LogPurgeParams = ServerSchema<'PurgeExecutionLogsRequest'>

export type LogPurgeResult = ServerSchema<'PurgeExecutionLogsResponse'>
