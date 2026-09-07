import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { vi } from 'vitest'

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
})
