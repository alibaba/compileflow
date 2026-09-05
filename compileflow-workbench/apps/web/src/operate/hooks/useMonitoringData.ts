import { useCallback, useEffect, useRef, useState } from 'react'

import { getDeploymentControlHealth } from '@/operate/api/deployments'
import {
  getDeployRuntimeDiagnostics,
  getExecutionTrends,
  getMetrics,
  getRecentErrors,
  getTopProcesses,
  getVersionDistribution,
} from '@/operate/api/monitoring'
import { resolveTrendInterval } from '@/operate/monitoring/trendInterval'
import { getAsyncInvocationHealth } from '@/shared/api/processes'
import { TIMEOUTS } from '@/shared/constants'
import type {
  AsyncInvocationHealth,
  DeploymentControlHealth,
  DeployRuntimeDiagnostics,
  ErrorSummary,
  ExecutionTrend,
  MonitoringMetrics,
  MonitoringTimeRange,
  TopProcessStats,
  VersionDistributionStats,
} from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('Monitoring')

export interface MonitoringData {
  metrics: MonitoringMetrics | null
  trends: ExecutionTrend[]
  topProcesses: TopProcessStats[]
  errors: ErrorSummary[]
  versionDistribution: VersionDistributionStats[]
  deployRuntime: DeployRuntimeDiagnostics | null
  routingOutboxControl: DeploymentControlHealth | null
  asyncHealth: AsyncInvocationHealth | null
}

export type MonitoringSource = keyof MonitoringData

interface MonitoringFailure {
  source: MonitoringSource
  error: unknown
}

interface MonitoringLoadResult {
  data: Partial<MonitoringData>
  failures: MonitoringFailure[]
}

type MonitoringLoaders = {
  [Source in MonitoringSource]: () => Promise<MonitoringData[Source]>
}

const ALL_SOURCES: MonitoringSource[] = [
  'metrics',
  'trends',
  'topProcesses',
  'errors',
  'versionDistribution',
  'deployRuntime',
  'routingOutboxControl',
  'asyncHealth',
]
const OPS_SOURCES: MonitoringSource[] = ['routingOutboxControl', 'asyncHealth']

const EMPTY_MONITORING_DATA: MonitoringData = {
  metrics: null,
  trends: [],
  topProcesses: [],
  errors: [],
  versionDistribution: [],
  deployRuntime: null,
  routingOutboxControl: null,
  asyncHealth: null,
}

function createLoaders(timeRange: MonitoringTimeRange): MonitoringLoaders {
  return {
    metrics: () => getMetrics(timeRange),
    trends: () => getExecutionTrends({ timeRange, interval: resolveTrendInterval(timeRange) }),
    topProcesses: () => getTopProcesses({ timeRange, limit: 10 }),
    errors: () => getRecentErrors({ timeRange, limit: 10 }),
    versionDistribution: () => getVersionDistribution({ timeRange, limit: 10 }),
    deployRuntime: getDeployRuntimeDiagnostics,
    routingOutboxControl: getDeploymentControlHealth,
    asyncHealth: getAsyncInvocationHealth,
  }
}

function setSourceValue(
  data: Partial<MonitoringData>,
  source: MonitoringSource,
  value: MonitoringData[MonitoringSource]
): void {
  const writable = data as Record<MonitoringSource, MonitoringData[MonitoringSource]>
  writable[source] = value
}

async function loadSources(
  timeRange: MonitoringTimeRange,
  sources: MonitoringSource[]
): Promise<MonitoringLoadResult> {
  const loaders = createLoaders(timeRange)
  const results = await Promise.all(
    sources.map(async (source) => {
      try {
        return { source, value: await loaders[source]() }
      } catch (error) {
        return { source, error }
      }
    })
  )
  const data: Partial<MonitoringData> = {}
  const failures: MonitoringFailure[] = []
  for (const result of results) {
    if ('error' in result) {
      failures.push({ source: result.source, error: result.error })
    } else {
      setSourceValue(data, result.source, result.value)
    }
  }
  return { data, failures }
}

function reconcileFailures(
  current: MonitoringSource[],
  refreshed: MonitoringSource[],
  failures: MonitoringFailure[]
): MonitoringSource[] {
  const next = new Set(current)
  for (const source of refreshed) next.delete(source)
  for (const failure of failures) next.add(failure.source)
  return ALL_SOURCES.filter((source) => next.has(source))
}

