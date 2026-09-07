import { describe, expect, it } from 'vitest'

import {
  AppError,
  ErrorSeverity,
  handleApiError,
  isTransientApiError,
  NetworkError,
  TimeoutError,
} from '@/shared/api/errorHandler'

function axiosError(status: number | undefined, data?: unknown, code?: string): unknown {
  return {
    isAxiosError: true,
    name: 'AxiosError',
    message: status === undefined ? 'Network Error' : `Request failed with status code ${status}`,
    code,
    response: status === undefined ? undefined : { status, data },
  }
}

describe('handleApiError', () => {
  it('preserves an existing application error', () => {
    const original = new AppError('already classified', 'KNOWN')

    expect(handleApiError(original)).toBe(original)
  })

  it('maps an RFC 9457 response to its stable CompileFlow error code', () => {
    const result = handleApiError(
      axiosError(422, {
        type: 'urn:compileflow:problem:process-preflight-failed',
        title: 'Process preflight failed',
        status: 422,
        detail: 'The process cannot be published',
        instance: '/api/processes/payment.approve/publish',
        code: 'PROCESS_PREFLIGHT_FAILED',
        preflight: { valid: false },
      })
    )

    expect(result.message).toBe('The process cannot be published')
    expect(result.code).toBe('PROCESS_PREFLIGHT_FAILED')
    expect(result.severity).toBe(ErrorSeverity.MEDIUM)
    expect(result.context).toEqual({
      statusCode: 422,
      type: 'urn:compileflow:problem:process-preflight-failed',
      title: 'Process preflight failed',
      problemStatus: 422,
      instance: '/api/processes/payment.approve/publish',
      extensions: { preflight: { valid: false } },
    })
  })

  it('falls back to the HTTP status for a problem without an extension code', () => {
    const result = handleApiError(
      axiosError(503, {
        type: 'about:blank',
        title: 'Service Unavailable',
        status: 503,
        detail: 'The upstream service is unavailable',
      })
    )

    expect(result.message).toBe('The upstream service is unavailable')
    expect(result.code).toBe('HTTP_503')
    expect(result.severity).toBe(ErrorSeverity.HIGH)
  })

  it('does not interpret an arbitrary JSON error as Problem Details', () => {
    const result = handleApiError(
      axiosError(502, { message: 'unstructured upstream response', code: 17 })
    )

    expect(result.message).toBe('Request failed with status code 502')
    expect(result.code).toBe('HTTP_502')
    expect(result.context).toEqual({ statusCode: 502 })
  })

  it('classifies an Axios timeout without an HTTP response', () => {
    expect(handleApiError(axiosError(undefined, undefined, 'ECONNABORTED'))).toBeInstanceOf(
      TimeoutError
    )
  })

  it('classifies other Axios failures without a response as network errors', () => {
    expect(handleApiError(axiosError(undefined))).toBeInstanceOf(NetworkError)
  })

  it('uses a structured HTTP status when deciding whether to retry', () => {
    const result = handleApiError(
      axiosError(503, {
        type: 'urn:compileflow:problem:service-unavailable',
        title: 'Service unavailable',
        status: 503,
        detail: 'Capacity is temporarily exhausted',
        code: 'BRIDGE_CAPACITY_EXHAUSTED',
      })
    )

    expect(isTransientApiError(result)).toBe(true)
  })

  it('does not infer retry authority from error prose', () => {
    expect(isTransientApiError(new Error('HTTP 503 network failure'))).toBe(false)
    expect(isTransientApiError(handleApiError(axiosError(400)))).toBe(false)
    expect(isTransientApiError(new NetworkError('disconnected'))).toBe(true)
    expect(isTransientApiError(new TimeoutError())).toBe(true)
  })
})
