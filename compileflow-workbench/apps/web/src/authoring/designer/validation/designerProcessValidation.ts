import type { ProcessVariable, UnifiedProcessDefinition } from '../types/flowDefinition'
import { isJavaIdentifier, isReservedJavaIdentifier } from '../types/javaIdentifiers'

import { BpmnValidator } from './BpmnValidator'
import {
  type TopologyValidationResult,
  validateProcessTopology,
  type ValidationIssue,
  ValidationLevel,
} from './ProcessTopologyValidator'
import { TbbpmValidator } from './TbbpmValidator'

function severityToLevel(severity: 'error' | 'warning' | 'info'): ValidationLevel {
  if (severity === 'error') return ValidationLevel.ERROR
  if (severity === 'warning') return ValidationLevel.WARNING
  return ValidationLevel.INFO
}

function mapNodeValidationErrors(
  errors: Array<{
    elementId: string
    elementType?: 'connection'
    code: string
    params?: Record<string, string | number>
    severity: 'error' | 'warning' | 'info'
  }>
): ValidationIssue[] {
  return errors.map((error) => {
    const isConnection = error.elementType === 'connection'
    const isProcess = error.elementId === 'process'
    return {
      level: severityToLevel(error.severity),
      type: 'property',
      code: error.code,
      params: error.params,
      nodeIds: !isConnection && !isProcess ? [error.elementId] : [],
      connectionIds: isConnection ? [error.elementId] : undefined,
    }
  })
}

function summarizeIssues(issues: ValidationIssue[]): TopologyValidationResult {
  const errorCount = issues.filter((i) => i.level === ValidationLevel.ERROR).length
  const warningCount = issues.filter((i) => i.level === ValidationLevel.WARNING).length
  const infoCount = issues.filter((i) => i.level === ValidationLevel.INFO).length

  return {
    valid: errorCount === 0,
    issues,
    errorCount,
    warningCount,
    infoCount,
  }
}

function validateProcessVariables(
  variables: readonly ProcessVariable[] | undefined
): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const names = new Set<string>()

  for (const variable of variables ?? []) {
    const name = typeof variable.name === 'string' ? variable.name.trim() : ''
    const type = typeof variable.type === 'string' ? variable.type.trim() : ''

    if (!name) {
      issues.push({
        level: ValidationLevel.ERROR,
        type: 'property',
        code: 'designer.validation.property.process.variable.missingName',
        nodeIds: [],
      })
    } else {
      if (!isJavaIdentifier(name)) {
        issues.push({
          level: ValidationLevel.ERROR,
          type: 'property',
          code: 'designer.validation.property.process.variable.invalidName',
          params: { name },
          nodeIds: [],
        })
      } else if (isReservedJavaIdentifier(name)) {
        issues.push({
          level: ValidationLevel.ERROR,
          type: 'property',
          code: 'designer.validation.property.process.variable.reservedName',
          params: { name },
          nodeIds: [],
        })
      }

      if (names.has(name)) {
        issues.push({
          level: ValidationLevel.ERROR,
          type: 'property',
          code: 'designer.validation.property.process.variable.duplicateName',
          params: { name },
          nodeIds: [],
        })
      }
      names.add(name)
    }

    if (!type) {
      issues.push({
        level: ValidationLevel.ERROR,
        type: 'property',
        code: 'designer.validation.property.process.variable.missingType',
        params: { name },
        nodeIds: [],
      })
    }

    if (
      variable.inOutType !== 'param' &&
      variable.inOutType !== 'return' &&
      variable.inOutType !== 'inner'
    ) {
      issues.push({
        level: ValidationLevel.ERROR,
        type: 'property',
        code: 'designer.validation.property.process.variable.invalidDirection',
        params: { name },
        nodeIds: [],
      })
    }
  }

  return issues
}

export function validateDesignerProcess(
  flowDef: UnifiedProcessDefinition
): TopologyValidationResult {
  const topology = validateProcessTopology(flowDef)

  const nodeErrors =
    flowDef.type === 'BPMN'
      ? new BpmnValidator(
          flowDef.nodes,
          flowDef.connections,
          flowDef.variables,
          flowDef.messages
        ).validateNodeRules()
      : new TbbpmValidator(
          flowDef.nodes,
          flowDef.connections,
          flowDef.variables
        ).validateNodeRules()

  const propertyIssues = mapNodeValidationErrors(nodeErrors)
  return summarizeIssues([
    ...topology.issues,
    ...validateProcessVariables(flowDef.variables),
    ...propertyIssues,
  ])
}
