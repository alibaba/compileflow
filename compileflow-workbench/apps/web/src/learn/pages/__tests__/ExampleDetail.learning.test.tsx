import { act, fireEvent, render, renderHook, screen, waitFor, within } from '@testing-library/react'
import { App } from 'antd'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import ExampleDetail from '../ExampleDetail'

import { findMockExample } from '@/shared/api/exampleMockData'
import type { Example } from '@/shared/contracts'
import { readLearningProgress, writeLearningProgress } from '@/shared/hooks/learningProgressStorage'
import { useLearningProgress } from '@/shared/hooks/useLearningProgress'

const mocks = vi.hoisted(() => ({ all: vi.fn(), one: vi.fn() }))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'en', resolvedLanguage: 'en' },
  }),
}))
vi.mock('react-router-dom', () => ({
  useParams: () => ({ id: 'audit-one' }),
  useNavigate: () => vi.fn(),
}))
vi.mock('@/shared/api/examples', () => ({ getAllExamples: mocks.all, getExample: mocks.one }))
vi.mock('@/shared/api/execution', () => ({ executePreview: vi.fn() }))
vi.mock('@/shared/contexts/ThemeContext', () => ({ useTheme: () => ({ theme: 'light' }) }))
vi.mock('@/shared/components/syntaxHighlighter', () => ({
  default: ({ children }: { children: ReactNode }) => <pre>{children}</pre>,
}))
vi.mock('@/shared/components/CodeBlock', () => ({ default: () => null }))
vi.mock('@/shared/components/ExampleNavigation', () => ({ default: () => null }))
vi.mock('@/shared/components/FeedbackActions', () => ({ default: () => null }))
vi.mock('@/learn/components/RelatedExamples', () => ({ default: () => null }))
vi.mock('@/shared/components/skeletons/ExampleDetailSkeleton', () => ({
  default: () => <div>Loading</div>,
}))
vi.mock('@/shared/services/designerNavigation', () => ({ openDesignerFromExample: vi.fn() }))

const example: Example = {
  id: 'audit-one',
  name: 'Audit Example',
  description: 'Fixture',
  category: 'basics',
  duration: '5 minutes',
  difficulty: 1,
  level: 0,
  tags: [],
  modelType: 'BPMN',
  code: '<definitions/>',
  overview: '# Overview Heading',
  documentation: '# Docs Heading\n\n## Docs Section',
  explanation: '',
  nextSteps: '',
  keyConcepts: [],
  whatYouWillLearn: ['Learn item'],
}

let resolveCatalog: (data: Example[]) => void
let scrolled: Element[]
let originalScrollIntoView: PropertyDescriptor | undefined

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame'] })
  localStorage.clear()
  mocks.one.mockReset().mockResolvedValue(example)
  mocks.all.mockReset().mockImplementation(
    () =>
      new Promise<Example[]>((resolve) => {
        resolveCatalog = resolve
      })
  )
  scrolled = []
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
      unobserve() {}
    }
  )
  vi.spyOn(window, 'scrollTo').mockImplementation(() => {})
  originalScrollIntoView = Object.getOwnPropertyDescriptor(Element.prototype, 'scrollIntoView')
  Object.defineProperty(Element.prototype, 'scrollIntoView', {
    configurable: true,
    value(this: Element) {
      scrolled.push(this)
    },
  })
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  if (originalScrollIntoView) {
    Object.defineProperty(Element.prototype, 'scrollIntoView', originalScrollIntoView)
  } else {
    Reflect.deleteProperty(Element.prototype, 'scrollIntoView')
  }
})

function mountPage() {
  return render(
    <App>
      <ExampleDetail />
    </App>
  )
}

describe('ExampleDetail learning progress ownership', () => {
  it('preserves a completed example when a delayed catalog arrives and the page is reloaded', async () => {
    writeLearningProgress({ completedExamples: [], totalExamples: 2, lastAccessTime: 1 })
    const page = mountPage()
    fireEvent.click(await screen.findByRole('button', { name: 'learning.markComplete' }))
    expect(readLearningProgress()?.completedExamples).toEqual([example.id])

    await act(async () => {
      resolveCatalog([example, { ...example, id: 'audit-two' }])
    })
    expect(await screen.findByText('learning.completed')).toBeInTheDocument()
    expect(readLearningProgress()?.completedExamples).toEqual([example.id])

    page.unmount()
    const reloaded = renderHook(() => useLearningProgress())
    expect(reloaded.result.current.isCompleted(example.id)).toBe(true)
  })

  it('shares the catalog total with the mounted card and preserves it on completion', async () => {
    mountPage()
    await screen.findByRole('button', { name: 'learning.markComplete' })
    await act(async () => {
      resolveCatalog([example, { ...example, id: 'audit-two' }])
    })
    expect(readLearningProgress()?.totalExamples).toBe(2)
    expect(screen.getByText('0 / 2')).toBeInTheDocument()

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: 'learning.markComplete' }))
    })

    expect(readLearningProgress()?.totalExamples).toBe(2)
    expect(readLearningProgress()?.completedExamples).toEqual([example.id])
    expect(screen.getByText('1 / 2')).toBeInTheDocument()
  })
})

describe('ExampleDetail table of contents target identity', () => {
  it('targets the built-in Greeting execution parameters instead of hidden learning objectives', async () => {
    mocks.one.mockResolvedValue(findMockExample('learn.tbbpm.greeting'))
    mountPage()
    const overview = await screen.findByRole('heading', { name: 'detail.whatYouWillLearn' })
    await waitFor(() => expect(overview.id).not.toBe(''))
    fireEvent.click(screen.getByRole('tab', { name: 'detail.tab.execute' }))
    const active = await screen.findByRole('heading', { name: 'exec.params' })
    const toc = screen.getByRole('navigation', { name: 'toc.title' })
    const link = await within(toc).findByRole('link', { name: 'exec.params' })
    expect(overview.closest('[role="tabpanel"]')).toHaveAttribute('aria-hidden', 'true')

    fireEvent.click(link)

    expect(scrolled[scrolled.length - 1]).toBe(active)
    expect(active.id).not.toBe(overview.id)
    expect(document.getElementById(active.id)).toBe(active)
  })

  it('targets a Docs Markdown heading instead of a retained Overview heading', async () => {
    mountPage()
    const overview = await screen.findByRole('heading', { name: 'Overview Heading' })
    await waitFor(() => expect(overview.id).not.toBe(''))
    const toc = await screen.findByRole('navigation', { name: 'toc.title' })
    fireEvent.click(within(toc).getByRole('link', { name: 'Overview Heading' }))
    expect(scrolled[scrolled.length - 1]).toBe(overview)

    fireEvent.click(screen.getByRole('tab', { name: 'detail.tab.docs' }))
    const docs = await screen.findByRole('heading', { name: 'Docs Section' })
    const docsLink = await within(toc).findByRole('link', { name: 'Docs Section' })
    const retained = screen.getByRole('heading', {
      name: 'detail.whatYouWillLearn',
      hidden: true,
    })
    expect(retained.closest('[role="tabpanel"]')).toHaveAttribute('aria-hidden', 'true')
    expect(docs.closest('[role="tabpanel"]')).toHaveAttribute('aria-hidden', 'false')

    fireEvent.click(docsLink)

    expect(scrolled[scrolled.length - 1]).toBe(docs)
    expect(docs.id).not.toBe(retained.id)
    expect(document.getElementById(docs.id)).toBe(docs)
  })
})
