import type { Graph } from '@antv/x6'
import { App } from 'antd'
import type { MessageInstance } from 'antd/es/message/interface'
import type { TFunction } from 'i18next'
import {
  type MutableRefObject,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useTranslation } from 'react-i18next'
import type { NavigateFunction } from 'react-router-dom'

import {
  createProcess,
  deleteProcess,
  importXml,
  saveProcess,
  updateProcessInfo,
} from '../store/editorSlice'
import { openRightPanelTab, toggleGridlines, togglePanel } from '../store/uiSlice'
import type { UnifiedProcessDefinition } from '../types/flowDefinition'

import type { AppDispatch } from '@/app/store'
import { UndoActionCreators } from '@/app/store'
import { exportBoth } from '@/authoring/designer/canvas/canvasExport'
import { generateProcessXml } from '@/authoring/designer/serialization/flowXml'
import { validateDesignerProcess } from '@/authoring/designer/validation/designerProcessValidation'
import { translateValidationIssue } from '@/authoring/designer/validation/topologyValidationI18n'
import { ROUTES } from '@/shared/constants'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'
import {
  type DesignerEntryDescriptor,
  openDesignerFromWorkspaceProcess,
} from '@/shared/services/designerNavigation'

interface UseDesignerPageActionsOptions {
  currentProcess: UnifiedProcessDefinition | null
  graph: Graph | null
  dispatch: AppDispatch
  navigate: NavigateFunction
  copyNodes: (nodeIds: string[]) => void
  pasteNodes: () => void
  selectedNodeId: string | null
  entryPayload?: DesignerEntryDescriptor
  operateBinding?: { processCode: string; revision: number } | null
}

type OperateBinding = { processCode: string; revision: number } | null | undefined
type DesignerProcessRef = MutableRefObject<UnifiedProcessDefinition | null>
type OperateBindingRef = MutableRefObject<OperateBinding>

const logger = createLogger('useDesignerPageActions')

function useLatestRef<T>(value: T) {
  const ref = useRef(value)
  useEffect(() => {
    ref.current = value
  }, [value])
  return ref
}

const flowXmlFileName = (flow: UnifiedProcessDefinition) =>
  `${flow.name || 'flow'}_${flow.type.toLowerCase()}.xml`

function downloadTextFile(content: string, filename: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
}

async function saveCurrentProcess(
  dispatch: AppDispatch,
  currentProcessRef: DesignerProcessRef,
  operateBindingRef: OperateBindingRef,
  t: TFunction,
  message: MessageInstance
) {
  if (!currentProcessRef.current) return false
  try {
    const saved = await dispatch(saveProcess({ createSnapshot: true })).unwrap()
    message.success(
      operateBindingRef.current
        ? t('designer.save.operateSuccess')
        : t('designer.save.workspaceSuccess')
    )
    return dispatch((_dispatch, getState) => {
      const editor = getState().editor.present
      return (
        editor.documentRequestId === saved.documentRequestId &&
        !editor.isModified &&
        !editor.isLoading &&
        !editor.isSaving
      )
    })
  } catch (error) {
    logger.error('Failed to save designer flow', toError(error))
    const validationIssue = currentProcessRef.current
      ? validateDesignerProcess(currentProcessRef.current).issues.find(
          (issue) => issue.level === 'error' && issue.type === 'property'
        )
      : undefined
    message.error(
      validationIssue
        ? translateValidationIssue(validationIssue, t).message
        : t('designer.save.failed')
    )
    return false
  }
}

function useSaveNavigationActions({
  currentProcessRef,
  dispatch,
  exitRoute,
  navigate,
  operateBindingRef,
  t,
}: {
  currentProcessRef: DesignerProcessRef
  dispatch: AppDispatch
  exitRoute: string
  navigate: NavigateFunction
  operateBindingRef: OperateBindingRef
  t: TFunction
}) {
  const { message } = App.useApp()
  const handleSave = useCallback(
    () => saveCurrentProcess(dispatch, currentProcessRef, operateBindingRef, t, message),
    [currentProcessRef, dispatch, message, operateBindingRef, t]
  )

  const handleBack = useCallback(() => {
    void navigate(exitRoute)
  }, [exitRoute, navigate])

  return { handleBack, handleSave }
}

function useClipboardActions({
  copyNodes,
  pasteNodes,
  selectedNodeId,
  t,
}: Pick<UseDesignerPageActionsOptions, 'copyNodes' | 'pasteNodes' | 'selectedNodeId'> & {
  t: TFunction
}) {
  const { message } = App.useApp()
  const handleCopy = useCallback(() => {
    if (!selectedNodeId) {
      message.warning(t('designer.actions.selectNodeToCopy'))
      return
    }
    copyNodes([selectedNodeId])
  }, [copyNodes, message, selectedNodeId, t])

  const handlePaste = useCallback(() => {
    pasteNodes()
  }, [pasteNodes])

  return { handleCopy, handlePaste }
}

