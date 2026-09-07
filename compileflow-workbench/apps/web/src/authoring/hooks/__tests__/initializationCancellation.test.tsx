import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { useProcessInitialization } from '../useProcessInitialization'

import { store } from '@/app/store'
import type { ProcessTemplate } from '@/authoring/designer/api/processStorageTypes'
import { loadProcess } from '@/authoring/designer/store/editorSlice'
import type { DesignerEntryDescriptor } from '@/shared/services/designerNavigation'

const mocks = vi.hoisted(() => ({
  getTemplate: vi.fn(),
  loadProcess: vi.fn(),
  suggestAvailableProcessName: vi.fn(),
  saveProcess: vi.fn(),
  navigate: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
}))
vi.mock('antd', () => ({ App: { useApp: () => ({ message: mocks }) } }))
vi.mock('react-router-dom', () => ({ useNavigate: () => mocks.navigate }))
vi.mock('@/authoring/designer/api/processStorage', () => ({ processStorage: mocks }))
vi.mock('@/authoring/designer/api/builtInTemplates', () => ({ ensureBuiltInTemplates: vi.fn() }))

const template: ProcessTemplate = {
  id: 'template',
  name: 'Template',
  type: 'TBBPM',
  content: '<bpm code="template"/>',
}
function entry(source: DesignerEntryDescriptor['source']): DesignerEntryDescriptor {
  return {
    source,
    modelType: 'tbbpm',
    processId: 'newer',
    templateId: 'template',
    exampleId: null,
    processCode: null,
  }
}
const wrapper = ({ children }: { children: ReactNode }) => (
  <Provider store={store}>{children}</Provider>
)

describe('initialization cancellation', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    mocks.getTemplate.mockResolvedValue(template)
    mocks.suggestAvailableProcessName.mockResolvedValue('Created')
    mocks.saveProcess.mockImplementation(async (value) => value)
    mocks.loadProcess.mockResolvedValue({
      id: 'newer',
      code: 'newer',
      name: 'Newer',
      type: 'TBBPM',
      definition: '<bpm code="newer"/>',
      createdAt: 1,
      updatedAt: 1,
    })
    store.dispatch(loadProcess.pending('seed', 'retained'))
    store.dispatch(
      loadProcess.fulfilled(
        {
          flow: {
            id: 'retained',
            code: 'retained',
            name: 'Retained',
            type: 'TBBPM',
            nodes: [],
            connections: [],
          },
          warnings: [],
        },
        'seed',
        'retained'
      )
    )
  })

  it('does not dispatch a late template create after another entry loads', async () => {
    let finish!: (value: ProcessTemplate) => void
    const pending = new Promise<ProcessTemplate>((resolve) => {
      finish = resolve
    })
    mocks.getTemplate.mockReturnValue(pending)
    const { result, rerender } = renderHook(
      ({ descriptor }) => useProcessInitialization({ entryPayload: descriptor }),
      { wrapper, initialProps: { descriptor: entry('template') } }
    )
    await waitFor(() => expect(mocks.getTemplate).toHaveBeenCalledOnce())
    rerender({ descriptor: entry('workspaceProcess') })
    await waitFor(() => expect(result.current.status).toBe('ready'))
    await act(async () => {
      finish(template)
      await pending
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    expect(mocks.suggestAvailableProcessName).not.toHaveBeenCalled()
    expect(store.getState().editor.present.currentProcess?.id).toBe('newer')
    expect(mocks.navigate).not.toHaveBeenCalled()
  })

  it.each(['new', 'template'] as const)(
    'aborts a %s creation waiting for its name after unmount',
    async (source) => {
      let finish!: (name: string) => void
      const pending = new Promise<string>((resolve) => {
        finish = resolve
      })
      mocks.suggestAvailableProcessName.mockReturnValue(pending)
      const { unmount } = renderHook(
        () => useProcessInitialization({ entryPayload: entry(source) }),
        { wrapper }
      )
      await waitFor(() => expect(mocks.suggestAvailableProcessName).toHaveBeenCalledOnce())
      unmount()
      await act(async () => {
        finish('Late')
        await pending
        await new Promise((resolve) => setTimeout(resolve, 0))
      })
      expect(mocks.saveProcess).not.toHaveBeenCalled()
      expect(store.getState().editor.present.currentProcess?.id).toBe('retained')
      expect(mocks.navigate).not.toHaveBeenCalled()
    }
  )

  it('adopts a newly created process when canonicalizing the URL without reloading it', async () => {
    const { result, rerender } = renderHook(
      ({ descriptor }) => useProcessInitialization({ entryPayload: descriptor }),
      { wrapper, initialProps: { descriptor: entry('new') } }
    )

    await waitFor(() => expect(mocks.navigate).toHaveBeenCalledOnce())
    const canonicalPath = String(mocks.navigate.mock.calls[0]?.[0])
    const processId = new URL(canonicalPath, 'http://workbench.local').searchParams.get('processId')
    expect(processId).toBeTruthy()

    mocks.loadProcess.mockClear()
    rerender({
      descriptor: {
        ...entry('workspaceProcess'),
        processId,
      },
    })

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(mocks.loadProcess).not.toHaveBeenCalled()
    expect(store.getState().editor.present.currentProcess?.id).toBe(processId)
  })

  it('reloads a canonical workspace URL whose model type does not match the in-memory process', async () => {
    const { result } = renderHook(
      () =>
        useProcessInitialization({
          entryPayload: {
            ...entry('workspaceProcess'),
            processId: 'retained',
            modelType: 'bpmn',
          },
        }),
      { wrapper }
    )

    await waitFor(() => expect(result.current.status).toBe('ready'))
    expect(mocks.loadProcess).toHaveBeenCalledWith('retained')
  })
})
