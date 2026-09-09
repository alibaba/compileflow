import { act, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'

import { useCompactContainer } from '../useCompactContainer'

vi.mock('../useMediaQuery', () => ({ useMediaQuery: () => false }))

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

it('tracks available container width, ignores hidden layouts and releases its observer', () => {
  const width = vi.spyOn(HTMLElement.prototype, 'clientWidth', 'get').mockReturnValue(1024)
  const disconnect = vi.fn()
  let resize = () => {}
  vi.stubGlobal(
    'ResizeObserver',
    class {
      constructor(callback: () => void) {
        resize = callback
      }
      observe = vi.fn()
      disconnect = disconnect
    }
  )
  function Harness() {
    const { containerRef, compact } = useCompactContainer()
    return <div ref={containerRef}>{compact ? 'compact' : 'wide'}</div>
  }
  const view = render(<Harness />)
  expect(screen.getByText('wide')).toBeInTheDocument()
  width.mockReturnValue(640)
  act(resize)
  expect(screen.getByText('compact')).toBeInTheDocument()
  width.mockReturnValue(0)
  act(resize)
  expect(screen.getByText('compact')).toBeInTheDocument()
  width.mockReturnValue(1024)
  act(resize)
  expect(screen.getByText('wide')).toBeInTheDocument()
  view.unmount()
  expect(disconnect).toHaveBeenCalledOnce()
})
