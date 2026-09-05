import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import MockBanner from '../MockBanner'

const mockRuntimeConfig = vi.hoisted(() => ({
  operateMode: 'mock' as 'mock' | 'real',
}))

vi.mock('@/shared/config/buildConfig', () => ({
  APP_BUILD_CONFIG: mockRuntimeConfig,
}))

describe('MockBanner', () => {
  beforeEach(() => {
    mockRuntimeConfig.operateMode = 'mock'
  })

  it('renders a demo-mode declaration when operate mode is mock', () => {
    render(<MockBanner />)

    expect(screen.getByText(/演示模式/)).toBeInTheDocument()
    expect(screen.getByText(/非真实引擎输出/)).toBeInTheDocument()
  })

  it('does not render in real mode', () => {
    mockRuntimeConfig.operateMode = 'real'

    const { container } = render(<MockBanner />)

    expect(container.firstChild).toBeNull()
  })
})
