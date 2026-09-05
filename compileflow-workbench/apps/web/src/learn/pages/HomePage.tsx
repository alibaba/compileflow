import { ArrowRightOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { HeroBanner } from '@/shared/components/HeroBanner'
import { HubFeature, HubFeatureRail, HubSection, HubSurface } from '@/shared/components/page'
import { createLearnExamplesPath, ROUTES } from '@/shared/constants'
import { usePageTitle } from '@/shared/hooks/usePageTitle'

interface WorkspaceItem {
  key: string
  index: string
  accent: 'learn' | 'build' | 'operate'
  titleKey: string
  descKey: string
  actionKey: string
  onClick: () => void
}

const HomePage = () => {
  usePageTitle('pageTitle.home')
  const navigate = useNavigate()
  const { t } = useTranslation()

  const workspaces: WorkspaceItem[] = [
    {
      key: 'learn',
      index: '01',
      accent: 'learn',
      titleKey: 'feature.learn.title',
      descKey: 'feature.learn.desc',
      actionKey: 'feature.learn.action',
      onClick: () => navigate(createLearnExamplesPath()),
    },
    {
      key: 'build',
      index: '02',
      accent: 'build',
      titleKey: 'feature.workspace.title',
      descKey: 'feature.workspace.desc',
      actionKey: 'feature.workspace.action',
      onClick: () => navigate(ROUTES.BUILD),
    },
    {
      key: 'operate',
      index: '03',
      accent: 'operate',
      titleKey: 'feature.ops.title',
      descKey: 'feature.ops.desc',
      actionKey: 'feature.ops.action',
      onClick: () => navigate(ROUTES.OPERATE),
    },
  ]

  return (
    <HubSurface className="fade-in">
      <HeroBanner
        variant="learn"
        layout="split"
        tone="cinematic"
        eyebrow={t('home.eyebrow')}
        title={t('home.title')}
        subtitle={t('home.subtitle')}
        showSilhouette
        primaryAction={{
          labelKey: 'home.start',
          label: t('home.start'),
          onClick: () => navigate(ROUTES.BUILD),
          type: 'primary',
        }}
        secondaryAction={{
          labelKey: 'home.docs',
          label: t('home.docs'),
          onClick: () => navigate(createLearnExamplesPath()),
          type: 'secondary',
        }}
      />

      <HubSection title={t('home.features')} description={t('home.featuresDesc')}>
        <HubFeatureRail>
          {workspaces.map((item) => (
            <HubFeature
              key={item.key}
              index={item.index}
              accent={item.accent}
              title={t(item.titleKey)}
              description={t(item.descKey)}
              action={
                <>
                  {t(item.actionKey)} <ArrowRightOutlined />
                </>
              }
              onClick={item.onClick}
            />
          ))}
        </HubFeatureRail>
      </HubSection>
    </HubSurface>
  )
}

export default HomePage
