import type { HTMLAttributes, ReactNode } from 'react'

import styles from './SemanticList.module.css'

interface SemanticListProps extends HTMLAttributes<HTMLUListElement> {
  children: ReactNode
}

interface SemanticListItemProps extends Omit<HTMLAttributes<HTMLLIElement>, 'onClick'> {
  activateLabel?: string
  actions?: ReactNode
  children: ReactNode
  onActivate?: () => void
}

interface SemanticListMetaProps {
  avatar?: ReactNode
  description?: ReactNode
  title: ReactNode
}

function mergeClassNames(base: string, extra?: string) {
  return extra ? `${base} ${extra}` : base
}

export function SemanticList({ children, className, ...props }: SemanticListProps) {
  return (
    <ul className={mergeClassNames(styles.list, className)} {...props}>
      {children}
    </ul>
  )
}

export function SemanticListItem({
  activateLabel,
  actions,
  children,
  className,
  onActivate,
  ...props
}: SemanticListItemProps) {
  const content = (
    <>
      <div className={styles.body}>{children}</div>
      {actions != null && <div className={styles.actions}>{actions}</div>}
    </>
  )

  return (
    <li className={mergeClassNames(styles.item, className)} {...props}>
      {onActivate ? (
        <button
          type="button"
          className={styles.interactiveRow}
          onClick={onActivate}
          aria-label={activateLabel}
        >
          {content}
        </button>
      ) : (
        <div className={styles.row}>{content}</div>
      )}
    </li>
  )
}

export function SemanticListMeta({ avatar, description, title }: SemanticListMetaProps) {
  return (
    <div className={styles.meta}>
      {avatar != null && <div className={styles.avatar}>{avatar}</div>}
      <div className={styles.metaContent}>
        <div className={styles.title}>{title}</div>
        {description != null && <div className={styles.description}>{description}</div>}
      </div>
    </div>
  )
}
