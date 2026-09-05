import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Provider } from 'react-redux'
import { vi } from 'vitest'

import ExampleDetail from '../ExampleDetail'

import { store } from '@/app/store'
import { getExample } from '@/shared/api/examples'
import { renderWithApp } from '@/test/renderWithApp'

const executePreviewMock = vi.fn()
const useParamsMock = vi.fn()
const navigateMock = vi.fn()

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    i18n: { language: 'en', resolvedLanguage: 'en' },
    t: (key: string, options?: Record<string, unknown>) => {
      if (options?.type) return `${key}:${String(options.type)}`
      if (options?.error) return `${key}:${String(options.error)}`
      return key
    },
  }),
}))

vi.mock('react-router-dom', () => ({
  useParams: () => useParamsMock(),
  useNavigate: () => navigateMock,
}))

vi.mock('@/shared/api/examples', () => ({
  getExample: vi.fn(async (id: string) => ({
    id,
    name: 'Example Process',
    description: 'Example description',
    category: 'basics',
    duration: '5m',
    difficulty: 3,
    level: 'beginner',
    tags: ['demo'],
    modelType: 'BPMN',
    code: '<definitions/>',
    overview: 'Overview',
    documentation: 'Docs',
  })),
  getAllExamples: vi.fn(async () => [
    {
      id: 'example-1',
      name: 'Example Process',
      description: 'Example description',
      category: 'basics',
      duration: '5m',
      difficulty: 3,
      level: 'beginner',
      tags: ['demo'],
      modelType: 'BPMN',
      code: '<definitions/>',
      overview: 'Overview',
      documentation: 'Docs',
    },
  ]),
}))

vi.mock('@/shared/api/execution', () => ({
  executePreview: (...args: unknown[]) => executePreviewMock(...args),
}))

vi.mock('@/shared/components/CodeBlock', () => ({
  default: ({ code, filename }: { code: string; filename: string }) => (
    <pre data-filename={filename}>{code}</pre>
  ),
}))

vi.mock('@/shared/components/ExampleNavigation', () => ({
  default: () => <div>Example Navigation</div>,
}))

vi.mock('@/shared/components/FeedbackActions', () => ({
  default: () => <div>Feedback Actions</div>,
}))

vi.mock('@/shared/components/MarkdownRenderer', () => ({
  default: ({ content }: { content: string }) => <div>{content}</div>,
}))

vi.mock('@/learn/components/RelatedExamples', () => ({
  default: () => <div>Related Examples</div>,
}))

vi.mock('@/shared/components/skeletons/ExampleDetailSkeleton', () => ({
  default: () => <div>Loading...</div>,
}))

vi.mock('@/shared/components/TableOfContents', () => ({
  default: () => <div>Table Of Contents</div>,
}))

vi.mock('@/shared/hooks/useLearningProgress', () => ({
  useLearningProgress: () => ({
    setTotalExamples: vi.fn(),
  }),
  LearningProgressCard: () => <div>Learning Progress Card</div>,
}))

vi.mock('@/shared/constants', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/constants')>()
  return {
    ...actual,
    buildDesignerRoute: vi.fn(() => '/build/designer'),
    createLearnExampleDetailPath: vi.fn((id: string) => `/learn/examples/${id}`),
    createLearnExamplesPath: vi.fn(() => '/learn/examples'),
  }
})

describe('ExampleDetail execution integration', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    useParamsMock.mockReturnValue({ id: 'example-1' })
    executePreviewMock.mockResolvedValue({
      success: true,
      message: 'ok',
      result: { output: 'done' },
    })
  })

  it('executes examples through the shared execution API', async () => {
    const user = userEvent.setup()

    render(
      renderWithApp(
        <Provider store={store}>
          <ExampleDetail />
        </Provider>
      )
    )

    await screen.findByText('Example Process')
    await user.click(screen.getByRole('tab', { name: 'detail.tab.execute' }))

    const executeButtonLabels = await screen.findAllByText('exec.execute')
    const executeButton = executeButtonLabels
      .map((node) => node.closest('button'))
      .find((button): button is HTMLButtonElement => button instanceof HTMLButtonElement)

    expect(executeButton).toBeDefined()
    await user.click(executeButton!)

    await waitFor(() => {
      expect(executePreviewMock).toHaveBeenCalledWith({
        code: 'example-1',
        modelType: 'BPMN',
        xml: '<definitions/>',
        params: {},
      })
    })
    expect(await screen.findByText(/"output": "done"/)).toBeInTheDocument()
  })

  it('shows a warning before running the draft on the server', async () => {
    const user = userEvent.setup()

    render(
      renderWithApp(
        <Provider store={store}>
          <ExampleDetail />
        </Provider>
      )
    )

    await screen.findByText('Example Process')
    await user.click(screen.getByRole('tab', { name: 'detail.tab.execute' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveClass('ant-alert-warning')
    expect(alert).toHaveTextContent('exec.title')
    expect(alert).toHaveTextContent('exec.desc')
  })

  it('uses the model-specific filename in the code tab', async () => {
    const user = userEvent.setup()

    vi.mocked(getExample).mockResolvedValueOnce({
      id: 'example-1',
      name: 'Example Process',
      description: 'Example description',
      category: 'basics',
      duration: '5m',
      difficulty: 3,
      level: 0,
      tags: ['demo'],
      modelType: 'TBBPM',
      code: '<bpm/>',
      overview: 'Overview',
      documentation: 'Docs',
      explanation: '',
      keyConcepts: [],
      nextSteps: '',
      whatYouWillLearn: [],
    })

    render(
      renderWithApp(
        <Provider store={store}>
          <ExampleDetail />
        </Provider>
      )
    )

    await screen.findByText('Example Process')
    await user.click(screen.getByRole('tab', { name: 'detail.tab.code' }))

    expect(screen.getByText('<bpm/>')).toHaveAttribute('data-filename', 'example-1.bpm')
  })

  it('localizes a missing example instead of exposing the internal error message', async () => {
    useParamsMock.mockReturnValue({ id: 'missing-example' })
    vi.mocked(getExample).mockRejectedValueOnce(new Error('Example not found: missing-example'))

    render(
      renderWithApp(
        <Provider store={store}>
          <ExampleDetail />
        </Provider>
      )
    )

    expect(await screen.findByText('error.exampleNotFound')).toBeInTheDocument()
    expect(screen.queryByText('Example not found: missing-example')).not.toBeInTheDocument()
  })
})
