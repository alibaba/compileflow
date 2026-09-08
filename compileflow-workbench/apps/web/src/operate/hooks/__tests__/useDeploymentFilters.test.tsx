import { act, renderHook, waitFor } from '@testing-library/react'
import type { PropsWithChildren } from 'react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { useDeploymentFilters } from '@/operate/hooks/useDeploymentFilters'

function wrapper({ children }: PropsWithChildren) {
  return (
    <MemoryRouter
      initialEntries={['/operate/deployments?statusFilter=invalid&page=broken&source=shared']}
    >
      {children}
    </MemoryRouter>
  )
}

describe('useDeploymentFilters', () => {
  it('normalizes invalid URL state and resets the page when a filter changes', async () => {
    const { result, rerender } = renderHook(
      () => ({ filters: useDeploymentFilters(), location: useLocation() }),
      { wrapper }
    )

    await waitFor(() => expect(result.current.location.search).toBe('?source=shared'))
    expect(result.current.filters.filters.page).toBe(1)
    const updateFilter = result.current.filters.updateFilter
    rerender()
    expect(result.current.filters.updateFilter).toBe(updateFilter)

    act(() => result.current.filters.updateFilter('page', 3))
    await waitFor(() => expect(result.current.location.search).toContain('page=3'))

    act(() => result.current.filters.updateFilter('statusFilter', 'completed'))
    await waitFor(() =>
      expect(result.current.location.search).toBe('?source=shared&statusFilter=completed')
    )
    expect(result.current.filters.filters.page).toBe(1)
  })
})
