import { act, renderHook } from '@testing-library/react'
import { expect, it, vi } from 'vitest'

import { useMediaQuery } from '@/shared/hooks/useMediaQuery'

it('updates only when the media query boundary changes', () => {
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  let matches = false
  const matchMedia = vi.fn((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) =>
      listeners.add(listener),
    removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) =>
      listeners.delete(listener),
    dispatchEvent: () => false,
    addListener: vi.fn(),
    removeListener: vi.fn(),
  }))
  vi.stubGlobal('matchMedia', matchMedia)

  const { result, unmount } = renderHook(() => useMediaQuery('(max-width: 575px)'))
  expect(result.current).toBe(false)

  act(() => {
    matches = true
    for (const listener of listeners) listener({ matches } as MediaQueryListEvent)
  })
  expect(result.current).toBe(true)

  unmount()
  expect(listeners).toHaveLength(0)
  vi.unstubAllGlobals()
})
