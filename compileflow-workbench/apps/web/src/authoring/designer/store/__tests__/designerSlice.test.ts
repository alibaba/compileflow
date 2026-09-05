import { configureStore } from '@reduxjs/toolkit'
import type { StateWithHistory } from 'redux-undo'
import undoable from 'redux-undo'
import { beforeEach, describe, expect, it } from 'vitest'

import type { TbbpmConnection, TbbpmNode } from '../../types/tbbpm'
import type { EditorState } from '../editorSlice'
import editorReducer, {
  addConnection,
  addNode,
  deleteConnection,
  deleteNode,
  moveNode,
  replaceContainerChildren,
  updateConnection,
  updateProcessInfo,
  updateNode,
} from '../editorSlice'
import uiReducer, { selectEdge, selectNode } from '../uiSlice'

import { LIMITS } from '@/shared/constants'

// ---------------------------------------------------------------------------
// Local store type helpers
// ---------------------------------------------------------------------------

interface TestStoreState {
  editor: StateWithHistory<EditorState>
  ui: ReturnType<typeof uiReducer>
}

// Initialise currentProcess before every test so that addNode/addConnection reducers actually run.
// EditorState.currentProcess starts as null, and addNode/addConnection guard with
// `if (!state.currentProcess) return`, making every test that calls addNode a no-op without this bootstrap.
//
// redux-undo's `undoable()` wraps the reducer; to start with a pre-populated currentProcess we use
// `preloadedState` so the undoable history begins with currentProcess already set and past = [].
const bootstrapProcess = {
  id: 'test-flow',
  code: 'test',
  name: 'Test Process',
  type: 'TBBPM' as const,
  nodes: [],
  connections: [],
  createdAt: Date.now(),
  updatedAt: Date.now(),
}

function createTestStore(
  currentProcess: NonNullable<EditorState['currentProcess']> = bootstrapProcess
) {
  return configureStore<TestStoreState>({
    reducer: {
      editor: undoable(editorReducer, {
        // Use LIMITS.MAX_UNDO_HISTORY so the test store limit is always in sync with production.
        limit: LIMITS.MAX_UNDO_HISTORY,
        filter: (action) => {
          const undoableActions = [
            addNode.type,
            deleteNode.type,
            updateNode.type,
            replaceContainerChildren.type,
            moveNode.type,
            addConnection.type,
            deleteConnection.type,
            updateConnection.type,
            updateProcessInfo.type,
          ]
          return (undoableActions as string[]).includes(action.type)
        },
      }),
      ui: uiReducer,
    },
    preloadedState: {
      editor: {
        past: [],
        present: {
          currentProcess,
          isModified: false,
          changeToken: null,
          isLoading: false,
          isSaving: false,
          error: null,
          warnings: [],
          validationResult: null,
          lastSavedTime: null,
          operateBinding: null,
          activeLoadRequestId: null,
        },
        future: [],
      },
      ui: uiReducer(undefined, { type: '@@INIT' }),
    },
  })
}

