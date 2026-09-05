import type { ActionExecution, EffectPolicy } from './action'
import { isJavaIdentifier } from './javaIdentifiers'
import { parseProtocolDuration } from './protocolDuration'

const MAX_ATTEMPTS = 100
const MAX_RECONCILE_ATTEMPTS = 1000
const MAX_RECOVERY_DELAY_MS = 24n * 60n * 60n * 1000n
const MAX_RECOVERY_DURATION_MS = 30n * 24n * 60n * 60n * 1000n

export function validateEffectPolicy(
  policy: EffectPolicy,
  execution: ActionExecution | undefined
): void {
  if (execution !== 'effect') throw new Error('effectPolicy requires execution="effect"')
  if (policy.recoveryPlanVariable !== undefined) {
    validateDynamicPolicy(policy)
    return
  }

  if (policy.maxRecoveryDuration !== undefined) {
    parseProtocolDuration(policy.maxRecoveryDuration, 'maxRecoveryDuration', {
      positive: true,
      maximumMilliseconds: MAX_RECOVERY_DURATION_MS,
    })
  }
  switch (policy.recovery ?? 'manual') {
    case 'manual':
      validateManualPolicy(policy)
      return
    case 'retry':
      validateRetryPolicy(policy)
      return
    case 'reconcile':
      validateReconcilePolicy(policy)
      return
    default:
      throw new Error('recovery must be one of: manual, retry, reconcile')
  }
}

function validateDynamicPolicy(policy: EffectPolicy): void {
  requireCanonicalText(policy.recoveryPlanVariable!, 'recoveryPlanVariable')
  if (!isJavaIdentifier(policy.recoveryPlanVariable!)) {
    throw new Error('recoveryPlanVariable must be a variable name')
  }
  requireAbsent(policy.recovery, 'dynamic recovery')
  requireAbsent(policy.maxAttempts, 'dynamic maxAttempts')
  requireAbsent(policy.maxReconcileAttempts, 'dynamic maxReconcileAttempts')
  requireAbsent(policy.recoveryDelay, 'dynamic recoveryDelay')
  requireAbsent(policy.maxRecoveryDuration, 'dynamic maxRecoveryDuration')
}

function validateManualPolicy(policy: EffectPolicy): void {
  if (policy.maxAttempts !== undefined && policy.maxAttempts !== 1) {
    throw new Error('manual maxAttempts must be 1 when present')
  }
  requireAbsent(policy.maxReconcileAttempts, 'manual maxReconcileAttempts')
  requireAbsent(policy.recoveryDelay, 'manual recoveryDelay')
  requireAbsent(policy.maxRecoveryDuration, 'manual maxRecoveryDuration')
  requireAbsent(policy.reconcileAction, 'manual reconcileAction')
}

function validateRetryPolicy(policy: EffectPolicy): void {
  requireBoundedInteger(policy.maxAttempts, 'maxAttempts', 2, MAX_ATTEMPTS)
  requireAbsent(policy.maxReconcileAttempts, 'retry maxReconcileAttempts')
  requireRecoveryDelay(policy.recoveryDelay)
  requireAbsent(policy.reconcileAction, 'retry reconcileAction')
}

function validateReconcilePolicy(policy: EffectPolicy): void {
  requireBoundedInteger(policy.maxAttempts, 'maxAttempts', 1, MAX_ATTEMPTS)
  requireBoundedInteger(
    policy.maxReconcileAttempts,
    'maxReconcileAttempts',
    1,
    MAX_RECONCILE_ATTEMPTS
  )
  requireRecoveryDelay(policy.recoveryDelay)
  if (!policy.reconcileAction) throw new Error('reconcileAction is required')
}

function requireRecoveryDelay(value: string | undefined): void {
  if (value === undefined) throw new Error('recoveryDelay is required')
  parseProtocolDuration(value, 'recoveryDelay', {
    positive: true,
    maximumMilliseconds: MAX_RECOVERY_DELAY_MS,
  })
}

function requireBoundedInteger(
  value: number | undefined,
  name: string,
  minimum: number,
  maximum: number
): void {
  if (!Number.isSafeInteger(value) || value! < minimum || value! > maximum) {
    throw new Error(`${name} must be an integer from ${minimum} through ${maximum}`)
  }
}

function requireCanonicalText(value: string, name: string): void {
  if (!value.trim()) throw new Error(`${name} must not be blank`)
  if (value !== value.trim()) throw new Error(`${name} must not contain surrounding whitespace`)
}

function requireAbsent(value: unknown, name: string): void {
  if (value !== undefined) throw new Error(`${name} must be absent`)
}
