import { z } from 'zod'

import type { BaseConnection, BaseNode, BpmnMessageDefinition } from '../types/flowDefinition'
import { isBpmnNode, isTbbpmNode } from '../types/typeGuards'

import type { ProcessModelType } from '@/shared/contracts'

export const DESIGNER_CLIPBOARD_KEY = 'compileflow:designer-clipboard'
export const DESIGNER_CLIPBOARD_TTL_MS = 60 * 60 * 1000

const nodeSchema = z.object({
  id: z.string().min(1),
  parentId: z.string().min(1).optional(),
  type: z.string().min(1),
  name: z.string().optional(),
  documentation: z.string().optional(),
  position: z.object({
    x: z.number().finite(),
    y: z.number().finite(),
  }),
  size: z
    .object({
      width: z.number().finite().positive(),
      height: z.number().finite().positive(),
    })
    .optional(),
  properties: z.record(z.unknown()),
  metadata: z
    .object({
      editable: z.boolean().optional(),
      deletable: z.boolean().optional(),
      style: z.record(z.unknown()).optional(),
      uiState: z.record(z.unknown()).optional(),
    })
    .optional(),
})

const clipboardDataSchema = z.object({
  modelType: z.enum(['TBBPM', 'BPMN']),
  nodes: z.array(nodeSchema).min(1),
  connections: z.array(
    z.object({
      id: z.string().min(1),
      sourceId: z.string().min(1),
      targetId: z.string().min(1),
      name: z.string().optional(),
      condition: z.string().optional(),
      waypoints: z.array(z.object({ x: z.number().finite(), y: z.number().finite() })).optional(),
    })
  ),
  messages: z.array(z.object({ id: z.string().min(1), name: z.string() })),
  timestamp: z.number().finite().nonnegative(),
})

export interface ClipboardData {
  modelType: ProcessModelType
  nodes: BaseNode[]
  connections: BaseConnection[]
  messages: BpmnMessageDefinition[]
  timestamp: number
}

export function parseClipboardData(raw: string | null): ClipboardData | null {
  if (raw === null) return null

  try {
    const result = clipboardDataSchema.safeParse(JSON.parse(raw))
    if (!result.success) return null
    const data = result.data
    const nodes = new Map(data.nodes.map((node) => [node.id, node]))
    const identifiers = [
      ...nodes.keys(),
      ...data.connections.map((connection) => connection.id),
      ...data.messages.map((message) => message.id),
    ]
    if (
      nodes.size !== data.nodes.length ||
      new Set(identifiers).size !== identifiers.length ||
      data.nodes.some(
        (node) => !(data.modelType === 'BPMN' ? isBpmnNode(node) : isTbbpmNode(node))
      ) ||
      data.connections.some(
        (connection) => !nodes.has(connection.sourceId) || !nodes.has(connection.targetId)
      )
    ) {
      return null
    }
    for (const node of data.nodes) {
      const ancestors = new Set([node.id])
      let parentId = node.parentId
      while (parentId) {
        if (ancestors.has(parentId) || !nodes.has(parentId)) return null
        ancestors.add(parentId)
        parentId = nodes.get(parentId)?.parentId
      }
    }
    return data
  } catch {
    return null
  }
}

export function isClipboardDataFresh(data: ClipboardData, now = Date.now()): boolean {
  const age = now - data.timestamp
  return age >= 0 && age <= DESIGNER_CLIPBOARD_TTL_MS
}
