import { describe, expect, it } from 'vitest'

import { canConnectNodes } from '../connectionRules'

const position = { x: 0, y: 0 }

describe('connection rules', () => {
  it('applies the same TBBPM endpoint and container rules to every creation path', () => {
    const graph = {
      type: 'TBBPM' as const,
      connections: [],
      nodes: [
        { id: 'start', type: 'start' as const, position, properties: {} },
        { id: 'task', type: 'autoTask' as const, position, properties: {} },
        { id: 'nested', type: 'autoTask' as const, parentId: 'scope', position, properties: {} },
        { id: 'end', type: 'end' as const, position, properties: {} },
        { id: 'note', type: 'note' as const, position, properties: {} },
      ],
    }

    expect(canConnectNodes(graph, 'start', 'task')).toBe(true)
    expect(canConnectNodes(graph, 'task', 'nested')).toBe(false)
    expect(canConnectNodes(graph, 'end', 'task')).toBe(false)
    expect(canConnectNodes(graph, 'task', 'start')).toBe(false)
    expect(canConnectNodes(graph, 'task', 'note')).toBe(false)
  })

  it('rejects invalid BPMN endpoints, cross-container and duplicate sequence flows', () => {
    const graph = {
      type: 'BPMN' as const,
      connections: [{ id: 'edge', sourceId: 'task', targetId: 'end' }],
      nodes: [
        { id: 'start', type: 'bpmn:StartEvent' as const, position, properties: {} },
        { id: 'task', type: 'bpmn:ServiceTask' as const, position, properties: {} },
        {
          id: 'nested',
          type: 'bpmn:ServiceTask' as const,
          parentId: 'scope',
          position,
          properties: {},
        },
        { id: 'end', type: 'bpmn:EndEvent' as const, position, properties: {} },
      ],
    }

    expect(canConnectNodes(graph, 'start', 'task')).toBe(true)
    expect(canConnectNodes(graph, 'task', 'nested')).toBe(false)
    expect(canConnectNodes(graph, 'end', 'task')).toBe(false)
    expect(canConnectNodes(graph, 'task', 'start')).toBe(false)
    expect(canConnectNodes(graph, 'task', 'end')).toBe(false)
    expect(canConnectNodes(graph, 'task', 'end', 'edge')).toBe(true)
  })
})
