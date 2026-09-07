import { GlobalOutlined, LinkOutlined, SkinOutlined } from '@ant-design/icons'
import { Descriptions, Select, Typography } from 'antd'
import { useTranslation } from 'react-i18next'

import styles from './Settings.module.css'

import { DataPageShell, SurfacePanel } from '@/shared/components/page'
import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import { useLanguage } from '@/shared/contexts/LanguageContext'
import { useTheme } from '@/shared/contexts/ThemeContext'
import { usePageTitle } from '@/shared/hooks/usePageTitle'

const { Link } = Typography

function Settings() {
  usePageTitle('pageTitle.settings')
  const { t } = useTranslation()
  const { language, setLanguage } = useLanguage()
  const { setTheme, theme } = useTheme()

  return (
    <DataPageShell
      title={t('settings.title')}
      subtitle={t('settings.subtitle')}
      eyebrow={t('settings.eyebrow')}
      accent="primary"
    >
      <div className={styles.stack}>
        <SurfacePanel title={t('settings.preferences')}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item
              label={
                <span className={styles.label}>
                  <GlobalOutlined />
                  {t('settings.language')}
                </span>
              }
            >
              <Select
                aria-label={t('settings.language')}
                value={language}
                onChange={setLanguage}
                className={styles.languageSelect}
                options={[
                  { value: 'zh', label: '中文' },
                  { value: 'en', label: 'English' },
                ]}
              />
            </Descriptions.Item>
            <Descriptions.Item
              label={
                <span className={styles.label}>
                  <SkinOutlined />
                  {t('settings.theme')}
                </span>
              }
            >
              <Select
                aria-label={t('settings.theme')}
                value={theme}
                onChange={setTheme}
                className={styles.languageSelect}
                options={[
                  { value: 'light', label: t('settings.theme.light') },
                  { value: 'dark', label: t('settings.theme.dark') },
                ]}
              />
            </Descriptions.Item>
          </Descriptions>
        </SurfacePanel>

        <SurfacePanel title={t('settings.build')}>
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label={t('settings.operateMode')}>
              <code className={styles.code}>{APP_BUILD_CONFIG.operateMode}</code>
            </Descriptions.Item>
            <Descriptions.Item label={t('settings.buildMode')}>
              <code className={styles.code}>{APP_BUILD_CONFIG.buildMode}</code>
            </Descriptions.Item>
            <Descriptions.Item label={t('settings.appVersion')}>
              {APP_BUILD_CONFIG.appVersion}
            </Descriptions.Item>
          </Descriptions>
        </SurfacePanel>

        <SurfacePanel title={t('settings.resources')}>
          <Link
            href="https://github.com/alibaba/compileflow"
            target="_blank"
            rel="noopener noreferrer"
          >
            <LinkOutlined /> {t('navigation.github')}
          </Link>
        </SurfacePanel>
      </div>
    </DataPageShell>
  )
}

export default Settings
