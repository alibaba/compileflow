import { App } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import type { TFunction } from 'i18next'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { NavigateFunction } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'

import {
  createProcess,
  loadProcess,
  loadOperateProcess,
  selectCurrentProcess,
} from '../designer/store/editorSlice'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import type { AppDispatch } from '@/app/store'
import { UndoActionCreators } from '@/app/store'
import { ROUTES } from '@/shared/constants'
import type { ProcessModelType } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import {
  type DesignerEntryDescriptor,
  openDesignerFromWorkspaceProcess,
} from '@/shared/services/designerNavigation'

type ProcessInitStatus = 'loading' | 'ready' | 'error'

interface LearnExamplePayload {
  processXml: string
  processName?: string
  modelType: ProcessModelType
  exampleId: string
}

export interface ProcessInitOptions {
  entryPayload: DesignerEntryDescriptor
}

export interface ProcessInitState {
  status: ProcessInitStatus
  errorMessage: string | null
  /** Suggested exit route for the error Result back action; never auto-navigated. */
  exitRoute: string | null
  retry: () => void
}

interface CancellationToken {
  value: boolean
  abortCurrentLoad: (() => void) | null
}

interface AbortableLoad<T> {
  abort: (reason?: string) => void
  unwrap: () => Promise<T>
}

async function awaitAbortableLoad<T>(
  cancelled: CancellationToken,
  load: AbortableLoad<T>
): Promise<T> {
  const abort = () => load.abort('Designer initialization changed')
  cancelled.abortCurrentLoad = abort
  try {
    return await load.unwrap()
  } finally {
    if (cancelled.abortCurrentLoad === abort) {
      cancelled.abortCurrentLoad = null
    }
  }
}

interface ProcessInitRuntime {
  dispatch: AppDispatch
  messageApi: MessageInstance
  navigate: NavigateFunction
  t: TFunction
  markFailed: (errorMsg: string, navigateTo?: string) => void
}

interface ProcessInitRequest extends ProcessInitRuntime {
  entryPayload: DesignerEntryDescriptor
  cancelled: CancellationToken
  setReady: () => void
}

async function resolveExampleById(id: string, t: TFunction): Promise<LearnExamplePayload> {
  const { getExample } = await import('@/shared/api/examples')
  const example = await getExample(id)
  if (!example.code) {
    throw new Error(t('designer.flowInit.exampleMissingXml'))
  }

  return {
    exampleId: example.id,
    processXml: example.code,
    processName: example.name,
    modelType: example.modelType,
  }
}

async function loadFromExampleId(request: ProcessInitRequest): Promise<void> {
  const { cancelled, dispatch, entryPayload, markFailed, messageApi, t } = request
  if (cancelled.value) return
  if (!entryPayload.exampleId) {
    markFailed(t('designer.flowInit.invalidEntry'), ROUTES.LEARN_EXAMPLES)
    return
  }

  try {
    const example = await resolveExampleById(entryPayload.exampleId, t)
    if (cancelled.value) return

    const flow = await awaitAbortableLoad(
      cancelled,
      dispatch(
        createProcess({
          definition: example.processXml,
          type: example.modelType,
          name: example.processName || t('designer.flow.exampleName'),
        })
      )
    )

    if (cancelled.value) return

    messageApi.success(
      t('designer.flowInit.exampleLoaded', { name: example.processName || example.exampleId })
    )
    openDesignerFromWorkspaceProcess(
      request.navigate,
      { processId: flow.id, modelType: flow.type },
      { replace: true }
    )
  } catch (err: unknown) {
    if (!cancelled.value) {
      const msg = toError(err, t('designer.flowInit.exampleCheckData')).message
      messageApi.error(t('designer.flowInit.exampleLoadFailed', { message: msg }))
      markFailed(t('designer.flowInit.exampleLoadFailed', { message: msg }), ROUTES.LEARN_EXAMPLES)
    }
  }
}

