import { act, fireEvent, render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import UnifiedDesigner from '../UnifiedDesigner'

import { store } from '@/app/store'
import { loadOperateProcess } from '@/authoring/designer/store/editorSlice'

vi.mock('../../hooks/useProcessInitialization', () => ({
  useProcessInitialization: () => ({ status: 'ready' }),
}))
vi.mock('../../designer/components/BpmnDesigner', () => ({ default: () => <div>BPMN canvas</div> }))
vi.mock('../../designer/components/TbbpmDesigner', () => ({
  default: () => <div>TBBPM canvas</div>,
}))
vi.mock('../../designer/components/DesignerStatusBar', () => ({ default: () => null }))
vi.mock('../../designer/components/LocalSnapshotsModal', () => ({ default: () => null }))
vi.mock('../../designer/components/XmlEditorModal', () => ({ default: () => null }))
vi.mock('../../designer/hooks/useAutoSave', () => ({
  useAutoSave: () => ({ autoSavePending: false }),
}))
vi.mock('../../designer/hooks/useClipboard', () => ({
  useClipboard: () => ({ copyNodes: vi.fn(), pasteNodes: vi.fn() }),
}))
vi.mock('../../designer/hooks/useUnsavedChangesGuard', () => ({
  useUnsavedChangesGuard: () => null,
}))
vi.mock('@/shared/services/designerNavigation', async (original) => ({
  ...(await original<typeof import('@/shared/services/designerNavigation')>()),
  parseDesignerEntryDescriptor: () => ({
    source: 'operateProcessCode',
    processCode: 'flow',
    modelType: 'tbbpm',
  }),
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function load(request: string, type: 'BPMN' | 'TBBPM', name = 'Current') {
  store.dispatch(loadOperateProcess.pending(request, 'flow'))
  store.dispatch(
    loadOperateProcess.fulfilled(
      {
        flow: { id: 'flow', code: 'flow', name, type, nodes: [], connections: [] },
        operateProcessCode: 'flow',
        revision: 1,
        warnings: [],
      },
      request,
      'flow'
    )
  )
}

describe('designer loaded document identity', () => {
  it('chooses the loaded model instead of a stale entry type', () => {
    load('one', 'BPMN')
    render(
      <Provider store={store}>
        <MemoryRouter>
          <UnifiedDesigner />
        </MemoryRouter>
      </Provider>
    )
    expect(screen.getByText('BPMN canvas')).toBeInTheDocument()
    expect(screen.queryByText('TBBPM canvas')).not.toBeInTheDocument()
  })

  it('discards the old name buffer on a same-id document reload', () => {
    load('one', 'TBBPM', 'Old')
    render(
      <Provider store={store}>
        <MemoryRouter>
          <UnifiedDesigner />
        </MemoryRouter>
      </Provider>
    )
    fireEvent.click(screen.getByRole('button', { name: 'designer.header.editName' }))
    fireEvent.change(screen.getByLabelText('designer.header.editName'), {
      target: { value: 'Obsolete edit' },
    })
    act(() => load('two', 'TBBPM', 'New'))
    const input = screen.queryByLabelText('designer.header.editName')
    if (input) fireEvent.blur(input)
    expect(store.getState().editor.present.currentProcess?.name).toBe('New')
  })
})
