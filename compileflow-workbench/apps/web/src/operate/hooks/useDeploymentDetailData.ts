import { App } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import type { TFunction } from 'i18next'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'

import {
  abortCanary,
  evaluateCanaryHealth,
  getDeployment,
  getDeploymentEvents,
  getDeploymentRoute,
  promoteCanary,
  rollbackDeployment,
  updateCanaryWeightBps,
} from '@/operate/api/deployments'
import type {
  CanaryHealthEvaluationResponse,
  Deployment,
  DeploymentEvent,
  DeploymentRoute,
} from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { createUniqueId } from '@/shared/identifiers'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('DeploymentDetail')

function releaseDeploymentMutation(lock: { current: Set<string> }, id: string): void {
  lock.current.delete(id)
}

export type DeploymentAction = 'update-canary' | 'evaluate-health' | 'promote' | 'restore-baseline'

function canRestoreBaseline(
  deployment: Deployment,
  route: DeploymentRoute | undefined,
  message: MessageInstance,
  t: TFunction
): boolean {
  if (route?.revision !== deployment.routeRevision) {
    message.error(t('deployment.routeSuperseded'))
    return false
  }
  if (deployment.baselineVersion) return true

  const isCanary = deployment.status === 'in_progress'
  message.error(
    t(isCanary ? 'deployment.abortMissingBaseline' : 'deployment.rollbackMissingBaseline')
  )
  return false
}

function useDeploymentDetailState(id: string | undefined, message: MessageInstance, t: TFunction) {
  const [deployment, setDeployment] = useState<Deployment | null>(null)
  const [events, setEvents] = useState<DeploymentEvent[]>([])
  const [route, setRoute] = useState<DeploymentRoute>()
  const [health, setHealth] = useState<CanaryHealthEvaluationResponse>()
  const [loadState, setLoadState] = useState<'loading' | 'ready' | 'error'>('loading')
  const [canaryValue, setCanaryValue] = useState(1_000)
  const loadGeneration = useRef(0)
  const loadedId = useRef<string | undefined>(undefined)

  const loadDetail = useCallback(
    async (showLoading = true) => {
      const generation = ++loadGeneration.current
      if (!id) {
        setDeployment(null)
        setEvents([])
        setRoute(undefined)
        setHealth(undefined)
        setLoadState('ready')
        return
      }
      if (showLoading) {
        setLoadState('loading')
        if (loadedId.current !== id) {
          loadedId.current = id
          setDeployment(null)
          setEvents([])
          setRoute(undefined)
          setHealth(undefined)
        }
      }
      try {
        const current = await getDeployment(id)
        const [history, currentRoute] = await Promise.all([
          getDeploymentEvents(id),
          getDeploymentRoute(current.processCode, current.alias),
        ])
        if (generation !== loadGeneration.current) return
        setDeployment(current)
        setEvents(history)
        setRoute(currentRoute)
        if (current.canaryWeightBps != null) setCanaryValue(current.canaryWeightBps)
        setLoadState('ready')
      } catch (error) {
        if (generation !== loadGeneration.current) return
        setLoadState('error')
        logger.error('Failed to load deployment detail', toError(error), { deploymentId: id })
        message.error(t('deployment.loadDetailError'))
      }
    },
    [id, message, t]
  )

  useEffect(() => {
    void loadDetail()
    return () => {
      loadGeneration.current += 1
    }
  }, [loadDetail])

  const applyMutation = useCallback(
    async (updated: Deployment) => {
      const generation = ++loadGeneration.current
      setDeployment(updated)
      setHealth(undefined)
      if (updated.canaryWeightBps != null) setCanaryValue(updated.canaryWeightBps)
      try {
        const [history, currentRoute] = await Promise.all([
          getDeploymentEvents(updated.id),
          getDeploymentRoute(updated.processCode, updated.alias),
        ])
        if (generation !== loadGeneration.current) return
        setEvents(history)
        setRoute(currentRoute)
      } catch (error) {
        if (generation !== loadGeneration.current) return
        logger.warn('Deployment changed but related detail could not be refreshed', {
          deploymentId: updated.id,
          error: toError(error).message,
        })
        message.warning(t('deployment.partialRefresh'))
      }
    },
    [message, t]
  )

  return {
    applyMutation,
    canaryValue,
    deployment,
    events,
    health,
    loadDetail,
    loadState,
    route,
    setCanaryValue,
    setHealth,
  }
}

function useDeploymentActionLifetime(
  id: string | undefined,
  setAction: (action: DeploymentAction | undefined) => void
) {
  const selection = useMemo(() => ({ active: true }), [id])
  const currentSelection = useRef(selection)
  currentSelection.current = selection
  const actionGeneration = useRef(0)

  useEffect(() => {
    selection.active = true
    setAction(undefined)
    return () => {
      selection.active = false
    }
  }, [selection, setAction])

  return useCallback(
    (action: DeploymentAction) => {
      // The callback captures a selection lifetime, so A -> B -> A cannot revive it.
      if (!selection.active || currentSelection.current !== selection) return undefined
      const generation = ++actionGeneration.current
      setAction(action)
      return () =>
        selection.active &&
        currentSelection.current === selection &&
        generation === actionGeneration.current
    },
    [selection, setAction]
  )
}

