import { describe, expect, it } from 'vitest'

import { safeParseTbbpmNode } from '../tbbpmSchemas'

describe('TBBPM effect policy schema', () => {
  it.each([
    { recovery: 'manual' },
    { recovery: 'retry', maxAttempts: 3, recoveryDelay: 'PT1S' },
    {
      recoveryPlanVariable: 'plan',
      reconcileAction: {
        actionType: 'java',
        className: 'example.Service',
        method: 'check',
        inputs: [{ source: 'input', target: 'value', dataType: 'java.lang.String' }],
      },
    },
  ])('accepts modeled effect policy without dropping it: %j', (effectPolicy) => {
    const result = safeParseTbbpmNode({
      id: 'task',
      type: 'autoTask',
      position: { x: 0, y: 0 },
      properties: {
        action: {
          actionType: 'java',
          className: 'example.Service',
          method: 'run',
          execution: 'effect',
          effectPolicy,
        },
      },
    })
    expect(result.success).toBe(true)
    if (result.success) expect(result.data.properties).toMatchObject({ action: { effectPolicy } })
  })
})
