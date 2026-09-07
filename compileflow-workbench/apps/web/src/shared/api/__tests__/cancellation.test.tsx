import { render } from '@testing-library/react'
import { CanceledError } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { GlobalErrorHandler, handleApiError, isTransientApiError } from '../errorHandler'

const notification = vi.hoisted(() => ({ error: vi.fn() }))
vi.mock('antd', () => ({ App: { useApp: () => ({ notification }) } }))

const cancellations = [
  new CanceledError('request canceled'),
  new DOMException('aborted', 'AbortError'),
  Object.assign(new Error('Canceled'), { name: 'Canceled' }),
]

describe('expected cancellation', () => {
  beforeEach(() => vi.clearAllMocks())

  it.each(cancellations)('is not a transient request failure: $name', (error) => {
    const classified = handleApiError(error)
    expect(classified.code).toBe('REQUEST_CANCELLED')
    expect(isTransientApiError(classified)).toBe(false)
  })

  it.each(cancellations)('does not notify for canceled async work: $name', (reason) => {
    render(<GlobalErrorHandler />)
    const rejection = new Event('unhandledrejection', { cancelable: true })
    Object.defineProperty(rejection, 'reason', { value: reason })
    window.dispatchEvent(rejection)
    window.dispatchEvent(new ErrorEvent('error', { error: reason, message: reason.message }))
    expect(notification.error).not.toHaveBeenCalled()
  })

  it('still reports ordinary errors even when their message mentions cancellation', () => {
    render(<GlobalErrorHandler />)
    window.dispatchEvent(
      new ErrorEvent('error', { error: new Error('Canceled'), message: 'Canceled' })
    )
    expect(notification.error).toHaveBeenCalledOnce()
  })
})
