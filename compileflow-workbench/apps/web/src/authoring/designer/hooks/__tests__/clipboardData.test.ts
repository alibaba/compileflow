import { describe, expect, it } from 'vitest'

import {
  DESIGNER_CLIPBOARD_TTL_MS,
  isClipboardDataFresh,
  parseClipboardData,
} from '../clipboardData'

const validData = {
  modelType: 'TBBPM' as const,
  connections: [],
  messages: [],
  nodes: [
    {
      id: 'task-1',
      type: 'autoTask',
      name: 'Task',
      position: { x: 10, y: 20 },
      properties: {
        action: { actionType: 'java', className: 'example.Task', method: 'run' },
      },
    },
  ],
  timestamp: 1_000,
}

describe('clipboard data boundary', () => {
  it('parses a valid designer clipboard payload', () => {
    expect(parseClipboardData(JSON.stringify(validData))).toEqual(validData)
  })

  it.each([
    null,
    '{',
    JSON.stringify({ ...validData, nodes: 'invalid' }),
    JSON.stringify({ ...validData, nodes: [] }),
    JSON.stringify({ ...validData, nodes: [{ id: 'task-1' }] }),
    JSON.stringify({ ...validData, timestamp: Number.NaN }),
    JSON.stringify({ ...validData, modelType: 'BPMN' }),
    JSON.stringify({ ...validData, nodes: [...validData.nodes, ...validData.nodes] }),
    JSON.stringify({ ...validData, nodes: [{ ...validData.nodes[0], parentId: 'missing' }] }),
    JSON.stringify({ ...validData, nodes: [{ ...validData.nodes[0], parentId: 'task-1' }] }),
    JSON.stringify({
      ...validData,
      connections: [{ id: 'edge', sourceId: 'task-1', targetId: 'missing' }],
    }),
    JSON.stringify({ ...validData, messages: [{ id: 'task-1', name: 'Conflicting' }] }),
  ])('rejects malformed clipboard payloads', (raw) => {
    expect(parseClipboardData(raw)).toBeNull()
  })

  it('rejects cycles between distinct container nodes', () => {
    expect(
      parseClipboardData(
        JSON.stringify({
          ...validData,
          nodes: [
            { ...validData.nodes[0], id: 'a', parentId: 'b' },
            { ...validData.nodes[0], id: 'b', parentId: 'a' },
          ],
        })
      )
    ).toBeNull()
  })

  it('accepts only current, non-future clipboard data within the TTL', () => {
    expect(isClipboardDataFresh(validData, 1_000)).toBe(true)
    expect(isClipboardDataFresh(validData, 1_000 + DESIGNER_CLIPBOARD_TTL_MS)).toBe(true)
    expect(isClipboardDataFresh(validData, 999)).toBe(false)
    expect(isClipboardDataFresh(validData, 1_001 + DESIGNER_CLIPBOARD_TTL_MS)).toBe(false)
  })
})
