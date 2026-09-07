import { act, render, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { loadOperateProcess, updateProcessInfo } from '../../store/editorSlice'
import { setRightPanelTab } from '../../store/uiSlice'
import { DesignerLayout } from '../DesignerLayout'

import { store } from '@/app/store'

const panels = vi.hoisted(() => ({ debug: vi.fn(), validation: vi.fn() }))
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
    </Provider>
  )
}

describe('real designer layout ownership', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
})
