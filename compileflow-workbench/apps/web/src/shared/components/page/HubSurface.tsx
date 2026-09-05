import { Children, isValidElement, type ReactNode } from 'react'

import styles from './HubSurface.module.css'

function isDecorativeLead(node: ReactNode): boolean {
  if (!isValidElement(node)) return true
  if (node.type === 'input') return true
  const style = (node.props as { style?: { display?: string } }).style
  if (style?.display === 'none') return true
  return false
}

export function HubSurface({ children, className }: { children: ReactNode; className?: string }) {
  const items = Children.toArray(children)
  const heroIndex = items.findIndex((node) => !isDecorativeLead(node))
  const lead = heroIndex > 0 ? items.slice(0, heroIndex) : []
  const hero = heroIndex >= 0 ? items[heroIndex] : null
  const deck = heroIndex >= 0 ? items.slice(heroIndex + 1) : items

  return (
    <div className={[styles.surface, className].filter(Boolean).join(' ')} data-hub-surface="true">
      {lead}
      {hero}
      {deck.length > 0 ? <div className={styles.deck}>{deck}</div> : null}
    </div>
  )
}

export function HubSection({
  title,
  description,
  children,
  className,
}: {
  title?: string
  description?: string
  children: ReactNode
  className?: string
}) {
  return (
    <section className={[styles.section, className].filter(Boolean).join(' ')}>
      {(title || description) && (
        <div className={styles.sectionHeader}>
          {title && <h2 className={styles.sectionTitle}>{title}</h2>}
          {description && <p className={styles.sectionDesc}>{description}</p>}
        </div>
      )}
      {children}
    </section>
  )
}

export type HubRowAccent = 'learn' | 'build' | 'operate' | 'primary' | 'success' | 'warning'

const accentMap: Record<HubRowAccent, string> = {
  learn: styles.accentLearn,
  build: styles.accentBuild,
  operate: styles.accentOperate,
  primary: styles.accentPrimary,
  success: styles.accentSuccess,
  warning: styles.accentWarning,
}

export function HubRowList({ children }: { children: ReactNode }) {
  return (
    <ul className={styles.rowList}>
      {Children.map(children, (child) => (
        <li className={styles.rowListItem}>{child}</li>
      ))}
    </ul>
  )
}

export function HubFeatureRail({ children }: { children: ReactNode }) {
  return (
    <ul className={styles.featureRail}>
      {Children.map(children, (child) => (
        <li className={styles.featureRailItem}>{child}</li>
      ))}
    </ul>
  )
}

export function HubFeature({
  index,
  title,
  description,
  action,
  accent = 'primary',
  onClick,
}: {
  index: string
  title: string
  description: string
  action: ReactNode
  accent?: HubRowAccent
  onClick: () => void
}) {
  return (
    <button type="button" className={`${styles.feature} ${accentMap[accent]}`} onClick={onClick}>
      <span className={styles.featureIndex}>{index}</span>
      <span className={styles.featureTitle}>{title}</span>
      <span className={styles.featureDesc}>{description}</span>
      <span className={styles.featureAction}>{action}</span>
    </button>
  )
}

export function HubRow({
  index,
  title,
  description,
  action,
  accent = 'primary',
  onClick,
  ariaLabel,
}: {
  index: string
  title: string
  description: string
  action: ReactNode
  accent?: HubRowAccent
  onClick: () => void
  ariaLabel?: string
}) {
  return (
    <button
      type="button"
      className={`${styles.row} ${accentMap[accent]}`}
      onClick={onClick}
      aria-label={ariaLabel}
    >
      <span className={styles.rowIndex}>{index}</span>
      <span className={styles.rowCopy}>
        <span className={styles.rowTitle}>{title}</span>
        <span className={styles.rowDesc}>{description}</span>
      </span>
      <span className={styles.rowAction}>{action}</span>
    </button>
  )
}

export function HubPanel({ children, className }: { children: ReactNode; className?: string }) {
  return <div className={[styles.panel, className].filter(Boolean).join(' ')}>{children}</div>
}

export function HubMetrics({ children }: { children: ReactNode }) {
  return <div className={styles.metrics}>{children}</div>
}
