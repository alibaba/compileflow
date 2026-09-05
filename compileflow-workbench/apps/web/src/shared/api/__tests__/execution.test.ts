import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { executePreview, getEngineStatus } from '@/shared/api/execution'

const previewRequest = {
  code: 'order-approval-bpmn',
  modelType: 'BPMN' as const,
  xml: '<definitions/>',
}

describe('execution', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn())
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('getEngineStatus parses development gateway status response', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ engineAvailable: true, message: 'mock mode' }),
    } as Response)

    const status = await getEngineStatus()
    expect(status.engineAvailable).toBe(true)
    expect(status.message).toBe('mock mode')
  })

  it('executePreview posts the explicit draft definition', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        message: 'ok',
        traceId: 'trace-1',
        invocationId: 'inv-client-1',
        processCode: 'order-approval-bpmn',
        durationMs: 12,
        routing: {
          namespace: 'default',
          requestedAlias: 'production',
          alias: 'production',
          effectiveVersion: 'v2',
          routeRevision: 7,
          target: 'CANDIDATE',
        },
      }),
    } as Response)

    const response = await executePreview({
      ...previewRequest,
      invocationId: 'inv-client-1',
      params: { amount: 100 },
    })

    expect(response.success).toBe(true)
    expect(response.invocationId).toBe('inv-client-1')
    expect(response.processCode).toBe('order-approval-bpmn')
    expect(response.routing.effectiveVersion).toBe('v2')
    expect(fetch).toHaveBeenCalledWith(
      '/api/executions/preview',
      expect.objectContaining({ method: 'POST' })
    )
    const requestBody = JSON.parse(vi.mocked(fetch).mock.calls[0][1]?.body as string)
    expect(requestBody).toEqual({
      ...previewRequest,
      invocationId: 'inv-client-1',
      params: { amount: 100 },
    })
  })

  it('executePreview preserves backend problem details', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      json: async () => ({
        type: 'urn:compileflow:problem:invalid-argument',
        title: 'Invalid request',
        status: 400,
        detail: 'Invalid request: params must be a JSON object when provided',
        instance: '/api/executions/preview',
        code: 'INVALID_ARGUMENT',
      }),
    } as Response)

    await expect(
      executePreview({
        ...previewRequest,
        params: { amount: 100 },
      })
    ).rejects.toMatchObject({
      message: 'Invalid request: params must be a JSON object when provided',
      code: 'INVALID_ARGUMENT',
      context: {
        statusCode: 400,
        instance: '/api/executions/preview',
      },
    })
  })

  it('executePreview rejects invalid success response shape', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ message: 'missing success flag' }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('executePreview rejects missing execution attribution', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        message: 'ok',
        traceId: 'trace-1',
        invocationId: 'inv-1',
        durationMs: 1,
        routing: {},
      }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('executePreview never replays an execution after a network failure', async () => {
    vi.mocked(fetch).mockRejectedValue(new TypeError('connection reset'))

    await expect(
      executePreview({
        ...previewRequest,
        invocationId: 'inv-no-replay-1',
      })
    ).rejects.toThrow('connection reset')

    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('executePreview rejects an unstructured execution failure', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ success: false, message: 'failed', error: 'boom' }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('executePreview rejects a non-machine-readable failure code', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: false,
        message: 'failed',
        errorCode: 'invalid code',
        error: 'boom',
      }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('executePreview rejects contradictory success fields', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        message: 'ok',
        errorCode: 'CF_EXEC_004',
        error: 'must not coexist with success',
      }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })

  it('executePreview rejects malformed process attribution', async () => {
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        success: true,
        message: 'ok',
        traceId: 'trace-1',
        invocationId: 'inv-1',
        processCode: '',
        durationMs: 1,
        routing: { namespace: 'default' },
      }),
    } as Response)

    await expect(executePreview(previewRequest)).rejects.toThrow(
      'backend returned an invalid response'
    )
  })
})
