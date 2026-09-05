import type { ProcessSnapshot } from './processStorageTypes'

export function compareProcessSnapshotsNewestFirst(
  left: ProcessSnapshot,
  right: ProcessSnapshot
): number {
  if (left.createdAt !== right.createdAt) return right.createdAt - left.createdAt
  if (left.id === right.id) return 0
  return left.id > right.id ? -1 : 1
}
