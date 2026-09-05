import type { Example } from '@/shared/contracts'

function matches(example: Example | undefined, query: string): boolean {
  return Boolean(
    example &&
    (example.name.toLowerCase().includes(query) ||
      example.description?.toLowerCase().includes(query) ||
      example.tags?.some((tag) => tag.toLowerCase().includes(query)))
  )
}

export function filterExamplesByText(
  examples: Example[],
  sourceExamples: Example[],
  searchText: string,
  limit?: number
): Example[] {
  const query = searchText.trim().toLowerCase()
  if (!query) return []

  const sourceById = new Map(sourceExamples.map((example) => [example.id, example]))
  const matchesQuery = examples.filter(
    (example) => matches(example, query) || matches(sourceById.get(example.id), query)
  )
  return limit == null ? matchesQuery : matchesQuery.slice(0, limit)
}
