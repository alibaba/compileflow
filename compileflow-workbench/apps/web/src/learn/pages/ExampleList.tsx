import { CloseCircleOutlined, InboxOutlined, SearchOutlined } from '@ant-design/icons'
import { Alert, Button, Input, Row, Select } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import ExampleCard from '../components/ExampleCard'
import { type ExampleSort, useExampleFilter } from '../hooks/useExampleFilter'
import { getLevelLabel } from '../presentation/exampleMetadata'

import styles from './ExampleList.module.css'

import { getAllExamples } from '@/shared/api/examples'
import {
  type ActiveFilter,
  EmptyState,
  FilterBar,
  MetricGrid,
  PageHeader,
} from '@/shared/components/page'
import ExampleCardSkeleton from '@/shared/components/skeletons/ExampleCardSkeleton'
import '@/shared/components/skeletons/SkeletonStyles.css'
import { LEARN_CATEGORIES } from '@/shared/constants'
import type { Example, ProcessModelType } from '@/shared/contracts'
import { localizeExample } from '@/shared/examples/localizeExample'
import {
  type FilterParsers,
  type FilterUpdater,
  useFilterState,
} from '@/shared/hooks/useFilterState'
import { usePageTitle } from '@/shared/hooks/usePageTitle'

const { Option } = Select

interface ExampleFilterState {
  level: number | undefined
  category: Example['category'] | undefined
  modelType: ProcessModelType | undefined
  search: string
  sortBy: ExampleSort
}

const EXAMPLE_FILTER_DEFAULTS: ExampleFilterState = {
  level: undefined,
  category: undefined,
  modelType: undefined,
  search: '',
  sortBy: 'name',
}

const EXAMPLE_FILTER_PARSERS: FilterParsers<ExampleFilterState> = {
  level: (raw) => {
    const level = Number(raw)
    return Number.isInteger(level) && level >= 0 && level <= 3 ? level : undefined
  },
  category: (raw) => {
    if (raw === 'basics' || raw === 'business' || raw === 'advanced') return raw
    return undefined
  },
  modelType: (raw) => {
    if (raw === 'BPMN' || raw === 'TBBPM') return raw
    return undefined
  },
  sortBy: (raw) => {
    if (raw === 'name' || raw === 'difficulty' || raw === 'duration') return raw
    return 'name'
  },
}

interface ExampleListFiltersProps {
  activeFilters: ActiveFilter[] | undefined
  clearAllFilters: () => void
  filterState: ExampleFilterState
  hasActiveFilters: boolean
  onRemoveFilter: (key: string) => void
  updateFilter: FilterUpdater<ExampleFilterState>
}

