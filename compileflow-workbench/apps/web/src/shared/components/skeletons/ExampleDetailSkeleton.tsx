import { useTranslation } from 'react-i18next'

import './SkeletonStyles.css'
import styles from './ExampleDetailSkeleton.module.css'

const ExampleDetailSkeleton: React.FC = () => {
  const { t } = useTranslation()
  return (
    <div role="status" aria-label={t('common.loading')}>
      <div className={styles.headerSection}>
        <div
          className={`skeleton ${styles.line}`}
          style={{ height: 32, width: 100, marginBottom: 'var(--spacing-4)' }}
        />
        <div
          className={`skeleton ${styles.line}`}
          style={{ height: 36, width: '40%', marginBottom: 'var(--spacing-4)' }}
        />

        <div className={styles.tagRow}>
          <div
            className="skeleton"
            style={{ height: 24, width: 80, borderRadius: 'var(--radius-sm)' }}
          />
          <div
            className="skeleton"
            style={{ height: 24, width: 100, borderRadius: 'var(--radius-sm)' }}
          />
          <div
            className="skeleton"
            style={{ height: 24, width: 60, borderRadius: 'var(--radius-sm)' }}
          />
        </div>

        <div className={styles.metaRow}>
          {Array.from({ length: 3 }).map((_, i) => (
            <div
              key={i}
              className="skeleton"
              style={{ height: 20, width: 120, borderRadius: 'var(--radius-sm)' }}
            />
          ))}
        </div>
      </div>

      <div className={styles.contentPanel}>
        <div className={styles.tabRow}>
          {Array.from({ length: 4 }).map((_, i) => (
            <div
              key={i}
              className="skeleton"
              style={{ height: 32, width: 100, borderRadius: 'var(--radius-sm)' }}
            />
          ))}
        </div>

        <div>
          <div
            className={`skeleton ${styles.line}`}
            style={{ height: 24, width: '30%', marginBottom: 'var(--spacing-4)' }}
          />

          {Array.from({ length: 5 }).map((_, i) => (
            <div
              key={i}
              className={`skeleton ${styles.line}`}
              style={{ height: 16, width: i === 4 ? '60%' : '100%' }}
            />
          ))}

          <div style={{ marginTop: 'var(--spacing-6)' }}>
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className={styles.bulletRow}>
                <div className={`skeleton ${styles.bullet}`} />
                <div
                  className="skeleton"
                  style={{ height: 14, width: '90%', borderRadius: 'var(--radius-sm)' }}
                />
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}

export default ExampleDetailSkeleton
