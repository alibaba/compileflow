import { renderHook } from '@testing-library/react'
import React from 'react'
import { Provider } from 'react-redux'
import { MemoryRouter } from 'react-router-dom'

import { useBreadcrumb } from '../useBreadcrumb'

import { setBreadcrumbs, store } from '@/app/store'

const wrapper = ({
  children,
  initialPath = '/',
}: {
  children: React.ReactNode
  initialPath?: string
}) => (
  <Provider store={store}>
    <MemoryRouter initialEntries={[initialPath]}>{children}</MemoryRouter>
  </Provider>
)

describe('useBreadcrumb', () => {
  beforeEach(() => {
    store.dispatch(setBreadcrumbs([]))
  })

  it('should set breadcrumbs for /learn path', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/learn' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([{ labelKey: 'nav.learn', path: '/learn' }])
  })

  it('should set breadcrumbs for /learn/examples path', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/learn/examples' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([
      { labelKey: 'nav.learn', path: '/learn' },
      { labelKey: 'nav.learn.examples', path: '/learn/examples' },
    ])
  })

  it('should show build → designer breadcrumbs for /build/designer', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/build/designer' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([
      { labelKey: 'nav.workspace', path: '/build' },
      { labelKey: 'nav.workspace.designer', path: '/build/designer' },
    ])
  })

  it('should show empty breadcrumbs for unknown /build/designer/new path', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/build/designer/new' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([])
  })

  it('should handle dynamic routes like /learn/examples/123', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/learn/examples/123' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([
      { labelKey: 'nav.learn', path: '/learn' },
      { labelKey: 'nav.learn.examples', path: '/learn/examples' },
      { labelKey: 'nav.learn.exampleDetail', path: '/learn/examples/123' },
    ])
  })

  it('should set empty breadcrumbs for unknown path', () => {
    renderHook(() => useBreadcrumb(), {
      wrapper: ({ children }) => wrapper({ children, initialPath: '/unknown' }),
    })

    const state = store.getState()
    expect(state.navigation.breadcrumbs).toEqual([])
  })
})
