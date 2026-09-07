import { Tag } from 'antd'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

import styles from './page.module.css'

export interface ActiveFilter {
  key: string
  label: string
}

export interface FilterBarProps {
  children: ReactNode
  activeFilters?: ActiveFilter[]
  onRemoveFilter?: (key: string) => void
  onClearAll?: () => void
  activeLabel?: string
  clearLabel?: string
  className?: string
}

export function FilterBar({
  children,
  activeFilters = [],
  onRemoveFilter,
  onClearAll,
  activeLabel,
  clearLabel,
  className,
}: FilterBarProps) {
  const { t } = useTranslation()
  const hasActive = activeFilters.length > 0
  const resolvedActiveLabel = activeLabel ?? t('filters.active')
  const resolvedClearLabel = clearLabel ?? t('filters.clear')

  return (
    <div className={[styles.filterBar, className].filter(Boolean).join(' ')}>
      <div className={styles.filterBarRow}>{children}</div>
      {hasActive && (
        <div className={styles.filterBarActive}>
          <span className={styles.filterBarLabel}>{resolvedActiveLabel}</span>
          {activeFilters.map((filter) => (
            <Tag
              key={filter.key}
              closable={!!onRemoveFilter}
              onClose={() => onRemoveFilter?.(filter.key)}
            >
              {filter.label}
            </Tag>
          ))}
          {onClearAll && (
            <Tag style={{ cursor: 'pointer' }} onClick={onClearAll}>
              {resolvedClearLabel}
            </Tag>
          )}
        </div>
      )}
    </div>
  )
}
