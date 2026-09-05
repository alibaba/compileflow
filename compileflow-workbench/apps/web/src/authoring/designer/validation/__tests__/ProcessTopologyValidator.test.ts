import { describe, expect, test } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import type { TbbpmConnection, TbbpmNode } from '../../types/tbbpm'
import { validateProcessTopology } from '../ProcessTopologyValidator'

function node(
  id: string,
  type: TbbpmNode['type'],
  parentId?: string,
  properties: TbbpmNode['properties'] = {}
): TbbpmNode {
  return {
    id,
    type,
    parentId,
    position: { x: 0, y: 0 },
    properties,
  }
}

function flow(
  nodes: TbbpmNode[],
  connections: UnifiedProcessDefinition['connections']
): UnifiedProcessDefinition {
  return {
    id: 'topology',
    code: 'topology',
    name: 'topology',
    type: 'TBBPM',
    nodes,
    connections,
  }
}

describe('ProcessTopologyValidator container semantics', () => {
  test('requires exactly one start and one end in the root process', () => {
    const missing = validateProcessTopology(flow([], [])).issues
    const multiple = validateProcessTopology(
      flow(
        [
          node('start-1', 'start'),
          node('start-2', 'start'),
          node('end-1', 'end'),
          node('end-2', 'end'),
        ],
        []
      )
    ).issues

    expect(missing.map((issue) => issue.type)).toEqual(
      expect.arrayContaining(['multi-start', 'no-end'])
    )
    expect(multiple.map((issue) => issue.type)).toEqual(
      expect.arrayContaining(['multi-start', 'multi-end'])
    )
  })

  test('reports cycles and isolated executable nodes', () => {
    const definition = flow(
      [
        node('start', 'start'),
        node('left', 'autoTask'),
        node('right', 'autoTask'),
        node('end', 'end'),
        node('isolated', 'autoTask'),
      ],
      [
        { id: 'to_left', sourceId: 'start', targetId: 'left' },
        { id: 'to_right', sourceId: 'left', targetId: 'right' },
        { id: 'cycle', sourceId: 'right', targetId: 'left' },
        { id: 'to_end', sourceId: 'right', targetId: 'end' },
      ]
    )

    expect(validateProcessTopology(definition).issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ type: 'cycle' }),
        expect.objectContaining({ type: 'isolated', nodeIds: ['isolated'] }),
      ])
    )
  })

  test('requires boundaries in every nested process container', () => {
    const definition = flow(
      [
        node('start', 'start'),
        node('scope', 'subBpm'),
        node('end', 'end'),
        node('scopeStart', 'start', 'scope'),
      ],
      [
        { id: 'to_scope', sourceId: 'start', targetId: 'scope' },
        { id: 'to_end', sourceId: 'scope', targetId: 'end' },
      ]
    )

    expect(validateProcessTopology(definition).issues).toEqual(
      expect.arrayContaining([expect.objectContaining({ type: 'no-end' })])
    )
  })

  test('ignores isolated TBBPM notes because they are design-time elements', () => {
    const definition = flow(
      [node('start', 'start'), node('end', 'end'), node('note', 'note')],
      [{ id: 'to_end', sourceId: 'start', targetId: 'end' }]
    )

    expect(validateProcessTopology(definition).issues).toEqual([])
  })

  test('validates each TBBPM loop body as an independent graph', () => {
    const definition = flow(
      [
        node('start', 'start'),
        node('loop', 'while', undefined, {
          condition: 'active',
          maxIterations: 10,
        }),
        node('end', 'end'),
        node('loopStart', 'start', 'loop'),
        node('loopEnd', 'end', 'loop'),
      ],
      [
        { id: 'to_loop', sourceId: 'start', targetId: 'loop' },
        { id: 'to_end', sourceId: 'loop', targetId: 'end' },
      ]
    )

    const issues = validateProcessTopology(definition).issues

    expect(issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          type: 'unreachable',
          nodeIds: ['loopEnd'],
        }),
      ])
    )
  })

  test('accepts a loop body connected from local start to local end', () => {
    const definition = flow(
      [
        node('start', 'start'),
        node('loop', 'while', undefined, {
          condition: 'active',
          maxIterations: 10,
        }),
        node('end', 'end'),
        node('loopStart', 'start', 'loop'),
        node('body', 'autoTask', 'loop'),
        node('loopEnd', 'end', 'loop'),
      ],
      [
        { id: 'to_loop', sourceId: 'start', targetId: 'loop' },
        { id: 'to_end', sourceId: 'loop', targetId: 'end' },
        { id: 'body_start', sourceId: 'loopStart', targetId: 'body' },
        { id: 'body_end', sourceId: 'body', targetId: 'loopEnd' },
      ]
    )

    expect(validateProcessTopology(definition).issues).toEqual([])
  })

  test('validates a 10,000-node linear process without recursive stack growth', () => {
    const nodeCount = 10_000
    const nodes: TbbpmNode[] = Array.from({ length: nodeCount }, (_, index) => ({
      id: `node-${index}`,
      type: index === 0 ? 'start' : index === nodeCount - 1 ? 'end' : 'autoTask',
      position: { x: index, y: 0 },
      properties:
        index === 0 || index === nodeCount - 1
          ? {}
          : { action: { actionType: 'java', className: 'Handler', method: 'run' } },
    }))
    const connections: TbbpmConnection[] = Array.from({ length: nodeCount - 1 }, (_, index) => ({
      id: `flow-${index}`,
      sourceId: `node-${index}`,
      targetId: `node-${index + 1}`,
    }))

    expect(validateProcessTopology(flow(nodes, connections))).toMatchObject({
      valid: true,
      issues: [],
    })
  })
})
