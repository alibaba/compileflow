interface DfsFrame {
  nodeId: string
  nextNeighborIndex: number
}

/**
 * Finds directed back-edge cycles without using the JavaScript call stack.
 *
 * Each reported path repeats its first node at the end, and equivalent
 * rotations are deduplicated.
 */
export function findDirectedCycles(
  nodeIds: Iterable<string>,
  neighborsOf: (nodeId: string) => readonly string[]
): string[][] {
  const colors = new Map<string, 'white' | 'gray' | 'black'>()
  for (const nodeId of nodeIds) colors.set(nodeId, 'white')

  const cycles: string[][] = []
  const seenCycles = new Set<string>()

  for (const startId of colors.keys()) {
    if (colors.get(startId) !== 'white') continue

    const path: string[] = [startId]
    const pathIndexes = new Map<string, number>([[startId, 0]])
    const stack: DfsFrame[] = [{ nodeId: startId, nextNeighborIndex: 0 }]
    colors.set(startId, 'gray')

    while (stack.length > 0) {
      const frame = stack[stack.length - 1]
      const neighbors = neighborsOf(frame.nodeId)
      if (frame.nextNeighborIndex >= neighbors.length) {
        stack.pop()
        colors.set(frame.nodeId, 'black')
        pathIndexes.delete(frame.nodeId)
        path.pop()
        continue
      }

      const neighborId = neighbors[frame.nextNeighborIndex]
      frame.nextNeighborIndex += 1
      const neighborColor = colors.get(neighborId)
      if (neighborColor === 'white') {
        colors.set(neighborId, 'gray')
        pathIndexes.set(neighborId, path.length)
        path.push(neighborId)
        stack.push({ nodeId: neighborId, nextNeighborIndex: 0 })
        continue
      }
      if (neighborColor !== 'gray') continue

      const cycleStart = pathIndexes.get(neighborId)
      if (cycleStart === undefined) continue
      const normalized = normalizeCycle([...path.slice(cycleStart), neighborId])
      const signature = normalized.join('\u0000')
      if (!seenCycles.has(signature)) {
        seenCycles.add(signature)
        cycles.push(normalized)
      }
    }
  }

  return cycles
}

/** Collects every node reachable from the supplied roots in linear time. */
export function collectReachableNodes(
  roots: Iterable<string>,
  neighborsOf: (nodeId: string) => readonly string[]
): Set<string> {
  const reachable = new Set<string>()
  const queue = Array.from(roots)
  for (let cursor = 0; cursor < queue.length; cursor += 1) {
    const nodeId = queue[cursor]
    if (reachable.has(nodeId)) continue
    reachable.add(nodeId)
    for (const neighborId of neighborsOf(nodeId)) {
      if (!reachable.has(neighborId)) queue.push(neighborId)
    }
  }
  return reachable
}

function normalizeCycle(cycle: string[]): string[] {
  const body = cycle.slice(0, -1)
  let firstIndex = 0
  for (let index = 1; index < body.length; index += 1) {
    if (body[index] < body[firstIndex]) firstIndex = index
  }
  const normalizedBody = [...body.slice(firstIndex), ...body.slice(0, firstIndex)]
  return [...normalizedBody, normalizedBody[0]]
}
