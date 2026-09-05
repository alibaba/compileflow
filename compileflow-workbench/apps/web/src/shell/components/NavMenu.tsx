import type { MenuProps } from 'antd'
import { Menu } from 'antd'
import { useTranslation } from 'react-i18next'
import { Link, useLocation } from 'react-router-dom'

import styles from './NavMenu.module.css'

import { isRouteWithin, MODULE_META, ROUTES } from '@/shared/constants'
import { useTheme } from '@/shared/contexts/ThemeContext'
import { isHubRootPath } from '@/shared/navigation/hubRoots'

interface NavMenuProps {
  mode?: 'horizontal' | 'vertical'
  onNavigate?: () => void
}

export function NavMenu({ mode = 'horizontal', onNavigate }: NavMenuProps) {
  const location = useLocation()
  const { t } = useTranslation()
  const { theme } = useTheme()
  const isHub = isHubRootPath(location.pathname)
  const menuItems: MenuProps['items'] = [
    {
      key: ROUTES.LEARN,
      label: (
        <Link className={styles.navLink} to={ROUTES.LEARN}>
          {t(MODULE_META.learn.labelKey)}
        </Link>
      ),
    },
    {
      key: ROUTES.BUILD,
      label: (
        <Link className={styles.navLink} to={ROUTES.BUILD}>
          {t(MODULE_META.build.labelKey)}
        </Link>
      ),
    },
    {
      key: ROUTES.OPERATE,
      label: (
        <Link className={styles.navLink} to={ROUTES.OPERATE}>
          {t(MODULE_META.operate.labelKey)}
        </Link>
      ),
    },
  ]

  const getActiveKey = () => {
    return [ROUTES.LEARN, ROUTES.BUILD, ROUTES.OPERATE].find((root) =>
      isRouteWithin(location.pathname, root)
    )
  }

  const modeClass = mode === 'horizontal' ? styles.navMenuHorizontal : styles.navMenuVertical
  const activeKey = getActiveKey()

  return (
    <Menu
      role="navigation"
      aria-label={t('navigation.mainMenu')}
      theme={theme === 'dark' ? 'dark' : 'light'}
      mode={mode}
      disabledOverflow
      selectedKeys={activeKey ? [activeKey] : []}
      items={menuItems}
      onClick={onNavigate}
      className={`navMenu ${styles.navMenu} ${modeClass} ${isHub ? styles.navMenuHub : ''}`}
    />
  )
}
