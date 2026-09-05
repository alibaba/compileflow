import type { ReactNode } from 'react'

import styles from './page.module.css'
import { type PageAccent, PageHeader, type PageHeaderProps } from './PageHeader'

export interface DataPageShellProps {
  title: string
  subtitle?: string
  eyebrow?: string
  actions?: ReactNode
  accent?: PageAccent
  filters?: ReactNode
  metrics?: ReactNode
  children: ReactNode
  headerCompact?: boolean
  className?: string
  contentClassName?: string
}

export function DataPageShell({
  title,
  subtitle,
  eyebrow,
  actions,
  accent = 'primary',
  filters,
  metrics,
  children,
  headerCompact = false,
  className,
  contentClassName,
}: DataPageShellProps) {
  const headerProps: PageHeaderProps = {
    title,
    subtitle,
    eyebrow,
    actions,
    accent,
    compact: headerCompact,
  }

  return (
    <div className={[styles.pageRoot, className].filter(Boolean).join(' ')}>
      <PageHeader {...headerProps} />
      {metrics}
      {filters}
      <div className={[styles.dataContent, contentClassName].filter(Boolean).join(' ')}>
        <div className={styles.dataContentInner}>
          <div className={styles.dataTableWrap}>{children}</div>
        </div>
      </div>
    </div>
  )
}
