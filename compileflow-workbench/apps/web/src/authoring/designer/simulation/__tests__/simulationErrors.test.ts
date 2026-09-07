import { createInstance } from 'i18next'
import { describe, expect, it } from 'vitest'

import { formatSimulationError } from '../simulationErrors'

import en from '@/shared/i18n/en/authoring'
import zh from '@/shared/i18n/zh/authoring'

describe.each([
  ['en', en],
  ['zh', zh],
] as const)('simulation error presentation (%s)', (language, catalog) => {
  it.each([
    ['SIM_ERROR_CYCLE:node:42', 'designer.debug.sim.errorCycle', { nodeId: 'node:42' }],
    ['SIM_ERROR_STEP_LIMIT:node:42', 'designer.debug.sim.errorStepLimit', { nodeId: 'node:42' }],
    ['SIM_ERROR_RUN_SUPERSEDED', 'designer.debug.sim.errorRunSuperseded', {}],
    ['SIM_NO_START', 'designer.debug.sim.errorNoStart', {}],
    ['SIM_STEP_NOT_PAUSED', 'designer.debug.sim.errorStepNotPaused', {}],
    ['SIM_ERROR_CONTINUE_NOT_PAUSED', 'designer.debug.sim.errorContinueNotPaused', {}],
    [
      'SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED',
      'designer.debug.sim.errorConcurrentGatewayUnsupported',
      {},
    ],
    [
      'SIM_ERROR_NODE_NOT_FOUND:node:42',
      'designer.debug.sim.errorNodeNotFound',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_NO_BRANCH_MATCHED:node:42',
      'designer.debug.sim.errorNoBranchMatched',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED:node:42',
      'designer.debug.sim.errorTriggerEntryUnsupported',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_TIMER_UNSUPPORTED:node:42',
      'designer.debug.sim.errorTimerUnsupported',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_LOOP_UNSUPPORTED:node:42',
      'designer.debug.sim.errorLoopUnsupported',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_CALLED_PROCESS_UNSUPPORTED:node:42',
      'designer.debug.sim.errorCalledProcessUnsupported',
      { nodeId: 'node:42' },
    ],
    [
      'SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED:node:42',
      'designer.debug.sim.errorEmbeddedProcessUnsupported',
      { nodeId: 'node:42' },
    ],
    ['SIM_ERROR_DEAD_END:node:42', 'designer.debug.sim.errorDeadEnd', { nodeId: 'node:42' }],
    [
      'SIM_ERROR_EXPRESSION_EVALUATION_FAILED:edge:42',
      'designer.debug.sim.errorExpressionEvaluationFailed',
      { elementId: 'edge:42' },
    ],
  ] as const)('localizes %s', async (code, key, parameters) => {
    const translator = createInstance()
    await translator.init({ lng: language, resources: { [language]: { translation: catalog } } })
    expect(translator.exists(key)).toBe(true)
    const message = formatSimulationError(code, translator.t.bind(translator))
    expect(message).toBe(translator.t(key, parameters))
    expect(message).not.toMatch(/SIM_ERROR_|designer\.debug|\{\{/)
    if ('nodeId' in parameters) expect(message).toContain(parameters.nodeId)
  })

  it.each([
    'Unexpected connection error',
    'SIM_ERROR_CYCLE',
    'SIM_ERROR_STEP_LIMIT',
    'SIM_ERROR_RUN_SUPERSEDED:old',
    'SIM_NO_START:extra',
    'toString:node',
  ])('preserves unknown or incomplete errors: %s', (message) => {
    expect(formatSimulationError(message, (key) => key)).toBe(message)
  })
})
