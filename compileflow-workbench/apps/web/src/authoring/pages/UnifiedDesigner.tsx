import type { Graph } from '@antv/x6'
import { Alert, App, Button, Result, Skeleton, Tabs } from 'antd'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'

import BpmnDesigner from '../designer/components/BpmnDesigner'
import DesignerHeader from '../designer/components/DesignerHeader'
import DesignerStatusBar from '../designer/components/DesignerStatusBar'
import LocalSnapshotsModal from '../designer/components/LocalSnapshotsModal'
import TbbpmDesigner from '../designer/components/TbbpmDesigner'
import { XmlCodeEditorPanel } from '../designer/components/XmlCodeEditorPanel'
import XmlEditorModal from '../designer/components/XmlEditorModal'
import { useAutoSave } from '../designer/hooks/useAutoSave'
import { useClipboard } from '../designer/hooks/useClipboard'
import { useDesignerPageActions } from '../designer/hooks/useDesignerPageActions'
import {
  createDesignerShortcuts,
  useKeyboardShortcuts,
} from '../designer/hooks/useKeyboardShortcuts'
import { useUnsavedChangesGuard } from '../designer/hooks/useUnsavedChangesGuard'
import { useXmlDraft } from '../designer/hooks/useXmlDraft'
import type { UnifiedProcessDefinition } from '../designer/types/flowDefinition'
import { useProcessInitialization } from '../hooks/useProcessInitialization'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { generateBpmnXml } from '@/authoring/designer/serialization/bpmnXmlCodec'
import { generateTbbpmXml } from '@/authoring/designer/serialization/tbbpmXmlCodec'
import { translateParseWarning } from '@/authoring/designer/serialization/xmlParseWarningI18n'
import {
  clearWarnings,
  importXml,
  selectCanRedo,
  selectCanUndo,
  selectCurrentProcess,
  selectIsModified,
  selectIsSaving,
  selectOperateBinding,
  selectWarnings,
} from '@/authoring/designer/store/editorSlice'
import {
  selectSelectedNodeId,
  selectShowGridlines,
  selectViewMode,
  switchView,
} from '@/authoring/designer/store/uiSlice'
import { type DesignerViewMode, isDesignerViewMode } from '@/authoring/designer/types'
import { ROUTES } from '@/shared/constants'
import { toError } from '@/shared/errors'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { createLogger } from '@/shared/logging/logger'
import {
  type DesignerEntryDescriptor,
  parseDesignerEntryDescriptor,
} from '@/shared/services/designerNavigation'
import './UnifiedDesigner.css'
import './DesignerPage.css'

const log = createLogger('UnifiedDesigner')

type DesignerPageActionsResult = ReturnType<typeof useDesignerPageActions>
type DesignerActions = DesignerPageActionsResult['actions']
type ProcessVariablesDialogState = DesignerPageActionsResult['processVariablesDialog']
type ProcessInitializationState = ReturnType<typeof useProcessInitialization>
type ParseWarnings = ReturnType<typeof selectWarnings>

function useCurrentXml(currentProcess: UnifiedProcessDefinition | null) {
  return useMemo(() => {
    if (!currentProcess) return { xml: '', error: null as string | null }

    try {
      const xml =
        currentProcess.type === 'BPMN'
          ? generateBpmnXml(currentProcess)
          : generateTbbpmXml(currentProcess)
      return { xml, error: null }
    } catch (error) {
      const normalizedError = toError(error)
      log.error('XML generation failed', normalizedError)
      return { xml: '', error: normalizedError.message }
    }
  }, [currentProcess])
}

function DesignerLoadingState({
  flowInit,
  onBack,
}: {
  flowInit: ProcessInitializationState
  onBack: () => void
}) {
  const { t } = useTranslation()

  if (flowInit.status === 'error') {
    return (
      <div className="unified-designer-loading">
        <Result
          status="error"
          title={t('designer.flowInit.errorTitle')}
          subTitle={flowInit.errorMessage ?? t('designer.flowInit.errorGeneric')}
          extra={[
            <Button key="retry" type="primary" onClick={flowInit.retry}>
              {t('designer.flowInit.retry')}
            </Button>,
            <Button key="back" onClick={onBack}>
              {t('designer.flowInit.backToWorkspace')}
            </Button>,
          ]}
        />
      </div>
    )
  }

  return (
    <div className="unified-designer-loading">
      <Skeleton.Input active className="loading-header-skeleton" />
      <div className="loading-body-skeleton">
        <Skeleton.Input active className="loading-left-skeleton" />
        <Skeleton active className="loading-center-skeleton" paragraph={{ rows: 8 }} />
        <Skeleton.Input active className="loading-right-skeleton" />
      </div>
    </div>
  )
}

