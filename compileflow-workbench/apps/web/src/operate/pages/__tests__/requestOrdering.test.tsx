import { act, renderHook, waitFor } from '@testing-library/react'
import type { MessageInstance } from 'antd/es/message/interface'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getLogById, getLogs } from '@/operate/api/logs'
import { mockLogs } from '@/operate/api/mockLogData'
import { useLogsPageState } from '@/operate/pages/Logs'
import { useProcessManagementData } from '@/operate/pages/ProcessManagement'
import { getMockProcesses } from '@/shared/api/mockProcessData'
import { createProcess, deleteProcess, getProcesses } from '@/shared/api/processes'
import type { ExecutionLog, LogListResponse, ProcessListResponse } from '@/shared/contracts'
import i18n from '@/shared/i18n'

vi.mock('@/shared/config/buildConfig', async (original) => ({
  ...(await original<typeof import('@/shared/config/buildConfig')>()),
  isOperateMockMode: () => false,
}))

vi.mock('@/operate/api/logs', () => ({
  exportLogs: vi.fn(),
  getLogById: vi.fn(),
  getLogs: vi.fn(),
}))

vi.mock('@/shared/api/processes', () => ({
  createProcess: vi.fn(),
  deleteProcess: vi.fn(),
  duplicateProcess: vi.fn(),
  getProcesses: vi.fn(),
  importProcessXml: vi.fn(),
  publishProcess: vi.fn(),
}))

const message = {
  error: vi.fn(),
  success: vi.fn(),
} as unknown as MessageInstance

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

function routerWrapper({ children }: PropsWithChildren) {
  return <MemoryRouter>{children}</MemoryRouter>
}

const translate = i18n.t.bind(i18n)

