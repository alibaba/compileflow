import type { TFunction } from 'i18next'

export function translatePropertyValidationCode(
  code: string,
  t: TFunction,
  params?: Record<string, string | number>
): string {
  const key = `designer.validation.property.${code}`
  const translated = t(key, params ?? {})
  return translated === key ? code : translated
}
