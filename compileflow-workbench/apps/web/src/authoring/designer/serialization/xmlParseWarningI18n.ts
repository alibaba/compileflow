import type { TFunction } from 'i18next'

import type { ParseWarning } from './xmlTypes'

export function translateParseWarning(warning: ParseWarning, t: TFunction): string {
  const key = `designer.xmlParse.warning.${warning.code}`
  const translated = t(key, {
    message: warning.message,
    location: warning.location ?? '',
    defaultValue: warning.message,
  })
  return translated === key ? warning.message : translated
}
