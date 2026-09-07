import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearDesignerClipboard,
  readDesignerClipboard,
  writeDesignerClipboard,
} from '../clipboardStorage'

const clipboardData = {
  modelType: 'TBBPM' as const,
  connections: [],
  messages: [],
  nodes: [
    {
      id: 'task-1',
      type: 'autoTask',
      name: 'Task',
      position: { x: 10, y: 20 },
      properties: {},
    },
  ],
  timestamp: 1_000,
}

describe('designer clipboard storage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    clearDesignerClipboard()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('round-trips valid clipboard data through session storage', () => {
    writeDesignerClipboard(clipboardData)

    expect(readDesignerClipboard()).toEqual(clipboardData)
  })

  it('removes malformed persisted clipboard data', () => {
    sessionStorage.setItem('compileflow:designer-clipboard', '{"nodes":"invalid"}')

    expect(readDesignerClipboard()).toBeNull()
    expect(sessionStorage.getItem('compileflow:designer-clipboard')).toBeNull()
  })

  it('keeps clipboard operations usable when browser storage is unavailable', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })

    writeDesignerClipboard(clipboardData)
    expect(readDesignerClipboard()).toEqual(clipboardData)

    clearDesignerClipboard()
    expect(readDesignerClipboard()).toBeNull()
  })

  it('reads the latest copy when a quota failure leaves an older persisted value', () => {
    writeDesignerClipboard(clipboardData)
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage full', 'QuotaExceededError')
    })
    const newer = { ...clipboardData, timestamp: 2_000 }

    writeDesignerClipboard(newer)

    expect(readDesignerClipboard()).toEqual(newer)
  })

  it('does not resurrect a cleared clipboard when persisted removal fails', () => {
    writeDesignerClipboard(clipboardData)
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })

    clearDesignerClipboard()

    expect(readDesignerClipboard()).toBeNull()
  })
})
