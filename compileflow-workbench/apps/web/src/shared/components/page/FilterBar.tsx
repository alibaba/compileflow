import { Tag } from 'antd'
import type { ReactNode } from 'react'

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
  activeLabel = 'Active filters',
  clearLabel = 'Clear all',
  className,
}: FilterBarProps) {
  const hasActive = activeFilters.length > 0

  return (
    <div className={[styles.filterBar, className].filter(Boolean).join(' ')}>
      <div className={styles.filterBarRow}>{children}</div>
      {hasActive && (
        <div className={styles.filterBarActive}>
          <span className={styles.filterBarLabel}>{activeLabel}</span>
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
              {clearLabel}
            </Tag>
          )}
        </div>
      )}
    </div>
  )
}
