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
  test.each(['rename', 'delete'])(
    'reports dangling output references after variable %s without rewriting source text',
    (operation) => {
      const definition = flow([{ name: 'result', type: 'java.lang.String', inOutType: 'return' }])
      if (definition.type !== 'TBBPM') throw new Error('Expected TBBPM fixture')
      definition.nodes.push({
        id: 'task',
        type: 'autoTask',
        position: { x: 0, y: 0 },
        properties: {
          action: {
            actionType: 'java',
            className: 'example.Service',
            method: 'run',
            mappings: [{ direction: 'output', target: 'result', dataType: 'java.lang.String' }],
          },
        },
      })
      definition.variables =
        operation === 'rename'
          ? [{ name: 'renamed', type: 'java.lang.String', inOutType: 'return' }]
          : []
      expect(validateDesignerProcess(definition).issues).toEqual(
        expect.arrayContaining([
          expect.objectContaining({ code: 'tbbpm.mapping.unknownOutputTarget', nodeIds: ['task'] }),
        ])
      )
      expect(definition.nodes[2].properties.action?.mappings?.[0].target).toBe('result')
    }
  )
  test('connection issues identify actual canvas edges', () => {
    const definition = flow([])
    definition.connections = [{ id: 'real-edge', sourceId: 'start', targetId: 'start' }]
    const issues = validateDesignerProcess(definition).issues.filter(
      (issue) => issue.type === 'property' && issue.code.endsWith('conn.selfLoop')
    )
    expect(issues).toHaveLength(1)
    expect(issues[0].connectionIds).toEqual(['real-edge'])
  })
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
