import { render, screen } from '@testing-library/react'
import { App } from 'antd'
import React from 'react'
import { Provider } from 'react-redux'
import { vi } from 'vitest'

import { createProcess } from '../../store/editorSlice'
import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import TbbpmDesigner from '../TbbpmDesigner'

import { store } from '@/app/store'

vi.mock('@/shared/contexts/ThemeContext', () => ({
  useTheme: () => ({ theme: 'light', toggleTheme: vi.fn(), isTransitioning: false }),
  ThemeProvider: ({ children }: { children: React.ReactNode }) => children,
}))

// Mock @antv/x6 — the library declares ESM but ships CJS, and its Graph API
// needs a real DOM/canvas.  Mock at the module level so imports succeed.
vi.mock('@antv/x6', () => ({
  Graph: vi.fn().mockImplementation(() => ({
    on: vi.fn(),
    off: vi.fn(),
    dispose: vi.fn(),
    addNode: vi.fn(),
    addEdge: vi.fn(),
    getNodes: vi.fn().mockReturnValue([]),
    getEdges: vi.fn().mockReturnValue([]),
    toJSON: vi.fn().mockReturnValue({ cells: [] }),
  })),
  Node: class {},
  Edge: class {},
  Cell: class {},
  Dnd: vi.fn().mockImplementation(() => ({ start: vi.fn() })),
}))

// Mock the canvas child — it requires a real X6 Graph instance.
vi.mock('../TbbpmCanvas', () => ({
  default: ({ onGraphReady }: { onGraphReady?: (g: unknown) => void }) => {
    // Simulate graph-ready so the parent's onGraphReady callback fires.
    React.useEffect(() => {
      onGraphReady?.({ fakeGraph: true })
    }, [])
    return <div data-testid="tbbpm-canvas-mock">Canvas Mock</div>
  },
}))

// Mock the palette child — it also requires X6 Dnd.
vi.mock('../NodePalette', () => ({
  default: () => <div data-testid="node-palette-mock">Palette Mock</div>,
}))

// Mock lazy-loaded panels.
vi.mock('../TbbpmPropertiesPanel', () => ({
  default: () => <div data-testid="properties-panel">Properties Panel</div>,
}))

describe('TbbpmDesigner', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    const process: UnifiedProcessDefinition = {
      id: 'test-flow',
      code: 'test',
      name: 'Test Process',
      type: 'TBBPM',
      nodes: [],
      connections: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    }
    store.dispatch(createProcess.fulfilled(process, 'test-request', { type: 'TBBPM' }))
  })

  describe('组件渲染', () => {
    it('should render successfully with the designer layout', () => {
      render(
        <Provider store={store}>
          <App>
            <TbbpmDesigner processVariablesDialog={{ open: false, onClose: vi.fn() }} />
          </App>
        </Provider>
      )

      // The mocked canvas and palette should both render.
      expect(screen.getByTestId('tbbpm-canvas-mock')).toBeInTheDocument()
      expect(screen.getByTestId('node-palette-mock')).toBeInTheDocument()
    })
  })

  describe('Graph初始化', () => {
    it('should call onGraphReady after mount', async () => {
      const handleGraphReady = vi.fn()

      render(
        <Provider store={store}>
          <App>
            <TbbpmDesigner
              onGraphReady={handleGraphReady}
              processVariablesDialog={{ open: false, onClose: vi.fn() }}
            />
          </App>
        </Provider>
      )

      // The mocked canvas fires onGraphReady in useEffect.
      await screen.findByTestId('tbbpm-canvas-mock')
      expect(handleGraphReady).toHaveBeenCalledTimes(1)
      expect(handleGraphReady).toHaveBeenCalledWith(expect.objectContaining({ fakeGraph: true }))
    })
  })
})