function DesignerHeaderBar({
  actions,
  canRedo,
  canUndo,
  currentProcess,
  isModified,
  isSaving,
  operateProcessCode,
  onShowLocalSnapshots,
  showGridlines,
}: {
  actions: DesignerActions
  canRedo: boolean
  canUndo: boolean
  currentProcess: UnifiedProcessDefinition
  isModified: boolean
  isSaving: boolean
  operateProcessCode: string | null
  onShowLocalSnapshots: () => void
  showGridlines: boolean
}) {
  return (
    <DesignerHeader
      currentProcess={currentProcess}
      isModified={isModified}
      isSaving={isSaving}
      canUndo={canUndo}
      canRedo={canRedo}
      onBack={actions.onBack}
      onSave={actions.onSave}
      onUndo={actions.onUndo}
      onRedo={actions.onRedo}
      onCopy={actions.onCopy}
      onPaste={actions.onPaste}
      onUpdateName={actions.onUpdateName}
      onExportXml={actions.onExportXml}
      onImportXml={actions.onImportXml}
      onExportImage={actions.onExportImage}
      onDuplicate={actions.onDuplicate}
      onDelete={actions.onDelete}
      onValidate={actions.onValidate}
      onDebug={actions.onDebug}
      onToggleGrid={actions.onToggleGrid}
      showGridlines={showGridlines}
      onSearch={actions.onSearch}
      onShowShortcuts={actions.onShowShortcuts}
      onShowVariables={actions.onShowVariables}
      onShowHelp={actions.onShowHelp}
      onShowXmlEditor={actions.onShowXmlEditor}
      onShowLocalSnapshots={onShowLocalSnapshots}
      operateProcessCode={operateProcessCode}
    />
  )
}

function DesignerWarnings({
  onDismiss,
  warnings,
}: {
  onDismiss: () => void
  warnings: ParseWarnings
}) {
  const { t } = useTranslation()

  if (warnings.length === 0) return null

  return (
    <Alert
      type="warning"
      showIcon
      closable
      className="unified-designer-warnings"
      title={t('designer.warnings.title')}
      description={
        <ul className="unified-designer-warnings-list">
          {warnings.map((warning, index) => (
            <li key={`${warning.code}-${warning.location ?? index}`}>
              {translateParseWarning(warning, t)}
            </li>
          ))}
        </ul>
      }
      onClose={onDismiss}
      closeText={t('designer.warnings.dismiss')}
    />
  )
}

function XmlGenerationErrorAlert({ error, visible }: { error: string | null; visible: boolean }) {
  const { t } = useTranslation()

  if (!error || !visible) return null

  return (
    <Alert
      type="error"
      showIcon
      className="unified-designer-warnings"
      title={t('designer.xmlEditor.generationFailed')}
      description={error}
    />
  )
}

function DesignerViewTabs({
  onChange,
  viewMode,
}: {
  onChange: (viewMode: DesignerViewMode) => void
  viewMode: DesignerViewMode
}) {
  const { t } = useTranslation()

  return (
    <Tabs
      activeKey={viewMode}
      onChange={(key) => {
        if (isDesignerViewMode(key)) onChange(key)
      }}
      data-testid="designer-view-tabs"
      aria-label={t('designer.view.tabsLabel')}
      items={[
        {
          key: 'visual',
          label: <span data-testid="designer-tab-visual">{t('designer.view.visual')}</span>,
        },
        {
          key: 'xml',
          label: <span data-testid="designer-tab-xml">{t('designer.view.code')}</span>,
        },
        {
          key: 'split',
          label: <span data-testid="designer-tab-split">{t('designer.view.preview')}</span>,
        },
      ]}
      styles={{ header: { marginBottom: 0, padding: '0 16px', background: 'var(--bg-secondary)' } }}
      size="small"
    />
  )
}

