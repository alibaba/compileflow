import { randomUUID } from 'node:crypto'

import type { DevGatewayLogLevel } from './config.js'
import { createLogger, type DevGatewayLogger } from './logger.js'

type MockProcessType = 'TBBPM' | 'BPMN'

export interface MockPreviewRequest {
  code: string
  modelType: MockProcessType
  xml: string
  invocationId?: string
  params?: Record<string, unknown>
}

export interface MockExecutionResult {
  success: true
  message: string
  traceId: string
  invocationId: string
  processCode: string
  durationMs: number
  routing: {
    namespace: 'default'
  }
  result: Record<string, unknown>
}

export class MockEngine {
  private readonly logger: DevGatewayLogger

  constructor(logLevel: DevGatewayLogLevel = 'info') {
    this.logger = createLogger('MockEngine', logLevel)
  }

  async execute(request: MockPreviewRequest): Promise<MockExecutionResult> {
    const startedAt = performance.now()
    const invocationId = request.invocationId ?? `inv-${randomUUID()}`
    const traceId = `trace-${randomUUID()}`
    this.logger.info('Executing simulated draft preview', {
      code: request.code,
      modelType: request.modelType,
    })

    return {
      success: true,
      message: 'Draft preview completed (mock)',
      result: {
        output: 'Mock preview result',
        inputParams: request.params ?? {},
      },
      traceId,
      invocationId,
      processCode: request.code,
      durationMs: Math.max(0, Math.round(performance.now() - startedAt)),
      routing: { namespace: 'default' },
    }
  }
}
