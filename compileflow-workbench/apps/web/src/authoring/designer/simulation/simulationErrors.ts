export const SIM_ERROR_NO_START = 'SIM_NO_START'
export const SIM_ERROR_STEP_NOT_PAUSED = 'SIM_STEP_NOT_PAUSED'
export const SIM_ERROR_CONTINUE_NOT_PAUSED = 'SIM_ERROR_CONTINUE_NOT_PAUSED'
export const SIM_ERROR_NODE_NOT_FOUND = 'SIM_ERROR_NODE_NOT_FOUND'
export const SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED = 'SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED'
export const SIM_ERROR_NO_BRANCH_MATCHED = 'SIM_ERROR_NO_BRANCH_MATCHED'
export const SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED = 'SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED'
export const SIM_ERROR_TIMER_UNSUPPORTED = 'SIM_ERROR_TIMER_UNSUPPORTED'
export const SIM_ERROR_LOOP_UNSUPPORTED = 'SIM_ERROR_LOOP_UNSUPPORTED'
export const SIM_ERROR_CALLED_PROCESS_UNSUPPORTED = 'SIM_ERROR_CALLED_PROCESS_UNSUPPORTED'
export const SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED = 'SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED'
export const SIM_ERROR_EXPRESSION_EVALUATION_FAILED = 'SIM_ERROR_EXPRESSION_EVALUATION_FAILED'
export const SIM_ERROR_DEAD_END = 'SIM_ERROR_DEAD_END'
export const SIM_ERROR_CYCLE = 'SIM_ERROR_CYCLE'
export const SIM_ERROR_STEP_LIMIT = 'SIM_ERROR_STEP_LIMIT'
export const SIM_ERROR_RUN_SUPERSEDED = 'SIM_ERROR_RUN_SUPERSEDED'

const NODE_ERROR_KEYS = new Map([
  [SIM_ERROR_NODE_NOT_FOUND, 'designer.debug.sim.errorNodeNotFound'],
  [SIM_ERROR_NO_BRANCH_MATCHED, 'designer.debug.sim.errorNoBranchMatched'],
  [SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED, 'designer.debug.sim.errorTriggerEntryUnsupported'],
  [SIM_ERROR_TIMER_UNSUPPORTED, 'designer.debug.sim.errorTimerUnsupported'],
  [SIM_ERROR_LOOP_UNSUPPORTED, 'designer.debug.sim.errorLoopUnsupported'],
  [SIM_ERROR_CALLED_PROCESS_UNSUPPORTED, 'designer.debug.sim.errorCalledProcessUnsupported'],
  [SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED, 'designer.debug.sim.errorEmbeddedProcessUnsupported'],
  [SIM_ERROR_DEAD_END, 'designer.debug.sim.errorDeadEnd'],
  [SIM_ERROR_CYCLE, 'designer.debug.sim.errorCycle'],
  [SIM_ERROR_STEP_LIMIT, 'designer.debug.sim.errorStepLimit'],
])

export function formatSimulationError(
  codeOrMessage: string,
  t: (key: string, opts?: Record<string, unknown>) => string
): string {
  switch (codeOrMessage) {
    case SIM_ERROR_NO_START:
      return t('designer.debug.sim.errorNoStart')
    case SIM_ERROR_STEP_NOT_PAUSED:
      return t('designer.debug.sim.errorStepNotPaused')
    case SIM_ERROR_CONTINUE_NOT_PAUSED:
      return t('designer.debug.sim.errorContinueNotPaused')
    case SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED:
      return t('designer.debug.sim.errorConcurrentGatewayUnsupported')
    case SIM_ERROR_RUN_SUPERSEDED:
      return t('designer.debug.sim.errorRunSuperseded')
    default: {
      if (codeOrMessage.startsWith(SIM_ERROR_EXPRESSION_EVALUATION_FAILED + ':')) {
        const elementId = codeOrMessage.slice(SIM_ERROR_EXPRESSION_EVALUATION_FAILED.length + 1)
        return t('designer.debug.sim.errorExpressionEvaluationFailed', { elementId })
      }
      const separator = codeOrMessage.indexOf(':')
      const nodeErrorKey = NODE_ERROR_KEYS.get(codeOrMessage.slice(0, separator))
      if (separator >= 0 && nodeErrorKey) {
        return t(nodeErrorKey, { nodeId: codeOrMessage.slice(separator + 1) })
      }
      return codeOrMessage
    }
  }
}
