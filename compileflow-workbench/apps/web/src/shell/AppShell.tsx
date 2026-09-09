import { Layout, Spin } from 'antd'
import { Suspense, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Outlet, useLocation } from 'react-router-dom'

import AppBar from './AppBar'
import styles from './AppShell.module.css'

import ContextualSidebar from '@/shared/components/ContextualSidebar'
import MockBanner from '@/shared/components/MockBanner'
import { ROUTES } from '@/shared/constants'
import { useSidebar } from '@/shared/contexts/SidebarContext'
import { isHubRootPath } from '@/shared/navigation/hubRoots'
import type { AppBarProps } from '@/shell/AppBar'
import { useBreadcrumb } from '@/shell/useBreadcrumb'

const { Content } = Layout

function RouteContent() {
  const { t } = useTranslation()
  return (
    <Suspense
      fallback={
        <div
          role="status"
          aria-live="polite"
          aria-busy="true"
          style={{
            display: 'flex',
            flexDirection: 'column',
            justifyContent: 'center',
            alignItems: 'center',
            minHeight: '60vh',
          }}
        >
          <Spin size="large" aria-hidden="true" />
          <div style={{ marginTop: 16, color: 'var(--text-secondary)' }}>{t('common.loading')}</div>
        </div>
      }
    >
      <Outlet />
    </Suspense>
  )
}

function AppShell({ loadExamples, loadProcesses }: AppBarProps) {
  const { t } = useTranslation()
  const { isSidebarVisible, sidebarWidth } = useSidebar()
  const location = useLocation()
  const [showMockBanner, setShowMockBanner] = useState(true)
  useBreadcrumb()

  const isDesignerPage = location.pathname.startsWith(ROUTES.BUILD_DESIGNER)
  const isHubRoot = isHubRootPath(location.pathname)

  return (
    <Layout
      className={`${styles.shell} ${isHubRoot ? styles.shellHub : ''} ${isDesignerPage ? styles.shellDesigner : ''}`}
    >
      <a className={styles.skipLink} href="#main-content">
        {t('common.skipToContent')}
      </a>
      <AppBar loadExamples={loadExamples} loadProcesses={loadProcesses} />
      <Layout className={styles.innerLayout}>
        {!isDesignerPage && <ContextualSidebar />}
        <Content
          id="main-content"
          tabIndex={-1}
          className={`${styles.content} ${isDesignerPage ? styles.contentDesigner : styles.contentStandard} ${isSidebarVisible && !isDesignerPage ? styles.contentWithSidebar : ''}`}
          style={{
            marginLeft: isSidebarVisible && !isDesignerPage ? sidebarWidth : 0,
          }}
        >
          {isDesignerPage ? (
            <div className={styles.designerContainer}>
              {showMockBanner && (
                <MockBanner
                  closable
                  onClose={() => setShowMockBanner(false)}
                  style={{ borderRadius: 0, flexShrink: 0 }}
                />
              )}
              <RouteContent />
            </div>
          ) : (
            <div className={`${styles.pageContainer} ${isHubRoot ? styles.pageContainerHub : ''}`}>
              {showMockBanner && (!isHubRoot || location.pathname === ROUTES.OPERATE) && (
                <MockBanner closable onClose={() => setShowMockBanner(false)} />
              )}
              <RouteContent />
            </div>
          )}
        </Content>
      </Layout>
    </Layout>
  )
}

export default AppShell
