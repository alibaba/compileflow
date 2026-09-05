import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import { vi } from 'vitest'

import FeedbackActions from '../FeedbackActions'

// The component uses `const { message } = App.useApp()` instead of the
// deprecated static `message` import. We mock only `App.useApp` (not the
// entire `App` component, which we still need to render as a provider).
const messageMock: MessageInstance = {
  error: vi.fn(),
  info: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  loading: vi.fn(),
  open: vi.fn(),
  destroy: vi.fn(),
  context: null as unknown,
} as unknown as MessageInstance

vi.mock('antd', async (importOriginal) => {
  const actual = await importOriginal<typeof import('antd')>()
  return {
    ...actual,
    App: Object.assign(actual.App, {
      useApp: () => ({ message: messageMock }),
    }),
  }
})

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}))

describe('FeedbackActions', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.defineProperty(navigator, 'share', {
      configurable: true,
      value: undefined,
    })
  })

  it('reports success only after the clipboard write succeeds', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })

    render(
      <App>
        <FeedbackActions exampleId="example-1" exampleTitle="Example" />
      </App>
    )

    await userEvent.click(screen.getByRole('button', { name: 'feedback.share' }))

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith(window.location.href)
      expect(messageMock.success).toHaveBeenCalledWith('feedback.linkCopied')
    })
    expect(messageMock.error).not.toHaveBeenCalled()
  })

  it('reports a clipboard failure instead of claiming the link was copied', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('clipboard denied'))
    Object.defineProperty(navigator, 'clipboard', {
      configurable: true,
      value: { writeText },
    })

    render(
      <App>
        <FeedbackActions exampleId="example-1" exampleTitle="Example" />
      </App>
    )

    await userEvent.click(screen.getByRole('button', { name: 'feedback.share' }))

    await waitFor(() => {
      expect(messageMock.error).toHaveBeenCalledWith('feedback.copyFailed')
    })
    expect(messageMock.success).not.toHaveBeenCalled()
  })
})
