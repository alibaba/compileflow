import { type VariableMapping, mappingDefaultConflict } from '../types/action'

export interface MappingFinding {
  code: string
  params?: Record<string, string | number>
}

export interface MappingValidationOptions {
  dialect: 'bpmn' | 'tbbpm'
  mappings?: VariableMapping[]
  processCall: boolean
  singleOutput: boolean
  outputDataTypeRequired: boolean
  processVariableNames: ReadonlySet<string>
}

export function mappingFindings({
  dialect,
  mappings,
  processCall,
  singleOutput,
  outputDataTypeRequired,
  processVariableNames,
}: MappingValidationOptions): MappingFinding[] {
  if (!mappings) return []
  const findings: MappingFinding[] = []
  const mappingKeys = new Set<string>()
  const outputTargets = new Set<string>()
  let outputCount = 0

  for (const mapping of mappings) {
    const binding = mappingBinding(mapping, processCall)
    if (!binding?.trim() || !mapping.target.trim()) {
      findings.push({ code: `${dialect}.mapping.incomplete` })
    }
    if (hasInvalidDataType(mapping, processCall, outputDataTypeRequired)) {
      findings.push({ code: `${dialect}.mapping.incomplete` })
    }
    if (binding) {
      const key = `${mapping.direction}\u0000${binding}`
      if (mappingKeys.has(key)) {
        findings.push({ code: `${dialect}.mapping.duplicate`, params: { name: binding } })
      }
      mappingKeys.add(key)
    }
    const defaultConflict = mappingDefaultConflict(mapping)
    if (defaultConflict) {
      findings.push({
        code: 'mapping.inapplicableDefault',
        params: { reason: defaultConflict },
      })
    }
    if (mapping.direction === 'output') {
      outputCount += 1
      validateOutputTarget(mapping.target, dialect, processVariableNames, outputTargets, findings)
    }
  }
  if (singleOutput && outputCount > 1) {
    findings.push({ code: `${dialect}.mapping.multipleOutputs` })
  }
  return findings
}

function mappingBinding(mapping: VariableMapping, processCall: boolean): string | undefined {
  if (mapping.direction === 'input') return mapping.target
  return processCall ? mapping.source : mapping.target
}

function hasInvalidDataType(
  mapping: VariableMapping,
  processCall: boolean,
  outputDataTypeRequired: boolean
): boolean {
  if (mapping.direction === 'input') {
    return processCall ? mapping.dataType !== undefined : !mapping.dataType?.trim()
  }
  if (processCall) return mapping.dataType !== undefined
  return outputDataTypeRequired && !mapping.dataType?.trim()
}

function validateOutputTarget(
  rawTarget: string,
  dialect: MappingValidationOptions['dialect'],
  processVariableNames: ReadonlySet<string>,
  outputTargets: Set<string>,
  findings: MappingFinding[]
): void {
  const target = rawTarget.trim()
  if (!target) {
    findings.push({ code: `${dialect}.mapping.missingOutputTarget` })
    return
  }
  if (!processVariableNames.has(target)) {
    findings.push({
      code: `${dialect}.mapping.unknownOutputTarget`,
      params: { target },
    })
  }
  if (outputTargets.has(target)) {
    findings.push({
      code: `${dialect}.mapping.duplicateOutputTarget`,
      params: { target },
    })
  }
  outputTargets.add(target)
}
