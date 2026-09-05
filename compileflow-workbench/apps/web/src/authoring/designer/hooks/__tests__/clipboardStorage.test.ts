import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  clearDesignerClipboard,
  readDesignerClipboard,
  writeDesignerClipboard,
} from '../clipboardStorage'

const clipboardData = {
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
  source: 'order-flow',
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
    vi.spyOn(sessionStorage, 'setItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(sessionStorage, 'getItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })
    vi.spyOn(sessionStorage, 'removeItem').mockImplementation(() => {
      throw new DOMException('Storage denied', 'SecurityError')
    })

    writeDesignerClipboard(clipboardData)
    expect(readDesignerClipboard()).toEqual(clipboardData)

    clearDesignerClipboard()
    expect(readDesignerClipboard()).toBeNull()
  })
})
