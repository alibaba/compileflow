import { fireEvent, render, screen } from '@testing-library/react'
import { Link, MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import AppShell from '../AppShell'

import { SidebarProvider } from '@/shared/contexts/SidebarContext'

vi.mock('../AppBar', () => ({ default: () => null }))
vi.mock('../useBreadcrumb', () => ({ useBreadcrumb: () => undefined }))
vi.mock('@/shared/components/MockBanner', () => ({ default: () => null }))

describe('AppShell sidebar layout', () => {
  it('removes the sidebar offset when entering the designer from operate', () => {
    render(
      <MemoryRouter initialEntries={['/operate/processes']}>
        <SidebarProvider>
          <Link to="/build/designer">Open designer</Link>
          <Link to="/operate/processes">Open processes</Link>
          <AppShell
            loadExamples={() => Promise.resolve([])}
            loadProcesses={() => Promise.resolve([])}
          />
        </SidebarProvider>
      </MemoryRouter>
    )

    expect(screen.getByRole('main')).toHaveStyle({ marginLeft: '212px' })
    fireEvent.click(screen.getByRole('link', { name: 'Open designer' }))
    expect(screen.getByRole('main')).toHaveStyle({ marginLeft: '0px' })
    fireEvent.click(screen.getByRole('link', { name: 'Open processes' }))
    expect(screen.getByRole('main')).toHaveStyle({ marginLeft: '212px' })
  })
})
