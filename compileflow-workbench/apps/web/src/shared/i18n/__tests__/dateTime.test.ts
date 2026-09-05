import { describe, expect, it } from 'vitest'

import i18n from '@/shared/i18n'
import { formatDate, formatDateTime } from '@/shared/i18n/dateTime'

const instant = new Date(2026, 7, 2, 8, 1, 0)

describe('localized date formatting', () => {
  it('follows the active Workbench language', async () => {
    await i18n.changeLanguage('zh')
    expect(formatDate(instant)).toContain('2026年')

    await i18n.changeLanguage('en')
    expect(formatDate(instant)).toContain('Aug')
  })

  it('renders missing or invalid timestamps as an empty value', () => {
    expect(formatDateTime(undefined)).toBe('-')
    expect(formatDateTime('not-a-date')).toBe('-')
  })
})
