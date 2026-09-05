import type { UnifiedProcessDefinition } from '../types/flowDefinition'

import { generateBpmnXml } from './bpmnXmlCodec'
import { generateTbbpmXml } from './tbbpmXmlCodec'

/** Serializes the current designer model through its format-owned writer. */
export function generateProcessXml(definition: UnifiedProcessDefinition): string {
  return definition.type === 'BPMN' ? generateBpmnXml(definition) : generateTbbpmXml(definition)
}
