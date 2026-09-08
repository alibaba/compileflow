import '@testing-library/jest-dom'
import 'fake-indexeddb/auto'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeAll, vi } from 'vitest'

import i18n, { i18nReady } from '../shared/i18n'

function createMemoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key: string) => values.get(key) ?? null,
    key: (index: number) => Array.from(values.keys())[index] ?? null,
    removeItem: (key: string) => values.delete(key),
    setItem: (key: string, value: string) => values.set(key, String(value)),
  }
}

const testLocalStorage = createMemoryStorage()

Object.defineProperty(window, 'localStorage', {
  configurable: true,
  value: testLocalStorage,
})

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: testLocalStorage,
})

beforeAll(async () => {
  await i18nReady
  await i18n.changeLanguage('zh')
})

// Cleanup after each test case
afterEach(async () => {
  cleanup()
  await i18n.changeLanguage('zh')
})

// Mock window.matchMedia
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
})

// JSDOM does not implement pseudo-element style lookup. Ant Design only needs
// the element styles when measuring overlays and scrollbars.
const getComputedStyle = window.getComputedStyle.bind(window)
Object.defineProperty(window, 'getComputedStyle', {
  configurable: true,
  value: (element: Element) => getComputedStyle(element),
})

// Mock ResizeObserver
global.ResizeObserver = class ResizeObserver {
  constructor(_callback: ResizeObserverCallback) {}

  observe() {}

  unobserve() {}

  disconnect() {}
}

// Mock @antv/x6 — the canvas library declares ESM in its package.json but
// ships CommonJS (uses `exports`), which breaks under vitest's jsdom ESM
// loader.  Components that render X6 graphs are unit-tested via their
// non-canvas props/logic; the real graph rendering is covered by e2e tests.
vi.mock('@antv/x6', () => {
  const noop = () => {}
  const graphStub = {
    addNode: noop,
    addEdge: noop,
    removeNode: noop,
    removeEdge: noop,
    toJSON: () => ({ cells: [] }),
    fromJSON: noop,
    dispose: noop,
    bind: noop,
    on: noop,
    off: noop,
    trigger: noop,
    centerContent: noop,
    zoomToFit: noop,
    getNodes: () => [],
    getEdges: () => [],
    select: noop,
    unselect: noop,
    cleanSelection: noop,
  }
  return {
    Graph: class {
      constructor() {
        return graphStub
      }
    },
    Shape: { Rect: {}, Circle: {}, Polygon: {}, Path: {} },
    Addon: {
      Stencil: class {},
    },
  }
})

vi.mock('@antv/x6-react-shape', () => ({
  register: () => {},
}))
