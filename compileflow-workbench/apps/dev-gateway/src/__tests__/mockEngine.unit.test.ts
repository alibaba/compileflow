import { describe, expect, it } from 'vitest'

import { MockEngine } from '../mockEngine'

describe('MockEngine', () => {
  it('returns realistic attribution for a simulated draft preview', async () => {
    const xml = '<definitions/>'
    const result = await new MockEngine().execute({
      code: 'bpm.hello',
      modelType: 'BPMN',
      xml,
      params: { userId: 'u-1' },
    })

    expect(result).toEqual({
      success: true,
      message: expect.stringContaining('mock'),
      traceId: expect.stringMatching(/^trace-/),
      invocationId: expect.stringMatching(/^inv-/),
      processCode: 'bpm.hello',
      durationMs: expect.any(Number),
      routing: { namespace: 'default' },
      result: {
        output: 'Mock preview result',
        inputParams: { userId: 'u-1' },
      },
    })
    expect(result.durationMs).toBeGreaterThanOrEqual(0)
  })

  it('preserves a caller-supplied invocation id', async () => {
    const result = await new MockEngine().execute({
      code: 'bpm.hello',
      modelType: 'TBBPM',
      xml: '<bpm/>',
      invocationId: 'inv-client-1',
    })

    expect(result.invocationId).toBe('inv-client-1')
  })
})