function useMetadataActions(
  currentProcess: UnifiedProcessDefinition | null,
  dispatch: AppDispatch
) {
  const handleUpdateName = useCallback(
    (name: string) => {
      if (!currentProcess) return
      dispatch(updateProcessInfo({ name, updatedAt: Date.now() }))
    },
    [currentProcess, dispatch]
  )

  return { handleUpdateName }
}

function useFileActions({
  currentProcess,
  dispatch,
  graph,
  t,
}: {
  currentProcess: UnifiedProcessDefinition | null
  dispatch: AppDispatch
  graph: Graph | null
  t: TFunction
}) {
  const { message } = App.useApp()
  const mounted = useRef(false)
  useLayoutEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])
  const handleExportXml = useCallback(() => {
    if (!currentProcess) {
      message.warning(t('designer.actions.noProcessToExport'))
      return
    }
    try {
      downloadTextFile(
        generateProcessXml(currentProcess),
        flowXmlFileName(currentProcess),
        'application/xml;charset=utf-8'
      )
      message.success(t('designer.actions.exportXmlSuccess'))
    } catch (error) {
      logger.error('Failed to export XML', toError(error))
      message.error(t('designer.actions.exportXmlFailed'))
    }
  }, [currentProcess, message, t])

  const handleImportXml = useCallback(() => {
    if (!currentProcess) return
    const documentId = dispatch(
      (_dispatch, getState) => getState().editor.present.documentRequestId
    )
    const ownsDocument = () =>
      mounted.current &&
      dispatch((_dispatch, getState) => {
        const editor = getState().editor.present
        return editor.documentRequestId === documentId && !editor.isLoading
      })
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = '.xml,.bpmn'
    input.onchange = async (event: Event) => {
      const file = (event.target as HTMLInputElement).files?.[0]
      if (!file) return
      try {
        const xmlText = await file.text()
        if (!ownsDocument()) return
        await dispatch(
          importXml({ xml: xmlText, type: currentProcess.type, preferXmlName: true })
        ).unwrap()
        if (!ownsDocument()) return
        message.success(t('designer.actions.importXmlSuccess'))
      } catch (error) {
        if (!ownsDocument()) return
        logger.error('Failed to import XML', toError(error))
        message.error(
          t('designer.actions.importXmlFailed', {
            message: toError(error, t('designer.actions.importXmlInvalid')).message,
          })
        )
      }
    }
    document.body.appendChild(input)
    input.click()
    document.body.removeChild(input)
  }, [currentProcess, dispatch, message, t])

  const handleExportImage = useCallback(async () => {
    if (!graph) {
      message.warning(t('designer.actions.canvasNotReady'))
      return
    }
    try {
      await exportBoth(graph, {
        filename: currentProcess?.name || `flow_${Date.now()}`,
        backgroundColor: 'var(--color-bg-base, #ffffff)',
      })
      message.success(t('designer.actions.exportImageSuccess'))
    } catch (error) {
      logger.error('Failed to export canvas image', toError(error))
      message.error(t('designer.actions.exportImageFailed'))
    }
  }, [currentProcess, graph, message, t])

  return { handleExportImage, handleExportXml, handleImportXml }
}

function useProcessLifecycleActions({
  currentProcess,
  dispatch,
  navigate,
  operateBindingRef,
  t,
}: {
  currentProcess: UnifiedProcessDefinition | null
  dispatch: AppDispatch
  navigate: NavigateFunction
  operateBindingRef: OperateBindingRef
  t: TFunction
}) {
  const { message } = App.useApp()
  const handleDuplicate = useCallback(async () => {
    if (!currentProcess) return
    if (operateBindingRef.current) {
      message.warning(t('designer.actions.duplicateOperateUnavailable'))
      return
    }

    try {
      const xml = generateProcessXml(currentProcess)
      const duplicate = await dispatch(
        createProcess({
          type: currentProcess.type,
          name: `${currentProcess.name}${t('designer.actions.duplicateSuffix')}`,
          definition: xml,
        })
      ).unwrap()
      openDesignerFromWorkspaceProcess(
        navigate,
        {
          processId: duplicate.id,
          modelType: duplicate.type,
        },
        { replace: true }
      )
      message.success(t('designer.actions.duplicateSuccess'))
    } catch (error) {
      logger.error('Failed to duplicate flow', toError(error))
      message.error(t('designer.actions.duplicateFailed'))
    }
  }, [currentProcess, dispatch, message, navigate, operateBindingRef, t])

  const handleDelete = useCallback(async () => {
    if (operateBindingRef.current) {
      try {
        const { deleteProcess: deleteOperateProcess } = await import('@/shared/api/processes')
        await deleteOperateProcess(
          operateBindingRef.current.processCode,
          operateBindingRef.current.revision
        )
        message.success(t('designer.delete.operateSuccess'))
        void navigate(ROUTES.OPERATE_PROCESSES)
      } catch (error) {
        logger.error('Failed to delete operate flow', toError(error))
        message.error(t('designer.actions.deleteFailed'))
      }
      return
    }

    if (!currentProcess?.id) return
    try {
      await dispatch(deleteProcess(currentProcess.id)).unwrap()
      message.success(t('designer.actions.deleteSuccess'))
      void navigate(ROUTES.BUILD)
    } catch (error) {
      logger.error('Failed to delete workspace flow', toError(error))
      message.error(t('designer.actions.deleteFailed'))
    }
  }, [currentProcess, dispatch, message, navigate, operateBindingRef, t])

  return { handleDelete, handleDuplicate }
}