async function loadFromIndexedDb(request: ProcessInitRequest): Promise<void> {
  const { cancelled, dispatch, entryPayload, markFailed, messageApi, setReady, t } = request
  if (cancelled.value) return
  if (!entryPayload.processId) {
    markFailed(t('designer.flowInit.invalidEntry'), ROUTES.BUILD)
    return
  }
  try {
    await awaitAbortableLoad(cancelled, dispatch(loadProcess(entryPayload.processId)))
    if (cancelled.value) return
    // Anchor undo history on the loaded flow (present), not the empty bootstrap state.
    dispatch(UndoActionCreators.clearHistory())
    setReady()
  } catch (err: unknown) {
    if (!cancelled.value) {
      const msg = toError(err, t('designer.flowInit.flowNotFound')).message
      messageApi.error(msg)
      markFailed(msg, ROUTES.BUILD)
    }
  }
}

async function createProcessFromTemplate(request: ProcessInitRequest): Promise<void> {
  const { cancelled, dispatch, entryPayload, markFailed, messageApi, t } = request
  if (cancelled.value) return
  if (!entryPayload.templateId) {
    markFailed(t('designer.flowInit.invalidEntry'), ROUTES.BUILD)
    return
  }
  const { processStorage } = await import('../designer/api/processStorage')
  try {
    const { ensureBuiltInTemplates } = await import('../designer/api/builtInTemplates')
    if (cancelled.value) return
    await ensureBuiltInTemplates()
    if (cancelled.value) return
    const template = await processStorage.getTemplate(entryPayload.templateId)
    if (cancelled.value) return
    if (!template) {
      throw new Error(t('designer.flowInit.templateNotFound', { id: entryPayload.templateId }))
    }
    const templateNameKey = `workspace.template.${template.id}.name`
    const translatedTemplateName = t(templateNameKey)
    const localizedTemplateName =
      translatedTemplateName === templateNameKey ? template.name : translatedTemplateName
    const newProcess = await awaitAbortableLoad(
      cancelled,
      dispatch(
        createProcess({
          type: template.type,
          name: t('designer.flowInit.templateFrom', { name: localizedTemplateName }),
          definition: template.content,
        })
      )
    )

    if (cancelled.value) return

    messageApi.success(t('designer.flowInit.templateCreated', { name: localizedTemplateName }))
    openDesignerFromWorkspaceProcess(
      request.navigate,
      { processId: newProcess.id, modelType: newProcess.type },
      { replace: true }
    )
  } catch (err: unknown) {
    if (!cancelled.value) {
      const msg = toError(err, t('designer.flowInit.templateLoadFailed')).message
      messageApi.error(msg)
      markFailed(msg, ROUTES.BUILD)
    }
  }
}

async function createNewProcess(request: ProcessInitRequest): Promise<void> {
  const { cancelled, dispatch, entryPayload, markFailed, messageApi, t } = request
  if (cancelled.value) return
  const runtimeType = entryPayload.modelType === 'bpmn' ? 'BPMN' : 'TBBPM'
  try {
    const flow = await awaitAbortableLoad(
      cancelled,
      dispatch(
        createProcess({
          type: runtimeType,
          name: t('designer.flow.defaultName', { type: runtimeType }),
        })
      )
    )

    if (cancelled.value) return

    openDesignerFromWorkspaceProcess(
      request.navigate,
      { processId: flow.id, modelType: flow.type },
      { replace: true }
    )
  } catch {
    if (!cancelled.value) {
      const msg = t('designer.flowInit.createFailed')
      messageApi.error(msg)
      markFailed(msg, ROUTES.BUILD)
    }
  }
}