function DesignerCanvas({
  modelType,
  onGraphReady,
  processVariablesDialog,
}: {
  modelType: UnifiedProcessDefinition['type']
  onGraphReady: (graph: Graph | null) => void
  processVariablesDialog: ProcessVariablesDialogState
}) {
  if (modelType === 'BPMN') {
    return (
      <BpmnDesigner onGraphReady={onGraphReady} processVariablesDialog={processVariablesDialog} />
    )
  }

  return (
    <TbbpmDesigner onGraphReady={onGraphReady} processVariablesDialog={processVariablesDialog} />
  )
}

function DesignerBody({
  children,
  xmlDraft,
  isModified,
  viewMode,
}: {
  children: React.ReactNode
  xmlDraft: ReturnType<typeof useXmlDraft>
  isModified: boolean
  viewMode: DesignerViewMode
}) {
  const showXml = viewMode === 'xml' || viewMode === 'split'

  return (
    <div
      className={`unified-designer-body${viewMode === 'split' ? ' unified-designer-body--split' : ''}`}
    >
      <div
        className={`unified-designer-canvas-pane${viewMode === 'xml' ? ' unified-designer-canvas-pane--hidden' : ''}`}
        aria-hidden={viewMode === 'xml'}
      >
        {children}
      </div>

      {showXml && (
        <div className="unified-designer-xml-pane">
          <XmlCodeEditorPanel
            editor={xmlDraft}
            showCanvasStaleHint={viewMode === 'split' && isModified}
          />
        </div>
      )}
    </div>
  )
}

function DesignerModals({
  onCloseLocalSnapshots,
  localSnapshotsOpen,
  xmlEditorState,
}: {
  onCloseLocalSnapshots: () => void
  localSnapshotsOpen: boolean
  xmlEditorState: DesignerPageActionsResult['xmlEditorState']
}) {
  return (
    <>
      <XmlEditorModal
        open={xmlEditorState.open}
        title={xmlEditorState.title}
        value={xmlEditorState.value}
        baselineValue={xmlEditorState.baselineValue}
        onChange={xmlEditorState.onChange}
        onApply={xmlEditorState.onApply}
        onClose={xmlEditorState.onClose}
      />
      <LocalSnapshotsModal open={localSnapshotsOpen} onClose={onCloseLocalSnapshots} />
    </>
  )
}

