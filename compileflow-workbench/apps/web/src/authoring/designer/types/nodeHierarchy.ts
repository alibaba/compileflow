export interface HierarchicalNode {
  id: string
  parentId?: string
}

export function isNodeAncestor<T extends HierarchicalNode>(
  candidateId: string,
  node: T,
  nodesById: ReadonlyMap<string, T>
): boolean {
  const visited = new Set<string>()
  let parentId = node.parentId
  while (parentId) {
    if (parentId === candidateId) return true
    if (visited.has(parentId)) return true
    visited.add(parentId)
    parentId = nodesById.get(parentId)?.parentId
  }
  return false
}
