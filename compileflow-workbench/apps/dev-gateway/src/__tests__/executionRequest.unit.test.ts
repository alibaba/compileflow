import { describe, expect, it } from 'vitest'

import { parseExecutionRequest } from '../executionRequest'

describe('parseExecutionRequest', () => {
  it('parses an explicit draft preview', () => {
    expect(
      parseExecutionRequest({
        code: 'orders.approve',
        modelType: 'BPMN',
        xml: '<definitions/>',
        invocationId: 'inv-client:1',
        params: { amount: 100 },
      })
    ).toEqual({
      ok: true,
      request: {
        code: 'orders.approve',
        modelType: 'BPMN',
        xml: '<definitions/>',
        invocationId: 'inv-client:1',
        params: { amount: 100 },
      },
    })
  })

  it('requires code, model type, and XML', () => {
    expect(
      parseExecutionRequest({
        modelType: 'BPMN',
        xml: '<definitions/>',
      })
    ).toMatchObject({ ok: false, message: expect.stringContaining('code') })
    expect(
      parseExecutionRequest({
        code: 'orders.approve',
        modelType: 'UNKNOWN',
        xml: '<definitions/>',
      })
    ).toMatchObject({ ok: false, message: expect.stringContaining('modelType') })
    expect(
      parseExecutionRequest({
        code: 'orders.approve',
        modelType: 'BPMN',
        xml: ' ',
      })
    ).toMatchObject({ ok: false, message: expect.stringContaining('xml') })
  })

  it('rejects unsupported fields and non-object params', () => {
    expect(
      parseExecutionRequest({
        code: 'orders.approve',
        modelType: 'BPMN',
        xml: '<definitions/>',
        routing: { alias: 'production' },
      })
    ).toMatchObject({
      ok: false,
      message: 'Invalid request: unsupported preview field: routing',
    })
    expect(
      parseExecutionRequest({
        code: 'orders.approve',
        modelType: 'BPMN',
        xml: '<definitions/>',
        params: [],
      })
    ).toMatchObject({
      ok: false,
      message: 'Invalid request: params must be a JSON object when provided',
    })
  })
})
