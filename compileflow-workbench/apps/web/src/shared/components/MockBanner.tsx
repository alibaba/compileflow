import { InfoCircleOutlined } from '@ant-design/icons'
import { useTranslation } from 'react-i18next'

import { APP_BUILD_CONFIG } from '../config/buildConfig'

import styles from './MockBanner.module.css'

interface MockBannerProps {
  style?: React.CSSProperties
  closable?: boolean
  onClose?: () => void
}

function MockBanner({ style, closable = false, onClose }: MockBannerProps) {
  const { t } = useTranslation()
  if (APP_BUILD_CONFIG.operateMode !== 'mock') {
    return null
  }

  return (
    <div className={styles.banner} style={style} role="status">
      <InfoCircleOutlined className={styles.icon} aria-hidden />
      <span className={styles.text}>
        {t('mockBanner.prefix')} <code>VITE_COMPILEFLOW_OPERATE_MODE=real</code>{' '}
        {t('mockBanner.suffix')}
      </span>
      {closable && (
        <button
          type="button"
          className={styles.dismiss}
          onClick={onClose}
          aria-label={t('mockBanner.dismiss')}
        >
          ×
        </button>
      )}
    </div>
  )
}

export default MockBanner
