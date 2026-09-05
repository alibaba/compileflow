import { App } from 'antd'
import { isAxiosError } from 'axios'
import type { ErrorInfo } from 'react'
import { useEffect } from 'react'

import { devError } from '../config/buildConfig'
import { AppError, ErrorSeverity, NetworkError, toError } from '../errors'
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
    Object.setPrototypeOf(this, TimeoutError.prototype)
  }
}

/** Convert any thrown value into a typed AppError. */
export function handleApiError(error: unknown): AppError {
  if (error instanceof AppError) {
    devError('AppError:', error.toJSON())
    return error
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
      devError('Unhandled Promise Rejection:', event.reason)
      notify(event.reason)
    }
    const handleWindowError = (event: ErrorEvent) => {
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

export async function retryOperation<T>(
  operation: () => Promise<T>,
  options: {
    maxRetries?: number
    delay?: number
    onRetry?: (attempt: number, error: Error) => void
    isRetryable?: (error: Error) => boolean
  } = {}
): Promise<T> {
  const { maxRetries = 3, delay = 1000, onRetry, isRetryable = () => true } = options
  let lastError: Error | undefined

  for (let attempt = 1; attempt <= maxRetries; attempt++) {
    try {
      return await operation()
    } catch (error) {
      lastError = toError(error)
      if (!isRetryable(lastError) || attempt >= maxRetries) {
        break
      }
      onRetry?.(attempt, lastError)
      // Full jitter: multiply base exponential by U(0.5, 1.0) to desynchronise concurrent retries.
      const jitter = 0.5 + Math.random() * 0.5
      await new Promise((resolve) => setTimeout(resolve, delay * Math.pow(2, attempt - 1) * jitter))
    }
  }

  throw lastError ?? new Error(i18n.t('error.retryExhausted'))
}

/** Whether a fetch-based execution call should be retried (transient network / overload). */
export function isTransientFetchError(error: Error): boolean {
  if (error.name === 'AbortError') {
    return true
  }
  if (
    error instanceof AppError &&
    typeof error.context?.statusCode === 'number' &&
    [408, 429, 502, 503, 504].includes(error.context.statusCode)
  ) {
    return true
  }
  if (/HTTP (408|429|502|503|504)/.test(error.message)) {
    return true
  }
  return /fetch failed|network|ECONNRESET|ETIMEDOUT/i.test(error.message)
}
