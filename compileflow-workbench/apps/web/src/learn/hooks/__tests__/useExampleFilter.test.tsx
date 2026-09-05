import { renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { useExampleFilter } from '../useExampleFilter'

import type { Example } from '@/shared/contracts'

function example(id: string, name: string, duration: string): Example {
  return {
    id,
    name,
    description: `${name} description`,
    category: 'basics',
    code: '<bpm />',
    documentation: '',
    explanation: '',
    level: 0,
    difficulty: 1,
    duration,
    keyConcepts: [],
    modelType: 'TBBPM',
    nextSteps: '',
    overview: '',
    tags: [],
    whatYouWillLearn: [],
  }
}

describe('useExampleFilter', () => {
  it('searches the presentation text supplied by the caller', () => {
    const localized = example('parallel', 'TBBPM 并行计算', '12 minutes')
    const { result } = renderHook(() =>
      useExampleFilter({
        examples: [localized],
        searchText: '并行',
        sortBy: 'name',
      })
    )

    expect(result.current).toEqual([localized])
  })

  it('keeps canonical text searchable while displaying localized examples', () => {
    const source = example('parallel', 'TBBPM Parallel Computation', '12 minutes')
    const localized = { ...source, name: 'TBBPM 并行计算' }
    const { result } = renderHook(() =>
      useExampleFilter({
        examples: [localized],
        sourceExamples: [source],
        searchText: 'Parallel',
        sortBy: 'name',
      })
    )

    expect(result.current).toEqual([localized])
  })

  it('sorts durations numerically instead of lexicographically', () => {
    const examples = [
      example('ten', 'Ten', '10 minutes'),
      example('five', 'Five', '5 minutes'),
      example('twelve', 'Twelve', '12 minutes'),
    ]
    const { result } = renderHook(() =>
      useExampleFilter({ examples, searchText: '', sortBy: 'duration' })
    )

    expect(result.current.map(({ id }) => id)).toEqual(['five', 'ten', 'twelve'])
  })
})
