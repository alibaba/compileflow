import { GithubOutlined, GlobalOutlined, MenuOutlined, ThunderboltFilled } from '@ant-design/icons'
import { Button, Drawer, Layout } from 'antd'
import type { CSSProperties } from 'react'
import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'

import styles from './AppBar.module.css'
import { AppMenu } from './components/AppMenu'
import { DeferredGlobalSearch } from './components/DeferredGlobalSearch'
import type { SearchableProcess } from './components/GlobalSearch'
import { NavMenu } from './components/NavMenu'

import ThemeToggle from '@/shared/components/ThemeToggle'
import { isRouteWithin, MODULE_META, ROUTES } from '@/shared/constants'
import { useLanguage } from '@/shared/contexts/LanguageContext'
import type { Example } from '@/shared/contracts'
import { useMediaQuery } from '@/shared/hooks/useMediaQuery'
import { isHubRootPath } from '@/shared/navigation/hubRoots'
import Breadcrumb from '@/shell/components/Breadcrumb'

const { Header } = Layout

const NAVBAR_HEIGHT = 52
const BREADCRUMB_HEIGHT = 32
const GITHUB_URL = 'https://github.com/alibaba/compileflow'

type ModuleKey = keyof typeof MODULE_META
type ModuleMeta = (typeof MODULE_META)[ModuleKey]

const MODULE_ROOTS: Record<ModuleKey, string> = {
  learn: ROUTES.LEARN,
  build: ROUTES.BUILD,
  operate: ROUTES.OPERATE,
  system: ROUTES.SETTINGS,
}

export interface AppBarProps {
  loadExamples: () => Promise<Example[]>
  loadProcesses: () => Promise<SearchableProcess[]>
}

function getActiveModule(pathname: string): ModuleKey | undefined {
  return (Object.keys(MODULE_ROOTS) as ModuleKey[]).find((key) => {
    const root = MODULE_ROOTS[key]
    return isRouteWithin(pathname, root)
  })
}

function useHeaderHeight(isMobile: boolean, hideBreadcrumb: boolean): void {
  useEffect(() => {
    const breadcrumbHeight = !isMobile && !hideBreadcrumb ? BREADCRUMB_HEIGHT : 0
    const totalHeight = NAVBAR_HEIGHT + breadcrumbHeight
    document.documentElement.style.setProperty('--header-height', `${totalHeight}px`)
  }, [isMobile, hideBreadcrumb])
}

function moduleBadgeStyle(moduleMeta: ModuleMeta): CSSProperties {
  return {
    background: `color-mix(in srgb, ${moduleMeta.color} 9%, transparent)`,
    border: `1px solid color-mix(in srgb, ${moduleMeta.color} 18%, transparent)`,
    color: moduleMeta.color,
  }
}

function BrandLink({ isMobile }: { isMobile: boolean }) {
  const { t } = useTranslation()
  return (
    <Link
      to={ROUTES.LEARN}
      aria-label={t('navigation.home')}
      className={`${styles.logoButton} ${isMobile ? styles.logoButtonMobile : styles.logoButtonDesktop}`}
    >
      <div className={styles.logoIcon}>
        <ThunderboltFilled style={{ color: '#fff', fontSize: 16 }} />
      </div>
      {!isMobile && (
        <span className={styles.brandText}>
          CompileFlow
          <span className={styles.brandSuffix}>Workbench</span>
        </span>
      )}
      {isMobile && <span className={styles.mobileBrand}>CompileFlow</span>}
    </Link>
  )
}

function ActiveModuleBadge({
  moduleMeta,
  isMobile,
}: {
  moduleMeta: ModuleMeta | null
  isMobile: boolean
}) {
  const { t } = useTranslation()
  if (isMobile || !moduleMeta) return null
  return (
    <div className={styles.moduleBadge} style={moduleBadgeStyle(moduleMeta)}>
      {t(moduleMeta.labelKey)}
    </div>
  )
}

function DesktopNavExtras({ isMobile }: { isMobile: boolean }) {
  return (
    <div
      style={{ display: isMobile ? 'none' : 'contents' }}
      aria-hidden={isMobile}
      inert={isMobile ? true : undefined}
    >
      <NavMenu />
    </div>
  )
}

function DesktopUtilityButtons({
  isMobile,
  language,
  onLanguageToggle,
}: {
  isMobile: boolean
  language: string
  onLanguageToggle: () => void
}) {
  const { t } = useTranslation()
  return (
    <div
      style={{ display: isMobile ? 'none' : 'contents' }}
      aria-hidden={isMobile}
      inert={isMobile ? true : undefined}
    >
      <Button
        href={GITHUB_URL}
        target="_blank"
        rel="noopener noreferrer"
        type="text"
        size="small"
        icon={<GithubOutlined />}
        className={styles.iconButton}
        aria-label={t('navigation.github')}
      />
      <Button
        type="text"
        size="small"
        icon={<GlobalOutlined />}
        onClick={onLanguageToggle}
        className={styles.iconButton}
        aria-label={t(
          language === 'zh' ? 'navigation.toggleToEnglish' : 'navigation.toggleToChinese'
        )}
      >
        {language === 'zh' ? 'EN' : '中'}
      </Button>
    </div>
  )
}

