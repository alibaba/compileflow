import { useTranslation } from 'react-i18next'

import './SkeletonStyles.css'
import styles from './ExampleCardSkeleton.module.css'

interface ExampleCardSkeletonProps {
  count?: number
}

const ExampleCardSkeleton: React.FC<ExampleCardSkeletonProps> = ({ count = 6 }) => {
  const { t } = useTranslation()
  return (
    <>
      {Array.from({ length: count }).map((_, index) => (
        <div
          key={index}
          className={styles.card}
          role={index === 0 ? 'status' : undefined}
          aria-label={index === 0 ? t('common.loading') : undefined}
        >
          <div className={styles.accent} />

          <div
            className="skeleton"
            style={{
              height: 24,
              width: '60%',
              borderRadius: 'var(--radius-sm)',
              marginBottom: 'var(--spacing-3)',
            }}
          />

          <div className={styles.tagRow}>
            <div
              className="skeleton"
              style={{ height: 24, width: 60, borderRadius: 'var(--radius-sm)' }}
            />
            <div
              className="skeleton"
              style={{ height: 24, width: 80, borderRadius: 'var(--radius-sm)' }}
            />
          </div>

          <div style={{ marginBottom: 'var(--spacing-4)' }}>
            <div
              className="skeleton"
              style={{
                height: 16,
                width: '100%',
                borderRadius: 'var(--radius-sm)',
                marginBottom: 'var(--spacing-2)',
              }}
            />
            <div
              className="skeleton"
              style={{
                height: 16,
                width: '90%',
                borderRadius: 'var(--radius-sm)',
                marginBottom: 'var(--spacing-2)',
              }}
            />
            <div
              className="skeleton"
              style={{ height: 16, width: '70%', borderRadius: 'var(--radius-sm)' }}
            />
          </div>

          <div style={{ marginBottom: 'var(--spacing-4)' }}>
            <div
              className="skeleton"
              style={{
                height: 14,
                width: 100,
                borderRadius: 'var(--radius-sm)',
                marginBottom: 'var(--spacing-2)',
              }}
            />
            {Array.from({ length: 2 }).map((_, i) => (
              <div
                key={i}
                className="skeleton"
                style={{
                  height: 12,
                  width: '80%',
                  borderRadius: 'var(--radius-sm)',
                  marginBottom: 'var(--spacing-1)',
                }}
              />
            ))}
          </div>

          <div className={styles.footer}>
            <div className={styles.footerMeta}>
              <div
                className="skeleton"
                style={{ height: 20, width: 60, borderRadius: 'var(--radius-sm)' }}
              />
              <div
                className="skeleton"
                style={{ height: 20, width: 50, borderRadius: 'var(--radius-sm)' }}
              />
            </div>
            <div
              className="skeleton"
              style={{ height: 32, width: 100, borderRadius: 'var(--radius-md)' }}
            />
          </div>
        </div>
      ))}
    </>
  )
}

export default ExampleCardSkeleton
