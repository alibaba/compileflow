import { describe, expect, it, vi } from 'vitest'

import {
  devLog,
  resolveBuildConfig,
  resolveOperateMode,
  sanitizeDiagnosticValue,
} from '../buildConfig'

import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'

describe('buildConfig operate mode resolution', () => {
  it('prefers explicit VITE_COMPILEFLOW_OPERATE_MODE when provided', () => {
    expect(
      resolveOperateMode({
        MODE: 'development',
        VITE_COMPILEFLOW_OPERATE_MODE: 'real',
      })
    ).toBe('real')
  })

  it('defaults to mock in development when mode is absent', () => {
    expect(resolveOperateMode({ MODE: 'development' })).toBe('mock')
  })

  it('defaults to real outside development when mode is absent', () => {
    expect(resolveOperateMode({ MODE: 'production' })).toBe('real')
  })
})

describe('buildConfig unified build config resolution', () => {
  it('resolves build-time values from a single top-level entrypoint', () => {
    expect(
      resolveBuildConfig(
        {
          MODE: 'test',
          VITE_COMPILEFLOW_OPERATE_MODE: 'mock',
          VITE_COMPILEFLOW_DEBUG: 'true',
        },
        '2.3.4'
      )
    ).toEqual({
      operateMode: 'mock',
      enableDebug: true,
      buildMode: 'test',
      appVersion: '2.3.4',
      useBuiltInExamples: true,
    })
  })

  it('provides defaults when optional build fields are absent', () => {
    const buildConfig = resolveBuildConfig({ MODE: 'development' })

    expect(buildConfig.operateMode).toBe('mock')
    expect(buildConfig.enableDebug).toBe(false)
    expect(buildConfig.buildMode).toBe('development')
    expect(buildConfig.appVersion).toBe('unknown')
    expect(buildConfig.useBuiltInExamples).toBe(true)
  })
})

describe('buildConfig singleton invariants', () => {
  it('rejects invalid explicit enum and boolean values', () => {
    expect(() => resolveBuildConfig({ VITE_COMPILEFLOW_OPERATE_MODE: 'automatic' })).toThrow(
      'VITE_COMPILEFLOW_OPERATE_MODE must be mock or real'
    )
    expect(() => resolveBuildConfig({ VITE_COMPILEFLOW_DEBUG: 'yes' })).toThrow(
      'VITE_COMPILEFLOW_DEBUG must be true or false'
    )
    expect(() => resolveBuildConfig({ VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES: '' })).toThrow(
      'VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES must be true or false'
    )
  })

  it('rejects a deployment bundle backed by in-browser operate mocks', () => {
    expect(() =>
      resolveBuildConfig({
        MODE: 'production',
        VITE_COMPILEFLOW_OPERATE_MODE: 'mock',
      })
    ).toThrow('VITE_COMPILEFLOW_OPERATE_MODE must be real in production builds')
  })

  it('rejects deployment environments as Web build modes', () => {
    expect(() => resolveBuildConfig({ MODE: 'staging' })).toThrow(
      'MODE must be development, production, or test'
    )
  })

  it('rejects mock mode without the only available Learn catalog', () => {
    expect(() =>
      resolveBuildConfig({
        MODE: 'development',
        VITE_COMPILEFLOW_OPERATE_MODE: 'mock',
        VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES: 'false',
      })
    ).toThrow(
      'VITE_COMPILEFLOW_USE_BUILT_IN_EXAMPLES must be true when ' +
        'VITE_COMPILEFLOW_OPERATE_MODE is mock'
    )
  })

  it('rejects retired cross-origin endpoint variables', () => {
    const retiredApiUrl = { MODE: 'test', VITE_COMPILEFLOW_API_BASE_URL: 'https://api.example.com' }
    const retiredBridgeUrl = {
      MODE: 'test',
      VITE_COMPILEFLOW_BRIDGE_URL: 'https://bridge.example.com',
    }
    const retiredMockData = { MODE: 'test', VITE_COMPILEFLOW_USE_MOCK_DATA: 'true' }

    expect(() => resolveBuildConfig(retiredApiUrl)).toThrow(
      'Unknown web build configuration variable(s): VITE_COMPILEFLOW_API_BASE_URL'
    )
    expect(() => resolveBuildConfig(retiredBridgeUrl)).toThrow(
      'Unknown web build configuration variable(s): VITE_COMPILEFLOW_BRIDGE_URL'
    )
    expect(() => resolveBuildConfig(retiredMockData)).toThrow(
      'Unknown web build configuration variable(s): VITE_COMPILEFLOW_USE_MOCK_DATA'
    )
  })

  it('rejects unknown variables in the public build namespace', () => {
    const env = { MODE: 'test', VITE_COMPILEFLOW_DEBIG: 'true' }
    expect(() => resolveBuildConfig(env)).toThrow(
      'Unknown web build configuration variable(s): VITE_COMPILEFLOW_DEBIG'
    )
  })

  it('does not claim unrelated Vite variable namespaces', () => {
    const env = { MODE: 'test', VITE_ANALYTICS_ID: 'third-party' }
    expect(() => resolveBuildConfig(env)).not.toThrow()
  })
})

describe('buildConfig diagnostic redaction', () => {
  it('keeps diagnostic logging disabled when the explicit debug option is false', () => {
    const log = vi.spyOn(console, 'info').mockImplementation(() => undefined)

    devLog('disabled diagnostic')

    expect(APP_BUILD_CONFIG.enableDebug).toBe(false)
    expect(log).not.toHaveBeenCalled()
    log.mockRestore()
  })

  it('sanitizes Error metadata and credential-shaped headers', () => {
    const error = Object.assign(new Error('request failed'), {
      config: {
        apiKey: 'client-key',
        headers: new Headers({ Authorization: 'Bearer server-key', Accept: 'application/json' }),
      },
    })

    expect(sanitizeDiagnosticValue(error)).toEqual(
      expect.objectContaining({
        name: 'Error',
        message: 'request failed',
        config: {
          apiKey: '[REDACTED]',
          headers: { accept: 'application/json', authorization: '[REDACTED]' },
        },
      })
    )
    expect(JSON.stringify(sanitizeDiagnosticValue(error))).not.toContain('server-key')
  })

  it('handles circular diagnostic data', () => {
    const data: Record<string, unknown> = {}
    data.self = data

    expect(sanitizeDiagnosticValue(data)).toEqual({ self: '[Circular]' })
  })

  it('redacts complete inline authorization and cookie values', () => {
    const diagnostic = [
      'Authorization: Bearer server-token',
      'Cookie: session=session-secret; csrf=csrf-secret',
    ].join('\n')

    const sanitized = sanitizeDiagnosticValue(diagnostic)

    expect(sanitized).toBe('Authorization: [REDACTED]\nCookie: [REDACTED]')
    expect(sanitized).not.toContain('server-token')
    expect(sanitized).not.toContain('session-secret')
    expect(sanitized).not.toContain('csrf-secret')
  })
})