function createSourceGenerations(): Record<MonitoringSource, number> {
  return Object.fromEntries(ALL_SOURCES.map((source) => [source, 0])) as Record<
    MonitoringSource,
    number
  >
}

function reserveSourceGenerations(
  sources: MonitoringSource[],
  generations: Record<MonitoringSource, number>
): Map<MonitoringSource, number> {
  const reserved = new Map<MonitoringSource, number>()
  for (const source of sources) {
    const generation = generations[source] + 1
    generations[source] = generation
    reserved.set(source, generation)
  }
  return reserved
}

function selectCurrentLoadResult(
  sources: MonitoringSource[],
  reservedGenerations: ReadonlyMap<MonitoringSource, number>,
  sourceGenerations: Readonly<Record<MonitoringSource, number>>,
  result: MonitoringLoadResult
): { data: Partial<MonitoringData>; failures: MonitoringFailure[]; sources: MonitoringSource[] } {
  const currentSources = sources.filter(
    (source) => sourceGenerations[source] === reservedGenerations.get(source)
  )
  const data: Partial<MonitoringData> = {}
  for (const source of currentSources) {
    const value = result.data[source]
    if (value !== undefined) setSourceValue(data, source, value)
  }
  return {
    data,
    failures: result.failures.filter(({ source }) => currentSources.includes(source)),
    sources: currentSources,
  }
}

function shouldFinishVisibleLoad(
  mounted: boolean,
  visible: boolean,
  visibleGeneration: number,
  currentGeneration: number,
  signal?: AbortSignal
): boolean {
  return mounted && visible && visibleGeneration === currentGeneration && !signal?.aborted
}

export function useMonitoringData(timeRange: MonitoringTimeRange) {
  const [data, setData] = useState<MonitoringData>(EMPTY_MONITORING_DATA)
  const [failedSources, setFailedSources] = useState<MonitoringSource[]>([])
  const [loading, setLoading] = useState(true)
  const sourceGenerations = useRef(createSourceGenerations())
  const initialized = useRef(false)
  const visibleLoadGeneration = useRef(0)
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      visibleLoadGeneration.current += 1
    }
  }, [])

  const refreshSources = useCallback(
    async (sources: MonitoringSource[], signal?: AbortSignal, showLoading = false) => {
      if (!mounted.current) return []
      const reservedGenerations = reserveSourceGenerations(sources, sourceGenerations.current)
      const visible = !initialized.current || showLoading
      const visibleGeneration = visible ? ++visibleLoadGeneration.current : 0
      if (visible) {
        setLoading(true)
      }
      try {
        const result = await loadSources(timeRange, sources)
        if (signal?.aborted || !mounted.current) return []

        const current = selectCurrentLoadResult(
          sources,
          reservedGenerations,
          sourceGenerations.current,
          result
        )
        if (current.sources.length === 0) return []

        setData((previous) => ({ ...previous, ...current.data }))
        setFailedSources((previous) =>
          reconcileFailures(previous, current.sources, current.failures)
        )
        for (const failure of current.failures) {
          logger.warn('Monitoring source refresh failed', {
            source: failure.source,
            error: toError(failure.error).message,
          })
        }
        initialized.current = true
        return current.failures
      } finally {
        if (
          shouldFinishVisibleLoad(
            mounted.current,
            visible,
            visibleGeneration,
            visibleLoadGeneration.current,
            signal
          )
        ) {
          setLoading(false)
        }
      }
    },
    [timeRange]
  )

  const reloadOpsData = useCallback(async () => {
    const failures = await refreshSources(OPS_SOURCES)
    if (failures.length > 0) {
      throw new Error(
        `Failed to refresh monitoring sources: ${failures.map(({ source }) => source).join(', ')}`
      )
    }
  }, [refreshSources])

  const reloadAllData = useCallback(
    async () => void (await refreshSources(ALL_SOURCES, undefined, true)),
    [refreshSources]
  )

  useEffect(() => {
    const controller = new AbortController()
    let nextPoll: ReturnType<typeof setTimeout> | undefined

    const poll = async () => {
      await refreshSources(ALL_SOURCES, controller.signal)
      if (!controller.signal.aborted) {
        nextPoll = setTimeout(() => void poll(), TIMEOUTS.MONITORING_POLL_INTERVAL)
      }
    }

    void poll()
    return () => {
      controller.abort()
      if (nextPoll !== undefined) clearTimeout(nextPoll)
    }
  }, [refreshSources])

  return { data, failedSources, loading, reloadAllData, reloadOpsData }
}
