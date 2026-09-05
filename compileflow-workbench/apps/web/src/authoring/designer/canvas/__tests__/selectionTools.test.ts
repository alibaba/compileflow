import type { Graph, Node } from '@antv/x6'
import { describe, expect, it, vi } from 'vitest'

import { createMultiSelectionTools } from '../selectionTools'

function createNode(x: number, y: number, width: number, height: number) {
  let position = { x, y }
  return {
    isNode: () => true,
    position: vi.fn((nextX?: number, nextY?: number) => {
      if (nextX !== undefined && nextY !== undefined) position = { x: nextX, y: nextY }
      return position
    }),
    size: () => ({ height, width }),
  } as unknown as Node
}

function createGraph(nodes: Node[]) {
  return {
    getSelectedCells: () => nodes,
    trigger: vi.fn(),
  } as unknown as Graph
}

describe('DistributionTools', () => {
  it('distributes middle nodes horizontally without overlapping the first node', () => {
    const nodes = [
      createNode(0, 10, 50, 30),
      createNode(70, 20, 50, 30),
      createNode(200, 30, 50, 30),
    ]
    const graph = createGraph(nodes)

    createMultiSelectionTools(graph).distribute.distributeHorizontal()

    expect(nodes[0].position()).toEqual({ x: 0, y: 10 })
    expect(nodes[1].position()).toEqual({ x: 100, y: 20 })
    expect(nodes[2].position()).toEqual({ x: 200, y: 30 })
    expect(graph.trigger).toHaveBeenCalledWith('node:moved', { node: nodes[1] })
  })

  it('distributes middle nodes vertically without overlapping the first node', () => {
    const nodes = [
      createNode(10, 0, 40, 50),
      createNode(20, 70, 40, 50),
      createNode(30, 200, 40, 50),
    ]
    const graph = createGraph(nodes)

    createMultiSelectionTools(graph).distribute.distributeVertical()

    expect(nodes[0].position()).toEqual({ x: 10, y: 0 })
    expect(nodes[1].position()).toEqual({ x: 20, y: 100 })
    expect(nodes[2].position()).toEqual({ x: 30, y: 200 })
    expect(graph.trigger).toHaveBeenCalledWith('node:moved', { node: nodes[1] })
  })
})
