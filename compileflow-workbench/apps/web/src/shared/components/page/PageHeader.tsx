import type { ReactNode } from 'react'

import styles from './page.module.css'
import { ProductShot } from './ProductShot'

export type PageAccent = 'learn' | 'build' | 'operate' | 'primary' | 'none'

const accentClassMap: Record<PageAccent, string> = {
  learn: styles.accentLearn,
  build: styles.accentBuild,
  operate: styles.accentOperate,
  primary: styles.accentPrimary,
  none: '',
}

export interface PageHeaderProps {
  title: string
  subtitle?: string
  eyebrow?: string
  actions?: ReactNode
  accent?: PageAccent
  compact?: boolean
  className?: string
}

export function PageHeader({
  title,
  subtitle,
  eyebrow,
  actions,
  accent = 'primary',
  compact = false,
  className,
}: PageHeaderProps) {
  return (
    <header
      className={[
        styles.pageHeader,
        accent !== 'none' ? accentClassMap[accent] : '',
        compact ? styles.pageHeaderCompact : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.pageHeaderContent}>
        {eyebrow && <span className={styles.pageHeaderEyebrow}>{eyebrow}</span>}
        <h1 className={styles.pageHeaderTitle}>{title}</h1>
        {subtitle && <p className={styles.pageHeaderSubtitle}>{subtitle}</p>}
      </div>
      {actions && <div className={styles.pageHeaderActions}>{actions}</div>}
    </header>
  )
}

export type SiteHeroAccent = 'learn' | 'build' | 'operate' | 'primary'

const heroAccentMap: Record<SiteHeroAccent, string> = {
  learn: styles.heroAccentLearn,
  build: styles.heroAccentBuild,
  operate: styles.heroAccentOperate,
  primary: styles.heroAccentPrimary,
}

const heroLayoutClassMap: Record<NonNullable<SiteHeroProps['layout']>, string> = {
  stage: styles.siteHeroStage,
  split: styles.siteHeroSplit,
  simple: styles.siteHeroSimple,
}

const heroToneClassMap: Record<NonNullable<SiteHeroProps['tone']>, string> = {
  cinematic: styles.siteHeroCinematic,
  light: styles.siteHeroLight,
}

export interface SiteHeroProps {
  title: string
  subtitle?: string
  eyebrow?: string
  actions?: ReactNode
  visual?: ReactNode
  showSilhouette?: boolean
  /** stage = copy over full-bleed product; split = side-by-side; simple = copy only */
  layout?: 'stage' | 'split' | 'simple'
  tone?: 'light' | 'cinematic'
  accent?: SiteHeroAccent
  className?: string
}

export function SiteHero({
  title,
  subtitle,
  eyebrow,
  actions,
  visual,
  showSilhouette = false,
  layout,
  tone = 'cinematic',
  accent = 'primary',
  className,
}: SiteHeroProps) {
  const visualNode = visual ?? (showSilhouette ? <ProductShot /> : null)
  const resolvedLayout = layout ?? (visualNode ? 'stage' : 'simple')

  return (
    <section
      className={[
        styles.siteHero,
        heroToneClassMap[tone],
        heroLayoutClassMap[resolvedLayout],
        heroAccentMap[accent],
        className,
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.siteHeroGlow} aria-hidden="true" />
      <div className={styles.siteHeroInner}>
        <div className={styles.siteHeroCopy}>
          {eyebrow && <span className={styles.siteHeroEyebrow}>{eyebrow}</span>}
          <h1 className={styles.siteHeroTitle}>{title}</h1>
          {subtitle && <p className={styles.siteHeroSubtitle}>{subtitle}</p>}
          {actions && <div className={styles.siteHeroActions}>{actions}</div>}
        </div>
        {resolvedLayout === 'split' && visualNode ? (
          <div className={styles.siteHeroVisual}>{visualNode}</div>
        ) : null}
      </div>
      {resolvedLayout === 'stage' && visualNode ? (
        <div className={styles.siteHeroStageVisual}>{visualNode}</div>
      ) : null}
    </section>
  )
}