function usePanelActions({
  currentProcessRef,
  dispatch,
  t,
}: {
  currentProcessRef: DesignerProcessRef
  dispatch: AppDispatch
  t: TFunction
}) {
  const { message } = App.useApp()
  const collapseLeftForRightPanel = () => window.innerWidth <= 768
  const handleValidate = useCallback(() => {
    dispatch(
      openRightPanelTab({
        tab: 'validation',
        collapseLeft: collapseLeftForRightPanel(),
      })
    )
    const flow = currentProcessRef.current
    if (!flow) return
    const result = validateDesignerProcess(flow)
    if (result.valid && result.issues.length === 0) {
      message.success(t('designer.validation.toast.passed'))
    } else if (result.errorCount > 0) {
      message.error(t('designer.validation.toast.errors', { count: result.errorCount }))
    } else {
      message.warning(t('designer.validation.toast.warnings', { count: result.warningCount }))
    }
  }, [currentProcessRef, dispatch, message, t])

  const handleDebug = useCallback(() => {
    dispatch(
      openRightPanelTab({
        tab: 'debug',
        collapseLeft: collapseLeftForRightPanel(),
      })
    )
  }, [dispatch])

  const handleToggleGrid = useCallback(() => {
    dispatch(toggleGridlines())
  }, [dispatch])

  const handleSearch = useCallback(() => {
    dispatch(togglePanel('showSearch'))
  }, [dispatch])

  const handleShowShortcuts = useCallback(() => {
    dispatch(togglePanel('showShortcuts'))
  }, [dispatch])

  const handleShowHelp = useCallback(() => {
    dispatch(togglePanel('showHelpDocs'))
  }, [dispatch])

  return {
    handleDebug,
    handleSearch,
    handleShowHelp,
    handleShowShortcuts,
    handleToggleGrid,
    handleValidate,
  }
}

function useXmlEditorState({
  currentProcess,
  dispatch,
  t,
}: {
  currentProcess: UnifiedProcessDefinition | null
  dispatch: AppDispatch
  t: TFunction
}) {
  const { message } = App.useApp()
  const [open, setOpen] = useState(false)
  const [value, setValue] = useState('')
  const [baselineValue, setBaselineValue] = useState('')
  const documentId = dispatch((_dispatch, getState) => getState().editor.present.documentRequestId)
  const generation = useRef(0)
  const pending = useRef<{ abort: () => void } | null>(null)
  const cancelApply = useCallback(() => {
    generation.current += 1
    pending.current?.abort()
    pending.current = null
  }, [])

  useLayoutEffect(() => {
    cancelApply()
    setOpen(false)
    setValue('')
    setBaselineValue('')
    return cancelApply
  }, [documentId, cancelApply])

  const handleClose = useCallback(() => {
    cancelApply()
    setOpen(false)
    setBaselineValue('')
  }, [cancelApply])

  const handleChange = useCallback(
    (next: string) => {
      cancelApply()
      setValue(next)
    },
    [cancelApply]
  )

  const handleShow = useCallback(() => {
    if (!currentProcess) return
    cancelApply()
    const xml = generateProcessXml(currentProcess)
    setBaselineValue(xml)
    setValue(xml)
    setOpen(true)
  }, [currentProcess, cancelApply])

  const handleApply = useCallback(async () => {
    if (!currentProcess || !open || pending.current) return
    const ownsDocument = () =>
      dispatch((_dispatch, getState) => {
        const editor = getState().editor.present
        return editor.documentRequestId === documentId
      })
    if (!ownsDocument()) return
    const request = ++generation.current
    try {
      const applying = dispatch(importXml({ xml: value, type: currentProcess.type }))
      pending.current = applying
      await applying.unwrap()
      if (request !== generation.current || !ownsDocument()) return
      setOpen(false)
      message.success(t('designer.xmlEditor.applySuccess'))
    } catch (error) {
      if (request !== generation.current || !ownsDocument()) return
      logger.error('Failed to apply XML editor content', toError(error))
      message.error(
        t('designer.actions.xmlFormatError', {
          message: toError(error, t('designer.actions.xmlParseFailed')).message,
        })
      )
    } finally {
      if (request === generation.current) pending.current = null
    }
  }, [currentProcess, dispatch, documentId, message, open, t, value])

  const state = useMemo(
    () => ({
      open,
      title: currentProcess
        ? t('designer.actions.xmlEditorTitle', { name: currentProcess.name })
        : t('designer.actions.xmlEditorTitleDefault'),
      value,
      baselineValue,
      onChange: handleChange,
      onApply: handleApply,
      onClose: handleClose,
    }),
    [baselineValue, currentProcess, handleApply, handleChange, handleClose, open, t, value]
  )

  return { handleShowXmlEditor: handleShow, xmlEditorState: state }
}

