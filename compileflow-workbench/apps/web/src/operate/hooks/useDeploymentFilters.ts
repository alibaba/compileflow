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
}

const DEFAULTS: DeploymentFilters = {
  searchText: '',
  statusFilter: '',
  aliasFilter: '',
}

const PARSERS: FilterParsers<DeploymentFilters> = {
  statusFilter: (raw) =>
    raw === 'in_progress' || raw === 'completed' || raw === 'aborted' ? raw : '',
  aliasFilter: (raw) => raw,
}

interface UseDeploymentFiltersResult {
  filters: DeploymentFilters
  updateFilter: FilterUpdater<DeploymentFilters>
  clearAll: () => void
}

export function useDeploymentFilters(): UseDeploymentFiltersResult {
  const [filters, updateFilter, clearAll] = useFilterState(DEFAULTS, PARSERS)

  return { filters, updateFilter, clearAll }
}
