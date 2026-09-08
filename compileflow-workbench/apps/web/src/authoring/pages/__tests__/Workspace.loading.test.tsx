import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, vi } from 'vitest'

import { importData } from '../../designer/api/processStorage'
import Workspace from '../Workspace'

import i18n from '@/shared/i18n'
import { renderWithApp } from '@/test/renderWithApp'

const getRecentProcessesMock = vi.fn()
const listProcessesMock = vi.fn()
const ensureBuiltInTemplatesMock = vi.fn()

vi.mock('../../designer/api/builtInTemplates', () => ({
  BUILT_IN_TEMPLATES: [],
  ensureBuiltInTemplates: () => ensureBuiltInTemplatesMock(),
}))

vi.mock('../../designer/api/processStorage', () => ({
  exportAllData: vi.fn(),
  importData: vi.fn(),
  processStorage: {
    getRecentProcesses: () => getRecentProcessesMock(),
    listProcesses: () => listProcessesMock(),
  },
}))

describe('Workspace loading state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getRecentProcessesMock.mockResolvedValue([])
    listProcessesMock.mockResolvedValue([])
    ensureBuiltInTemplatesMock.mockResolvedValue([])
  })

  test('does not present empty workspace data before storage finishes loading', async () => {
    let finishLoading: (processes: []) => void = () => undefined
    getRecentProcessesMock.mockReturnValue(
      new Promise<[]>((resolve) => {
        finishLoading = resolve
      })
    )
    listProcessesMock.mockResolvedValue([])
    ensureBuiltInTemplatesMock.mockResolvedValue([])

    render(renderWithApp(<MemoryRouter>{<Workspace />}</MemoryRouter>))

    expect(screen.getByText(i18n.t('common.loading'))).toBeVisible()
    expect(screen.queryByText(i18n.t('workspace.noRecentProjects'))).not.toBeInTheDocument()

    finishLoading([])

    await waitFor(() => {
      expect(screen.queryByText(i18n.t('common.loading'))).not.toBeInTheDocument()
    })
    expect(screen.getByText(i18n.t('workspace.noRecentProjects'))).toBeVisible()
  })

  test('shows recovery without presenting failed storage as an empty workspace', async () => {
    getRecentProcessesMock.mockRejectedValue(new Error('storage unavailable'))
    listProcessesMock.mockResolvedValue([])
    ensureBuiltInTemplatesMock.mockResolvedValue([])

    render(renderWithApp(<MemoryRouter>{<Workspace />}</MemoryRouter>))

    expect(await screen.findByText(i18n.t('error.loadFailed'))).toBeVisible()
    expect(screen.queryByText(i18n.t('workspace.noRecentProjects'))).not.toBeInTheDocument()
    const retryName = new RegExp([...i18n.t('common.retry')].join('\\s*'))
    expect(screen.getByRole('button', { name: retryName })).toBeVisible()
  })

  test.each([
    [{ success: 3, skipped: 0, failed: 0 }, 'success', 2],
    [{ success: 0, skipped: 2, failed: 0 }, 'info', 1],
    [{ success: 2, skipped: 1, failed: 1 }, 'warning', 2],
    [{ success: 0, skipped: 0, failed: 2 }, 'error', 1],
  ] as const)(
    'reports import result %j as %s and reloads only when storage changed',
    async (result, severity, expectedLoadCount) => {
      vi.mocked(importData).mockResolvedValue(result)
      render(renderWithApp(<MemoryRouter>{<Workspace />}</MemoryRouter>))
      await screen.findByText(i18n.t('workspace.noRecentProjects'))

      const file = new File(['{}'], 'backup.json', { type: 'application/json' })
      Object.defineProperty(file, 'text', { value: vi.fn().mockResolvedValue('{}') })
      fireEvent.change(screen.getByLabelText(i18n.t('workspace.importFileLabel')), {
        target: { files: [file] },
      })

      const summary = await screen.findByText(i18n.t('workspace.importSummary', result))
      expect(summary.closest('.ant-message-notice')).toHaveClass(`ant-message-notice-${severity}`)
      await waitFor(() => expect(listProcessesMock).toHaveBeenCalledTimes(expectedLoadCount))
    }
  )
})
