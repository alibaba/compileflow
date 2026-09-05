import { useMemo } from 'react'

import type { Example } from '@/shared/contracts'
import { filterExamplesByText } from '@/shared/examples/exampleSearch'

export type ExampleSort = 'name' | 'difficulty' | 'duration'

function durationMinutes(duration?: string): number {
  if (!duration) return Number.POSITIVE_INFINITY
  const minutes = Number.parseInt(duration, 10)
  return Number.isNaN(minutes) ? Number.POSITIVE_INFINITY : minutes
}

interface UseExampleFilterOptions {
  examples: Example[]
  sourceExamples?: Example[]
  selectedLevel?: number
  selectedCategory?: Example['category']
  selectedProcessType?: Example['modelType']
  searchText: string
  sortBy: ExampleSort
}

export function useExampleFilter({
  examples,
  sourceExamples = examples,
  selectedLevel,
  selectedCategory,
  selectedProcessType,
  searchText,
  sortBy,
}: UseExampleFilterOptions): Example[] {
  return useMemo(() => {
    let filtered = examples

    if (selectedLevel !== undefined) {
      filtered = filtered.filter((e) => e.level === selectedLevel)
    }

    if (selectedCategory) {
      filtered = filtered.filter((e) => e.category === selectedCategory)
    }

    if (selectedProcessType) {
      filtered = filtered.filter((e) => e.modelType === selectedProcessType)
    }

    if (searchText) {
      filtered = filterExamplesByText(filtered, sourceExamples, searchText)
    }

    return [...filtered].sort((a, b) => {
      switch (sortBy) {
        case 'difficulty':
          return a.difficulty - b.difficulty
        case 'duration':
          return durationMinutes(a.duration) - durationMinutes(b.duration)
        case 'name':
        default:
          return a.name.localeCompare(b.name)
      }
    })
  }, [
    examples,
    sourceExamples,
    selectedLevel,
    selectedCategory,
    selectedProcessType,
    searchText,
    sortBy,
  ])
}
