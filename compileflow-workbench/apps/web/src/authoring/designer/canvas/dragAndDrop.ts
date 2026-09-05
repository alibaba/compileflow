import { Dnd, Graph } from '@antv/x6'

export function createUnifiedDnd(graph: Graph): Dnd {
  return new Dnd({
    target: graph,
    scaled: false,
    validateNode: () => true,
    getDropNode: (node) => node.clone({ keepId: false, deep: true }),
  })
}
