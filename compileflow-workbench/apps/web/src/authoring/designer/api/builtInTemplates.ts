import { processStorage } from './processStorage.indexeddb'
import type { ProcessTemplate } from './processStorageTypes'

import { DEFAULT_BPMN_WITH_EVENTS_XML, DEFAULT_BPMN_XML } from '@/shared/processes/bpmnTemplates'
import { DEFAULT_TBBPM_WITH_NODES_XML, DEFAULT_TBBPM_XML } from '@/shared/processes/tbbpmTemplates'

export const BUILT_IN_TEMPLATES: readonly ProcessTemplate[] = [
  {
    id: 'tpl-2',
    name: 'TBBPM Greeting',
    type: 'TBBPM',
    description: 'Self-contained Java inline action with input and output mapping',
    content: DEFAULT_TBBPM_WITH_NODES_XML,
    category: 'Getting Started',
    tags: ['TBBPM', 'Java Inline'],
  },
  {
    id: 'tpl-4',
    name: 'TBBPM Starter',
    type: 'TBBPM',
    description: 'Minimal executable TBBPM flow',
    content: DEFAULT_TBBPM_XML,
    category: 'Getting Started',
    tags: ['TBBPM'],
  },
  {
    id: 'tpl-1',
    name: 'BPMN Starter',
    type: 'BPMN',
    description: 'Minimal executable start-to-end flow',
    content: DEFAULT_BPMN_WITH_EVENTS_XML,
    category: 'Getting Started',
    tags: ['BPMN'],
  },
  {
    id: 'tpl-3',
    name: 'Blank BPMN',
    type: 'BPMN',
    description: 'Empty BPMN process for visual modeling',
    content: DEFAULT_BPMN_XML,
    category: 'Blank',
    tags: ['BPMN'],
  },
]

/**
 * Idempotently make built-in templates available from every entry point,
 * including a fresh deep link that has never visited the Build workspace.
 */
export async function ensureBuiltInTemplates(): Promise<ProcessTemplate[]> {
  const storedTemplates = await processStorage.listTemplates()
  await Promise.all(BUILT_IN_TEMPLATES.map((template) => processStorage.saveTemplate(template)))
  const customTemplates = storedTemplates.filter(
    (template) => !BUILT_IN_TEMPLATES.some((builtIn) => builtIn.id === template.id)
  )
  return [...BUILT_IN_TEMPLATES, ...customTemplates]
}
