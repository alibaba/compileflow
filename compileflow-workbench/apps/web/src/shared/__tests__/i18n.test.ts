import { readdirSync, readFileSync } from 'node:fs'
import { extname, resolve } from 'node:path'

import { describe, expect, it } from 'vitest'

import i18n from '../i18n'

const SOURCE_ROOT = resolve(process.cwd(), 'src')

function sourceFiles(directory: string): string[] {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === '__tests__' || entry.name === 'i18n' || entry.name === 'test') return []
      return sourceFiles(path)
    }
    return ['.ts', '.tsx'].includes(extname(entry.name)) ? [path] : []
  })
}

function referencedStaticKeys(): string[] {
  const keys = new Set<string>()
  const staticTranslationCall = /\bt\(\s*['"]([A-Za-z][\w.-]+)['"]/g
  for (const file of sourceFiles(SOURCE_ROOT)) {
    const source = readFileSync(file, 'utf8')
    for (const match of source.matchAll(staticTranslationCall)) keys.add(match[1])
  }
  return [...keys].sort()
}

describe('translation resources', () => {
  it('keeps English and Chinese translation keys in parity', () => {
    const english = i18n.getDataByLanguage('en')?.translation
    const chinese = i18n.getDataByLanguage('zh')?.translation

    expect(english).toBeDefined()
    expect(chinese).toBeDefined()
    expect(Object.keys(english ?? {}).sort()).toEqual(Object.keys(chinese ?? {}).sort())
  })

  it('keeps English resources free of untranslated Chinese text', () => {
    const english = i18n.getDataByLanguage('en')?.translation

    expect(english).toBeDefined()
    for (const [key, value] of Object.entries(english ?? {})) {
      expect(String(value), key).not.toMatch(/\p{Script=Han}/u)
    }
  })

  it('defines every statically referenced translation key', () => {
    const english = i18n.getDataByLanguage('en')?.translation ?? {}
    const missingKeys = referencedStaticKeys().filter((key) => !(key in english))
    expect(missingKeys).toEqual([])
  })
})
