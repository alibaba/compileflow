import { describe, expect, it } from 'vitest'

import { findDirectedCycles } from '../directedGraph'

describe('cycle diagnostic identity', () => {
  it('reports one cycle for parallel edges returning to the same ancestor', () => {
    const graph: Record<string, string[]> = { a: ['b'], b: ['a', 'a'] }
    expect(findDirectedCycles(Object.keys(graph), (id) => graph[id])).toEqual([['a', 'b', 'a']])
  })
})