function useExamplesCatalog() {
  const { t } = useTranslation()
  const [examples, setExamples] = useState<Example[]>([])
  const [loading, setLoading] = useState(true)
  const [loadFailed, setLoadFailed] = useState(false)
  const requestGeneration = useRef(0)

  const loadExamples = useCallback(async () => {
    const generation = ++requestGeneration.current
    try {
      setLoading(true)
      setLoadFailed(false)

      const loadedExamples = await getAllExamples()
      if (generation === requestGeneration.current) setExamples(loadedExamples)
    } catch {
      if (generation === requestGeneration.current) setLoadFailed(true)
    } finally {
      if (generation === requestGeneration.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadExamples()
    return () => {
      requestGeneration.current += 1
    }
  }, [loadExamples])

  return {
    error: loadFailed ? t('error.networkError') : null,
    examples,
    loading,
    reload: loadExamples,
  }
}

function categoryLabel(category: string, t: ReturnType<typeof useTranslation>['t']): string {
  const labels: Record<string, string> = {
    [LEARN_CATEGORIES.BEGINNER]: t('category.beginner'),
    [LEARN_CATEGORIES.BUSINESS]: t('category.business'),
    [LEARN_CATEGORIES.ADVANCED]: t('category.advanced'),
  }
  return labels[category] ?? category
}

function useExampleListState(examples: Example[], sourceExamples: Example[]) {
  const { t } = useTranslation()
  const [filterState, updateFilter, clearAllFilters] = useFilterState(
    EXAMPLE_FILTER_DEFAULTS,
    EXAMPLE_FILTER_PARSERS
  )

  const selectedLevel = filterState.level
  const selectedCategory = filterState.category
  const selectedProcessType = filterState.modelType
  const searchText = filterState.search
  const sortBy = filterState.sortBy
  const hasActiveFilters =
    selectedLevel !== undefined ||
    selectedCategory !== undefined ||
    selectedProcessType !== undefined ||
    searchText !== ''

  const filteredExamples = useExampleFilter({
    examples,
    sourceExamples,
    selectedLevel,
    selectedCategory,
    selectedProcessType,
    searchText,
    sortBy,
  })

  const activeFilters = useMemo((): ActiveFilter[] => {
    const filters: ActiveFilter[] = []
    if (selectedLevel !== undefined) {
      filters.push({
        key: 'level',
        label: `${t('filters.difficulty')}: ${getLevelLabel(selectedLevel, t)}`,
      })
    }
    if (selectedCategory) {
      filters.push({
        key: 'category',
        label: `${t('filters.category')}: ${categoryLabel(selectedCategory, t)}`,
      })
    }
    if (selectedProcessType) {
      filters.push({
        key: 'modelType',
        label: `${t('filters.processType')}: ${selectedProcessType}`,
      })
    }
    if (searchText) {
      filters.push({
        key: 'search',
        label: `${t('filters.search')}: "${searchText}"`,
      })
    }
    return filters
  }, [searchText, selectedCategory, selectedProcessType, selectedLevel, t])

  const handleRemoveFilter = useCallback(
    (key: string) => {
      if (key === 'level') updateFilter('level', undefined)
      else if (key === 'category') updateFilter('category', undefined)
      else if (key === 'modelType') updateFilter('modelType', undefined)
      else if (key === 'search') updateFilter('search', '')
    },
    [updateFilter]
  )

  return {
    activeFilters: hasActiveFilters ? activeFilters : undefined,
    clearAllFilters,
    filterState,
    filteredExamples,
    hasActiveFilters,
    handleRemoveFilter,
    sortBy,
    updateFilter,
  }
}

function ExampleListLoading() {
  return (
    <div className="fade-in">
      <div className={styles.loadingHeader}>
        <div
          className="skeleton"
          style={{
            height: 14,
            width: 72,
            borderRadius: 'var(--radius-sm)',
            marginBottom: 'var(--spacing-3)',
          }}
        />
        <div
          className="skeleton"
          style={{
            height: 36,
            width: 280,
            borderRadius: 'var(--radius-sm)',
            marginBottom: 'var(--spacing-3)',
          }}
        />
        <div
          className="skeleton"
          style={{ height: 18, width: 420, maxWidth: '100%', borderRadius: 'var(--radius-sm)' }}
        />
      </div>
      <div className={styles.loadingMetrics}>
        {Array.from({ length: 4 }).map((_, index) => (
          <div key={index} className={styles.loadingMetric}>
            <div
              className="skeleton"
              style={{
                height: 14,
                width: 64,
                borderRadius: 'var(--radius-sm)',
                marginBottom: 'var(--spacing-2)',
              }}
            />
            <div
              className="skeleton"
              style={{ height: 28, width: 48, borderRadius: 'var(--radius-sm)' }}
            />
          </div>
        ))}
      </div>
      <div className={styles.loadingFilters}>
        {Array.from({ length: 4 }).map((_, index) => (
          <div
            key={index}
            className="skeleton"
            style={{ height: 36, width: 140, borderRadius: 'var(--radius-lg)' }}
          />
        ))}
      </div>
      <Row gutter={[24, 24]} className={styles.grid}>
        <ExampleCardSkeleton count={6} />
      </Row>
    </div>
  )
}

function ExampleListError({ error, onRetry }: { error: string; onRetry: () => void }) {
  const { t } = useTranslation()

  return (
    <Alert
      title={t('error.loadFailed')}
      description={error}
      type="error"
      showIcon
      action={<Button onClick={onRetry}>{t('common.retry')}</Button>}
    />
  )
}

function ExampleMetrics({
  examples,
  filteredCount,
}: {
  examples: Example[]
  filteredCount: number
}) {
  const { t } = useTranslation()
  const metrics = useMemo(
    () => [
      { key: 'total', label: t('examples.totalCount'), value: examples.length },
      {
        key: 'bpmn',
        label: 'BPMN',
        value: examples.filter((example) => example.modelType === 'BPMN').length,
        accent: 'primary' as const,
      },
      {
        key: 'tbbpm',
        label: 'TBBPM',
        value: examples.filter((example) => example.modelType === 'TBBPM').length,
        accent: 'warning' as const,
      },
      {
        key: 'shown',
        label: t('filters.resultCount', { count: filteredCount }),
        value: filteredCount,
      },
    ],
    [examples, filteredCount, t]
  )

  return <MetricGrid metrics={metrics} columns={4} />
}

function ExampleListFilters({
  activeFilters,
  clearAllFilters,
  filterState,
  hasActiveFilters,
  onRemoveFilter,
  updateFilter,
}: ExampleListFiltersProps) {
  const { t } = useTranslation()
  return (
    <FilterBar
      activeFilters={activeFilters}
      onRemoveFilter={onRemoveFilter}
      onClearAll={clearAllFilters}
      activeLabel={t('filters.active')}
      clearLabel={t('filters.clear')}
    >
      <Select
        placeholder={t('examples.difficulty')}
        aria-label={t('examples.difficulty')}
        className={styles.filterSelect}
        allowClear
        value={filterState.level}
        onChange={(value) => updateFilter('level', value)}
      >
        <Option value={0}>{t('level.beginner')}</Option>
        <Option value={1}>{t('level.basic')}</Option>
        <Option value={2}>{t('level.intermediate')}</Option>
        <Option value={3}>{t('level.advanced')}</Option>
      </Select>
      <Select
        placeholder={t('examples.category')}
        aria-label={t('examples.category')}
        className={styles.filterSelect}
        allowClear
        value={filterState.category}
        onChange={(value) => updateFilter('category', value)}
      >
        <Option value={LEARN_CATEGORIES.BEGINNER}>{t('category.beginner')}</Option>
        <Option value={LEARN_CATEGORIES.BUSINESS}>{t('category.business')}</Option>
        <Option value={LEARN_CATEGORIES.ADVANCED}>{t('category.advanced')}</Option>
      </Select>
      <Select
        placeholder={t('examples.modelType')}
        aria-label={t('examples.modelType')}
        className={styles.filterSelect}
        allowClear
        value={filterState.modelType}
        onChange={(value) => updateFilter('modelType', value)}
      >
        <Option value="BPMN">BPMN</Option>
        <Option value="TBBPM">TBBPM</Option>
      </Select>
      <Select
        placeholder={t('examples.sortBy')}
        aria-label={t('examples.sortBy')}
        className={styles.filterSelect}
        value={filterState.sortBy}
        onChange={(value) => updateFilter('sortBy', value)}
      >
        <Option value="name">{t('sort.name')}</Option>
        <Option value="difficulty">{t('sort.difficulty')}</Option>
        <Option value="duration">{t('sort.duration')}</Option>
      </Select>
      <Input
        placeholder={t('examples.search')}
        aria-label={t('examples.search')}
        prefix={<SearchOutlined aria-hidden="true" />}
        className={styles.filterSearch}
        value={filterState.search ?? ''}
        onChange={(event) => updateFilter('search', event.target.value)}
        allowClear
      />
      {hasActiveFilters && (
        <Button onClick={clearAllFilters} icon={<CloseCircleOutlined />}>
          {t('filters.clear')}
        </Button>
      )}
    </FilterBar>
  )
}

function ExampleGrid({
  examples,
  hasActiveFilters,
  onClearFilters,
}: {
  examples: Example[]
  hasActiveFilters: boolean
  onClearFilters: () => void
}) {
  const { t } = useTranslation()

  if (examples.length === 0) {
    return (
      <EmptyState
        icon={<InboxOutlined />}
        title={t('examples.notFound')}
        actionLabel={hasActiveFilters ? t('filters.clear') : undefined}
        onAction={hasActiveFilters ? onClearFilters : undefined}
      />
    )
  }

  return (
    <Row gutter={[24, 24]} className={styles.grid}>
      {examples.map((example, index) => (
        <ExampleCard key={example.id} example={example} index={index} />
      ))}
    </Row>
  )
}

function ExampleList() {
  usePageTitle('pageTitle.learn')
  const { i18n, t } = useTranslation()
  const { error, examples, loading, reload } = useExamplesCatalog()
  const language = i18n.resolvedLanguage || i18n.language
  const localizedExamples = useMemo(
    () => examples.map((example) => localizeExample(example, t, language)),
    [examples, language, t]
  )
  const {
    activeFilters,
    clearAllFilters,
    filterState,
    filteredExamples,
    hasActiveFilters,
    handleRemoveFilter,
    updateFilter,
  } = useExampleListState(localizedExamples, examples)

  if (loading) return <ExampleListLoading />
  if (error) return <ExampleListError error={error} onRetry={() => void reload()} />

  return (
    <div className="fade-in">
      <PageHeader
        accent="learn"
        eyebrow={t('nav.learn.examples')}
        title={t('examples.title')}
        subtitle={t('examples.subtitle')}
      />
      <ExampleMetrics examples={localizedExamples} filteredCount={filteredExamples.length} />
      <ExampleListFilters
        activeFilters={activeFilters}
        clearAllFilters={clearAllFilters}
        filterState={filterState}
        hasActiveFilters={hasActiveFilters}
        onRemoveFilter={handleRemoveFilter}
        updateFilter={updateFilter}
      />
      <ExampleGrid
        examples={filteredExamples}
        hasActiveFilters={hasActiveFilters}
        onClearFilters={clearAllFilters}
      />
    </div>
  )
}

export default ExampleList
