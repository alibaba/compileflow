import { Button } from 'antd'
import type { ReactNode } from 'react'

import styles from './page.module.css'

export interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: string
  actionLabel?: string
  onAction?: () => void
  className?: string
}

export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  onAction,
  className,
}: EmptyStateProps) {
  return (
    <div className={[styles.emptyState, className].filter(Boolean).join(' ')}>
      {icon && <div className={styles.emptyStateIcon}>{icon}</div>}
      <h3 className={styles.emptyStateTitle}>{title}</h3>
      {description && <p className={styles.emptyStateDescription}>{description}</p>}
      {actionLabel && onAction && (
        <Button type="primary" onClick={onAction}>
          {actionLabel}
        </Button>
      )}
    </div>
  )
}