describe('latest request ordering', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it.each(['BPMN', 'TBBPM'] as const)(
    'creates %s XML with the requested process identity',
    async (type) => {
      vi.mocked(getProcesses).mockResolvedValue(getMockProcesses())
      const { result } = renderHook(() => useProcessManagementData(translate, vi.fn()), {
        wrapper: routerWrapper,
      })
      await act(async () => result.current.handleCreateProcess(type))
      const calls = vi.mocked(createProcess).mock.calls
      const request = calls[calls.length - 1][0]
      const xml = new DOMParser().parseFromString(request.xml!, 'application/xml')
      const process =
        type === 'BPMN'
          ? xml.getElementsByTagNameNS('http://www.omg.org/spec/BPMN/20100524/MODEL', 'process')[0]
          : xml.documentElement
      expect(process.getAttribute(type === 'BPMN' ? 'id' : 'code')).toBe(request.code)
      expect(process.getAttribute('name')).toBe(request.name)
    }
  )

  it('does not navigate back into a late creation after unmount', async () => {
    vi.mocked(getProcesses).mockResolvedValue(getMockProcesses())
    const creation = deferred<Awaited<ReturnType<typeof createProcess>>>()
    vi.mocked(createProcess).mockReturnValueOnce(creation.promise)
    const navigate = vi.fn()
    const { result, unmount } = renderHook(() => useProcessManagementData(translate, navigate), {
      wrapper: routerWrapper,
    })
    let creating!: Promise<void>
    act(() => {
      creating = result.current.handleCreateProcess('TBBPM')
    })
    unmount()
    await act(async () => {
      creation.resolve({ ...getMockProcesses().data[0], xml: '<bpm/>' })
      await creating
    })
    expect(navigate).not.toHaveBeenCalled()
    expect(message.error).not.toHaveBeenCalled()
  })

  it.each(['page change', 'unmount'] as const)(
    'does not reload an obsolete query after a mutation and %s',
    async (change) => {
      const deletion = deferred<void>()
      vi.mocked(deleteProcess).mockReturnValueOnce(deletion.promise)
      vi.mocked(getProcesses).mockImplementation(async (params) => getMockProcesses(params))
      const { result, unmount } = renderHook(() => useProcessManagementData(translate, vi.fn()), {
        wrapper: routerWrapper,
      })
      await waitFor(() => expect(result.current.loading).toBe(false))
      let deleting!: Promise<void>
      act(() => {
        deleting = result.current.handleDelete('old-row', 1)
      })
      if (change === 'page change') {
        act(() => result.current.setPage(2))
        await waitFor(() =>
          expect(getProcesses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
        )
      } else unmount()
      vi.mocked(getProcesses).mockClear()
      await act(async () => {
        deletion.resolve()
        await deleting
      })
      if (change === 'page change') {
        expect(getProcesses).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
      } else {
        expect(getProcesses).not.toHaveBeenCalled()
        expect(message.success).not.toHaveBeenCalled()
      }
    }
  )

  it('keeps the newest log list when an older page responds last', async () => {
    const first = deferred<LogListResponse>()
    const oldLog = { ...mockLogs[0], id: 'old-log' }
    const newLog = { ...mockLogs[1], id: 'new-log' }
    vi.mocked(getLogs)
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce({ data: [newLog], total: 1, page: 2, pageSize: 20 })

    const { result } = renderHook(() => useLogsPageState(translate), {
      wrapper: routerWrapper,
    })
    await waitFor(() => expect(getLogs).toHaveBeenCalledTimes(1))

    act(() => result.current.setPage(2))
    await waitFor(() => expect(result.current.logs).toEqual([newLog]))

    first.resolve({ data: [oldLog], total: 1, page: 1, pageSize: 20 })
    await act(async () => {
      await first.promise
    })

    expect(result.current.logs).toEqual([newLog])
    expect(result.current.loading).toBe(false)
  })

  it('keeps the newest log detail after rapid row selection', async () => {
    const first = deferred<ExecutionLog>()
    const oldLog = { ...mockLogs[0], id: 'old-detail' }
    const newLog = { ...mockLogs[1], id: 'new-detail' }
    vi.mocked(getLogs).mockResolvedValue({ data: [], total: 0, page: 1, pageSize: 20 })
    vi.mocked(getLogById).mockReturnValueOnce(first.promise).mockResolvedValueOnce(newLog)

    const { result } = renderHook(() => useLogsPageState(translate), {
      wrapper: routerWrapper,
    })
    await waitFor(() => expect(result.current.loading).toBe(false))

    void result.current.handleViewDetail(oldLog.id)
    await act(async () => {
      await result.current.handleViewDetail(newLog.id)
    })
    expect(result.current.selectedLog).toEqual(newLog)

    first.resolve(oldLog)
    await act(async () => {
      await first.promise
    })

    expect(result.current.selectedLog).toEqual(newLog)
  })

  it('keeps the newest process page when an older page responds last', async () => {
    const first = deferred<ProcessListResponse>()
    const oldPage = getMockProcesses({ page: 1, pageSize: 1 })
    const newPage = getMockProcesses({ page: 2, pageSize: 1 })
    vi.mocked(getProcesses).mockReturnValueOnce(first.promise).mockResolvedValueOnce(newPage)

    const { result } = renderHook(() => useProcessManagementData(translate, vi.fn()), {
      wrapper: routerWrapper,
    })
    await waitFor(() => expect(getProcesses).toHaveBeenCalledTimes(1))

    act(() => result.current.setPage(2))
    await waitFor(() => expect(result.current.processes).toEqual(newPage.data))

    first.resolve(oldPage)
    await act(async () => {
      await first.promise
    })

    expect(result.current.processes).toEqual(newPage.data)
    expect(result.current.loading).toBe(false)
  })

  it('keeps log load failures visible until a retry succeeds', async () => {
    const recovered = { data: [mockLogs[0]], total: 1, page: 1, pageSize: 20 }
    vi.mocked(getLogs)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce(recovered)

    const { result } = renderHook(() => useLogsPageState(translate), {
      wrapper: routerWrapper,
    })
    await waitFor(() => expect(result.current.loadError).toBe(true))

    await act(async () => {
      result.current.reload()
      await Promise.resolve()
    })
    await waitFor(() => expect(result.current.loadError).toBe(false))
    expect(result.current.logs).toEqual(recovered.data)
  })

  it('keeps process load failures visible until a retry succeeds', async () => {
    const recovered = getMockProcesses({ page: 1, pageSize: 20 })
    vi.mocked(getProcesses)
      .mockRejectedValueOnce(new Error('unavailable'))
      .mockResolvedValueOnce(recovered)

    const { result } = renderHook(() => useProcessManagementData(translate, vi.fn()), {
      wrapper: routerWrapper,
    })
    await waitFor(() => expect(result.current.loadError).toBe(true))

    await act(async () => {
      result.current.reload()
      await Promise.resolve()
    })
    await waitFor(() => expect(result.current.loadError).toBe(false))
    expect(result.current.processes).toEqual(recovered.data)
  })
})
