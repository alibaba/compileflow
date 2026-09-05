import { describe, expect, it } from 'vitest'

import { loadDevGatewayConfig } from '../config'

describe('development gateway config', () => {
  it('uses loopback development defaults', () => {
    const config = loadDevGatewayConfig({})

    expect(config).toEqual({
      logLevel: 'info',
      host: '127.0.0.1',
      port: 3001,
      maxRequestBytes: 10 * 1024 * 1024,
    })
    expect(Object.isFrozen(config)).toBe(true)
  })

  it('normalizes explicit non-secret values', () => {
    const config = loadDevGatewayConfig({
      NODE_ENV: 'test',
      COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL: ' DEBUG ',
      COMPILEFLOW_DEV_GATEWAY_PORT: ' 3101 ',
      COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES: ' 2048 ',
    })

    expect(config).toMatchObject({
      host: '127.0.0.1',
      logLevel: 'debug',
      port: 3101,
      maxRequestBytes: 2048,
    })
  })

  it('rejects production startup', () => {
    expect(() => loadDevGatewayConfig({ NODE_ENV: 'production' })).toThrow(
      'must not run with NODE_ENV=production'
    )
  })

  it('rejects unknown Node environments', () => {
    expect(() => loadDevGatewayConfig({ NODE_ENV: 'staging' })).toThrow(
      'NODE_ENV must be development or test'
    )
  })

  it('rejects invalid numeric settings', () => {
    expect(() =>
      loadDevGatewayConfig({
        COMPILEFLOW_DEV_GATEWAY_PORT: '70000',
      })
    ).toThrow('between 1 and 65535')
    expect(() =>
      loadDevGatewayConfig({
        COMPILEFLOW_DEV_GATEWAY_MAX_REQUEST_BYTES: '0',
      })
    ).toThrow('must be an integer')
  })

  it('rejects unknown log levels and variables', () => {
    expect(() =>
      loadDevGatewayConfig({
        COMPILEFLOW_DEV_GATEWAY_LOG_LEVEL: 'trace',
      })
    ).toThrow('must be error, warn, info, or debug')
    expect(() =>
      loadDevGatewayConfig({
        COMPILEFLOW_DEV_GATEWAY_MAX_CONCURRENCY: '4',
      })
    ).toThrow(
      'Unknown development gateway variable(s): ' + 'COMPILEFLOW_DEV_GATEWAY_MAX_CONCURRENCY'
    )
  })
})
