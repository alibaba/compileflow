import type {
  CanaryHealthEvaluationResponse,
  Deployment,
  DeploymentControlHealth,
  DeploymentEvent,
  DeploymentListResponse,
  DeploymentRoute,
  RequeueDeploymentDeadLettersResponse,
} from './deployContract'
import type { ExecutionResponse } from './executionContract'
import type { components, paths } from './generated/workbenchServerOpenApi'
import type {
  AsyncInvocationAttempt,
  AsyncInvocationAttemptListResponse,
  AsyncInvocationDeadLetterRequeueResponse,
  AsyncInvocationHealth,
  AsyncInvocationListResponse,
  AsyncInvocationResponse,
  DeployRuntimeAvailableDiagnostics,
  ExecutionLog,
  LogListResponse,
  MonitoringMetrics,
} from './runtimeContract'

type Schemas = components['schemas']
type Operations = paths
type DeployRuntimeAliasState = DeployRuntimeAvailableDiagnostics['aliases'][number]
type PreviewOperation = Operations['/api/executions/preview']['post']
type PreviewResponseSchema = PreviewOperation['responses'][200]['content']['application/json']

type Present<T> = T extends readonly (infer Item)[]
  ? Present<Item>[]
  : T extends object
    ? { -readonly [Key in keyof T]-?: Present<Exclude<T[Key], undefined>> }
    : Exclude<T, undefined>

type SameKeys<Left, Right> = [keyof Present<Left>] extends [keyof Present<Right>]
  ? [keyof Present<Right>] extends [keyof Present<Left>]
    ? true
    : false
  : false

type RequiredKeys<Value> = {
  [Key in keyof Value]-?: object extends Pick<Value, Key> ? never : Key
}[keyof Value]

type SameRequiredKeys<Left, Right> = [RequiredKeys<Left>] extends [RequiredKeys<Right>]
  ? [RequiredKeys<Right>] extends [RequiredKeys<Left>]
    ? true
    : false
  : false

type Compatible<Frontend, Schema> =
  Present<Frontend> extends Present<Schema> ? SameRequiredKeys<Frontend, Schema> : false
type WireCompatible<Frontend, Schema> = [Frontend] extends [Schema] ? true : false
type SameWireValue<Left, Right> =
  WireCompatible<Left, Right> extends true ? WireCompatible<Right, Left> : false

type Assert<Condition extends true> = Condition

/**
 * Compile-time proof that refined Workbench domain contracts still match the
 * generated CompileFlow Workbench Server wire schemas.
 */
