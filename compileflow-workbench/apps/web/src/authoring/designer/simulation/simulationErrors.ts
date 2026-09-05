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
    default:
      if (codeOrMessage.startsWith(SIM_ERROR_NODE_NOT_FOUND + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_NODE_NOT_FOUND.length + 1)
        return t('designer.debug.sim.errorNodeNotFound', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_NO_BRANCH_MATCHED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_NO_BRANCH_MATCHED.length + 1)
        return t('designer.debug.sim.errorNoBranchMatched', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED.length + 1)
        return t('designer.debug.sim.errorTriggerEntryUnsupported', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_TIMER_UNSUPPORTED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_TIMER_UNSUPPORTED.length + 1)
        return t('designer.debug.sim.errorTimerUnsupported', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_LOOP_UNSUPPORTED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_LOOP_UNSUPPORTED.length + 1)
        return t('designer.debug.sim.errorLoopUnsupported', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_CALLED_PROCESS_UNSUPPORTED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_CALLED_PROCESS_UNSUPPORTED.length + 1)
        return t('designer.debug.sim.errorCalledProcessUnsupported', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED.length + 1)
        return t('designer.debug.sim.errorEmbeddedProcessUnsupported', { nodeId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_EXPRESSION_EVALUATION_FAILED + ':')) {
        const elementId = codeOrMessage.slice(SIM_ERROR_EXPRESSION_EVALUATION_FAILED.length + 1)
        return t('designer.debug.sim.errorExpressionEvaluationFailed', { elementId })
      }
      if (codeOrMessage.startsWith(SIM_ERROR_DEAD_END + ':')) {
        const nodeId = codeOrMessage.slice(SIM_ERROR_DEAD_END.length + 1)
        return t('designer.debug.sim.errorDeadEnd', { nodeId })
      }
      return codeOrMessage
  }
}
