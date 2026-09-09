import { z } from 'zod'

import { CONNECTION_PORTS, type BaseConnection } from '../types/graphTypes'

const geometrySchema = z.object({
  sourcePort: z.enum(CONNECTION_PORTS).optional(),
  targetPort: z.enum(CONNECTION_PORTS).optional(),
  waypoints: z.array(z.object({ x: z.number().finite(), y: z.number().finite() })).optional(),
})

// XML processing instructions carry editor-only geometry without extending the engine grammar.
export function writeConnectionGeometry(connection: BaseConnection): string {
  const geometry = geometrySchema.parse(connection)
  if (Object.values(geometry).every((value) => value === undefined)) return ''
  return `<?workbench-edge ${JSON.stringify(geometry)}?>`
}

export function readConnectionGeometry(element: Element): Partial<BaseConnection> {
  const instructions = Array.from(element.childNodes).filter(
    (node) => node.nodeType === 7 && node.nodeName === 'workbench-edge'
  )
  if (instructions.length > 1) throw new Error('Duplicate connection geometry')
  if (instructions.length === 0) return {}
  const geometry = geometrySchema.parse(JSON.parse(instructions[0].nodeValue || ''))
  // BPMN DI includes endpoints; editor vertices contain only user-created bends.
  return { ...geometry, waypoints: geometry.waypoints }
}
