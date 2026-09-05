import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { MessageInstance } from 'antd/es/message/interface'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import DeploymentWizard from '@/operate/pages/DeploymentWizard'
import { getMockProcesses } from '@/shared/api/mockProcessData'
import { getProcesses, getProcessVersions } from '@/shared/api/processes'

vi.mock('@/operate/api/deployments', () => ({
  createDeployment: vi.fn(),
  getDeploymentRoute: vi.fn(),
}))

vi.mock('@/shared/api/processes', () => ({
  getProcessByCode: vi.fn(),
  getProcesses: vi.fn(),
  getProcessVersions: vi.fn(),
}))

const message = { error: vi.fn() } as unknown as MessageInstance

vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>()
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({ message }),
    },
  }
})

const processes = getMockProcesses({ page: 1, pageSize: 20 })

function renderWizard(initialEntry = '/operate/deploy-wizard') {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <DeploymentWizard />
    </MemoryRouter>
  )
}

describe('DeploymentWizard recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('keeps process-source failures visible until retry succeeds', async () => {
    vi.mocked(getProcesses)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce(processes)

    renderWizard()
    const error = await screen.findByText(/加载失败/i)
    const alert = error.closest('[role="alert"]')
    expect(alert).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/i }))

    await waitFor(() => expect(alert).not.toBeInTheDocument())
    expect(getProcesses).toHaveBeenCalledTimes(2)
  })

  it('keeps version-source failures visible until retry succeeds', async () => {
    const process = processes.data[0]
    vi.mocked(getProcesses).mockResolvedValue(processes)
    vi.mocked(getProcessVersions)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce({ data: [], hasMore: false, nextCursor: null })

    renderWizard(`/operate/deploy-wizard?processCode=${process.code}`)
    const error = await screen.findByText(/加载失败/i)
    const alert = error.closest('[role="alert"]')
    expect(alert).not.toBeNull()
    fireEvent.click(screen.getByRole('button', { name: /重\s*试/i }))

    await waitFor(() => expect(alert).not.toBeInTheDocument())
    expect(getProcessVersions).toHaveBeenCalledTimes(2)
  })
})
