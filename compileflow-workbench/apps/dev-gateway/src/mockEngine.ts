import { createHash, randomUUID } from 'node:crypto'

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

interface ExecutionResultBase {
  message: string
  traceId: string
  invocationId: string
  modelType: MockProcessType
  sourceDigest: string
  durationMs: number
  routing: {
    namespace: 'default'
  }
}

interface ExecutionSuccessResult extends ExecutionResultBase {
  success: true
  result: Record<string, unknown>
  errorCode?: never
  error?: never
}

export type MockExecutionResult = ExecutionSuccessResult

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
        processCode: request.code,
        inputParams: request.params ?? {},
      },
      traceId,
      invocationId,
      modelType: request.modelType,
      sourceDigest: createHash('sha256').update(request.xml, 'utf8').digest('hex'),
      durationMs: Math.max(0, Math.round(performance.now() - startedAt)),
      routing: { namespace: 'default' },
    }
  }
}