async function loadFromOperateProcessCode(request: ProcessInitRequest): Promise<void> {
  const { cancelled, dispatch, entryPayload, markFailed, messageApi, setReady, t } = request
  if (cancelled.value) return
  if (!entryPayload.processCode) {
    markFailed(t('designer.flowInit.invalidEntry'), ROUTES.OPERATE_PROCESSES)
    return
  }

  try {
    const result = await awaitAbortableLoad(
      cancelled,
      dispatch(loadOperateProcess(entryPayload.processCode))
    )

    if (cancelled.value) return

    dispatch(UndoActionCreators.clearHistory())
    messageApi.success(t('designer.flowInit.operateLoaded', { name: result.flow.name }))
    setReady()
  } catch (err: unknown) {
    if (!cancelled.value) {
      const msg = toError(err, t('designer.flowInit.operateLoadFailedGeneric')).message
      messageApi.error(t('designer.flowInit.operateLoadFailed', { message: msg }))
      markFailed(
        t('designer.flowInit.operateLoadFailed', { message: msg }),
        ROUTES.OPERATE_PROCESSES
      )
    }
  }
}

async function runProcessInitialization(request: ProcessInitRequest): Promise<void> {
  const { entryPayload } = request
  switch (entryPayload.source) {
    case 'operateProcessCode':
      await loadFromOperateProcessCode(request)
      break
    case 'example':
      await loadFromExampleId(request)
      break
    case 'workspaceProcess':
      await loadFromIndexedDb(request)
      break
    case 'template':
      await createProcessFromTemplate(request)
      break
    case 'new':
      await createNewProcess(request)
      break
  }
}

export const useProcessInitialization = ({
  entryPayload,
}: ProcessInitOptions): ProcessInitState => {
  const { t } = useTranslation()
  const { source, processId, modelType, templateId, exampleId, processCode } = entryPayload
  const dispatch = useAppDispatch()
  const currentProcess = useAppSelector(selectCurrentProcess)
  const workspaceProcessIsCurrent =
    source === 'workspaceProcess' &&
    processId === currentProcess?.id &&
    modelType === currentProcess.type.toLowerCase()
  const navigate = useNavigate()
  const { message: messageApi } = App.useApp()

  const [status, setStatus] = useState<ProcessInitStatus>('loading')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [exitRoute, setExitRoute] = useState<string | null>(null)
  const [retryToken, setRetryToken] = useState(0)
  const retry = useCallback(() => {
    setStatus('loading')
    setErrorMessage(null)
    setExitRoute(null)
    setRetryToken((value) => value + 1)
  }, [])

  const markFailed = useCallback((errorMsg: string, navigateTo?: string) => {
    // Keep the designer error Result mounted so Retry stays reachable.
    // Callers may supply navigateTo as the preferred Back destination only.
    setStatus('error')
    setErrorMessage(errorMsg)
    setExitRoute(navigateTo ?? null)
  }, [])

  useEffect(() => {
    const cancelled: CancellationToken = { value: false, abortCurrentLoad: null }

    if (workspaceProcessIsCurrent) {
      dispatch(UndoActionCreators.clearHistory())
      setStatus('ready')
      setErrorMessage(null)
      setExitRoute(null)
      return
    }

    const run = async () => {
      if (cancelled.value) return
      setStatus('loading')
      setErrorMessage(null)
      setExitRoute(null)

      try {
        await runProcessInitialization({
          cancelled,
          dispatch,
          entryPayload,
          markFailed,
          messageApi,
          navigate,
          setReady: () => setStatus('ready'),
          t,
        })
      } catch {
        if (!cancelled.value) {
          const message = t('designer.flowInit.errorGeneric')
          messageApi.error(message)
          markFailed(message, ROUTES.BUILD)
        }
      }
    }

    void run()

    return () => {
      cancelled.value = true
      cancelled.abortCurrentLoad?.()
      cancelled.abortCurrentLoad = null
    }
  }, [
    source,
    processId,
    modelType,
    templateId,
    exampleId,
    processCode,
    workspaceProcessIsCurrent,
    retryToken,
    dispatch,
    markFailed,
    navigate,
    messageApi,
    t,
  ])

  return { status, errorMessage, exitRoute, retry }
}
