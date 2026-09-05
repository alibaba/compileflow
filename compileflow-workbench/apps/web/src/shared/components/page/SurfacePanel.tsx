import type { ReactNode } from 'react'

import styles from './SurfacePanel.module.css'

export interface SurfacePanelProps {
  title?: ReactNode
  icon?: ReactNode
  children: ReactNode
  className?: string
  /** Remove inner padding (e.g. for bordered tables) */
  flush?: boolean
}

export function SurfacePanel({
  title,
  icon,
  children,
  className,
  flush = false,
}: SurfacePanelProps) {
  const panelClass = [styles.panel, className].filter(Boolean).join(' ')
  const bodyClass = [styles.body, flush && styles.bodyFlush].filter(Boolean).join(' ')

  return (
    <section className={panelClass}>
      {title && (
        <header className={styles.header}>
          {icon && <span className={styles.headerIcon}>{icon}</span>}
          <h2 className={styles.title}>{title}</h2>
        </header>
      )}
      <div className={bodyClass}>{children}</div>
    </section>
  )
}
