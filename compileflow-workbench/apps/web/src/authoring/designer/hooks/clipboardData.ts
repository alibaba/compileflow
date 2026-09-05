import { z } from 'zod'

import type { BaseNode } from '../types/flowDefinition'

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
  nodes: z.array(nodeSchema),
  timestamp: z.number().finite().nonnegative(),
  source: z.string().min(1),
})

export interface ClipboardData {
  nodes: BaseNode[]
  timestamp: number
  source: string
}

export function parseClipboardData(raw: string | null): ClipboardData | null {
  if (raw === null) return null

  try {
    const result = clipboardDataSchema.safeParse(JSON.parse(raw))
    return result.success ? result.data : null
  } catch {
    return null
  }
}

export function isClipboardDataFresh(data: ClipboardData, now = Date.now()): boolean {
  const age = now - data.timestamp
  return age >= 0 && age <= DESIGNER_CLIPBOARD_TTL_MS
}
