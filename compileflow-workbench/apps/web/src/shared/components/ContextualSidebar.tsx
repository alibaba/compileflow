import {
  AppstoreOutlined,
  BookOutlined,
  CloudServerOutlined,
  DatabaseOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  MonitorOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { Button, Menu } from 'antd'
import type { TFunction } from 'i18next'
import { memo, useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import styles from './ContextualSidebar.module.css'

import {
  createLearnExamplesPath,
  isRouteWithin,
  LEARN_CATEGORIES,
  ROUTES,
} from '@/shared/constants'
import { useSidebar } from '@/shared/contexts/SidebarContext'
import { isHubRootPath } from '@/shared/navigation/hubRoots'

type SidebarDomain = 'learn' | 'operate'
type MenuItems = NonNullable<MenuProps['items']>
type MenuClickInfo = Parameters<NonNullable<MenuProps['onClick']>>[0]
const detectDomain = (pathname: string): SidebarDomain | null => {
  if (isHubRootPath(pathname)) return null
  if (isRouteWithin(pathname, ROUTES.LEARN)) return 'learn'
  if (isRouteWithin(pathname, ROUTES.OPERATE)) return 'operate'
  return null
}

const menuLink = (path: string, label: string) => <Link to={path}>{label}</Link>

const buildLearnMenuItems = (t: TFunction): MenuItems => [
  {
    key: 'examples',
    icon: <AppstoreOutlined />,
    label: t('sidebar.exampleCategories'),
    children: [
      {
        key: createLearnExamplesPath({ category: LEARN_CATEGORIES.BEGINNER }),
        label: menuLink(
          createLearnExamplesPath({ category: LEARN_CATEGORIES.BEGINNER }),
          t('sidebar.category.basics')
        ),
      },
      {
        key: createLearnExamplesPath({ category: LEARN_CATEGORIES.BUSINESS }),
        label: menuLink(
          createLearnExamplesPath({ category: LEARN_CATEGORIES.BUSINESS }),
          t('sidebar.category.business')
        ),
      },
      {
        key: createLearnExamplesPath({ category: LEARN_CATEGORIES.ADVANCED }),
        label: menuLink(
          createLearnExamplesPath({ category: LEARN_CATEGORIES.ADVANCED }),
          t('sidebar.category.advanced')
        ),
      },
    ],
  },
  {
    key: ROUTES.LEARN_EXAMPLES,
    icon: <BookOutlined />,
    label: menuLink(ROUTES.LEARN_EXAMPLES, t('sidebar.tutorials')),
  },
]

const buildOperateMenuItems = (t: TFunction): MenuItems => [
  {
    key: ROUTES.OPERATE_PROCESSES,
    icon: <AppstoreOutlined />,
    label: menuLink(ROUTES.OPERATE_PROCESSES, t('nav.ops.processes')),
  },
  {
    key: ROUTES.OPERATE_DEPLOYMENTS,
    icon: <CloudServerOutlined />,
    label: menuLink(ROUTES.OPERATE_DEPLOYMENTS, t('nav.ops.deployment')),
  },
  {
    key: ROUTES.OPERATE_MONITORING,
    icon: <MonitorOutlined />,
    label: menuLink(ROUTES.OPERATE_MONITORING, t('nav.ops.monitoring')),
  },
  {
    key: ROUTES.OPERATE_LOGS,
    icon: <DatabaseOutlined />,
    label: menuLink(ROUTES.OPERATE_LOGS, t('nav.ops.logs')),
  },
]

const buildMenuItems = (domain: SidebarDomain, t: TFunction): MenuItems => {
  if (domain === 'learn') return buildLearnMenuItems(t)
  return buildOperateMenuItems(t)
}

const getSelectedKey = (domain: SidebarDomain, pathname: string, search: string): string => {
  if (domain === 'learn') {
    const requestedPath = `${pathname}${search}`
    const categoryPath = Object.values(LEARN_CATEGORIES)
      .map((category) => createLearnExamplesPath({ category }))
      .find((path) => path === requestedPath)
    return categoryPath ?? ROUTES.LEARN_EXAMPLES
  }

  if (
    isRouteWithin(pathname, ROUTES.OPERATE_DEPLOYMENTS) ||
    pathname === ROUTES.OPERATE_DEPLOY_WIZARD
  ) {
    return ROUTES.OPERATE_DEPLOYMENTS
  }
  if (isRouteWithin(pathname, ROUTES.OPERATE_PROCESSES)) return ROUTES.OPERATE_PROCESSES
  if (isRouteWithin(pathname, ROUTES.OPERATE_MONITORING)) return ROUTES.OPERATE_MONITORING
  if (isRouteWithin(pathname, ROUTES.OPERATE_LOGS)) return ROUTES.OPERATE_LOGS
  return ''
}

function ContextualSidebarComponent() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const { isCollapsed, sidebarWidth, setSidebarVisible, toggleCollapse } = useSidebar()
  const [isMobile, setIsMobile] = useState(() =>
    typeof window === 'undefined' ? false : window.innerWidth <= 767
  )
  const [isMobileOpen, setIsMobileOpen] = useState(false)

  const domain = useMemo(() => detectDomain(location.pathname), [location.pathname])

  useEffect(() => {
    setSidebarVisible(domain !== null)
  }, [domain, setSidebarVisible])

  useEffect(() => {
    const handleResize = () => {
      const nextIsMobile = window.innerWidth <= 767
      setIsMobile(nextIsMobile)
      if (!nextIsMobile) setIsMobileOpen(false)
    }
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  useEffect(() => {
    setIsMobileOpen(false)
  }, [location.pathname, location.search])

  const menuItems = useMemo(() => (domain ? buildMenuItems(domain, t) : []), [domain, t])

  const handleMenuClick = useCallback(
    ({ key, domEvent }: MenuClickInfo) => {
      setIsMobileOpen(false)
      if (!(domEvent.target as HTMLElement).closest('a') && key.startsWith('/')) {
        void navigate(key)
      }
    },
    [navigate]
  )

  if (!domain) return null

  return (
    <>
      <Button
        type="default"
        size="small"
        icon={<MenuUnfoldOutlined />}
        onClick={() => setIsMobileOpen(true)}
        aria-label={t('navigation.openSidebar')}
        aria-expanded={isMobileOpen}
        className={styles.mobileOpenButton}
      />
      {isMobileOpen && (
        <button
          type="button"
          className={styles.mobileMask}
          onClick={() => setIsMobileOpen(false)}
          aria-label={t('navigation.closeSidebar')}
        />
      )}
      <div
        className={`${styles.sidebar} ${isMobileOpen ? styles.sidebarMobileOpen : ''}`}
        data-domain={domain}
        style={{ width: isMobile ? 280 : sidebarWidth }}
        aria-hidden={isMobile && !isMobileOpen}
        inert={isMobile && !isMobileOpen ? true : undefined}
      >
        <div className={styles.toolbar}>
          <Button
            type="text"
            size="small"
            icon={isMobile || !isCollapsed ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />}
            onClick={isMobile ? () => setIsMobileOpen(false) : toggleCollapse}
            aria-label={t(
              isMobile
                ? 'navigation.closeSidebar'
                : isCollapsed
                  ? 'navigation.expandSidebar'
                  : 'navigation.collapseSidebar'
            )}
            className={styles.collapseBtn}
          />
        </div>

        <Menu
          mode="inline"
          selectedKeys={[getSelectedKey(domain, location.pathname, location.search)]}
          items={menuItems}
          inlineCollapsed={isMobile ? false : isCollapsed}
          className={styles.menu}
          onClick={handleMenuClick}
        />
      </div>
    </>
  )
}

export default memo(ContextualSidebarComponent)
