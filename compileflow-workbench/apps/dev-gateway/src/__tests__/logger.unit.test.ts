import { describe, expect, it, vi } from 'vitest'

import { createLogger } from '../logger.js'

function transport() {
  return {
    error: vi.fn(),
    log: vi.fn(),
    warn: vi.fn(),
  }
}

describe('development gateway logger', () => {
  it('filters messages below the configured threshold', () => {
    const output = transport()
    const logger = createLogger('Test', 'warn', output)

    logger.debug('debug')
    logger.info('info')
    logger.warn('warn')
    logger.error('error')

    expect(output.log).not.toHaveBeenCalled()
    expect(output.warn).toHaveBeenCalledOnce()
    expect(output.error).toHaveBeenCalledOnce()
  })

  it('writes structured JSON and redacts nested credentials', () => {
    const output = transport()
    const logger = createLogger('Test', 'debug', output)

    logger.error('request failed', new Error('upstream failed'), {
      request: {
        apiKey: 'client-secret',
        headers: { authorization: 'Bearer server-secret', accept: 'application/json' },
      },
    })

    const event = JSON.parse(output.error.mock.calls[0][0] as string) as Record<string, unknown>
    expect(event).toMatchObject({ level: 'error', scope: 'Test', message: 'request failed' })
    expect(event.timestamp).toEqual(expect.any(String))
    expect(event.data).toEqual({
      request: {
        apiKey: '[REDACTED]',
        headers: { authorization: '[REDACTED]', accept: 'application/json' },
      },
    })
    expect(event.error).toEqual(
      expect.objectContaining({
        name: 'Error',
        message: 'upstream failed',
      })
    )
    expect(output.error.mock.calls[0][0]).not.toContain('client-secret')
    expect(output.error.mock.calls[0][0]).not.toContain('server-secret')
  })

  it('serializes circular data without throwing', () => {
    const output = transport()
    const logger = createLogger('Test', 'debug', output)
    const data: Record<string, unknown> = {}
    data.self = data

    expect(() => logger.debug('circular', data)).not.toThrow()
    expect(JSON.parse(output.log.mock.calls[0][0] as string).data).toEqual({ self: '[Circular]' })
  })

  it('redacts complete inline authorization and cookie values', () => {
    const output = transport()
    const logger = createLogger('Test', 'debug', output)

    logger.debug(
      [
        'Authorization: Bearer server-token',
        'Cookie: session=session-secret; csrf=csrf-secret',
      ].join('\n')
    )

    const serialized = output.log.mock.calls[0][0] as string
    const event = JSON.parse(serialized) as { message: string }
    expect(event.message).toBe('Authorization: [REDACTED]\nCookie: [REDACTED]')
    expect(serialized).not.toContain('server-token')
    expect(serialized).not.toContain('session-secret')
    expect(serialized).not.toContain('csrf-secret')
  })
})
