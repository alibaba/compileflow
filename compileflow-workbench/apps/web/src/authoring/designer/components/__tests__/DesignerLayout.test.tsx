import { act, render, waitFor } from '@testing-library/react'
import { App } from 'antd'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { loadOperateProcess, updateProcessInfo } from '../../store/editorSlice'
import {
  selectNode,
  setRightPanelTab,
  setSidePanelsCollapsed,
  showContextMenu,
} from '../../store/uiSlice'
import { DesignerLayout } from '../DesignerLayout'

import { store } from '@/app/store'

const panels = vi.hoisted(() => ({ contextMenu: vi.fn(), debug: vi.fn(), validation: vi.fn() }))
const viewport = vi.hoisted(() => ({ mobile: false }))
const graph = vi.hoisted(() => ({
  node: { isNode: () => true, setAttrs: vi.fn() },
  startBatch: vi.fn(),
  stopBatch: vi.fn(),
  centerCell: vi.fn(),
  getCellById: vi.fn(),
}))
vi.mock('../../context', () => {
  const graphRef = { current: graph }
  return { useDesignerContext: () => ({ graphRef }) }
})
vi.mock('@/shared/contexts/ThemeContext', () => ({ useTheme: () => ({ theme: 'light' }) }))
vi.mock('@/shared/hooks/useMediaQuery', () => ({ useMediaQuery: () => viewport.mobile }))
vi.mock('../ContextMenu', () => ({
  default: (props: unknown) => {
    panels.contextMenu(props)
    return null
  },
}))
vi.mock('../ProcessDebuggerPanel', () => ({
  default: (props: unknown) => {
    panels.debug(props)
    return null
  },
}))
vi.mock('../ValidationResultPanel', () => ({
  default: (props: unknown) => {
    panels.validation(props)
    return null
  },
}))

function mount() {
  return render(
    <Provider store={store}>
      <App>
        <DesignerLayout
          layoutClassName="test"
          palette={null}
          canvas={null}
          propertiesPanel={null}
          onCopy={vi.fn()}
          onPaste={vi.fn()}
          onDelete={vi.fn()}
          processVariablesDialog={{ open: false, onClose: vi.fn() }}
        />
      </App>
    </Provider>
  )
}

