import { App } from 'antd'
import { isAxiosError, isCancel } from 'axios'
import type { ErrorInfo } from 'react'
import { useEffect } from 'react'

import { devError } from '../config/buildConfig'
import { AppError, ErrorSeverity, NetworkError } from '../errors'
import i18n from '../i18n'

export { AppError, NetworkError, ErrorSeverity }

export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  extensions: Record<string, unknown>
}

const PROBLEM_STANDARD_FIELDS = new Set(['type', 'title', 'status', 'detail', 'instance', 'code'])

function nonBlankString(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() !== '' ? value : undefined
}

export function parseProblemDetail(value: unknown): ProblemDetail | undefined {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return undefined
  }

  const record = value as Record<string, unknown>
  const type = nonBlankString(record.type)
  const title = nonBlankString(record.title)
  const detail = nonBlankString(record.detail)
  const instance = nonBlankString(record.instance)
  const code = nonBlankString(record.code)
  const status =
    typeof record.status === 'number' && Number.isInteger(record.status) ? record.status : undefined

  if (detail === undefined || (type === undefined && title === undefined)) {
    return undefined
  }

  const extensions = Object.fromEntries(
    Object.entries(record).filter(([key]) => !PROBLEM_STANDARD_FIELDS.has(key))
  )
  return { type, title, status, detail, instance, code, extensions }
}

export function problemDetailToAppError(problem: ProblemDetail, httpStatus: number): AppError {
  return new AppError(
    problem.detail ?? problem.title ?? i18n.t('error.requestFailed'),
    problem.code ?? `HTTP_${httpStatus}`,
    httpStatus >= 500 ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM,
    {
      statusCode: httpStatus,
      type: problem.type,
      title: problem.title,
      problemStatus: problem.status,
      instance: problem.instance,
      extensions: problem.extensions,
    }
  )
}

/** Timeout error — wraps ECONNABORTED and request timeout cases. */
export class TimeoutError extends AppError {
  constructor(message = i18n.t('error.requestTimeout')) {
    super(message, 'TIMEOUT', ErrorSeverity.HIGH)
    this.name = 'TimeoutError'
  }
}

/** Convert any thrown value into a typed AppError. */
export function handleApiError(error: unknown): AppError {
  if (error instanceof AppError) {
    devError('AppError:', error.toJSON())
    return error
  }

  if (isExpectedCancellation(error)) {
    return new AppError(
      error instanceof Error || error instanceof DOMException ? error.message : 'Canceled',
      'REQUEST_CANCELLED'
    )
  }

  if (isAxiosError<unknown>(error)) {
    if (!error.response) {
      if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
        return new TimeoutError()
      }
      return new NetworkError(i18n.t('error.networkConnection'), {
        originalMessage: error.message,
      })
    }

    const { status, data } = error.response
    const problem = parseProblemDetail(data)
    if (problem !== undefined) {
      return problemDetailToAppError(problem, status)
    }

    return new AppError(
      error.message || i18n.t('error.requestFailed'),
      `HTTP_${status}`,
      status >= 500 ? ErrorSeverity.HIGH : ErrorSeverity.MEDIUM,
      { statusCode: status }
    )
  }

  if (error instanceof Error) {
    devError('Error:', error)
    return new AppError(error.message, 'UNKNOWN_ERROR')
  }

  devError('Unknown Error:', error)
  return new AppError(i18n.t('error.unknown'), 'UNKNOWN_ERROR', ErrorSeverity.MEDIUM, {
    raw: error,
  })
}

export function handleComponentError(error: Error, errorInfo: ErrorInfo): void {
  devError('Component Error:', {
    error: error.message,
    stack: error.stack,
    componentStack: errorInfo.componentStack,
  })
}

function isExpectedCancellation(error: unknown): boolean {
  if (isCancel(error)) return true
  if (error instanceof AppError) return error.code === 'REQUEST_CANCELLED'
  if (!(error instanceof Error) && !(error instanceof DOMException)) return false
  return error.name === 'AbortError' || (error.name === 'Canceled' && error.message === 'Canceled')
}

export function GlobalErrorHandler() {
  const { notification } = App.useApp()

  useEffect(() => {
    const notify = (error: unknown, description?: string) => {
      notification.error({
        message: i18n.t('error.operationFailed'),
        description: description ?? handleApiError(error).message,
      })
    }
    const handleUnhandledRejection = (event: PromiseRejectionEvent) => {
      event.preventDefault()
      if (isExpectedCancellation(event.reason)) return
      devError('Unhandled Promise Rejection:', event.reason)
      notify(event.reason)
    }
    const handleWindowError = (event: ErrorEvent) => {
      if (isExpectedCancellation(event.error)) {
        event.preventDefault()
        return
      }
      devError('Global Error:', {
        message: event.message,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
        error: event.error,
      })
      if (!event.filename) notify(event.error, i18n.t('error.application'))
    }

    window.addEventListener('unhandledrejection', handleUnhandledRejection)
    window.addEventListener('error', handleWindowError)
    return () => {
      window.removeEventListener('unhandledrejection', handleUnhandledRejection)
      window.removeEventListener('error', handleWindowError)
    }
  }, [notification])

  return null
}

/** Whether a classified query failure is transient. Mutating requests must not be replayed. */
export function isTransientApiError(error: Error): boolean {
  if (error instanceof NetworkError || error instanceof TimeoutError) {
    return true
  }
  return (
    error instanceof AppError &&
    typeof error.context?.statusCode === 'number' &&
    [408, 429, 502, 503, 504].includes(error.context.statusCode)
  )
}
