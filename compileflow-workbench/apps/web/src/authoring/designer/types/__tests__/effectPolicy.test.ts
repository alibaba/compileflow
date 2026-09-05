import { describe, expect, it } from 'vitest'

import type { ActionExecution } from '../action'
import { validateEffectPolicy } from '../effectPolicy'

describe('validateEffectPolicy', () => {
  it('accepts canonical manual, retry, reconcile, and dynamic policies', () => {
    expect(() => validateEffectPolicy({ recovery: 'manual' }, 'effect')).not.toThrow()
    expect(() =>
      validateEffectPolicy({ recovery: 'retry', maxAttempts: 2, recoveryDelay: 'PT1S' }, 'effect')
    ).not.toThrow()
    expect(() =>
      validateEffectPolicy(
        {
          recovery: 'reconcile',
          maxAttempts: 1,
          maxReconcileAttempts: 1,
          recoveryDelay: 'PT1S',
          reconcileAction: { actionType: 'java', className: 'com.example.Query' },
        },
        'effect'
      )
    ).not.toThrow()
    expect(() =>
      validateEffectPolicy({ recoveryPlanVariable: 'recoveryPlan' }, 'effect')
    ).not.toThrow()
  })

  it.each([
    [{ recovery: 'manual' } as const, 'replayable' as ActionExecution],
    [
      { recovery: 'retry', maxAttempts: 1, recoveryDelay: 'PT1S' } as const,
      'effect' as ActionExecution,
    ],
    [{ recovery: 'retry', maxAttempts: 2 } as const, 'effect' as ActionExecution],
    [{ recoveryPlanVariable: ' plan' } as const, 'effect' as ActionExecution],
    [{ recoveryPlanVariable: 'not valid' } as const, 'effect' as ActionExecution],
    [{ recoveryPlanVariable: 'class' } as const, 'effect' as ActionExecution],
    [
      {
        recovery: 'reconcile',
        maxAttempts: 1,
        maxReconcileAttempts: 1,
        recoveryDelay: 'PT1S',
      } as const,
      'effect' as ActionExecution,
    ],
  ])('rejects an invalid policy %#', (policy, execution) => {
    expect(() => validateEffectPolicy(policy, execution)).toThrow()
  })
})
