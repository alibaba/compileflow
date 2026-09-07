import { render, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { LanguageProvider } from '../LanguageContext'

import i18n from '@/shared/i18n'

describe('LanguageProvider', () => {
  afterEach(async () => {
    vi.restoreAllMocks()
    await i18n.changeLanguage('zh')
  })

  it('keeps the app usable when browser storage access is denied', () => {
    vi.spyOn(localStorage, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage is blocked', 'SecurityError')
    })
    expect(() =>
      render(
        <LanguageProvider>
          <span>content</span>
        </LanguageProvider>
      )
    ).not.toThrow()
  })

  it('keeps the document language synchronized with the selected locale', async () => {
    const { rerender } = render(
      <LanguageProvider>
        <span>content</span>
      </LanguageProvider>
    )

    await waitFor(() => expect(document.documentElement.lang).toBe('zh-CN'))

    await i18n.changeLanguage('en')
    rerender(
      <LanguageProvider>
        <span>content</span>
      </LanguageProvider>
    )

    await waitFor(() => expect(document.documentElement.lang).toBe('en'))
  })

  it('synchronizes language changes from another tab', async () => {
    render(
      <LanguageProvider>
        <span>content</span>
      </LanguageProvider>
    )

    window.dispatchEvent(
      new StorageEvent('storage', { key: 'compileflow:language', newValue: 'en' })
    )

    await waitFor(() => expect(document.documentElement.lang).toBe('en'))
  })
})