export type ServerContractParity = [
  Assert<'get' extends keyof Operations['/api/status'] ? true : false>,
  Assert<'post' extends keyof Operations['/api/executions/preview'] ? true : false>,
  Assert<'post' extends keyof Operations['/api/processes/{code}/execute'] ? true : false>,
  Assert<'post' extends keyof Operations['/api/processes/{code}/async-invocations'] ? true : false>,
  Assert<'get' extends keyof Operations['/api/async-invocations'] ? true : false>,
  Assert<
    'get' extends keyof Operations['/api/async-invocations/{invocationId}/attempts'] ? true : false
  >,
  Assert<'get' extends keyof Operations['/api/async-invocations/health'] ? true : false>,
  Assert<'get' extends keyof Operations['/api/execution-logs'] ? true : false>,
  Assert<'post' extends keyof Operations['/api/execution-logs/purge'] ? true : false>,
  Assert<'get' extends keyof Operations['/api/monitoring/deploy-runtime'] ? true : false>,
  Assert<WireCompatible<ExecutionResponse, PreviewResponseSchema>>,
  Assert<SameKeys<Deployment, Schemas['DeploymentResponse']>>,
  Assert<Compatible<Deployment, Schemas['DeploymentResponse']>>,
  Assert<SameKeys<DeploymentListResponse, Schemas['DeploymentListResponse']>>,
  Assert<Compatible<DeploymentListResponse, Schemas['DeploymentListResponse']>>,
  Assert<SameKeys<DeploymentRoute, Schemas['DeploymentRouteResponse']>>,
  Assert<Compatible<DeploymentRoute, Schemas['DeploymentRouteResponse']>>,
  Assert<SameKeys<CanaryHealthEvaluationResponse, Schemas['CanaryHealthEvaluationResponse']>>,
  Assert<Compatible<CanaryHealthEvaluationResponse, Schemas['CanaryHealthEvaluationResponse']>>,
  Assert<SameKeys<DeploymentControlHealth, Schemas['DeploymentControlHealthResponse']>>,
  Assert<Compatible<DeploymentControlHealth, Schemas['DeploymentControlHealthResponse']>>,
  Assert<
    SameKeys<RequeueDeploymentDeadLettersResponse, Schemas['RequeueDeploymentDeadLettersResponse']>
  >,
  Assert<
    Compatible<
      RequeueDeploymentDeadLettersResponse,
      Schemas['RequeueDeploymentDeadLettersResponse']
    >
  >,
  Assert<SameKeys<DeploymentEvent, Schemas['DeploymentEventView']>>,
  Assert<SameKeys<DeployRuntimeAliasState, Schemas['DeployRuntimeAliasResponse']>>,
  Assert<Compatible<DeployRuntimeAliasState, Schemas['DeployRuntimeAliasResponse']>>,
  Assert<SameKeys<MonitoringMetrics, Schemas['MonitoringMetricsResponse']>>,
  Assert<Compatible<MonitoringMetrics, Schemas['MonitoringMetricsResponse']>>,
  Assert<SameKeys<ExecutionLog, Schemas['ExecutionLogResponse']>>,
  Assert<Compatible<ExecutionLog, Schemas['ExecutionLogResponse']>>,
  Assert<SameKeys<LogListResponse, Schemas['ExecutionLogListResponse']>>,
  Assert<Compatible<LogListResponse, Schemas['ExecutionLogListResponse']>>,
  Assert<SameKeys<AsyncInvocationResponse, Schemas['AsyncInvocationResponse']>>,
  Assert<Compatible<AsyncInvocationResponse, Schemas['AsyncInvocationResponse']>>,
  Assert<
    SameWireValue<AsyncInvocationResponse['status'], Schemas['AsyncInvocationResponse']['status']>
  >,
  Assert<SameKeys<AsyncInvocationAttempt, Schemas['AsyncInvocationAttemptResponse']>>,
  Assert<Compatible<AsyncInvocationAttempt, Schemas['AsyncInvocationAttemptResponse']>>,
  Assert<
    SameWireValue<
      AsyncInvocationAttempt['outcome'],
      Schemas['AsyncInvocationAttemptResponse']['outcome']
    >
  >,
  Assert<
    SameWireValue<
      AsyncInvocationAttempt['disposition'],
      Schemas['AsyncInvocationAttemptResponse']['disposition']
    >
  >,
  Assert<
    SameKeys<AsyncInvocationAttemptListResponse, Schemas['AsyncInvocationAttemptListResponse']>
  >,
  Assert<
    Compatible<AsyncInvocationAttemptListResponse, Schemas['AsyncInvocationAttemptListResponse']>
  >,
  Assert<SameKeys<AsyncInvocationListResponse, Schemas['AsyncInvocationListResponse']>>,
  Assert<Compatible<AsyncInvocationListResponse, Schemas['AsyncInvocationListResponse']>>,
  Assert<SameKeys<AsyncInvocationHealth, Schemas['AsyncInvocationHealthResponse']>>,
  Assert<Compatible<AsyncInvocationHealth, Schemas['AsyncInvocationHealthResponse']>>,
  Assert<
    SameKeys<
      AsyncInvocationDeadLetterRequeueResponse,
      Schemas['AsyncInvocationDeadLetterRequeueResponse']
    >
  >,
  Assert<
    Compatible<
      AsyncInvocationDeadLetterRequeueResponse,
      Schemas['AsyncInvocationDeadLetterRequeueResponse']
    >
  >,
]
