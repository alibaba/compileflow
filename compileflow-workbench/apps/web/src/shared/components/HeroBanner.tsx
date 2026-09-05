import { type ReactNode } from 'react'

import pageStyles from '@/shared/components/page/page.module.css'
import { SiteHero, type SiteHeroAccent } from '@/shared/components/page/PageHeader'

type HeroVariant = 'learn' | 'build' | 'operate'

interface HeroAction {
  labelKey: string
  label: string
  onClick: () => void
  icon?: ReactNode
  type?: 'primary' | 'secondary' | 'tertiary'
}

export interface HeroBannerProps {
  variant: HeroVariant
  eyebrow?: string
  title: string
  subtitle: string
  primaryAction?: HeroAction
  secondaryAction?: HeroAction
  tertiaryAction?: HeroAction
  visual?: ReactNode
  showSilhouette?: boolean
  /** stage for brand home; simple for Build/Operate content hubs */
  layout?: 'stage' | 'split' | 'simple'
  /** Hub heroes default to cinematic stage; content below stays theme-aware. */
  tone?: 'light' | 'cinematic' | 'auto'
  className?: string
}

const variantToAccent: Record<HeroVariant, SiteHeroAccent> = {
  learn: 'learn',
  build: 'build',
  operate: 'operate',
}

export function HeroBanner({
  variant,
  eyebrow,
  title,
  subtitle,
  primaryAction,
  secondaryAction,
  tertiaryAction,
  visual,
  showSilhouette = false,
  layout,
  /** Hub heroes stay cinematic for material depth; content below remains theme-aware. */
  tone = 'cinematic',
  className,
}: HeroBannerProps) {
  const resolvedTone = tone === 'auto' ? 'cinematic' : tone
  const actions = [primaryAction, secondaryAction, tertiaryAction].filter(Boolean) as HeroAction[]
  const cinematic = resolvedTone === 'cinematic'

  return (
    <SiteHero
      title={title}
      subtitle={subtitle}
      eyebrow={eyebrow}
      accent={variantToAccent[variant]}
      className={className}
      visual={visual}
      showSilhouette={showSilhouette}
      layout={layout}
      tone={resolvedTone}
      actions={
        actions.length > 0 ? (
          <>
            {actions.map((action) => {
              const isPrimary = action.type === 'primary'
              return (
                <button
                  key={action.labelKey}
                  type="button"
                  onClick={action.onClick}
                  className={[
                    pageStyles.heroCta,
                    isPrimary ? pageStyles.heroCtaPrimary : pageStyles.heroCtaGhost,
                    cinematic ? pageStyles.heroCtaOnDark : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                >
                  {action.icon ? (
                    <span className={pageStyles.heroCtaIcon}>{action.icon}</span>
                  ) : null}
                  <span>{action.label}</span>
                </button>
              )
            })}
          </>
        ) : undefined
      }
    />
  )
}
