import { parseBpmnXml } from '@/authoring/designer/serialization/bpmnXmlCodec'
import { parseTbbpmXml } from '@/authoring/designer/serialization/tbbpmXmlCodec'
import type { ParseWarning } from '@/authoring/designer/serialization/xmlTypes'
import type { UnifiedProcessDefinition } from '@/authoring/designer/types/flowDefinition'
import type { ProcessDefinition, ProcessUpdateRequest } from '@/shared/contracts'

const OPERATE_FLOW_ID_PREFIX = 'operate:'

export function buildOperateProcessId(processCode: string): string {
  return `${OPERATE_FLOW_ID_PREFIX}${processCode}`
}

export function isOperateBoundProcessId(processId: string | undefined): boolean {
  return Boolean(processId?.startsWith(OPERATE_FLOW_ID_PREFIX))
}

interface ParsedOperateXml {
  data: UnifiedProcessDefinition
  warnings?: ParseWarning[]
}

function parseOperateXml(xml: string, type: UnifiedProcessDefinition['type']): ParsedOperateXml {
  const parseResult = type === 'BPMN' ? parseBpmnXml(xml) : parseTbbpmXml(xml)
  if (!parseResult.success || !parseResult.data) {
    throw new Error(parseResult.error?.message || 'Failed to parse flow XML')
  }
  return {
    data: parseResult.data,
    warnings: parseResult.warnings,
  }
}

export function parseProcessContractTimestamp(
  value: string,
  field: 'createdAt' | 'updatedAt'
): number {
  const parsed = Date.parse(value)
  if (Number.isNaN(parsed)) {
    throw new Error(`Process ${field} is not a valid timestamp`)
  }
  return parsed
}

export function mapOperateDefinitionToUnified(def: ProcessDefinition): {
  flow: UnifiedProcessDefinition
  warnings: ParseWarning[]
} {
  const xml = def.xml?.trim()
  if (!xml) {
    throw new Error('Process XML is empty')
  }

  const parseResult = parseOperateXml(xml, def.type)
  const parsedUpdatedAt = parseProcessContractTimestamp(def.updatedAt, 'updatedAt')
  const parsedCreatedAt = parseProcessContractTimestamp(def.createdAt, 'createdAt')

  return {
    flow: {
      ...parseResult.data,
      id: buildOperateProcessId(def.code),
      code: def.code,
      name: def.name,
      description: def.description,
      tags: [...def.tags],
      createdAt: parsedCreatedAt,
      updatedAt: parsedUpdatedAt,
    },
    warnings: parseResult.warnings ?? [],
  }
}

export function mapDesignerToOperateUpdate(
  flow: UnifiedProcessDefinition,
  xml: string,
  expectedRevision: number
): ProcessUpdateRequest {
  return {
    xml,
    name: flow.name,
    description: flow.description,
    tags: flow.tags ?? [],
    expectedRevision,
  }
}
