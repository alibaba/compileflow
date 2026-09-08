import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { createDeployment, getDeploymentRoute } from '@/operate/api/deployments'
import DeploymentWizard from '@/operate/pages/DeploymentWizard'
import { getMockProcesses } from '@/shared/api/mockProcessData'
import { getProcessByCode, getProcesses, getProcessVersions } from '@/shared/api/processes'
import i18n from '@/shared/i18n'

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
  function LocationProbe() {
    const location = useLocation()
    return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
  }

  return render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <DeploymentWizard />
        <LocationProbe />
      </MemoryRouter>
    </ConfigProvider>
  )
}

describe('DeploymentWizard recovery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('advances only one step when Next is clicked twice during validation', async () => {
    const process = processes.data[0]
    vi.mocked(getProcesses).mockResolvedValue(processes)
    vi.mocked(getProcessVersions).mockResolvedValue({
      data: [
        {
          processCode: process.code,
          version: 'v2',
          modelType: process.type,
          createdAt: '2026-09-07T00:00:00Z',
          publishedBy: 'tester',
        },
      ],
      hasMore: false,
      nextCursor: null,
    })
    renderWizard(`/operate/deploy-wizard?processCode=${process.code}`)
    await waitFor(() => expect(getProcessVersions).toHaveBeenCalled())
    const version = screen.getByRole('combobox', { name: i18n.t('deployment.version') })
    fireEvent.keyDown(version, { key: 'ArrowDown', keyCode: 40 })
    await screen.findByRole('option', { name: 'v2' })
    fireEvent.keyDown(version, { key: 'Enter', keyCode: 13 })
    const next = screen.getByRole('button', {
      name: new RegExp(i18n.t('common.next').split('').join('\\s*')),
    })
    await waitFor(() => expect(next).toBeEnabled())
    fireEvent.click(next)
    fireEvent.click(next)
    expect(await screen.findByRole('combobox', { name: i18n.t('deployment.alias') })).toBeVisible()
    expect(createDeployment).not.toHaveBeenCalled()
  })

  it.each(['retry', 'edit'] as const)(
    'preserves deployment intent identity for %s after an uncertain response',
    async (nextAction) => {
      const process = processes.data[0]
      vi.mocked(getProcesses).mockResolvedValue(processes)
      vi.mocked(getProcessVersions).mockResolvedValue({
        data: [
          {
            processCode: process.code,
            version: 'v2',
            modelType: process.type,
            createdAt: '2026-09-07T00:00:00Z',
            publishedBy: 'tester',
          },
        ],
        hasMore: false,
        nextCursor: null,
      })
      const route = {
        processCode: process.code,
        alias: 'production',
        stableVersion: 'v1',
        revision: 1,
        updatedBy: 'tester',
        updatedAt: 0,
      }
      vi.mocked(getDeploymentRoute)
        .mockResolvedValueOnce(route)
        .mockResolvedValueOnce({ ...route, revision: 2 })
      vi.mocked(createDeployment).mockRejectedValue(new Error('response lost'))
      renderWizard(`/operate/deploy-wizard?processCode=${process.code}`)
      await waitFor(() => expect(getProcessVersions).toHaveBeenCalled())
      const versionInput = screen.getByRole('combobox', { name: i18n.t('deployment.version') })
      fireEvent.keyDown(versionInput, { key: 'ArrowDown', keyCode: 40 })
      await screen.findByRole('option', { name: 'v2' })
      fireEvent.keyDown(versionInput, { key: 'Enter', keyCode: 13 })
      const button = (key: string) =>
        screen.getByRole('button', { name: new RegExp(i18n.t(key).split('').join('\\s*')) })
      await waitFor(() => expect(button('common.next')).toBeEnabled())
      fireEvent.click(button('common.next'))
      fireEvent.change(await screen.findByRole('combobox', { name: i18n.t('deployment.alias') }), {
        target: { value: 'production' },
      })
      await waitFor(() => expect(button('common.next')).toBeEnabled())
      fireEvent.click(button('common.next'))
      await waitFor(() => expect(button('deployment.deploy')).toBeEnabled())
      fireEvent.click(button('deployment.deploy'))
      await waitFor(() => expect(message.error).toHaveBeenCalledTimes(1))
      if (nextAction === 'edit') {
        fireEvent.click(button('common.previous'))
        fireEvent.change(
          await screen.findByRole('combobox', { name: i18n.t('deployment.alias') }),
          { target: { value: 'staging' } }
        )
        await waitFor(() => expect(button('common.next')).toBeEnabled())
        fireEvent.click(button('common.next'))
        await waitFor(() => expect(button('deployment.deploy')).toBeEnabled())
      }
      fireEvent.click(button('deployment.deploy'))
      await waitFor(() => expect(createDeployment).toHaveBeenCalledTimes(2))
      const [first, second] = vi.mocked(createDeployment).mock.calls.map(([request]) => request)
      if (nextAction === 'retry') expect(second).toEqual(first)
      else {
        expect(second.alias).toBe('staging')
        expect(second.idempotencyKey).not.toBe(first.idempotencyKey)
      }
    }
  )

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

  it('removes a missing process deep link and keeps the process picker usable', async () => {
    vi.mocked(getProcesses).mockResolvedValue(processes)
    vi.mocked(getProcessByCode).mockRejectedValue({
      isAxiosError: true,
      response: { status: 404 },
    })

    renderWizard('/operate/deploy-wizard?processCode=missing-process&source=shared')

    const processInput = screen.getByRole('combobox', {
      name: i18n.t('deployment.wizard.selectProcessLabel'),
    })
    await waitFor(() => expect(processInput).toHaveValue(''))
    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/operate/deploy-wizard?source=shared'
      )
    )
    expect(screen.queryByText(/加载失败/i)).not.toBeInTheDocument()
    expect(processInput).toBeEnabled()
  })
})
