import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import { DeferredGlobalSearch } from '../DeferredGlobalSearch'
import { GlobalSearch } from '../GlobalSearch'

import { listMockExamples } from '@/shared/api/exampleMockData'
import type { Example } from '@/shared/contracts'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((resolvePromise) => {
    resolve = resolvePromise
  })
  return { promise, resolve }
}

function renderSearch(loadExamples: () => Promise<Example[]>, loadProcesses: () => Promise<[]>) {
  return render(
    <MemoryRouter>
      <GlobalSearch loadExamples={loadExamples} loadProcesses={loadProcesses} />
    </MemoryRouter>
  )
}

describe('GlobalSearch catalog lifecycle', () => {
  it('defers search code and catalog reads until the first user intent', async () => {
    const loadExamples = vi.fn<() => Promise<Example[]>>().mockResolvedValue(listMockExamples())
    const loadProcesses = vi.fn<() => Promise<[]>>().mockResolvedValue([])
    render(
      <MemoryRouter>
        <DeferredGlobalSearch loadExamples={loadExamples} loadProcesses={loadProcesses} />
      </MemoryRouter>
    )

    expect(loadExamples).not.toHaveBeenCalled()
    expect(loadProcesses).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /打开全局搜索/ }))
    await waitFor(() => expect(loadExamples).toHaveBeenCalledTimes(1))
    expect(loadProcesses).toHaveBeenCalledTimes(1)
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('does not claim there are no matches while the search catalog is loading', async () => {
    const examples = deferred<Example[]>()
    const processes = deferred<[]>()
    renderSearch(
      () => examples.promise,
      () => processes.promise
    )

    fireEvent.click(screen.getByRole('button', { name: /打开全局搜索/ }))
    fireEvent.change(screen.getByRole('textbox', { name: '搜索示例和本地流程' }), {
      target: { value: '问候' },
    })

    expect(screen.getByRole('status')).toHaveTextContent('正在建立搜索索引')
    expect(screen.queryByText('无匹配结果，可尝试快速导航')).not.toBeInTheDocument()

    await act(async () => {
      examples.resolve(listMockExamples())
      processes.resolve([])
      await Promise.all([examples.promise, processes.promise])
    })

    expect(await screen.findByText('TBBPM 问候流程')).toBeInTheDocument()
  })

  it('reports failed sources and retries them without reopening the dialog', async () => {
    const loadExamples = vi
      .fn<() => Promise<Example[]>>()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(listMockExamples())
    const loadProcesses = vi.fn<() => Promise<[]>>().mockResolvedValue([])
    renderSearch(loadExamples, loadProcesses)

    fireEvent.click(screen.getByRole('button', { name: /打开全局搜索/ }))
    expect(await screen.findByText('部分搜索来源暂时不可用，请稍后重试')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: /重\s*试/ }))
    await waitFor(() => expect(loadExamples).toHaveBeenCalledTimes(2))
    await waitFor(() =>
      expect(screen.queryByText('部分搜索来源暂时不可用，请稍后重试')).not.toBeInTheDocument()
    )
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('refreshes the catalog whenever the dialog is reopened', async () => {
    const loadExamples = vi.fn<() => Promise<Example[]>>().mockResolvedValue(listMockExamples())
    const loadProcesses = vi.fn<() => Promise<[]>>().mockResolvedValue([])
    renderSearch(loadExamples, loadProcesses)

    fireEvent.click(screen.getByRole('button', { name: /打开全局搜索/ }))
    await waitFor(() => expect(loadExamples).toHaveBeenCalledTimes(1))
    fireEvent.keyDown(window, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: /打开全局搜索/ }))
    await waitFor(() => expect(loadExamples).toHaveBeenCalledTimes(2))
    expect(loadProcesses).toHaveBeenCalledTimes(2)
  })
})
