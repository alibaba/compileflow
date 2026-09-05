import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { createLogger, logger } from '../logger'

const mockRuntimeConfig = vi.hoisted(() => ({
  enableDebug: true,
}))

vi.mock('@/shared/config/buildConfig', async () => {
  const actual = await vi.importActual<typeof import('@/shared/config/buildConfig')>(
    '@/shared/config/buildConfig'
  )
  return {
    ...actual,
    APP_BUILD_CONFIG: mockRuntimeConfig,
  }
})

describe('Logger System', () => {
  let consoleLogSpy: ReturnType<typeof vi.spyOn>
  let consoleWarnSpy: ReturnType<typeof vi.spyOn>
  let consoleErrorSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    mockRuntimeConfig.enableDebug = true
    consoleLogSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    consoleWarnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })

  afterEach(() => {
    consoleLogSpy.mockRestore()
    consoleWarnSpy.mockRestore()
    consoleErrorSpy.mockRestore()
  })

  describe('Basic Logging', () => {
    it('should log debug message when debug logging is enabled', () => {
      logger.debug('Test debug message')
      expect(consoleLogSpy).toHaveBeenCalled()
    })

    it('should log info message', () => {
      logger.info('Test info message')
      expect(consoleLogSpy).toHaveBeenCalled()
    })

    it('should log warn message', () => {
      logger.warn('Test warn message')
      expect(consoleWarnSpy).toHaveBeenCalled()
    })

    it('should log error message', () => {
      const error = new Error('Test error')
      logger.error('Test error message', {}, error)
      expect(consoleErrorSpy).toHaveBeenCalled()
    })

    it('should remain silent when debug logging is disabled', () => {
      mockRuntimeConfig.enableDebug = false

      logger.warn('Hidden warning')
      logger.error('Hidden error')

      expect(consoleWarnSpy).not.toHaveBeenCalled()
      expect(consoleErrorSpy).not.toHaveBeenCalled()
    })
  })

  describe('Structured Logging', () => {
    it('should accept context data', () => {
      logger.debug('Test with context', {
        data: { userId: '123', action: 'test' },
      })
      expect(consoleLogSpy).toHaveBeenCalled()
      const logCall = consoleLogSpy.mock.calls[0][0]
      expect(logCall).toContain('Test with context')
    })

    it('should accept component context', () => {
      logger.info('Test with component', {
        component: 'TestComponent',
        data: { count: 42 },
      })
      expect(consoleLogSpy).toHaveBeenCalled()
    })

    it('should sanitize messages, context, and errors before transport', () => {
      logger.error(
        'Authorization: Bearer message-secret',
        { data: { apiKey: 'context-secret' } },
        new Error('token=error-secret')
      )

      const transported = JSON.stringify(consoleErrorSpy.mock.calls[0])
      expect(transported).not.toContain('message-secret')
      expect(transported).not.toContain('context-secret')
      expect(transported).not.toContain('error-secret')
      expect(transported).toContain('[REDACTED]')
    })
  })

  describe('Component Logger', () => {
    it('should create component logger with prefix', () => {
      const componentLogger = createLogger('TestComponent')
      componentLogger.debug('Component message')
      expect(consoleLogSpy).toHaveBeenCalled()
      const logCall = consoleLogSpy.mock.calls[0][0]
      expect(logCall).toContain('[TestComponent]')
    })

    it('should support action logging', () => {
      const componentLogger = createLogger('TestComponent')
      componentLogger.action('testAction', 'Action executed', { data: { id: '1' } })
      expect(consoleLogSpy).toHaveBeenCalled()
    })
  })

  describe('Performance Measurement', () => {
    it('should support time measurement', () => {
      const consoleTimeSpy = vi.spyOn(console, 'time').mockImplementation(() => {})
      const consoleTimeEndSpy = vi.spyOn(console, 'timeEnd').mockImplementation(() => {})

      logger.time('TestOperation')
      logger.timeEnd('TestOperation')

      expect(consoleTimeSpy).toHaveBeenCalledWith(expect.stringContaining('TestOperation'))
      expect(consoleTimeEndSpy).toHaveBeenCalledWith(expect.stringContaining('TestOperation'))

      consoleTimeSpy.mockRestore()
      consoleTimeEndSpy.mockRestore()
    })
  })

  describe('Group Logging', () => {
    it('should support log grouping', () => {
      const consoleGroupSpy = vi.spyOn(console, 'group').mockImplementation(() => {})
      const consoleGroupEndSpy = vi.spyOn(console, 'groupEnd').mockImplementation(() => {})

      logger.group('TestGroup')
      logger.debug('Grouped message')
      logger.groupEnd()

      expect(consoleGroupSpy).toHaveBeenCalled()
      expect(consoleGroupEndSpy).toHaveBeenCalled()

      consoleGroupSpy.mockRestore()
      consoleGroupEndSpy.mockRestore()
    })
  })
})
