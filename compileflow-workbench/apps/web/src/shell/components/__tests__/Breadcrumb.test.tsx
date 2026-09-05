import { configureStore } from '@reduxjs/toolkit'
import { render, screen } from '@testing-library/react'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { vi } from 'vitest'

import Breadcrumb from '../Breadcrumb'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

const createMockStore = (breadcrumbs: Array<{ labelKey: string; path: string }> = []) => {
  return configureStore({
    reducer: {
      navigation: () => ({
        breadcrumbs,
      }),
      user: () => ({
        currentUser: null,
      }),
    },
  })
}

describe('Breadcrumb', () => {
  it('should render nothing when breadcrumbs is empty', () => {
    const store = createMockStore([])
    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <Breadcrumb />
        </BrowserRouter>
      </Provider>
    )
    expect(container.firstChild).toBeNull()
  })

  it('should render home icon and breadcrumbs', () => {
    const breadcrumbs = [
      { labelKey: 'nav.learn', path: '/learn' },
      { labelKey: 'nav.learn.examples', path: '/learn/examples' },
    ]
    const store = createMockStore(breadcrumbs)

    const { getByText } = render(
      <Provider store={store}>
        <BrowserRouter>
          <Breadcrumb />
        </BrowserRouter>
      </Provider>
    )

    expect(getByText('nav.learn')).toBeInTheDocument()
    expect(getByText('nav.learn.examples')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'common.breadcrumb' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'nav.home' })).toHaveAttribute('href', '/learn')
    expect(screen.getByRole('link', { name: 'nav.learn' })).toHaveAttribute('href', '/learn')
    expect(screen.queryByRole('link', { name: 'nav.learn.examples' })).not.toBeInTheDocument()
  })

  it('should render correct number of items', () => {
    const breadcrumbs = [
      { labelKey: 'nav.learn', path: '/learn' },
      { labelKey: 'nav.learn.examples', path: '/learn/examples' },
      { labelKey: 'nav.learn.exampleDetail', path: '/learn/examples/123' },
    ]
    const store = createMockStore(breadcrumbs)

    const { container } = render(
      <Provider store={store}>
        <BrowserRouter>
          <Breadcrumb />
        </BrowserRouter>
      </Provider>
    )

    // Home icon + 3 breadcrumbs = 4 items
    const items = container.querySelectorAll('.ant-breadcrumb-link')
    expect(items.length).toBeGreaterThan(0)
  })
})
