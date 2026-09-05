import {
  type ActionDefinition,
  type ActionInvocationDefinition,
  type ReconcileActionDefinition,
  EFFECT_ID_SOURCE,
  isEffectIdSource,
  persistedEffectRequestFields,
} from '../types/action'
import { validateEffectPolicy } from '../types/effectPolicy'
import {
  isJavaClassName,
  isJavaIdentifier,
  isReservedJavaIdentifier,
} from '../types/javaIdentifiers'
import { isSymbolicIdentifier } from '../types/symbolicIdentifier'

export interface ActionFinding {
  code: string
  params?: Record<string, string | number>
}

export function actionFindings(action: ActionDefinition | undefined): ActionFinding[] {
  const actionType = action?.actionType
  if (!action || !actionType) {
    return [{ code: 'action.missingType' }]
  }

  return [
    ...invocationFindings(action),
    ...effectInputFindings(action),
    ...effectPolicyFindings(action),
  ]
}

function effectInputFindings(action: ActionDefinition): ActionFinding[] {
  const findings: ActionFinding[] = []
  for (const mapping of action.mappings ?? []) {
    if (mapping.direction !== 'input' || !mapping.source?.startsWith('__cf_effect_')) continue
    if (mapping.source !== EFFECT_ID_SOURCE) {
      findings.push(
        invalidEffectPolicy(`Unsupported Effect metadata input source: ${mapping.source}`)
      )
    } else if (action.execution !== 'effect') {
      findings.push(invalidEffectPolicy('Effect ID input requires execution="effect"'))
    }
  }
  return findings
}

export function reconcileActionFindings(
  action: ReconcileActionDefinition | undefined,
  requestFields?: ReadonlySet<string>
): ActionFinding[] {
  if (!action?.actionType) return [{ code: 'action.missingType' }]
  return [...invocationFindings(action), ...reconcileInputFindings(action, requestFields)]
}

function invocationFindings(action: ActionInvocationDefinition): ActionFinding[] {
  const actionType = action.actionType
  return (() => {
    switch (actionType) {
      case 'java':
        return [
          ...javaClassFindings(action.className),
          ...optionalJavaMethodFindings(action.method),
        ]
      case 'spring-bean':
        return [
          ...exactIdentityFindings(action.bean, 'action.missingBean', 'action.invalidBean'),
          ...javaClassFindings(action.className),
          ...optionalJavaMethodFindings(action.method),
        ]
      case 'script':
        return [
          ...symbolicNameFindings(action.language),
          ...requiredTextFinding(action.source, 'action.missingScriptSource'),
        ]
      default:
        return [{ code: 'action.unsupportedType', params: { actionType } }]
    }
  })()
}

function effectPolicyFindings(action: ActionDefinition): ActionFinding[] {
  if (!action.effectPolicy) return []
  try {
    validateEffectPolicy(action.effectPolicy, action.execution)
  } catch (error) {
    return [
      {
        code: 'effectPolicy.invalid',
        params: { message: error instanceof Error ? error.message : String(error) },
      },
    ]
  }
  if (!action.effectPolicy.reconcileAction) return []
  const requestFields = persistedEffectRequestFields(action.mappings)
  return reconcileActionFindings(action.effectPolicy.reconcileAction, requestFields)
}

function reconcileInputFindings(
  action: ReconcileActionDefinition,
  requestFields: ReadonlySet<string> | undefined
): ActionFinding[] {
  const findings: ActionFinding[] = []
  const targets = new Set<string>()
  for (const input of action.inputs ?? []) {
    addFinding(findings, reconcileSourceFinding(input.source, requestFields))
    addFinding(findings, reconcileTargetFinding(input.target, targets))
    targets.add(input.target)
    if (!input.dataType.trim() || input.dataType !== input.dataType.trim()) {
      findings.push(invalidEffectPolicy(`Invalid reconcile input dataType: ${input.dataType}`))
    }
  }
  return findings
}

function reconcileSourceFinding(
  source: string,
  requestFields: ReadonlySet<string> | undefined
): ActionFinding | undefined {
  if (source !== source.trim() || !isJavaIdentifier(source)) {
    return invalidEffectPolicy(`Invalid reconcile input source: ${source}`)
  }
  if (source.startsWith('__cf_effect_') && !isEffectIdSource(source)) {
    return invalidEffectPolicy(`Unsupported Effect metadata input source: ${source}`)
  }
  if (requestFields && !requestFields.has(source) && !isEffectIdSource(source)) {
    return invalidEffectPolicy(`Reconcile input references unknown Effect request field: ${source}`)
  }
}

function reconcileTargetFinding(
  target: string,
  targets: ReadonlySet<string>
): ActionFinding | undefined {
  if (target !== target.trim() || !isJavaIdentifier(target) || isReservedJavaIdentifier(target)) {
    return invalidEffectPolicy(`Invalid reconcile input target: ${target}`)
  }
  if (targets.has(target)) {
    return invalidEffectPolicy(`Duplicate reconcile input target: ${target}`)
  }
}

function addFinding(findings: ActionFinding[], finding: ActionFinding | undefined): void {
  if (finding) findings.push(finding)
}

function invalidEffectPolicy(message: string): ActionFinding {
  return { code: 'effectPolicy.invalid', params: { message } }
}

function javaClassFindings(value: string | undefined): ActionFinding[] {
  if (!value?.trim()) return [{ code: 'action.missingClass' }]
  return value === value.trim() && isJavaClassName(value) ? [] : [{ code: 'action.invalidClass' }]
}

function optionalJavaMethodFindings(value: string | undefined): ActionFinding[] {
  if (!value?.trim()) return []
  return value === value.trim() && isJavaIdentifier(value) ? [] : [{ code: 'action.invalidMethod' }]
}

function symbolicNameFindings(value: string | undefined): ActionFinding[] {
  if (!value?.trim()) return [{ code: 'action.missingScriptLanguage' }]
  return isSymbolicIdentifier(value) ? [] : [{ code: 'action.invalidScriptLanguage' }]
}

function exactIdentityFindings(
  value: string | undefined,
  missingCode: string,
  invalidCode: string
): ActionFinding[] {
  if (!value?.trim()) return [{ code: missingCode }]
  return value === value.trim() && !hasControlCharacter(value) ? [] : [{ code: invalidCode }]
}

function hasControlCharacter(value: string): boolean {
  return Array.from(value).some((character) => {
    const codePoint = character.codePointAt(0)!
    return codePoint <= 0x1f || (codePoint >= 0x7f && codePoint <= 0x9f)
  })
}

function requiredTextFinding(
  value: string | undefined,
  code: string,
  params?: Record<string, string | number>
): ActionFinding[] {
  return value?.trim() ? [] : [{ code, params }]
}
