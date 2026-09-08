import type { Graph } from '@antv/x6'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { App } from 'antd'
import { vi } from 'vitest'

import NodePalette from '../NodePalette'

// Wraps children in Ant Design's <App> so App.useApp() works in tests.
const renderWithApp = (ui: React.ReactElement) => render(<App>{ui}</App>)
const { dndDispose } = vi.hoisted(() => ({ dndDispose: vi.fn() }))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, params?: Record<string, string>) =>
      key === 'designer.palette.addNode' && params?.label ? `${key}:${params.label}` : key,
  }),
}))

vi.mock('@antv/x6', () => ({
  Graph: vi.fn(),
  Dnd: class {
    dispose = dndDispose
    start = vi.fn()
  },
}))

vi.mock('@/shared/styles/design-tokens', () => ({
  getNodeColor: vi.fn((_type: string) => `#color-${_type}`),
}))

describe('NodePalette', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('节点分类渲染', () => {
    it('should render all node categories', () => {
      renderWithApp(<NodePalette graph={null} />)

      for (const category of [
        'flow',
        'task',
        'gateway',
        'subprocess',
        'loop',
        'control',
        'annotation',
      ]) {
        expect(screen.getByText(`designer.palette.tbbpm.cat.${category}`)).toBeInTheDocument()
      }
    })

    it('should render all node types', () => {
      renderWithApp(<NodePalette graph={null} />)

      for (const category of ['subprocess', 'loop', 'control', 'annotation']) {
        fireEvent.click(screen.getByText(`designer.palette.tbbpm.cat.${category}`))
      }

      for (const type of [
        'start',
        'end',
        'autoTask',
        'waitTask',
        'waitEventTask',
        'timerTask',
        'scriptTask',
        'exclusive',
        'parallel',
        'inclusive',
        'subBpm',
        'bpmCall',
        'while',
        'foreach',
        'continue',
        'break',
        'note',
      ]) {
        expect(screen.getByText(`designer.palette.tbbpm.node.${type}`)).toBeInTheDocument()
      }
    })

    it('should display node counts in badges', () => {
      renderWithApp(<NodePalette graph={null} />)

      const badges = document.querySelectorAll('.ant-badge-count')
      expect(badges.length).toBeGreaterThan(0)
    })
  })

  describe('搜索功能', () => {
    it('should filter nodes based on search term', async () => {
      renderWithApp(<NodePalette graph={null} />)

      const searchInput = screen.getByPlaceholderText('designer.palette.searchPlaceholder')

      fireEvent.change(searchInput, { target: { value: 'task' } })

      await waitFor(() => {
        expect(screen.getByText('designer.palette.tbbpm.node.autoTask')).toBeInTheDocument()
        expect(screen.getByText('designer.palette.tbbpm.node.waitTask')).toBeInTheDocument()
        expect(screen.getByText('designer.palette.tbbpm.node.scriptTask')).toBeInTheDocument()
        expect(
          screen.getByRole('button', {
            name: 'designer.palette.addNode:designer.palette.tbbpm.node.autoTask',
          })
        ).toBeInTheDocument()
      })
    })

    it('should show empty state when no results', async () => {
      renderWithApp(<NodePalette graph={null} />)

      const searchInput = screen.getByPlaceholderText('designer.palette.searchPlaceholder')

      fireEvent.change(searchInput, { target: { value: 'nonexistent' } })

      await waitFor(() => {
        expect(screen.getByText('designer.palette.empty')).toBeInTheDocument()
      })
    })

    it('should clear search on input clear', async () => {
      renderWithApp(<NodePalette graph={null} />)

      const searchInput = screen.getByPlaceholderText(
        'designer.palette.searchPlaceholder'
      ) as HTMLInputElement

      fireEvent.change(searchInput, { target: { value: 'task' } })
      await waitFor(() => expect(searchInput.value).toBe('task'))

      fireEvent.change(searchInput, { target: { value: '' } })
      await waitFor(() => {
        expect(screen.getByText('designer.palette.tbbpm.node.start')).toBeInTheDocument()
        expect(screen.getByText('designer.palette.tbbpm.node.exclusive')).toBeInTheDocument()
      })
    })
  })

  describe('折叠/展开功能', () => {
    it('should render collapse panels for categories', () => {
      renderWithApp(<NodePalette graph={null} />)

      expect(screen.getByText('designer.palette.tbbpm.cat.flow')).toBeInTheDocument()
      expect(screen.getByText('designer.palette.tbbpm.cat.task')).toBeInTheDocument()
    })
  })

  describe('拖拽功能', () => {
    it('should handle mousedown on node item', () => {
      const mockGraph = {
        createNode: vi.fn().mockReturnValue({}),
      }

      renderWithApp(<NodePalette graph={mockGraph as unknown as Graph} />)

      const startNode = screen.getByText('designer.palette.tbbpm.node.start')

      fireEvent.mouseDown(startNode)
    })

    it('disposes the Dnd instance when the palette unmounts', () => {
      const mockGraph = { createNode: vi.fn().mockReturnValue({}) }
      const { unmount } = renderWithApp(<NodePalette graph={mockGraph as unknown as Graph} />)

      unmount()

      expect(dndDispose).toHaveBeenCalledOnce()
    })
  })
})
