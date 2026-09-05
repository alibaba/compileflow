import type { TFunction } from 'i18next'

import type { Example } from '@/shared/contracts'

export function localizeExample(example: Example, t: TFunction, language?: string): Example {
  if (language?.toLowerCase().startsWith('en')) return example

  const prefix = `exampleContent.${example.id}`
  const translateOrFallback = (key: string, fallback?: string) => {
    const translated = t(key)
    return translated === key ? (fallback ?? '') : translated
  }
  const translateList = (field: string, items?: string[]) =>
    (items ?? []).map((item, index) => translateOrFallback(`${prefix}.${field}.${index}`, item))

  return {
    ...example,
    description: translateOrFallback(`${prefix}.description`, example.description),
    name: translateOrFallback(`${prefix}.name`, example.name),
    overview: translateOrFallback(`${prefix}.overview`, example.overview),
    explanation: translateOrFallback(`${prefix}.explanation`, example.explanation),
    nextSteps: translateOrFallback(`${prefix}.nextSteps`, example.nextSteps),
    documentation: translateOrFallback(`${prefix}.documentation`, example.documentation),
    whatYouWillLearn: translateList('whatYouWillLearn', example.whatYouWillLearn),
    keyConcepts: translateList('keyConcepts', example.keyConcepts),
  }
}
