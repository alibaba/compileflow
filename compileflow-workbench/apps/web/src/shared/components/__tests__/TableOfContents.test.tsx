import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { StrictMode, useRef } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import TableOfContents from '../TableOfContents'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function Contents({
  title,
  headingId,
  extra = false,
}: {
  title: string
  headingId?: string
  extra?: boolean
}) {
  const contentRef = useRef<HTMLDivElement>(null)
  return (
    <>
      <div ref={contentRef}>
        <div role="tabpanel" aria-hidden="false">
          <h2 id={headingId}>{title}</h2>
          {extra && <h3>Extra</h3>}
        </div>
      </div>
      <TableOfContents contentRef={contentRef} />
    </>
  )
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame'] })
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
      unobserve() {}
    }
  )
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('TableOfContents heading identity', () => {
  it('keeps generated IDs unique across instances, StrictMode effects and inserted headings', async () => {
    const { rerender } = render(
      <StrictMode>
        <Contents title="One" />
        <Contents title="Two" />
      </StrictMode>
    )
    const one = screen.getByRole('heading', { name: 'One' })
    const two = screen.getByRole('heading', { name: 'Two' })
    await waitFor(() => {
      expect(one.id).not.toBe('')
      expect(two.id).not.toBe('')
    })
    const originalId = one.id
    expect(one.id).not.toBe(two.id)

    rerender(
      <StrictMode>
        <Contents title="One" extra />
        <Contents title="Two" />
      </StrictMode>
    )

    const extra = screen.getByRole('heading', { name: 'Extra' })
    await waitFor(() => expect(extra.id).not.toBe(''))
    expect(one.id).toBe(originalId)
    expect(new Set([one.id, two.id, extra.id]).size).toBe(3)
  })

  it('preserves an existing heading ID and resolves its element without a CSS selector', async () => {
    render(<Contents title="Existing" headingId="section:existing" />)
    const heading = screen.getByRole('heading', { name: 'Existing' })
    const scrollIntoView = vi.fn()
    Object.defineProperty(heading, 'scrollIntoView', { value: scrollIntoView })
    const toc = await screen.findByRole('navigation', { name: 'toc.title' })
    const link = within(toc).getByRole('link', { name: 'Existing' })

    fireEvent.click(link)

    expect(heading.id).toBe('section:existing')
    expect(link).toHaveAttribute('href', '#section:existing')
    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
  })
})