describe('real designer layout ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    viewport.mobile = false
    graph.getCellById.mockReturnValue(graph.node)
    store.dispatch(loadOperateProcess.pending('doc', 'flow'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow: {
            id: 'flow',
            code: 'flow',
            name: 'Flow',
            type: 'TBBPM',
            nodes: [],
            connections: [],
          },
          operateProcessCode: 'flow',
          revision: 1,
          warnings: [],
        },
        'doc',
        'flow'
      )
    )
    store.dispatch(selectNode(null))
    store.dispatch(setRightPanelTab('none'))
    store.dispatch(setSidePanelsCollapsed({ left: false, right: false }))
  })
  it('recreates the simulation engine on same-id content edits but not UI-only updates', async () => {
    store.dispatch(setRightPanelTab('debug'))
    mount()
    await waitFor(() => expect(panels.debug).toHaveBeenCalled())
    const first = panels.debug.mock.calls[0][0].simulationEngine
    act(() => {
      store.dispatch(updateProcessInfo({ name: 'Edited same ID' }))
    })
    const calls = panels.debug.mock.calls
    const last = calls[calls.length - 1][0]
    expect(last.flowDefinition.id).toBe('flow')
    expect(last.simulationEngine).not.toBe(first)
    act(() => {
      store.dispatch(setRightPanelTab('debug'))
    })
    expect(panels.debug.mock.calls[panels.debug.mock.calls.length - 1][0].simulationEngine).toBe(
      last.simulationEngine
    )
  })
  it('actually clears graph node styling for an empty validation selection', async () => {
    store.dispatch(setRightPanelTab('validation'))
    mount()
    await waitFor(() => expect(panels.validation).toHaveBeenCalled())
    const props = panels.validation.mock.calls[0][0]
    act(() => {
      props.onHighlightNodes(['old-node'])
    })
    await waitFor(() =>
      expect(graph.node.setAttrs).toHaveBeenCalledWith({
        body: { stroke: 'var(--color-error, #ff4d4f)', strokeWidth: 2 },
      })
    )
    act(() => {
      props.onHighlightNodes([])
    })
    await waitFor(() =>
      expect(graph.node.setAttrs).toHaveBeenLastCalledWith({
        body: { stroke: 'var(--color-border-light, #d9d9d9)', strokeWidth: 1 },
      })
    )
  })

  it('keeps the properties panel collapsed when selecting on a narrow canvas', async () => {
    viewport.mobile = true
    mount()
    await waitFor(() =>
      expect(store.getState().ui).toMatchObject({
        leftPanelCollapsed: true,
        rightPanelCollapsed: true,
      })
    )

    act(() => {
      store.dispatch(setSidePanelsCollapsed({ left: false, right: true }))
      store.dispatch(selectNode('existing-node'))
    })

    await waitFor(() =>
      expect(store.getState().ui).toMatchObject({
        leftPanelCollapsed: true,
        rightPanelCollapsed: true,
        selectedNodeId: 'existing-node',
      })
    )
  })

  it('collapses an empty properties panel after clearing a narrow-canvas selection', async () => {
    viewport.mobile = true
    mount()
    await waitFor(() => expect(store.getState().ui.rightPanelCollapsed).toBe(true))

    act(() => {
      store.dispatch(setSidePanelsCollapsed({ left: true, right: false }))
      store.dispatch(selectNode('existing-node'))
    })
    await waitFor(() => expect(store.getState().ui.rightPanelCollapsed).toBe(false))

    act(() => {
      store.dispatch(selectNode(null))
    })
    await waitFor(() => expect(store.getState().ui.rightPanelCollapsed).toBe(true))
  })

  it('keeps an explicitly opened validation panel visible after clearing a selection', async () => {
    viewport.mobile = true
    mount()
    await waitFor(() => expect(store.getState().ui.rightPanelCollapsed).toBe(true))

    act(() => {
      store.dispatch(selectNode('existing-node'))
      store.dispatch(setRightPanelTab('validation'))
      store.dispatch(setSidePanelsCollapsed({ left: true, right: false }))
    })
    act(() => {
      store.dispatch(selectNode(null))
    })

    await waitFor(() =>
      expect(store.getState().ui).toMatchObject({
        rightPanelCollapsed: false,
        rightPanelTab: 'validation',
      })
    )
  })

  it('explicitly opens the requested panel for narrow-canvas context-menu commands', async () => {
    viewport.mobile = true
    mount()
    await waitFor(() => expect(store.getState().ui.rightPanelCollapsed).toBe(true))

    act(() => {
      store.dispatch(
        showContextMenu({
          position: { x: 10, y: 10 },
          type: 'node',
          targetId: 'existing-node',
        })
      )
    })
    await waitFor(() => expect(panels.contextMenu).toHaveBeenCalled())
    const contextMenuCalls = panels.contextMenu.mock.calls
    const actions = contextMenuCalls[contextMenuCalls.length - 1]?.[0] as {
      onBreakpoint: () => void
      onEdit: () => void
    }

    act(() => actions.onEdit())
    expect(store.getState().ui).toMatchObject({
      leftPanelCollapsed: true,
      rightPanelCollapsed: false,
      rightPanelTab: 'properties',
    })

    act(() => {
      store.dispatch(setSidePanelsCollapsed({ left: false, right: true }))
      actions.onBreakpoint()
    })
    expect(store.getState().ui).toMatchObject({
      leftPanelCollapsed: true,
      rightPanelCollapsed: false,
      rightPanelTab: 'debug',
    })
  })
})
