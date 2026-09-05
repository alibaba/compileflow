import type { ServerOperationQuery, ServerSchema } from './serverSchema'

// ==================== 流程类型 ====================

export type ProcessModelType = ServerSchema<'ProcessDefinitionResponse'>['type']

// ==================== 流程定义 ====================

export type ProcessDefinition = ServerSchema<'ProcessDefinitionResponse'>

/** Process summary metadata returned by list endpoints; definition XML is detail-only. */
export type ProcessSummary = ServerSchema<'ProcessSummaryResponse'>

// ==================== 流程版本 ====================

export type ProcessVersion = ServerSchema<'ProcessVersionResponse'>

// ==================== API契约 ====================

/**
 * Process list query. Its generated shape includes:
 * `keyword?: string`, `sortBy?: 'name' | 'createdAt' | 'updatedAt'`, and `sortOrder?: 'asc' | 'desc'`.
 * `keyword` filters text; pagination is one-based.
 */
export type ProcessListParams = ServerOperationQuery<'listProcesses'>

export type ProcessListResponse = ServerSchema<'ProcessListResponse'>

export type ProcessVersionListParams = ServerOperationQuery<'getProcessVersions'>

export type ProcessVersionListResponse = ServerSchema<'ProcessVersionListResponse'>

export type ProcessCreateRequest = ServerSchema<'CreateProcessRequest'>

export type ProcessUpdateRequest = ServerSchema<'UpdateProcessRequest'>

// ==================== Validation types (canonical definitions used by all layers) ====================

/** Severity level for a validation finding. */
type ValidationSeverity = 'error' | 'warning'

/** A single validation finding, unifying XML-layer position info and graph-layer node references. */
interface ValidationFinding {
  /** Machine-readable error code, e.g. 'MISSING_START_NODE'. Optional for XML parse errors. */
  code?: string
  /** Human-readable message. */
  message: string
  /** Related node ID (for graph-layer validations). */
  nodeId?: string
  /** Severity level. */
  severity: ValidationSeverity
  /** Line number in source XML (for XML-layer validations). */
  line?: number
  /** Column number in source XML (for XML-layer validations). */
  column?: number
}

export interface ValidationResult {
  valid: boolean
  errors: ValidationFinding[]
  warnings: ValidationFinding[]
}