export function useDeploymentDetailData(id: string | undefined, t: TFunction) {
  const { message }: { message: MessageInstance } = App.useApp()
  const {
    applyMutation,
    canaryValue,
    deployment,
    events,
    health,
    loadDetail,
    loadState,
    route,
    setCanaryValue,
    setHealth,
  } = useDeploymentDetailState(id, message, t)
  const [action, setAction] = useState<DeploymentAction>()
  const rollbackIntents = useRef(new Map<string, string>())
  const mutationInFlightFor = useRef(new Set<string>())
  const beginAction = useDeploymentActionLifetime(id, setAction)

  const updateCanary = useCallback(async () => {
    if (!id || !deployment || mutationInFlightFor.current.has(id)) return
    mutationInFlightFor.current.add(id)
    const isCurrent = beginAction('update-canary')
    if (!isCurrent) {
      mutationInFlightFor.current.delete(id)
      return
    }
    try {
      const updated = await updateCanaryWeightBps(id, canaryValue, deployment.revision)
      if (!isCurrent()) return
      await applyMutation(updated)
      if (!isCurrent()) return
      message.success(t('deployment.canaryUpdated'))
    } catch (error) {
      if (!isCurrent()) return
      logger.error('Failed to update canary weight', toError(error), { deploymentId: id })
      message.error(t('deployment.canaryUpdateError'))
      await loadDetail(false)
    } finally {
      releaseDeploymentMutation(mutationInFlightFor, id)
      if (isCurrent()) setAction(undefined)
    }
  }, [applyMutation, beginAction, canaryValue, deployment, id, loadDetail, message, t])

  const evaluateHealth = useCallback(async () => {
    if (!id) return
    const isCurrent = beginAction('evaluate-health')
    if (!isCurrent) return
    try {
      const evaluated = await evaluateCanaryHealth(id)
      if (!isCurrent()) return
      setHealth(evaluated)
    } catch (error) {
      if (!isCurrent()) return
      logger.error('Failed to evaluate canary health', toError(error), { deploymentId: id })
      message.error(t('deployment.healthEvaluationError'))
    } finally {
      if (isCurrent()) setAction(undefined)
    }
  }, [beginAction, id, message, setHealth, t])

  const promote = useCallback(async (): Promise<Deployment | undefined> => {
    if (!id || !deployment || mutationInFlightFor.current.has(id)) return undefined
    mutationInFlightFor.current.add(id)
    const isCurrent = beginAction('promote')
    if (!isCurrent) {
      mutationInFlightFor.current.delete(id)
      return undefined
    }
    try {
      const updated = await promoteCanary(id, deployment.revision)
      if (!isCurrent()) return undefined
      await applyMutation(updated)
      if (!isCurrent()) return undefined
      message.success(t('deployment.canaryPromoted'))
      return updated
    } catch (error) {
      if (!isCurrent()) return undefined
      logger.error('Failed to promote canary', toError(error), { deploymentId: id })
      message.error(t('deployment.canaryPromoteError'))
      await loadDetail(false)
      return undefined
    } finally {
      releaseDeploymentMutation(mutationInFlightFor, id)
      if (isCurrent()) setAction(undefined)
    }
  }, [applyMutation, beginAction, deployment, id, loadDetail, message, t])

  const restoreBaseline = useCallback(async (): Promise<Deployment | undefined> => {
    if (!id || !deployment || mutationInFlightFor.current.has(id)) return undefined
    mutationInFlightFor.current.add(id)
    const isCanary = deployment.status === 'in_progress'
    const isCurrent = beginAction('restore-baseline')
    if (!isCurrent) {
      mutationInFlightFor.current.delete(id)
      return undefined
    }
    if (!canRestoreBaseline(deployment, route, message, t)) {
      mutationInFlightFor.current.delete(id)
      setAction(undefined)
      return undefined
    }
    try {
      let updated: Deployment
      if (isCanary) {
        updated = await abortCanary(id, deployment.revision, 'Operator restored the stable version')
      } else {
        const intent = `${id}\u0000${deployment.routeRevision}`
        const idempotencyKey = rollbackIntents.current.get(intent) ?? createUniqueId()
        rollbackIntents.current.set(intent, idempotencyKey)
        updated = await rollbackDeployment(id, deployment.routeRevision, idempotencyKey)
        rollbackIntents.current.delete(intent)
      }
      if (!isCurrent()) return undefined
      await applyMutation(updated)
      if (!isCurrent()) return undefined
      message.success(t(isCanary ? 'deployment.abortSuccess' : 'deployment.rollbackSuccess'))
      return updated
    } catch (error) {
      if (!isCurrent()) return undefined
      logger.error('Failed to restore deployment baseline', toError(error), { deploymentId: id })
      message.error(t(isCanary ? 'error.abortFailed' : 'error.rollbackFailed'))
      await loadDetail(false)
      return undefined
    } finally {
      releaseDeploymentMutation(mutationInFlightFor, id)
      if (isCurrent()) setAction(undefined)
    }
  }, [applyMutation, beginAction, deployment, id, loadDetail, message, route?.revision, t])

  return {
    action,
    canaryValue,
    deployment,
    evaluateHealth,
    events,
    health,
    loadError: loadState === 'error',
    loading: loadState === 'loading',
    promote,
    restoreBaseline,
    reload: loadDetail,
    route,
    setCanaryValue,
    updateCanary,
  }
}
