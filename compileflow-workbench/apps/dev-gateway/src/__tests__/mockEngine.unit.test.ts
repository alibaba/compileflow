import { createHash } from 'node:crypto'

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

    expect(result).toMatchObject({
      success: true,
      modelType: 'BPMN',
      routing: { namespace: 'default' },
      result: {
        processCode: 'bpm.hello',
        inputParams: { userId: 'u-1' },
      },
    })
    expect(result.message).toContain('mock')
    expect(result.traceId).toMatch(/^trace-/)
    expect(result.invocationId).toMatch(/^inv-/)
    expect(result.sourceDigest).toBe(createHash('sha256').update(xml, 'utf8').digest('hex'))
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