function UnifiedDesignerContent({ entryPayload }: { entryPayload: DesignerEntryDescriptor }) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const navigate = useNavigate()
  const dispatch = useAppDispatch()
  const flowInit = useProcessInitialization({ entryPayload })

  const currentProcess = useAppSelector(selectCurrentProcess)
  usePageTitle('pageTitle.build', currentProcess?.name)
  const selectedNodeId = useAppSelector(selectSelectedNodeId)
  const isModified = useAppSelector(selectIsModified)
  const isSaving = useAppSelector(selectIsSaving)
  const canUndo = useAppSelector(selectCanUndo)
  const canRedo = useAppSelector(selectCanRedo)
  const viewMode = useAppSelector(selectViewMode)
  const showGridlines = useAppSelector(selectShowGridlines)
  const operateBinding = useAppSelector(selectOperateBinding)
  const warnings = useAppSelector(selectWarnings)
  const documentId = useAppSelector((state) => state.editor.present.documentRequestId)

  const { copyNodes, pasteNodes } = useClipboard()
  const [graph, setGraph] = useState<Graph | null>(null)
  const [localSnapshotsOpen, setLocalSnapshotsOpen] = useState(false)

  const { actions, xmlEditorState, processVariablesDialog } = useDesignerPageActions({
    currentProcess,
    graph,
    dispatch,
    navigate,
    copyNodes,
    pasteNodes,
    selectedNodeId,
    entryPayload,
    operateBinding,
  })

  const { xml: currentXml, error: xmlGenerationError } = useCurrentXml(currentProcess)
  const handleApplyXmlFromEditor = useCallback(
    async (xml: string, signal: AbortSignal) => {
      if (!currentProcess) throw new Error('No flow to edit')
      await dispatch(importXml({ xml, type: currentProcess.type }, { signal })).unwrap()
      signal.throwIfAborted()
      message.success(t('designer.xmlEditor.applySuccess'))
    },
    [currentProcess, dispatch, message, t]
  )
  const xmlDraft = useXmlDraft({
    sourceXml: currentXml,
    documentId,
    onApply: handleApplyXmlFromEditor,
  })
  const saveDocument = useCallback(
    () => xmlDraft.save(actions.onSave),
    [xmlDraft.save, actions.onSave]
  )
  const hasUnsavedChanges = isModified || xmlDraft.isDirty

  const shortcuts = createDesignerShortcuts({
    onSave: saveDocument,
    onUndo: actions.onUndo,
    onRedo: actions.onRedo,
    onCopy: actions.onCopy,
    onPaste: actions.onPaste,
  })

  useKeyboardShortcuts(shortcuts)
  const unsavedChangesDialog = useUnsavedChangesGuard(hasUnsavedChanges, saveDocument)

  const { autoSavePending } = useAutoSave({
    isModified,
    isSaving,
    canSave:
      Boolean(currentProcess) && !xmlGenerationError && !xmlDraft.isDirty && !xmlDraft.isApplying,
    dispatch,
  })

  const handleBackToWorkspace = useCallback(() => {
    void navigate(flowInit.exitRoute ?? ROUTES.BUILD)
  }, [flowInit.exitRoute, navigate])

  const handleDismissWarnings = useCallback(() => {
    dispatch(clearWarnings())
  }, [dispatch])

  const handleSwitchView = useCallback(
    (nextViewMode: DesignerViewMode) => {
      dispatch(switchView(nextViewMode))
    },
    [dispatch]
  )

  const showXml = viewMode === 'xml' || viewMode === 'split'

  if (flowInit.status !== 'ready' || !currentProcess) {
    return <DesignerLoadingState flowInit={flowInit} onBack={handleBackToWorkspace} />
  }

  return (
    <div className="unified-designer-container" data-type={currentProcess.type.toLowerCase()}>
      <DesignerHeaderBar
        key={documentId}
        actions={{ ...actions, onSave: saveDocument }}
        canRedo={canRedo}
        canUndo={canUndo}
        currentProcess={currentProcess}
        isModified={hasUnsavedChanges}
        isSaving={isSaving}
        operateProcessCode={operateBinding?.processCode ?? null}
        onShowLocalSnapshots={() => setLocalSnapshotsOpen(true)}
        showGridlines={showGridlines}
      />
      <DesignerWarnings warnings={warnings} onDismiss={handleDismissWarnings} />
      <XmlGenerationErrorAlert error={xmlGenerationError} visible={showXml} />
      <DesignerViewTabs viewMode={viewMode} onChange={handleSwitchView} />
      <DesignerBody xmlDraft={xmlDraft} isModified={isModified} viewMode={viewMode}>
        <DesignerCanvas
          modelType={currentProcess.type}
          onGraphReady={setGraph}
          processVariablesDialog={processVariablesDialog}
        />
      </DesignerBody>
      <DesignerStatusBar
        graph={graph}
        isModified={hasUnsavedChanges}
        isSaving={isSaving}
        autoSavePending={autoSavePending}
      />
      <DesignerModals
        localSnapshotsOpen={localSnapshotsOpen}
        xmlEditorState={xmlEditorState}
        onCloseLocalSnapshots={() => setLocalSnapshotsOpen(false)}
      />
      {unsavedChangesDialog}
    </div>
  )
}

function InvalidDesignerEntry() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  usePageTitle('pageTitle.build')

  return (
    <div className="unified-designer-loading">
      <Result
        status="warning"
        title={t('designer.flowInit.invalidEntry')}
        extra={
          <Button type="primary" onClick={() => navigate(ROUTES.BUILD)}>
            {t('designer.flowInit.backToWorkspace')}
          </Button>
        }
      />
    </div>
  )
}

export default function UnifiedDesigner() {
  const [searchParams] = useSearchParams()

  try {
    return <UnifiedDesignerContent entryPayload={parseDesignerEntryDescriptor(searchParams)} />
  } catch {
    return <InvalidDesignerEntry />
  }
}
