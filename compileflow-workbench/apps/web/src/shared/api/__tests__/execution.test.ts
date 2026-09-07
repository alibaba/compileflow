import { AxiosError } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import apiClient from '@/shared/api/client'
import { TimeoutError } from '@/shared/api/errorHandler'
import { executePreview, getEngineStatus } from '@/shared/api/execution'

vi.mock('@/shared/api/client', () => ({ default: { get: vi.fn(), post: vi.fn() } }))

const previewRequest = {
  code: 'order-approval-bpmn',
  modelType: 'BPMN' as const,
  xml: '<definitions/>',
}

const success = {
  success: true,
  message: 'ok',
  traceId: 'trace-1',
  invocationId: 'inv-client-1',
  processCode: 'order-approval-bpmn',
  durationMs: 12,
  routing: { namespace: 'default' },
}

describe('execution', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('queries status through the shared client', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ engineAvailable: true, message: 'mock mode' })

    expect(await getEngineStatus()).toEqual({ engineAvailable: true, message: 'mock mode' })
    expect(apiClient.get).toHaveBeenCalledWith('/api/status')
  })

  it('posts the exact draft request through the shared client', async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce(success)
    const request = { ...previewRequest, invocationId: 'inv-client-1', params: { amount: 100 } }

    expect(await executePreview(request)).toEqual(success)
    expect(apiClient.post).toHaveBeenCalledExactlyOnceWith('/api/executions/preview', request)
  })

  it('preserves backend problem details', async () => {
    vi.mocked(apiClient.post).mockRejectedValueOnce({
      isAxiosError: true,
      message: 'Bad Request',
      response: {
        status: 400,
        data: {
          type: 'urn:compileflow:problem:invalid-argument',
          title: 'Invalid request',
          status: 400,
          detail: 'Invalid request: params must be a JSON object when provided',
          instance: '/api/executions/preview',
          code: 'INVALID_ARGUMENT',
        },
      },
    })

    await expect(executePreview(previewRequest)).rejects.toMatchObject({
      message: 'Invalid request: params must be a JSON object when provided',
      code: 'INVALID_ARGUMENT',
      context: { statusCode: 400, instance: '/api/executions/preview' },
    })
  })

  it.each([
    new AxiosError('connection reset', 'ERR_NETWORK'),
    new AxiosError('timeout', 'ECONNABORTED'),
  ])('never replays an execution after a transport failure: $code', async (failure) => {
    vi.mocked(apiClient.post).mockRejectedValue(failure)

    await expect(executePreview(previewRequest)).rejects.toMatchObject({
      code: failure.code === 'ECONNABORTED' ? 'TIMEOUT' : 'NETWORK_ERROR',
    })
    expect(apiClient.post).toHaveBeenCalledTimes(1)
  })

  it('retries a transient status failure, not the execution endpoint', async () => {
    vi.useFakeTimers()
    vi.mocked(apiClient.get)
      .mockRejectedValueOnce(new AxiosError('timeout', 'ECONNABORTED'))
      .mockResolvedValueOnce({ engineAvailable: true, message: 'ready' })

    const request = getEngineStatus()
    const result = expect(request).resolves.toEqual({ engineAvailable: true, message: 'ready' })
    await vi.runAllTimersAsync()
    await result
    expect(apiClient.get).toHaveBeenCalledTimes(2)
    expect(apiClient.post).not.toHaveBeenCalled()
  })

  it('bounds retries when the status endpoint keeps timing out', async () => {
    vi.useFakeTimers()
    vi.mocked(apiClient.get).mockRejectedValue(new AxiosError('timeout', 'ECONNABORTED'))

    const result = expect(getEngineStatus()).rejects.toBeInstanceOf(TimeoutError)
    await vi.runAllTimersAsync()
    await result
    expect(apiClient.get).toHaveBeenCalledTimes(3)
  })

  it('does not retry an invalid status response', async () => {
    vi.mocked(apiClient.get).mockResolvedValue({ engineAvailable: 'true' })

    await expect(getEngineStatus()).rejects.toThrow('backend returned an invalid response')
    expect(apiClient.get).toHaveBeenCalledTimes(1)
  })

  it.each([
    null,
    { message: 'missing success flag' },
    { ...success, invocationId: undefined },
    { ...success, processCode: '' },
    { ...success, durationMs: Number.NaN },
    { ...success, routing: {} },
    { ...success, routing: { namespace: 'default', routeRevision: 0 } },
    { ...success, routing: { namespace: 'default', target: 'UNKNOWN' } },
    { ...success, success: false, error: 'boom' },
    { ...success, success: false, errorCode: 'invalid code', error: 'boom' },
    { ...success, errorCode: 'CF_EXEC_004', error: 'contradiction' },
    { ...success, result: [] },
  ])('rejects an invalid execution response: %j', async (body) => {
    vi.mocked(apiClient.post).mockResolvedValueOnce(body)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('accepts a structured execution failure without treating it as a transport failure', async () => {
    const failure = { ...success, success: false, errorCode: 'CF_EXEC_004', error: 'failed' }
    vi.mocked(apiClient.post).mockResolvedValueOnce(failure)

    expect(await executePreview(previewRequest)).toEqual(failure)
    expect(apiClient.post).toHaveBeenCalledTimes(1)
  })
})
