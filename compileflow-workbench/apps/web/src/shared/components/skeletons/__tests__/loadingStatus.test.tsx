import { render, screen } from '@testing-library/react'
import { createInstance } from 'i18next'
import { I18nextProvider } from 'react-i18next'
import { describe, expect, it } from 'vitest'

import ExampleCardSkeleton from '../ExampleCardSkeleton'
import ExampleDetailSkeleton from '../ExampleDetailSkeleton'

import en from '@/shared/i18n/en/common'
import zh from '@/shared/i18n/zh/common'

describe.each([
  ['en', en],
  ['zh', zh],
] as const)('skeleton loading status (%s)', (language, catalog) => {
  it.each(['cards', 'detail'] as const)('exposes one localized status for %s', async (kind) => {
    const i18n = createInstance()
    await i18n.init({ lng: language, resources: { [language]: { translation: catalog } } })
    const { container, unmount } = render(
      <I18nextProvider i18n={i18n}>
        {kind === 'cards' ? <ExampleCardSkeleton count={6} /> : <ExampleDetailSkeleton />}
      </I18nextProvider>
    )
    expect(screen.getAllByRole('status')).toHaveLength(1)
    expect(screen.getByRole('status', { name: catalog['common.loading'] })).toBeVisible()
    expect(container.children).toHaveLength(kind === 'cards' ? 6 : 1)
    expect(container.textContent).toBe('')
    unmount()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('keeps the requested number of direct card children', () => {
    const { container, rerender } = render(<ExampleCardSkeleton count={2} />)
    expect(container.children).toHaveLength(2)
    expect(screen.getAllByRole('status')).toHaveLength(1)
    rerender(<ExampleCardSkeleton count={0} />)
    expect(container.children).toHaveLength(0)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })
})