describe('designerSlice - 节点操作', () => {
  let store: ReturnType<typeof createTestStore>

  beforeEach(() => {
    store = createTestStore()
  })

  it('应该能添加自动任务节点', () => {
    const node: TbbpmNode = {
      id: 'auto-1',
      type: 'autoTask',
      name: 'Process Data',
      position: { x: 100, y: 100 },
      properties: {
        action: {
          actionType: 'java',
          className: 'com.example.DataProcessor',
        },
      },
    }

    store.dispatch(addNode(node))

    const state = store.getState().editor.present
    expect(state.currentProcess?.nodes).toHaveLength(1)
    expect(state.currentProcess?.nodes[0]).toEqual(node)
  })

  it('应该拒绝向 TBBPM 流程添加 BPMN 节点', () => {
    expect(() =>
      store.dispatch(
        addNode({
          id: 'service-1',
          type: 'bpmn:ServiceTask',
          name: 'Service',
          position: { x: 100, y: 100 },
          properties: {},
        })
      )
    ).toThrow('Cannot add bpmn:ServiceTask to a TBBPM process')

    const state = store.getState().editor.present
    expect(state.currentProcess?.nodes).toEqual([])
    expect(state.isModified).toBe(false)
  })

  it('应该能更新节点属性', () => {
    const node: TbbpmNode = {
      id: 'exclusive-1',
      type: 'autoTask',
      name: 'Check Status',
      position: { x: 200, y: 100 },
      properties: { action: { actionType: 'java', className: 'com.example.Initial' } },
    }

    store.dispatch(addNode(node))
    store.dispatch(
      updateNode({
        id: 'exclusive-1',
        updates: {
          name: 'Updated Check',
          properties: {
            action: { actionType: 'java', className: 'com.example.UpdatedClass' },
          },
        },
      })
    )

    const state = store.getState().editor.present
    expect(state.currentProcess?.nodes[0].name).toBe('Updated Check')
    expect((state.currentProcess?.nodes[0] as TbbpmNode).properties.action?.className).toBe(
      'com.example.UpdatedClass'
    )
  })

  it('应该能删除节点', () => {
    const node: TbbpmNode = {
      id: 'wait-1',
      type: 'waitTask',
      name: 'Wait',
      position: { x: 100, y: 100 },
      properties: {},
    }

    store.dispatch(addNode(node))
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(1)

    store.dispatch(deleteNode('wait-1'))
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(0)
  })

  it('删除节点时应该删除相关连接', () => {
    // 添加两个节点
    store.dispatch(
      addNode({
        id: 'start-1',
        type: 'start',
        name: 'Start',
        position: { x: 100, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'auto-1',
        type: 'autoTask',
        name: 'Task',
        position: { x: 300, y: 100 },
        properties: {},
      })
    )

    // 添加连接
    const connection: TbbpmConnection = {
      id: 'edge-1',
      sourceId: 'start-1',
      targetId: 'auto-1',
      name: '',
      condition: '',
    }
    store.dispatch(addConnection(connection))

    expect(store.getState().editor.present.currentProcess?.connections).toHaveLength(1)

    // 删除源节点
    store.dispatch(deleteNode('start-1'))

    const state = store.getState().editor.present
    expect(state.currentProcess?.nodes).toHaveLength(1)
    expect(state.currentProcess?.connections).toHaveLength(0)
  })

  it('应该原子替换循环体归属', () => {
    store.dispatch(
      addNode({
        id: 'loop',
        type: 'while',
        name: 'Loop',
        position: { x: 0, y: 0 },
        properties: {
          condition: 'false',
          maxIterations: 10,
        },
      })
    )
    store.dispatch(
      addNode({
        id: 'body-a',
        type: 'autoTask',
        name: 'A',
        position: { x: 10, y: 10 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'body-b',
        type: 'autoTask',
        name: 'B',
        position: { x: 20, y: 10 },
        properties: {},
      })
    )

    store.dispatch(
      replaceContainerChildren({
        parentId: 'loop',
        childIds: ['body-b'],
      })
    )

    const nodes = store.getState().editor.present.currentProcess?.nodes as TbbpmNode[]
    expect(nodes.find((node) => node.id === 'body-a')?.parentId).toBeUndefined()
    expect(nodes.find((node) => node.id === 'body-b')?.parentId).toBe('loop')
  })

  it('容器归属变更必须拒绝自身或祖先形成父子环', () => {
    store.dispatch(
      addNode({
        id: 'outer',
        type: 'while',
        name: 'Outer',
        position: { x: 0, y: 0 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'inner',
        parentId: 'outer',
        type: 'while',
        name: 'Inner',
        position: { x: 10, y: 10 },
        properties: {},
      })
    )

    store.dispatch(
      replaceContainerChildren({
        parentId: 'inner',
        childIds: ['outer'],
      })
    )

    const nodes = store.getState().editor.present.currentProcess?.nodes as TbbpmNode[]
    expect(nodes.find((node) => node.id === 'outer')?.parentId).toBeUndefined()
    expect(nodes.find((node) => node.id === 'inner')?.parentId).toBe('outer')
  })

  it('从嵌套容器移除节点时应释放到直接父作用域', () => {
    store.dispatch(
      addNode({
        id: 'outer',
        type: 'while',
        name: 'Outer',
        position: { x: 0, y: 0 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'scope',
        parentId: 'outer',
        type: 'subBpm',
        name: 'Scope',
        position: { x: 10, y: 10 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'child',
        parentId: 'scope',
        type: 'autoTask',
        name: 'Child',
        position: { x: 20, y: 20 },
        properties: {},
      })
    )

    store.dispatch(replaceContainerChildren({ parentId: 'scope', childIds: [] }))

    const nodes = store.getState().editor.present.currentProcess?.nodes as TbbpmNode[]
    expect(nodes.find((node) => node.id === 'child')?.parentId).toBe('outer')
  })

  it('删除循环容器时应该删除完整子树及相关连接', () => {
    store.dispatch(
      addNode({
        id: 'loop',
        type: 'foreach',
        name: 'Loop',
        position: { x: 0, y: 0 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'nested-loop',
        parentId: 'loop',
        type: 'while',
        name: 'Nested',
        position: { x: 10, y: 10 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'nested-body',
        parentId: 'nested-loop',
        type: 'autoTask',
        name: 'Body',
        position: { x: 20, y: 20 },
        properties: {},
      })
    )
    store.dispatch(
      addConnection({
        id: 'nested-edge',
        sourceId: 'nested-loop',
        targetId: 'nested-body',
      })
    )

    store.dispatch(deleteNode('loop'))

    const flow = store.getState().editor.present.currentProcess
    expect(flow?.nodes).toHaveLength(0)
    expect(flow?.connections).toHaveLength(0)
  })

  it('移动容器时应该按相同位移移动完整子树', () => {
    store = createTestStore({ ...bootstrapProcess, type: 'BPMN' })
    store.dispatch(
      addNode({
        id: 'sub',
        type: 'bpmn:SubProcess',
        name: 'Subprocess',
        position: { x: 100, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'nested',
        parentId: 'sub',
        type: 'bpmn:SubProcess',
        name: 'Nested',
        position: { x: 140, y: 150 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'task',
        parentId: 'nested',
        type: 'bpmn:ServiceTask',
        name: 'Task',
        position: { x: 180, y: 190 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'outside',
        type: 'bpmn:ServiceTask',
        name: 'Outside',
        position: { x: 500, y: 500 },
        properties: {},
      })
    )

    store.dispatch(moveNode({ id: 'sub', x: 130, y: 80 }))

    const nodes = store.getState().editor.present.currentProcess?.nodes
    expect(nodes?.find((node) => node.id === 'sub')?.position).toEqual({ x: 130, y: 80 })
    expect(nodes?.find((node) => node.id === 'nested')?.position).toEqual({ x: 170, y: 130 })
    expect(nodes?.find((node) => node.id === 'task')?.position).toEqual({ x: 210, y: 170 })
    expect(nodes?.find((node) => node.id === 'outside')?.position).toEqual({ x: 500, y: 500 })
  })
})

describe('designerSlice - 连接操作', () => {
  let store: ReturnType<typeof createTestStore>

  beforeEach(() => {
    store = createTestStore()

    // 添加测试节点
    store.dispatch(
      addNode({
        id: 'start-1',
        type: 'start',
        name: 'Start',
        position: { x: 100, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'auto-1',
        type: 'autoTask',
        name: 'Task',
        position: { x: 300, y: 100 },
        properties: {},
      })
    )
  })

  it('应该能添加连接', () => {
    const connection: TbbpmConnection = {
      id: 'edge-1',
      sourceId: 'start-1',
      targetId: 'auto-1',
      name: '',
      condition: '',
    }

    store.dispatch(addConnection(connection))

    const state = store.getState().editor.present
    expect(state.currentProcess?.connections).toHaveLength(1)
    expect(state.currentProcess?.connections[0]).toEqual(connection)
  })

  it('应该能删除连接', () => {
    const connection: TbbpmConnection = {
      id: 'edge-1',
      sourceId: 'start-1',
      targetId: 'auto-1',
      name: '',
      condition: '',
    }

    store.dispatch(addConnection(connection))
    expect(store.getState().editor.present.currentProcess?.connections).toHaveLength(1)

    store.dispatch(deleteConnection('edge-1'))
    expect(store.getState().editor.present.currentProcess?.connections).toHaveLength(0)
  })
})

describe('designerSlice - 选择状态', () => {
  let store: ReturnType<typeof createTestStore>

  beforeEach(() => {
    store = createTestStore()
  })

  it('应该能选中节点', () => {
    store.dispatch(selectNode('node-1'))

    const state = store.getState().ui
    expect(state.selectedNodeId).toBe('node-1')
    expect(state.selectedEdge).toBeNull()
  })

  it('应该能选中连接', () => {
    const connection = {
      id: 'edge-1',
      sourceId: 'start-1',
      targetId: 'auto-1',
      name: '',
      condition: '',
    }
    store.dispatch(selectEdge(connection))

    const state = store.getState().ui
    expect(state.selectedEdge).toEqual(connection)
    expect(state.selectedNodeId).toBeNull()
  })

  it('选中节点时应该清除连接选中', () => {
    const connection = {
      id: 'edge-1',
      sourceId: 'start-1',
      targetId: 'auto-1',
      name: '',
      condition: '',
    }
    store.dispatch(selectEdge(connection))
    expect(store.getState().ui.selectedEdge).toEqual(connection)

    store.dispatch(selectNode('node-1'))

    const state = store.getState().ui
    expect(state.selectedNodeId).toBe('node-1')
    expect(state.selectedEdge).toBeNull()
  })
})

describe('designerSlice - 撤销重做', () => {
  let store: ReturnType<typeof createTestStore>

  beforeEach(() => {
    store = createTestStore()
  })

  it('应该支持撤销添加节点', () => {
    const node: TbbpmNode = {
      id: 'node-1',
      type: 'autoTask',
      name: 'Test',
      position: { x: 100, y: 100 },
      properties: {},
    }

    store.dispatch(addNode(node))
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(1)

    // 撤销
    store.dispatch({ type: '@@redux-undo/UNDO' })
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(0)
  })

  it('应该支持重做', () => {
    const node: TbbpmNode = {
      id: 'node-1',
      type: 'autoTask',
      name: 'Test',
      position: { x: 100, y: 100 },
      properties: {},
    }

    store.dispatch(addNode(node))
    store.dispatch({ type: '@@redux-undo/UNDO' })
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(0)

    // 重做
    store.dispatch({ type: '@@redux-undo/REDO' })
    expect(store.getState().editor.present.currentProcess?.nodes).toHaveLength(1)
  })

  it('应该限制历史记录为 LIMITS.MAX_UNDO_HISTORY 步', () => {
    // Add (LIMITS.MAX_UNDO_HISTORY + 1) nodes — exactly one more than the limit.
    for (let i = 0; i <= LIMITS.MAX_UNDO_HISTORY; i++) {
      store.dispatch(
        addNode({
          id: `node-${i}`,
          type: 'autoTask',
          name: `Node ${i}`,
          position: { x: 100, y: 100 },
          properties: {},
        })
      )
    }

    const state = store.getState().editor
    expect(state.past.length).toBeLessThanOrEqual(LIMITS.MAX_UNDO_HISTORY)
  })
})

describe('designerSlice - 复杂场景', () => {
  let store: ReturnType<typeof createTestStore>

  beforeEach(() => {
    store = createTestStore()
  })

  it('应该能处理复杂流程', () => {
    // 添加多个节点
    store.dispatch(
      addNode({
        id: 'start-1',
        type: 'start',
        name: 'Start',
        position: { x: 100, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'auto-1',
        type: 'autoTask',
        name: 'Task 1',
        position: { x: 300, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'exclusive-1',
        type: 'exclusive',
        name: 'Exclusive',
        position: { x: 500, y: 100 },
        properties: {},
      })
    )
    store.dispatch(
      addNode({
        id: 'end-1',
        type: 'end',
        name: 'End',
        position: { x: 700, y: 100 },
        properties: {},
      })
    )

    // 添加多个连接
    store.dispatch(
      addConnection({
        id: 'edge-1',
        sourceId: 'start-1',
        targetId: 'auto-1',
        name: '',
        condition: '',
      })
    )
    store.dispatch(
      addConnection({
        id: 'edge-2',
        sourceId: 'auto-1',
        targetId: 'exclusive-1',
        name: '',
        condition: '',
      })
    )
    store.dispatch(
      addConnection({
        id: 'edge-3',
        sourceId: 'exclusive-1',
        targetId: 'end-1',
        name: 'Yes',
        condition: '${approved}',
      })
    )

    const state = store.getState().editor.present
    expect(state.currentProcess?.nodes).toHaveLength(4)
    expect(state.currentProcess?.connections).toHaveLength(3)
  })
})
