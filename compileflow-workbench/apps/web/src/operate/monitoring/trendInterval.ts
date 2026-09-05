import type { MonitoringTimeRange } from '@/shared/contracts'

export type TrendInterval = '1m' | '5m' | '1h' | '1d'

/** Maps dashboard time range to server trend bucket interval. */
export function resolveTrendInterval(timeRange: MonitoringTimeRange): TrendInterval {
  switch (timeRange) {
    case '1h':
      return '1m'
    case '6h':
      return '5m'
    case '24h':
      return '1h'
    case '7d':
      return '1h'
    case '30d':
      return '1d'
    default:
      return '1h'
  }
}
