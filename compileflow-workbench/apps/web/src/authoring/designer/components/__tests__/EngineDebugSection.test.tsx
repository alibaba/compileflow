import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import type { MessageInstance } from 'antd/es/message/interface'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { EngineDebugSection } from '../EngineDebugSection'

import { executePreview, getEngineStatus } from '@/shared/api/execution'
import type { ExecutionResponse } from '@/shared/contracts/executionContract'

vi.mock('@/shared/api/execution', () => ({
  executePreview: vi.fn(),
  getEngineStatus: vi.fn(),
}))

const message = {
  error: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
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

const success: ExecutionResponse = {
  success: true,
  message: 'old result',
  traceId: 'trace-1',
  invocationId: 'invocation-1',
  processCode: 'first-flow',
  durationMs: 1,
  routing: { namespace: 'default' },
}

describe('EngineDebugSection request lifecycle', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(getEngineStatus).mockResolvedValue({ engineAvailable: true, message: 'ready' })
  })

  it('prevents duplicate execution and ignores a result for a replaced draft', async () => {
    const request = deferred<ExecutionResponse>()
    vi.mocked(executePreview).mockReturnValue(request.promise)
    const { rerender } = render(
      <EngineDebugSection
        processCode="first-flow"
        modelType="BPMN"
        flowXml="<first />"
        paramsJson="{}"
        onParamsJsonChange={vi.fn()}
      />
    )
    const run = await screen.findByRole('button', { name: /在服务器运行草稿/ })
    await waitFor(() => expect(run).toBeEnabled())

    fireEvent.click(run)
    fireEvent.click(run)
    expect(executePreview).toHaveBeenCalledOnce()

    rerender(
      <EngineDebugSection
        processCode="second-flow"
        modelType="BPMN"
        flowXml="<second />"
        paramsJson="{}"
        onParamsJsonChange={vi.fn()}
      />
    )
    request.resolve(success)

    await waitFor(() => expect(screen.queryByText('old result')).not.toBeInTheDocument())
    expect(message.success).not.toHaveBeenCalled()
  })
})
