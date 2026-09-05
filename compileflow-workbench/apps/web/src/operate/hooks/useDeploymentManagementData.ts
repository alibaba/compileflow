import { App } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  abortCanary,
  getDeploymentRoute,
  getDeployments,
  rollbackDeployment,
} from '@/operate/api/deployments'
import type { Deployment, DeploymentListParams } from '@/shared/contracts'

export interface UseDeploymentManagementDataResult {
  deployments: Deployment[]
  error: boolean
  loading: boolean
  hasMore: boolean
  loadMore: () => void
  reload: () => void
  handleRestoreBaseline: (id: string) => void
}

type DeploymentQuery = Pick<DeploymentListParams, 'keyword' | 'alias' | 'status'>

function isCurrentAction(
  mounted: boolean,
  generation: number,
  currentGeneration: number,
  queryGeneration: number,
  currentQueryGeneration: number
): boolean {
  return mounted && generation === currentGeneration && queryGeneration === currentQueryGeneration
}

function useRestoreBaselineAction(
  deployments: Deployment[],
  reload: () => Promise<void>,
  requestGeneration: { current: number },
  setLoading: (loading: boolean) => void
): (id: string) => void {
  const { message, modal } = App.useApp()
  const { t } = useTranslation()
  const rollbackIntents = useRef(new Map<string, string>())
  const actionGeneration = useRef(0)
  const mounted = useRef(true)
  const reloadRef = useRef(reload)
  reloadRef.current = reload

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      actionGeneration.current += 1
    }
  }, [])

  return useCallback(
    (id: string) => {
      const deployment = deployments.find((item) => item.id === id)
      if (!deployment?.baselineVersion) return
      const generation = ++actionGeneration.current
      const queryGeneration = requestGeneration.current
      void (async () => {
        try {
          setLoading(true)
          const route = await getDeploymentRoute(deployment.processCode, deployment.alias)
          if (
            !isCurrentAction(
              mounted.current,
              generation,
              actionGeneration.current,
              queryGeneration,
              requestGeneration.current
            )
          )
            return
          if (!route || route.revision !== deployment.routeRevision) {
            message.warning(t('deployment.routeChanged'))
            await reloadRef.current()
            return
          }
          const isCanary = deployment.status === 'in_progress'
          modal.confirm({
            title: t(isCanary ? 'deployment.abortConfirm' : 'deployment.rollbackConfirm'),
            content: t(isCanary ? 'deployment.abortWarning' : 'deployment.rollbackWarning'),
            okButtonProps: { danger: true },
            onOk: async () => {
              if (
                !isCurrentAction(
                  mounted.current,
                  generation,
                  actionGeneration.current,
                  queryGeneration,
                  requestGeneration.current
                )
              ) {
                return
              }
              const mutationGeneration = ++actionGeneration.current
              setLoading(true)
              try {
                if (isCanary) {
                  await abortCanary(id, deployment.revision, 'Operator restored the stable version')
                } else {
                  const intent = `${id}\u0000${deployment.routeRevision}`
                  const idempotencyKey = rollbackIntents.current.get(intent) ?? crypto.randomUUID()
                  rollbackIntents.current.set(intent, idempotencyKey)
                  await rollbackDeployment(id, deployment.routeRevision, idempotencyKey)
                  rollbackIntents.current.delete(intent)
                }
                if (!mounted.current || mutationGeneration !== actionGeneration.current) return
                message.success(
                  t(isCanary ? 'deployment.abortSuccess' : 'deployment.rollbackSuccess')
                )
                await reloadRef.current()
              } catch {
                if (!mounted.current || mutationGeneration !== actionGeneration.current) return
                message.error(t(isCanary ? 'error.abortFailed' : 'error.rollbackFailed'))
              } finally {
                if (mounted.current && mutationGeneration === actionGeneration.current) {
                  setLoading(false)
                }
              }
            },
          })
        } catch {
          if (!mounted.current || generation !== actionGeneration.current) return
          message.error(t('error.loadFailed'))
        } finally {
          if (mounted.current && generation === actionGeneration.current) setLoading(false)
        }
      })()
    },
    [deployments, message, modal, requestGeneration, setLoading, t]
  )
}

export function useDeploymentManagementData(
  query: DeploymentQuery = {}
): UseDeploymentManagementDataResult {
  const [deployments, setDeployments] = useState<Deployment[]>([])
  const [listLoading, setListLoading] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)
  const [error, setError] = useState(false)
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const requestGeneration = useRef(0)
  const loading = listLoading || actionLoading

  const reload = useCallback(async () => {
    const generation = ++requestGeneration.current
    try {
      setListLoading(true)
      setError(false)
      const response = await getDeployments({ ...query, limit: 100 })
      if (generation === requestGeneration.current) {
        setDeployments(response.deployments)
        setNextCursor(response.nextCursor)
      }
    } catch {
      if (generation !== requestGeneration.current) return
      setError(true)
    } finally {
      if (generation === requestGeneration.current) setListLoading(false)
    }
  }, [query.alias, query.keyword, query.status])

  useEffect(() => {
    setDeployments([])
    setNextCursor(null)
    void reload()
    return () => {
      requestGeneration.current += 1
    }
  }, [reload])

  const loadMore = useCallback(() => {
    if (!nextCursor || loading) return
    const generation = ++requestGeneration.current
    void (async () => {
      try {
        setListLoading(true)
        setError(false)
        const response = await getDeployments({ ...query, cursor: nextCursor, limit: 100 })
        if (generation !== requestGeneration.current) return
        setDeployments((current) => [...current, ...response.deployments])
        setNextCursor(response.nextCursor)
      } catch {
        if (generation !== requestGeneration.current) return
        setError(true)
      } finally {
        if (generation === requestGeneration.current) setListLoading(false)
      }
    })()
  }, [loading, nextCursor, query.alias, query.keyword, query.status])

  const handleRestoreBaseline = useRestoreBaselineAction(
    deployments,
    reload,
    requestGeneration,
    setActionLoading
  )

  return {
    deployments,
    error,
    loading,
    hasMore: nextCursor !== null,
    loadMore,
    reload,
    handleRestoreBaseline,
  }
}
