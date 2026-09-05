import styles from './page.module.css'

type MetricAccent = 'default' | 'success' | 'warning' | 'error' | 'primary'

export interface MetricItem {
  key: string
  label: string
  value: string | number
  suffix?: string
  accent?: MetricAccent
}

export interface MetricGridProps {
  metrics: MetricItem[]
  columns?: 2 | 3 | 4
  className?: string
}

const accentClassMap: Record<MetricAccent, string> = {
  default: '',
  success: styles.metricAccentSuccess,
  warning: styles.metricAccentWarning,
  error: styles.metricAccentError,
  primary: styles.metricAccentPrimary,
}

export function MetricGrid({ metrics, columns = 4, className }: MetricGridProps) {
  return (
    <div
      className={[styles.metricGrid, className].filter(Boolean).join(' ')}
      style={{ gridTemplateColumns: `repeat(${columns}, 1fr)` }}
    >
      {metrics.map((metric) => (
        <div key={metric.key} className={styles.metricCard}>
          <div className={styles.metricLabel}>{metric.label}</div>
          <div
            className={[styles.metricValue, accentClassMap[metric.accent ?? 'default']].join(' ')}
          >
            {metric.value}
            {metric.suffix && <span className={styles.metricSuffix}>{metric.suffix}</span>}
          </div>
        </div>
      ))}
    </div>
  )
}
