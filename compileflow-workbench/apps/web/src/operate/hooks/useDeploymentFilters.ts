import { useCallback } from 'react'

import type { Deployment } from '@/shared/contracts'
import {
  type FilterParsers,
  type FilterUpdater,
  useFilterState,
} from '@/shared/hooks/useFilterState'

interface DeploymentFilters {
  searchText: string
  statusFilter: Deployment['status'] | ''
  aliasFilter: string
  page: number
}

const DEFAULTS: DeploymentFilters = {
  searchText: '',
  statusFilter: '',
  aliasFilter: '',
  page: 1,
}

const PARSERS: FilterParsers<DeploymentFilters> = {
  statusFilter: (raw) =>
    raw === 'in_progress' || raw === 'completed' || raw === 'aborted' ? raw : '',
  aliasFilter: (raw) => raw,
  page: (raw) => {
    const page = Number(raw)
    return Number.isInteger(page) && page > 0 ? page : 1
  },
}

interface UseDeploymentFiltersResult {
  filters: DeploymentFilters
  updateFilter: FilterUpdater<DeploymentFilters>
  clearAll: () => void
}

export function useDeploymentFilters(): UseDeploymentFiltersResult {
  const [filters, , clearAll, updateFilters] = useFilterState(DEFAULTS, PARSERS)
  const updateFilter: FilterUpdater<DeploymentFilters> = useCallback(
    (key, value) => {
      updateFilters(key === 'page' ? { page: Number(value) } : { [key]: value, page: 1 })
    },
    [updateFilters]
  )

  return { filters, updateFilter, clearAll }
}
