import { describe, expect, test } from 'vitest'

import type { ProcessVariable, UnifiedProcessDefinition } from '../../types/flowDefinition'
import type { TbbpmNode } from '../../types/tbbpm'
import { validateDesignerProcess } from '../designerProcessValidation'

function flow(variables: ProcessVariable[]): UnifiedProcessDefinition {
  const nodes: TbbpmNode[] = [
    {
      id: 'start',
      type: 'start',
      position: { x: 0, y: 0 },
      properties: {},
    },
    {
      id: 'end',
      type: 'end',
      position: { x: 100, y: 0 },
      properties: {},
    },
  ]

  return {
    id: 'variable-contract',
    code: 'variable-contract',
    name: 'Variable contract',
    type: 'TBBPM',
    nodes,
    connections: [{ id: 'to-end', sourceId: 'start', targetId: 'end' }],
    variables,
  }
}

describe('designer process-variable validation', () => {
  test('accepts executable Java variable declarations', () => {
    const result = validateDesignerProcess(
      flow([
        {
          name: 'orderId',
          type: 'java.lang.String',
          inOutType: 'param',
        },
        {
          name: 'items',
          type: 'java.util.List<java.lang.String>',
          inOutType: 'inner',
        },
      ])
    )

    expect(result).toMatchObject({ valid: true, issues: [] })
  })

  test('reports every declaration error before code generation', () => {
    const variables = [
      { name: 'order-id', type: 'java.lang.String', inOutType: 'param' },
      { name: '_cf$state', type: 'java.lang.String', inOutType: 'inner' },
      { name: 'duplicate', type: 'java.lang.String', inOutType: 'param' },
      { name: 'duplicate', type: '', inOutType: 'unknown' },
    ] as unknown as ProcessVariable[]

    const result = validateDesignerProcess(flow(variables))

    expect(result.valid).toBe(false)
    expect(result.issues.map((issue) => issue.code)).toEqual(
      expect.arrayContaining([
        'designer.validation.property.process.variable.invalidName',
        'designer.validation.property.process.variable.reservedName',
        'designer.validation.property.process.variable.duplicateName',
        'designer.validation.property.process.variable.missingType',
        'designer.validation.property.process.variable.invalidDirection',
      ])
    )
  })
})
