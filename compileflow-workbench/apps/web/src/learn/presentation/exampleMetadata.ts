import type { TFunction } from 'i18next'

import type { Example } from '@/shared/contracts'
import { localizeExample } from '@/shared/examples/localizeExample'

export function getLevelLabel(level: number, t: TFunction): string {
  const keys = ['level.beginner', 'level.basic', 'level.intermediate', 'level.advanced']
  return t(keys[level] || 'level.beginner')
}

export function getCategoryLabel(category: string, t: TFunction): string {
  const knownCategoryKey = `category.${category}`
  const translated = t(knownCategoryKey)
  return translated === knownCategoryKey ? category : translated
}

export function getExampleDuration(duration: string | undefined, t: TFunction): string {
  if (!duration) return '—'
  const minuteMatch = /^(\d+)\s+minutes?$/i.exec(duration.trim())
  return minuteMatch ? t('detail.durationMinutes', { count: Number(minuteMatch[1]) }) : duration
}

export function getExamplePresentation(example: Example, t: TFunction) {
  const localized = localizeExample(example, t)
  return {
    description: localized.description,
    name: localized.name,
  }
}
