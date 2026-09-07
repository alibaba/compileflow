import { describe, expect, it } from 'vitest'

import enAuthoring from '../en/authoring'
import enCommon from '../en/common'
import enLearn from '../en/learn'
import enOperate from '../en/operate'
import zhAuthoring from '../zh/authoring'
import zhCommon from '../zh/common'
import zhLearn from '../zh/learn'
import zhOperate from '../zh/operate'

function interpolationVariables(value: string): string[] {
  return Array.from(value.matchAll(/{{\s*([^},\s]+)[^}]*}}/g), (match) => match[1]).sort()
}

function htmlTags(value: string): string[] {
  return Array.from(value.matchAll(/<\/?([A-Za-z][A-Za-z0-9]*)\b[^>]*>/g), (match) =>
    match[0].startsWith('</') ? `/${match[1]}` : match[1]
  ).sort()
}

type TranslationCatalog = Readonly<Record<string, string>>

const catalogs: ReadonlyArray<
  readonly [name: string, zh: TranslationCatalog, en: TranslationCatalog]
> = [
  ['common', zhCommon, enCommon],
  ['learn', zhLearn, enLearn],
  ['operate', zhOperate, enOperate],
  ['authoring', zhAuthoring, enAuthoring],
]

describe.each(catalogs)('%s translation catalog', (_name, zh, en) => {
  it('has identical Chinese and English keys', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(zh).sort())
  })

  it('keeps interpolation variables and inline HTML aligned', () => {
    for (const key of Object.keys(en)) {
      expect(interpolationVariables(zh[key]), `${key} interpolation variables`).toEqual(
        interpolationVariables(en[key])
      )
      expect(htmlTags(zh[key]), `${key} inline HTML`).toEqual(htmlTags(en[key]))
    }
  })
})
