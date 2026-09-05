// Monotonically increasing counter — prevents ID collisions within the same millisecond.
let counter = 0

/** Generates a globally unique element ID for nodes and edges on the canvas. */
export function generateId(): string {
  return `node_${Date.now()}_${++counter}_${Math.random().toString(36).slice(2, 8)}`
}

export function generateCode(prefix: string = 'node'): string {
  return `${prefix}_${Date.now()}_${++counter}`
}
