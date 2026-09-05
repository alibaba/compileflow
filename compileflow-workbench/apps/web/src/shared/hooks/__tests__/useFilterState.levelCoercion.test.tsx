import { act, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter, useSearchParams } from 'react-router-dom'

import { useFilterState } from '../useFilterState'

import { useExampleFilter } from '@/learn/hooks/useExampleFilter'
import type { Example } from '@/shared/contracts'

function wrapper(initialEntry: string) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <MemoryRouter initialEntries={[initialEntry]}>{children}</MemoryRouter>
  }
}

function example(
  partial: Pick<Example, 'id' | 'name' | 'level' | 'category' | 'modelType'>
): Example {
  return {
    description: 'd',
    tags: [],
    difficulty: 1,
    duration: '5m',
    whatYouWillLearn: [],
    keyConcepts: [],
    code: '<xml/>',
    overview: '',
    explanation: '',
    nextSteps: '',
    documentation: '',
    ...partial,
  }
}

const examples: Example[] = [
  example({ id: 'a', name: 'Beginner', level: 0, category: 'basics', modelType: 'BPMN' }),
  example({ id: 'b', name: 'Advanced', level: 3, category: 'advanced', modelType: 'TBBPM' }),
]

const parseLevel = (raw: string): number | undefined => {
  const level = Number(raw)
  return Number.isInteger(level) ? level : undefined
}

describe('useFilterState level coercion (ExampleList regression)', () => {
  it('reads numeric URL params with an explicit parser', () => {
    const { result } = renderHook(
      () =>
        useFilterState(
          {
            level: undefined as number | undefined,
            search: '',
          },
          { level: parseLevel }
        ),
      { wrapper: wrapper('/learn/examples?level=3') }
    )

    // Contract required by ExampleList + useExampleFilter (strict === on Example.level).
    expect(typeof result.current[0].level).toBe('number')
    expect(result.current[0].level).toBe(3)
  })

  it('round-trips Select-style numeric updates without becoming strings', () => {
    const { result } = renderHook(
      () => {
        const [filters, updateFilter] = useFilterState(
          { level: undefined as number | undefined },
          { level: parseLevel }
        )
        const [params] = useSearchParams()
        return { filters, updateFilter, params }
      },
      { wrapper: wrapper('/learn/examples') }
    )

    act(() => {
      result.current.updateFilter('level', 2)
    })

    expect(result.current.params.get('level')).toBe('2')
    expect(typeof result.current.filters.level).toBe('number')
    expect(result.current.filters.level).toBe(2)
  })

  it('uses the field parser instead of guessing the type of undefined-backed values', () => {
    const { result } = renderHook(
      () =>
        useFilterState(
          { modelType: undefined as Example['modelType'] | undefined },
          {
            modelType: (raw) => (raw === 'BPMN' || raw === 'TBBPM' ? raw : undefined),
          }
        ),
      { wrapper: wrapper('/learn/examples?modelType=123') }
    )

    expect(result.current[0].modelType).toBeUndefined()
  })

  it('updates related filters atomically without dropping either URL value', () => {
    const { result } = renderHook(
      () => {
        const [filters, , , updateFilters] = useFilterState({
          keyword: '',
          page: 1,
        })
        const [params] = useSearchParams()
        return { filters, params, updateFilters }
      },
      { wrapper: wrapper('/operate/processes?page=3') }
    )

    act(() => {
      result.current.updateFilters({ keyword: '订单', page: 1 })
    })

    expect(result.current.params.toString()).toBe('keyword=%E8%AE%A2%E5%8D%95')
    expect(result.current.filters).toEqual({ keyword: '订单', page: 1 })
  })

  it('removes invalid filter values while preserving unrelated URL state', async () => {
    const { result } = renderHook(
      () => {
        const [filters, , clearAll] = useFilterState(
          { page: 1, type: '' },
          {
            page: (raw) => {
              const page = Number(raw)
              return Number.isInteger(page) && page > 0 ? page : 1
            },
            type: (raw) => (raw === 'BPMN' || raw === 'TBBPM' ? raw : ''),
          }
        )
        const [params] = useSearchParams()
        return { clearAll, filters, params }
      },
      { wrapper: wrapper('/operate/processes?type=INVALID&page=-3&source=shared') }
    )

    expect(result.current.filters).toEqual({ page: 1, type: '' })
    await waitFor(() => expect(result.current.params.toString()).toBe('source=shared'))

    act(() => result.current.clearAll())
    expect(result.current.params.toString()).toBe('source=shared')
  })

  it('filters examples by level from URL state end-to-end', () => {
    const { result } = renderHook(
      () => {
        const [filterState] = useFilterState(
          {
            level: undefined as number | undefined,
            category: undefined as Example['category'] | undefined,
            modelType: undefined as Example['modelType'] | undefined,
            search: '',
            sortBy: 'name' as const,
          },
          {
            level: parseLevel,
            category: (raw) => (raw === 'advanced' ? raw : undefined),
            modelType: (raw) => (raw === 'BPMN' || raw === 'TBBPM' ? raw : undefined),
            sortBy: () => 'name',
          }
        )
        return useExampleFilter({
          examples,
          selectedLevel: filterState.level,
          selectedCategory: filterState.category,
          selectedProcessType: filterState.modelType,
          searchText: filterState.search,
          sortBy: filterState.sortBy,
        })
      },
      { wrapper: wrapper('/learn/examples?level=3') }
    )

    expect(result.current.map((e) => e.id)).toEqual(['b'])
  })
})
