import { afterEach, describe, expect, it, vi } from 'vitest'

import type { TbbpmProcessDefinition } from '../../types/flowDefinition'
import { generateTbbpmXml } from '../tbbpmXmlCodec'

afterEach(() => vi.restoreAllMocks())

function cyclicBody(type: 'subBpm' | 'while'): TbbpmProcessDefinition {
  return {
    id: 'flow',
    code: 'flow',
    name: 'Flow',
    type: 'TBBPM',
    nodes: [
      {
        id: 'container',
        type,
        position: { x: 0, y: 0 },
        properties: type === 'while' ? { condition: 'true', maxIterations: 10 } : {},
      },
      ...(['start', 'exclusive', 'end'] as const).map((nodeType) => ({
        id: nodeType,
        type: nodeType,
        parentId: 'container',
        position: { x: 0, y: 0 },
        properties: {},
      })),
    ],
    connections: [
      { id: 'enter', sourceId: 'start', targetId: 'exclusive' },
      { id: 'repeat', sourceId: 'exclusive', targetId: 'exclusive', condition: 'true' },
    ],
  }
}

describe('TBBPM traversal termination', () => {
  it.each(['subBpm', 'while'] as const)('reports unreachable nodes in a cyclic %s body', (type) => {
    // Bound the regression so a broken synchronous traversal cannot hang the test worker.
    const add = Set.prototype.add
    let visits = 0
    vi.spyOn(Set.prototype, 'add').mockImplementation(function (
      this: Set<unknown>,
      value: unknown
    ) {
      if (value === 'exclusive' && ++visits > 30) throw new Error('Traversal did not terminate')
      return add.call(this, value)
    })

    expect(() => generateTbbpmXml(cyclicBody(type))).toThrow(/unreachable/)
  })

  it('rejects a cyclic parent hierarchy before resolving enclosing loop variables', () => {
    const flow = cyclicBody('while')
    flow.nodes[0].parentId = 'container'
    const get = Map.prototype.get
    let visits = 0
    vi.spyOn(Map.prototype, 'get').mockImplementation(function (
      this: Map<unknown, unknown>,
      key: unknown
    ) {
      if (key === 'container' && ++visits > 30)
        throw new Error('Parent traversal did not terminate')
      return get.call(this, key)
    })

    expect(() => generateTbbpmXml(flow)).toThrow(/hierarchy contains a cycle/)
  })
})
