import { act, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { ThemeProvider, useTheme } from '../ThemeContext'

function ThemeValue() {
  const { theme, toggleTheme } = useTheme()
  return (
    <>
      <output>{theme}</output>
      <button type="button" onClick={toggleTheme}>
        toggle
      </button>
    </>
  )
}

describe('ThemeProvider', () => {
  it('synchronizes theme changes from another tab', async () => {
    localStorage.setItem('compileflow:theme', 'light')
    render(
      <ThemeProvider>
        <ThemeValue />
      </ThemeProvider>
    )

    act(() => {
      window.dispatchEvent(
        Object.assign(new Event('storage'), { key: 'compileflow:theme', newValue: 'dark' })
      )
    })

    await waitFor(() => {
      expect(screen.getByText('dark')).toBeInTheDocument()
      expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    })
  })

  it('persists an explicit theme change', async () => {
    localStorage.setItem('compileflow:theme', 'light')
    render(
      <ThemeProvider>
        <ThemeValue />
      </ThemeProvider>
    )

    act(() => screen.getByRole('button', { name: 'toggle' }).click())

    await waitFor(() => {
      expect(localStorage.getItem('compileflow:theme')).toBe('dark')
    })
  })
})
