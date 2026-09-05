import type { ReactNode } from 'react'

import styles from './ListPanel.module.css'

export interface ListPanelProps {
  /** Panel heading text */
  title: ReactNode
  /** Optional leading icon in the header */
  icon?: ReactNode
  /** Optional trailing action (e.g. view-all link) */
  action?: ReactNode
  children: ReactNode
  className?: string
  /** Taller min-height for workspace-style panels */
  tall?: boolean
  /** Hover highlight on list rows */
  interactive?: boolean
}

export function ListPanel({
  title,
  icon,
  action,
  children,
  className,
  tall = false,
  interactive = false,
}: ListPanelProps) {
  const panelClass = [styles.panel, tall && styles.panelTall, className].filter(Boolean).join(' ')
  const bodyClass = [styles.body, interactive && styles.bodyInteractive].filter(Boolean).join(' ')

  return (
    <section className={panelClass}>
      <header className={styles.header}>
        {icon && <span className={styles.headerIcon}>{icon}</span>}
        <h2 className={styles.title}>{title}</h2>
        {action && <div className={styles.action}>{action}</div>}
      </header>
      <div className={bodyClass}>{children}</div>
    </section>
  )
}
