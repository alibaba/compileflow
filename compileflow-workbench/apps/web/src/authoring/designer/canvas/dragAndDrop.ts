import { Dnd, Graph } from '@antv/x6'

export function createUnifiedDnd(graph: Graph, dndContainer: HTMLElement): Dnd {
  return new Dnd({
    target: graph,
    dndContainer,
    scaled: true,
    validateNode: () => true,
    getDropNode: (node) => node.clone({ keepId: false, deep: true }),
  })
}
