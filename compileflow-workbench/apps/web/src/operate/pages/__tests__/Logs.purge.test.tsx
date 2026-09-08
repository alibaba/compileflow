import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ConfigProvider } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getLogs, purgeLogs } from '@/operate/api/logs'
import Logs from '@/operate/pages/Logs'
import i18n from '@/shared/i18n'

vi.mock('@/operate/api/logs', () => ({
  exportLogs: vi.fn(),
  getLogById: vi.fn(),
  getLogs: vi.fn(),
  purgeLogs: vi.fn(),
}))

const message = { error: vi.fn(), success: vi.fn() } as unknown as MessageInstance

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

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function renderLogs() {
  return render(
    <ConfigProvider theme={{ token: { motion: false } }}>
      <MemoryRouter>
        <Logs />
      </MemoryRouter>
    </ConfigProvider>
  )
}

describe('Logs purge dialog', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    await i18n.changeLanguage('zh')
    vi.mocked(getLogs).mockResolvedValue({ data: [], total: 0, page: 1, pageSize: 20 })
  })

  it('cannot be dismissed while an irreversible purge is running', async () => {
    const request = deferred<{ deletedCount: number; hasMore: boolean; purgedAt: string }>()
    vi.mocked(purgeLogs).mockReturnValue(request.promise)
    const { container } = renderLogs()

    fireEvent.click(await screen.findByRole('button', { name: /清理日志/ }))
    fireEvent.click(screen.getByRole('button', { name: /确认清理/ }))
    await waitFor(() => expect(purgeLogs).toHaveBeenCalledOnce())

    expect(screen.getByRole('button', { name: /取\s*消/ })).toBeDisabled()
    expect(container.ownerDocument.querySelector('.ant-modal-close')).toBeNull()
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    request.resolve({ deletedCount: 1, hasMore: false, purgedAt: '2026-09-08T00:00:00Z' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })

  it('keeps the dialog open when the purge fails', async () => {
    vi.mocked(purgeLogs).mockRejectedValue(new Error('unavailable'))
    renderLogs()

    fireEvent.click(await screen.findByRole('button', { name: /清理日志/ }))
    fireEvent.click(screen.getByRole('button', { name: /确认清理/ }))

    await waitFor(() => expect(message.error).toHaveBeenCalled())
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /确认清理/ })).toBeEnabled()
  })
})
