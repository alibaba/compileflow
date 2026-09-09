import type { Edge, Node } from '@antv/x6'
import { act, render } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useCanvasSync } from '../../hooks/useCanvasSync'
import { useX6Graph } from '../../hooks/useX6Graph'
import { loadOperateProcess } from '../../store/editorSlice'
import BpmnCanvas from '../BpmnCanvas'
import TbbpmCanvas from '../TbbpmCanvas'

import { store } from '@/app/store'

const graph = vi.hoisted(() => ({ on: vi.fn(), select: vi.fn() }))
vi.mock('@/app/hooks', () => ({
  useAppDispatch: () => store.dispatch,
  useAppSelector: (selector: (state: ReturnType<typeof store.getState>) => unknown) =>
    selector(store.getState()),
}))
vi.mock('../../context', () => ({ useDesignerContext: () => ({ graphRef: { current: null } }) }))
vi.mock('../../hooks/useCanvasSync', () => ({ useCanvasSync: vi.fn() }))
vi.mock('../../hooks/useX6Graph', () => ({
  useX6Graph: vi.fn((_container: unknown, options: { onReady: (value: unknown) => void }) => {
    options.onReady(graph)
    return { current: graph }
  }),
}))
vi.mock('../nodes/registerBpmnNodes', () => ({ registerBpmnNodes: vi.fn() }))
vi.mock('../nodes/registerNodes', () => ({
  registerTbbpmNodes: vi.fn(),
  getNodeConfig: () => ({ width: 100, height: 80 }),
}))

const original = {
  id: 'edge',
  sourceId: 'a',
  targetId: 'b',
  sourcePort: 'right',
  targetPort: 'left',
  name: 'Branch',
  condition: 'ok',
  waypoints: [{ x: 10, y: 20 }],
}

