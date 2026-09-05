import type { InvocationPolicy } from './invocationPolicy'

export type MappingDirection = 'input' | 'output'

export interface InputMapping {
  direction: 'input'
  target: string
  dataType?: string
  source?: string
  defaultValue?: string
}

interface OutputMapping {
  direction: 'output'
  source?: string
  target: string
  dataType?: string
}

export type VariableMapping = InputMapping | OutputMapping

export interface VariableMappingUpdate {
  dataType?: string
  direction?: MappingDirection
  source?: string
  target?: string
  defaultValue?: string
}

export type MappingDefaultConflict = 'output' | 'source'

export const EFFECT_ID_SOURCE = '__cf_effect_id'

export function isEffectIdSource(source: string | undefined): boolean {
  return source === EFFECT_ID_SOURCE
}

export function mappingDefaultConflict(
  mapping: VariableMapping & { defaultValue?: string }
): MappingDefaultConflict | undefined {
  if (mapping.defaultValue === undefined) return undefined
  if (mapping.direction === 'output') return 'output'
  return mapping.source?.trim() ? 'source' : undefined
}

export function persistedEffectRequestFields(
  mappings: readonly VariableMapping[] | undefined
): ReadonlySet<string> {
  return new Set(
    (mappings ?? [])
      .filter((mapping) => mapping.direction === 'input' && !isEffectIdSource(mapping.source))
      .map((mapping) => mapping.target)
  )
}

export function applyVariableMappingUpdate(
  mapping: VariableMapping,
  updates: VariableMappingUpdate
): VariableMapping {
  const merged = { ...mapping, ...updates }
  if (merged.direction === 'input') {
    return {
      direction: 'input',
      target: merged.target ?? '',
      dataType: merged.dataType,
      source: merged.source,
      defaultValue: merged.defaultValue,
    }
  }

  return {
    direction: 'output',
    source: merged.source,
    target: merged.target ?? '',
    dataType: merged.dataType,
  }
}

export function mappingSourceUpdate(
  mapping: VariableMapping,
  source: string | undefined
): VariableMappingUpdate {
  return {
    source,
    defaultValue:
      source?.trim() || mapping.direction === 'output' ? undefined : mapping.defaultValue,
  }
}

export function mappingReferenceUpdate(
  mapping: VariableMapping,
  value: string | undefined
): VariableMappingUpdate {
  return mapping.direction === 'input' ? mappingSourceUpdate(mapping, value) : { target: value }
}

export function mappingDefaultUpdate(
  mapping: VariableMapping,
  defaultValue: string | undefined
): VariableMappingUpdate {
  return {
    defaultValue,
    source:
      defaultValue === undefined && mapping.direction === 'input' ? mapping.source : undefined,
  }
}

export function mappingDirectionUpdate(
  mapping: VariableMapping,
  direction: MappingDirection,
  processVariableNames: readonly string[],
  processCall: boolean
): VariableMappingUpdate {
  if (direction === 'input') {
    return {
      direction,
      source: mapping.target,
      target: mapping.direction === 'input' ? mapping.target : mapping.source,
      dataType: processCall ? undefined : (mapping.dataType ?? 'java.lang.String'),
    }
  }

  const candidate = mapping.source
  return {
    direction,
    source: processCall ? mapping.target : undefined,
    target: candidate && processVariableNames.includes(candidate) ? candidate : '',
    dataType: processCall ? undefined : mapping.dataType,
    defaultValue: undefined,
  }
}

export type ActionType = 'java' | 'spring-bean' | 'script'
export type ActionExecution = 'replayable' | 'effect'

export interface ActionInvocationDefinition {
  actionType: ActionType
  className?: string
  method?: string
  bean?: string
  language?: string
  source?: string
}

export interface ReconcileInputMapping {
  source: string
  target: string
  dataType: string
}

export interface ReconcileActionDefinition extends ActionInvocationDefinition {
  inputs?: ReconcileInputMapping[]
}

export type EffectRecovery = 'manual' | 'retry' | 'reconcile'

export interface EffectPolicy {
  recovery?: EffectRecovery
  recoveryPlanVariable?: string
  maxAttempts?: number
  maxReconcileAttempts?: number
  recoveryDelay?: string
  maxRecoveryDuration?: string
  reconcileAction?: ReconcileActionDefinition
}

export interface ActionDefinition extends ActionInvocationDefinition {
  execution?: ActionExecution
  mappings?: VariableMapping[]
  invocationPolicy?: InvocationPolicy
  effectPolicy?: EffectPolicy
}
