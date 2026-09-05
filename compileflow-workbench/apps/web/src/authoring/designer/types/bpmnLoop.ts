import type {
  LoopCharacteristics,
  MultiInstanceLoopCharacteristics,
  StandardLoopCharacteristics,
} from './bpmnNodeTypes'
import { isJavaClassName, requireGeneratedJavaIdentifier } from './javaIdentifiers'
import { isValidLoopIterationLimit, MAX_LOOP_ITERATIONS } from './loopLimits'

/** Keeps the Workbench loop contract aligned with generated Java identifiers. */
export function validateBpmnLoopCharacteristics(loop: LoopCharacteristics): void {
  if (loop.type === 'multiInstance') {
    validateMultiInstanceLoop(loop)
    return
  }
  validateStandardLoop(loop)
}

function validateMultiInstanceLoop(loop: MultiInstanceLoopCharacteristics): void {
  requireGeneratedJavaIdentifier(loop.collection, 'cf:collection')
  requireGeneratedJavaIdentifier(loop.item, 'cf:item')
  if (loop.index !== undefined) requireGeneratedJavaIdentifier(loop.index, 'cf:index')
  if (loop.itemType !== undefined && !isJavaClassName(loop.itemType)) {
    throw new Error(`cf:itemType must be a valid Java class name: ${loop.itemType}`)
  }
  if (loop.item === loop.index) {
    throw new Error('cf:item and cf:index must use different names')
  }
  if (loop.target !== undefined) {
    requireGeneratedJavaIdentifier(loop.target, 'cf:target')
  }
  if (loop.source !== undefined) {
    requireGeneratedJavaIdentifier(loop.source, 'cf:source')
  }
  if ((loop.target === undefined) !== (loop.source === undefined)) {
    throw new Error('cf:target and cf:source must be declared together')
  }
  if (loop.target !== undefined && loop.target === loop.source) {
    throw new Error('cf:target and cf:source must use different names')
  }
}

function validateStandardLoop(loop: StandardLoopCharacteristics): void {
  if (loop.loopMaximum !== undefined && !isValidLoopIterationLimit(loop.loopMaximum)) {
    throw new Error(`BPMN standard loopMaximum must be between 1 and ${MAX_LOOP_ITERATIONS}`)
  }
  if (loop.loopCondition !== undefined && loop.loopCondition.trim().length === 0) {
    throw new Error('BPMN standard loopCondition must not be blank when declared')
  }
  if (loop.loopMaximum === undefined && loop.loopCondition === undefined) {
    throw new Error('BPMN standard loop requires loopCondition or loopMaximum')
  }
}
