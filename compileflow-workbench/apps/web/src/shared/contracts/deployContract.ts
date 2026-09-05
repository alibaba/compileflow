import type { RefinedServerSchema, ServerOperationQuery, ServerSchema } from './serverSchema'

// ==================== 部署状态 ====================

type DeploymentStatus =
  | 'in_progress' // 灰度中
  | 'completed' // 已部署
  | 'aborted' // 已中止

export type DeploymentStrategy =
  | 'all_at_once' // 一次性切换别名
  | 'canary' // 稳定版与候选版分流

type DeploymentOperation = 'deploy' | 'rollback'

// ==================== 部署记录 ====================

export type Deployment = RefinedServerSchema<
  'DeploymentResponse',
  {
    operation: DeploymentOperation
    status: DeploymentStatus
    strategy: DeploymentStrategy
  }
>

// ==================== API契约 ====================

export type DeploymentRequest = RefinedServerSchema<
  'CreateDeploymentRequest',
  {
    idempotencyKey: string
    strategy?: DeploymentStrategy
  }
>

export type DeploymentListParams = ServerOperationQuery<'listDeployments'>

export type DeploymentListResponse = RefinedServerSchema<
  'DeploymentListResponse',
  { deployments: Deployment[] }
>

// ==================== 部署操作契约 ====================

export type DeploymentRoute = ServerSchema<'DeploymentRouteResponse'>

export type CanaryHealthEvaluationRequest = ServerSchema<'EvaluateCanaryRequest'>

type CanaryVersionStats = ServerSchema<'CanaryVersionStatsResponse'>

export type CanaryHealthEvaluationResponse = RefinedServerSchema<
  'CanaryHealthEvaluationResponse',
  {
    deployment: Deployment
    metricsScope: 'workbench_server'
    metricsSource: 'execution_logs'
    decision: 'insufficient_data' | 'healthy' | 'unhealthy'
    reason: string
    canary: CanaryVersionStats
    baseline: CanaryVersionStats
    thresholds: Required<CanaryHealthEvaluationRequest>
  }
>

// ==================== 部署控制面运维契约 ====================

export type DeploymentControlHealth = RefinedServerSchema<
  'DeploymentControlHealthResponse',
  { status: 'UP' | 'DEGRADED' | 'DOWN' }
>

export type RequeueDeploymentDeadLettersResponse = RefinedServerSchema<
  'RequeueDeploymentDeadLettersResponse',
  { health: DeploymentControlHealth }
>

type DeploymentPhase = 'in_progress' | 'completed' | 'aborted'

type DeploymentEventType =
  | 'CANARY_STARTED'
  | 'COMPLETED'
  | 'CANARY_WEIGHT_UPDATED'
  | 'PROMOTED'
  | 'ABORTED'

/** Append-only control-plane event for one rollout. */
export type DeploymentEvent = RefinedServerSchema<
  'DeploymentEventView',
  {
    type: DeploymentEventType
    fromPhase: DeploymentPhase | null
    toPhase: DeploymentPhase
    reason: string | null
  }
>
