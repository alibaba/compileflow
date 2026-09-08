import type { Cell, Graph, Node } from '@antv/x6'

function isNode(cell: Cell): cell is Node {
  return cell.isNode()
}

function selectedNodes(graph: Graph): Node[] {
  return graph.getSelectedCells().filter(isNode)
}

class SelectionTools {
  private graph: Graph

  constructor(graph: Graph) {
    this.graph = graph
  }

  enableRubberband(enabled: boolean = true) {
    const selection = this.graph.getPlugin('selection') as {
      options?: { rubberband: boolean }
    } | null
    if (selection?.options) {
      selection.options.rubberband = enabled
    }
  }

  getSelectedNodes(): Node[] {
    return selectedNodes(this.graph)
  }

  getSelectedEdges() {
    return this.graph.getSelectedCells().filter((cell) => cell.isEdge())
  }

  selectAll() {
    const allCells = this.graph.getCells()
    this.graph.select(allCells)
  }

  unselectAll() {
    this.graph.unselect(this.graph.getSelectedCells())
  }

  invertSelection() {
    const allCells = this.graph.getCells()
    const selectedCells = this.graph.getSelectedCells()
    const unselectedCells = allCells.filter((cell) => !selectedCells.includes(cell))

    this.graph.unselect(selectedCells)
    this.graph.select(unselectedCells)
  }

  selectByType(nodeType: string) {
    const cells = this.graph.getCells().filter((cell) => {
      if (!cell.isNode()) return false
      return cell.shape === nodeType
    })

    this.graph.select(cells)
  }
}

/** Programmatic moves do not emit X6 `node:moved`; fire it so canvas Redux sync persists layout. */
function moveNodeAndNotify(graph: Graph, node: Node, x: number, y: number) {
  const current = node.position()
  if (current.x === x && current.y === y) return
  node.position(x, y)
  void graph.trigger('node:moved', { node })
}

class BatchOperationTools {
  private graph: Graph

  constructor(graph: Graph) {
    this.graph = graph
  }

  moveSelected(dx: number, dy: number) {
    const nodes = selectedNodes(this.graph)

    nodes.forEach((node) => {
      const position = node.position()
      moveNodeAndNotify(this.graph, node, position.x + dx, position.y + dy)
    })
  }

  setSelectedStyle(attrs: NonNullable<Cell['attrs']>) {
    const selectedCells = this.graph.getSelectedCells()

    selectedCells.forEach((cell) => {
      cell.attr(attrs)
    })
  }
}

class AlignmentTools {
  private graph: Graph

  constructor(graph: Graph) {
    this.graph = graph
  }

  alignLeft() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    nodes.forEach((node) => {
      const position = node.position()
      moveNodeAndNotify(this.graph, node, bounds.minX, position.y)
    })
  }

  alignRight() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    nodes.forEach((node) => {
      const position = node.position()
      const size = node.size()
      moveNodeAndNotify(this.graph, node, bounds.maxX - size.width, position.y)
    })
  }

  alignTop() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    nodes.forEach((node) => {
      const position = node.position()
      moveNodeAndNotify(this.graph, node, position.x, bounds.minY)
    })
  }

  alignBottom() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    nodes.forEach((node) => {
      const position = node.position()
      const size = node.size()
      moveNodeAndNotify(this.graph, node, position.x, bounds.maxY - size.height)
    })
  }

  alignCenterHorizontal() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    const centerY = (bounds.minY + bounds.maxY) / 2

    nodes.forEach((node) => {
      const position = node.position()
      const size = node.size()
      moveNodeAndNotify(this.graph, node, position.x, centerY - size.height / 2)
    })
  }

  alignCenterVertical() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 2) return

    const bounds = this.getNodesBounds(nodes)
    if (!bounds) return

    const centerX = (bounds.minX + bounds.maxX) / 2

    nodes.forEach((node) => {
      const position = node.position()
      const size = node.size()
      moveNodeAndNotify(this.graph, node, centerX - size.width / 2, position.y)
    })
  }

  private getNodesBounds(nodes: Node[]) {
    if (nodes.length === 0) return null

    let minX = Infinity
    let minY = Infinity
    let maxX = -Infinity
    let maxY = -Infinity

    nodes.forEach((node) => {
      const bbox = node.getBBox()
      minX = Math.min(minX, bbox.x)
      minY = Math.min(minY, bbox.y)
      maxX = Math.max(maxX, bbox.x + bbox.width)
      maxY = Math.max(maxY, bbox.y + bbox.height)
    })

    return { minX, minY, maxX, maxY }
  }
}

class DistributionTools {
  private graph: Graph

  constructor(graph: Graph) {
    this.graph = graph
  }

  distributeHorizontal() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 3) return

    // Sort by X coordinate.
    const sortedNodes = [...nodes].sort((a, b) => {
      return a.position().x - b.position().x
    })

    const firstNode = sortedNodes[0]
    const lastNode = sortedNodes[sortedNodes.length - 1]

    const firstX = firstNode.position().x
    const lastX = lastNode.position().x + lastNode.size().width
    const totalWidth = lastX - firstX

    // Calculate total node width.
    const nodesWidth = sortedNodes.reduce((sum, node) => sum + node.size().width, 0)

    // Calculate spacing.
    const spacing = (totalWidth - nodesWidth) / (sortedNodes.length - 1)

    // Distribute nodes.
    let currentX = firstX + firstNode.size().width + spacing
    sortedNodes.forEach((node, index) => {
      if (index === 0 || index === sortedNodes.length - 1) return

      const position = node.position()
      moveNodeAndNotify(this.graph, node, currentX, position.y)
      currentX += node.size().width + spacing
    })
  }

  distributeVertical() {
    const nodes = selectedNodes(this.graph)
    if (nodes.length < 3) return

    // Sort by Y coordinate.
    const sortedNodes = [...nodes].sort((a, b) => {
      return a.position().y - b.position().y
    })

    const firstNode = sortedNodes[0]
    const lastNode = sortedNodes[sortedNodes.length - 1]

    const firstY = firstNode.position().y
    const lastY = lastNode.position().y + lastNode.size().height
    const totalHeight = lastY - firstY

    // Calculate total node height.
    const nodesHeight = sortedNodes.reduce((sum, node) => sum + node.size().height, 0)

    // Calculate spacing.
    const spacing = (totalHeight - nodesHeight) / (sortedNodes.length - 1)

    // Distribute nodes.
    let currentY = firstY + firstNode.size().height + spacing
    sortedNodes.forEach((node, index) => {
      if (index === 0 || index === sortedNodes.length - 1) return

      const position = node.position()
      moveNodeAndNotify(this.graph, node, position.x, currentY)
      currentY += node.size().height + spacing
    })
  }
}

export function createMultiSelectionTools(graph: Graph) {
  return {
    selection: new SelectionTools(graph),
    batch: new BatchOperationTools(graph),
    align: new AlignmentTools(graph),
    distribute: new DistributionTools(graph),
  }
}
