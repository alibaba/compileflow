import { HomeOutlined } from '@ant-design/icons'
import { Breadcrumb as AntBreadcrumb } from 'antd'
import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { useSelector } from 'react-redux'
import { Link } from 'react-router-dom'

import styles from './Breadcrumb.module.css'

import type { RootState } from '@/app/store'
import { ROUTES } from '@/shared/constants'

function BreadcrumbComponent() {
  const { t } = useTranslation()
  const breadcrumbs = useSelector((state: RootState) => state.navigation.breadcrumbs)

  if (breadcrumbs.length === 0) {
    return null
  }

  const items = [
    {
      title: (
        <Link to={ROUTES.LEARN} aria-label={t('nav.home')}>
          <HomeOutlined />
        </Link>
      ),
    },
    ...breadcrumbs.map((crumb, index) => ({
      title:
        index < breadcrumbs.length - 1 ? (
          <Link to={crumb.path}>{t(crumb.labelKey)}</Link>
        ) : (
          t(crumb.labelKey)
        ),
    })),
  ]

  return (
    <AntBreadcrumb
      className={styles.breadcrumb}
      items={items}
      aria-label={t('common.breadcrumb')}
    />
  )
}

export default memo(BreadcrumbComponent)