describe.each(['BPMN', 'TBBPM'] as const)('%s canvas connection round-trip', (type) => {
  beforeEach(() => {
    vi.clearAllMocks()
    store.dispatch(loadOperateProcess.pending('document', 'flow'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow:
            type === 'TBBPM'
              ? {
                  type,
                  id: 'flow',
                  code: 'flow',
                  name: 'Flow',
                  nodes: [
                    { id: 'start', type: 'start', position: { x: 0, y: 0 }, properties: {} },
                    {
                      id: 'task',
                      type: 'autoTask',
                      position: { x: 100, y: 0 },
                      properties: {},
                    },
                    { id: 'end', type: 'end', position: { x: 200, y: 0 }, properties: {} },
                    { id: 'break', type: 'break', position: { x: 300, y: 0 }, properties: {} },
                    {
                      id: 'continue',
                      type: 'continue',
                      position: { x: 400, y: 0 },
                      properties: {},
                    },
                  ],
                  connections: [original],
                }
              : {
                  type,
                  id: 'flow',
                  code: 'flow',
                  name: 'Flow',
                  nodes: [],
                  connections: [original],
                },
          warnings: [],
          operateProcessCode: 'flow',
          revision: 1,
        },
        'document',
        'flow'
      )
    )
  })

  const mount = () => render(type === 'BPMN' ? <BpmnCanvas /> : <TbbpmCanvas />)

  it('restores persisted TBBPM node sizes on creation and history synchronization', () => {
    if (type !== 'TBBPM') return
    mount()
    const calls = vi.mocked(useCanvasSync).mock.calls
    const options = calls[calls.length - 1][2]
    const node = {
      id: 'task',
      type: 'autoTask' as const,
      name: 'Task',
      position: { x: 10, y: 20 },
      size: { width: 220, height: 140 },
      properties: {},
    }
    expect(options.nodeToX6Cell(node)).toMatchObject(node.size)
    const cell = {
      position: vi.fn(() => node.position),
      getSize: () => ({ width: 100, height: 80 }),
      getData: () => ({ label: 'Task' }),
      setData: vi.fn(),
      resize: vi.fn(),
    }
    expect(options.checkNodeChanged(cell as unknown as Node, node)).toBe(true)
    options.syncNodeToCell(cell as unknown as Node, node)
    expect(cell.resize).toHaveBeenCalledWith(220, 140)
  })

  it('reconnects an existing edge once without losing its condition or name', () => {
    mount()
    const handler = graph.on.mock.calls.find(([name]) => name === 'edge:connected')?.[1]
    act(() => {
      handler({
        edge: {
          id: 'edge',
          getSourceCellId: () => 'a',
          getTargetCellId: () => 'c',
          getSourcePortId: () => 'right',
          getTargetPortId: () => 'left',
          getVertices: () => [{ x: 30, y: 40 }],
        },
      })
    })
    expect(store.getState().editor.present.currentProcess?.connections).toEqual([
      { ...original, targetId: 'c', waypoints: [{ x: 30, y: 40 }] },
    ])
  })

  it('creates edges with their persisted waypoints', () => {
    mount()
    const calls = vi.mocked(useCanvasSync).mock.calls
    const options = calls[calls.length - 1][2]
    expect(options.connectionToX6Edge(original)).toMatchObject({
      vertices: original.waypoints,
      zIndex: -1,
      source: { cell: 'a', port: 'right' },
      target: { cell: 'b', port: 'left' },
    })
  })

  it('detects a side-only change on the same connected nodes', () => {
    mount()
    const calls = vi.mocked(useCanvasSync).mock.calls
    const options = calls[calls.length - 1][2]
    const edge = {
      getData: () => ({ condition: original.condition }),
      getLabels: () => [{ attrs: { label: { text: original.name } } }],
      getSourceCellId: () => original.sourceId,
      getTargetCellId: () => original.targetId,
      getSourcePortId: () => 'bottom',
      getTargetPortId: () => original.targetPort,
      getVertices: () => original.waypoints,
    }
    expect(options.checkEdgeChanged(edge as unknown as Edge, original)).toBe(true)
    expect(
      options.checkEdgeChanged(edge as unknown as Edge, { ...original, sourcePort: 'bottom' })
    ).toBe(false)
  })

  it('enforces terminal and entry-only TBBPM port directions', () => {
    if (type !== 'TBBPM') return
    mount()
    const calls = vi.mocked(useX6Graph).mock.calls
    const validateConnection = calls[calls.length - 1][1]?.validateConnection
    expect(validateConnection).toBeTypeOf('function')
    expect(
      validateConnection?.({
        sourceView: { cell: { id: 'task', shape: 'tbbpm-auto-task' } },
        targetView: { cell: { id: 'start', shape: 'tbbpm-start' } },
      })
    ).toBe(false)
    for (const [id, shape] of [
      ['end', 'tbbpm-end'],
      ['break', 'tbbpm-break'],
      ['continue', 'tbbpm-continue'],
    ]) {
      expect(
        validateConnection?.({
          sourceView: { cell: { id, shape } },
          targetView: { cell: { id: 'task', shape: 'tbbpm-auto-task' } },
        })
      ).toBe(false)
    }
    expect(
      validateConnection?.({
        sourceView: { cell: { id: 'start', shape: 'tbbpm-start' } },
        targetView: { cell: { id: 'task', shape: 'tbbpm-auto-task' } },
      })
    ).toBe(true)
  })

  it('restores endpoints and clears vertices when undo changes only geometry', () => {
    mount()
    const calls = vi.mocked(useCanvasSync).mock.calls
    const options = calls[calls.length - 1][2]
    const edge = {
      getData: () => ({ condition: original.condition }),
      getLabels: () => [{ attrs: { label: { text: original.name } } }],
      getSourceCellId: () => 'a',
      getTargetCellId: () => 'c',
      getSourcePortId: () => 'right',
      getTargetPortId: () => 'left',
      getVertices: () => original.waypoints,
      setData: vi.fn(),
      setLabels: vi.fn(),
      setVertices: vi.fn(),
      setSource: vi.fn(),
      setTarget: vi.fn(),
    }
    const restored = { ...original, waypoints: undefined }
    expect(options.checkEdgeChanged(edge as unknown as Edge, restored)).toBe(true)
    options.syncEdgeToCell(edge as unknown as Edge, restored)
    expect(edge.setTarget).toHaveBeenCalledWith({ cell: 'b', port: 'left' })
    expect(edge.setVertices).toHaveBeenCalledWith([])
  })
})