function UtilityArea({ isMobile, onOpenDrawer }: { isMobile: boolean; onOpenDrawer: () => void }) {
  const { t } = useTranslation()
  return (
    <>
      <div
        style={{ display: isMobile ? 'contents' : 'none' }}
        aria-hidden={!isMobile}
        inert={isMobile ? undefined : true}
      >
        <Button
          type="text"
          icon={<MenuOutlined />}
          onClick={onOpenDrawer}
          aria-label={t('navigation.openMenu')}
        />
      </div>
      <div
        style={{ display: isMobile ? 'none' : 'contents' }}
        aria-hidden={isMobile}
        inert={isMobile ? true : undefined}
      >
        <div className={styles.headerDivider} />
        <AppMenu />
      </div>
    </>
  )
}

function MobileDrawer({
  loadExamples,
  loadProcesses,
  open,
  onClose,
}: {
  loadExamples: () => Promise<Example[]>
  loadProcesses: () => Promise<SearchableProcess[]>
  open: boolean
  onClose: () => void
}) {
  const { t } = useTranslation()
  return (
    <Drawer
      title={
        <div className={styles.drawerTitle}>
          <div className={styles.drawerLogoIcon}>
            <ThunderboltFilled style={{ color: '#fff', fontSize: 12 }} />
          </div>
          {t('navigation.drawerTitle')}
        </div>
      }
      placement="right"
      open={open}
      onClose={onClose}
      size="default"
      styles={{ body: { padding: 0 } }}
    >
      <div className={styles.drawerSearch}>
        <DeferredGlobalSearch
          compact
          loadExamples={loadExamples}
          loadProcesses={loadProcesses}
          onOpen={onClose}
        />
      </div>
      <NavMenu mode="vertical" onNavigate={onClose} />
      <div className={styles.drawerSection}>
        <AppMenu />
      </div>
      <div className={styles.drawerSection}>
        <Button
          href={GITHUB_URL}
          target="_blank"
          rel="noopener noreferrer"
          type="text"
          icon={<GithubOutlined />}
          block
          className={styles.drawerGithubButton}
        >
          {t('navigation.github')}
        </Button>
      </div>
    </Drawer>
  )
}

function AppBar({ loadExamples, loadProcesses }: AppBarProps) {
  const location = useLocation()
  const { t } = useTranslation()
  const isMobile = useMediaQuery('(max-width: 575px)')
  const [drawerOpen, setDrawerOpen] = useState(false)

  const { language, setLanguage } = useLanguage()
  const activeModule = getActiveModule(location.pathname)
  const moduleMeta = activeModule ? MODULE_META[activeModule] : null
  const isDesignerPage = location.pathname.startsWith(ROUTES.BUILD_DESIGNER)
  const isHubRoot = isHubRootPath(location.pathname)
  const hideBreadcrumb = isDesignerPage || isHubRoot

  useHeaderHeight(isMobile, hideBreadcrumb)

  useEffect(() => {
    setDrawerOpen(false)
  }, [isMobile, location.pathname, location.search])

  const handleLanguageToggle = () => {
    setLanguage(language === 'zh' ? 'en' : 'zh')
  }

  return (
    <Header
      role="banner"
      aria-label={t('navigation.main')}
      className={`${styles.header} ${isHubRoot ? styles.headerHub : ''}`}
      data-surface={isHubRoot ? 'hub' : 'app'}
    >
      <div
        className={`${styles.navbarRow} ${isMobile ? styles.navbarRowMobile : styles.navbarRowDesktop}`}
      >
        <BrandLink isMobile={isMobile} />
        {!isHubRoot && <ActiveModuleBadge isMobile={isMobile} moduleMeta={moduleMeta} />}
        <DesktopNavExtras isMobile={isMobile} />
        {!isMobile && (
          <DeferredGlobalSearch loadExamples={loadExamples} loadProcesses={loadProcesses} />
        )}
        <div
          className={`${styles.rightArea} ${isMobile ? styles.rightAreaMobile : styles.rightAreaDesktop}`}
        >
          <DesktopUtilityButtons
            isMobile={isMobile}
            language={language}
            onLanguageToggle={handleLanguageToggle}
          />
          <ThemeToggle />
          <UtilityArea isMobile={isMobile} onOpenDrawer={() => setDrawerOpen(true)} />
        </div>
      </div>

      {!isMobile && !hideBreadcrumb && (
        <div className={styles.breadcrumbBar}>
          <Breadcrumb />
        </div>
      )}

      <MobileDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        loadExamples={loadExamples}
        loadProcesses={loadProcesses}
      />
    </Header>
  )
}

export default AppBar