function useProcessVariablesDialog() {
  const [open, setOpen] = useState(false)
  const handleOpen = useCallback(() => setOpen(true), [])
  const handleClose = useCallback(() => setOpen(false), [])

  const state = useMemo(
    () => ({
      open,
      onOpen: handleOpen,
      onClose: handleClose,
    }),
    [handleClose, handleOpen, open]
  )

  return { handleShowVariables: handleOpen, processVariablesDialog: state }
}

export function useDesignerPageActions({
  currentProcess,
  graph,
  dispatch,
  navigate,
  copyNodes,
  pasteNodes,
  selectedNodeId,
  entryPayload,
  operateBinding,
}: UseDesignerPageActionsOptions) {
  const { t } = useTranslation()
  const currentProcessRef = useLatestRef(currentProcess)
  const operateBindingRef = useLatestRef(operateBinding)
  const exitRoute =
    entryPayload?.source === 'operateProcessCode' ? ROUTES.OPERATE_PROCESSES : ROUTES.BUILD

  const { handleBack, handleSave } = useSaveNavigationActions({
    currentProcessRef,
    dispatch,
    exitRoute,
    navigate,
    operateBindingRef,
    t,
  })
  const { handleCopy, handlePaste } = useClipboardActions({
    copyNodes,
    pasteNodes,
    selectedNodeId,
    t,
  })
  const { handleUpdateName } = useMetadataActions(currentProcess, dispatch)
  const { handleExportImage, handleExportXml, handleImportXml } = useFileActions({
    currentProcess,
    dispatch,
    graph,
    t,
  })
  const { handleDelete, handleDuplicate } = useProcessLifecycleActions({
    currentProcess,
    dispatch,
    navigate,
    operateBindingRef,
    t,
  })
  const {
    handleDebug,
    handleSearch,
    handleShowHelp,
    handleShowShortcuts,
    handleToggleGrid,
    handleValidate,
  } = usePanelActions({ currentProcessRef, dispatch, t })
  const { handleShowXmlEditor, xmlEditorState } = useXmlEditorState({ currentProcess, dispatch, t })
  const { handleShowVariables, processVariablesDialog } = useProcessVariablesDialog()

  const handleUndo = useCallback(() => {
    dispatch(UndoActionCreators.undo())
  }, [dispatch])

  const handleRedo = useCallback(() => {
    dispatch(UndoActionCreators.redo())
  }, [dispatch])

  const actions = useMemo(
    () => ({
      onBack: handleBack,
      onSave: handleSave,
      onUndo: handleUndo,
      onRedo: handleRedo,
      onCopy: handleCopy,
      onPaste: handlePaste,
      onUpdateName: handleUpdateName,
      onExportXml: handleExportXml,
      onImportXml: handleImportXml,
      onExportImage: handleExportImage,
      onDuplicate: handleDuplicate,
      onDelete: handleDelete,
      onValidate: handleValidate,
      onDebug: handleDebug,
      onToggleGrid: handleToggleGrid,
      onSearch: handleSearch,
      onShowShortcuts: handleShowShortcuts,
      onShowVariables: handleShowVariables,
      onShowHelp: handleShowHelp,
      onShowXmlEditor: handleShowXmlEditor,
    }),
    [
      handleBack,
      handleCopy,
      handleDebug,
      handleDelete,
      handleDuplicate,
      handleExportImage,
      handleExportXml,
      handleImportXml,
      handleRedo,
      handleSave,
      handleSearch,
      handleShowHelp,
      handleShowShortcuts,
      handleShowVariables,
      handleShowXmlEditor,
      handleToggleGrid,
      handleUndo,
      handleUpdateName,
      handleValidate,
      handlePaste,
    ]
  )

  return {
    actions,
    xmlEditorState,
    processVariablesDialog,
  }
}
