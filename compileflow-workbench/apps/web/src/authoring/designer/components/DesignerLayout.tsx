import {
  AppstoreOutlined,
  CodeOutlined,
  LeftOutlined,
  NodeIndexOutlined,
  RightOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import type { Graph } from '@antv/x6'
import { App, Layout, Segmented, Tooltip } from 'antd'
import {
  lazy,
  ReactNode,
  Suspense,
  useCallback,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { useTranslation } from 'react-i18next'

import type { DesignerContextValue } from '../context'
import { useDesignerContext } from '../context'
import { selectCurrentProcess } from '../store/editorSlice'
import type { UiState } from '../store/uiSlice'
import {
  hideContextMenu,
  openRightPanelTab,
  selectContextMenu,
  selectEdge,
  selectLeftPanelCollapsed,
  selectNode,
  selectRightPanelCollapsed,
  selectRightPanelTab,
  selectSelectedEdgeId,
  selectSelectedNodeId,
  selectShowHelpDocs,
  selectShowSearch,
  selectShowShortcuts,
  setSidePanelsCollapsed,
  setRightPanelTab,
  toggleLeftPanel,
  togglePanel,
  toggleRightPanel,
} from '../store/uiSlice'
import type { ProcessConnection, UnifiedProcessDefinition } from '../types/flowDefinition'

import ContextMenu from './ContextMenu'
import { SuspenseFallback } from './LoadingFeedback'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import type { AppDispatch } from '@/app/store'
import type { ProcessSimulationEngine } from '@/authoring/designer/simulation/ProcessSimulationEngine'
import { createSimulationEngine } from '@/authoring/designer/simulation/ProcessSimulationEngine'
import { useTheme } from '@/shared/contexts/ThemeContext'
import { useCompactContainer } from '@/shared/hooks/useCompactContainer'

const ValidationResultPanel = lazy(() => import('./ValidationResultPanel'))
const ProcessDebuggerPanel = lazy(() => import('./ProcessDebuggerPanel'))
const EdgePropertiesPanel = lazy(() => import('./EdgePropertiesPanel'))
const KeyboardShortcutsModal = lazy(() => import('./KeyboardShortcutsModal'))
const ProcessVariablesDialog = lazy(() => import('./ProcessVariablesDialog'))
const HelpDocumentation = lazy(() => import('./HelpDocumentation'))
const NodeSearch = lazy(() => import('./NodeSearch'))

const { Sider } = Layout

type RightPanelTab = UiState['rightPanelTab']
type RightTab = Exclude<RightPanelTab, 'edge' | 'none'>
type ContextMenuState = UiState['contextMenu']
type GraphRef = DesignerContextValue['graphRef']
type IdleHandle = number

export interface DesignerLayoutProps {
  layoutClassName: string
  palette: ReactNode
  canvas: ReactNode
  propertiesPanel: ReactNode
  onCopy: () => void
  onPaste: () => void
  onDelete: () => void
  processVariablesDialog: {
    open: boolean
    onClose: () => void
  }
}

interface LayoutState {
  contextMenu: ContextMenuState
  currentProcess: UnifiedProcessDefinition | null
  leftPanelCollapsed: boolean
  rightPanelCollapsed: boolean
  rightPanelTab: RightPanelTab
  selectedEdge: ProcessConnection | null
  selectedNodeId: string | null
  showHelpDocs: boolean
  showSearch: boolean
  showShortcuts: boolean
}

interface LeftSiderProps {
  collapsed: boolean
  layoutClassName: string
  onToggle: () => void
  palette: ReactNode
}

interface ContentAreaProps {
  canvas: ReactNode
  contextMenuActions: ContextMenuActions
  contextMenuState: ContextMenuState
  layoutClassName: string
  leftPanelCollapsed: boolean
  onToggleLeftPanel: () => void
  onToggleRightPanel: () => void
  rightPanelCollapsed: boolean
}

interface RightSiderProps {
  emptyPanel: boolean
  flowDefinition: UnifiedProcessDefinition
  graphHighlights: GraphHighlightHandlers
  layoutClassName: string
  onToggleRightPanel: () => void
  propertiesPanel: ReactNode
  rightPanelCollapsed: boolean
  rightPanelTab: RightPanelTab
  selectedEdge: ProcessConnection | null
  selectedNodeId: string | null
  segmentedValue: RightTab
  simulationEngine: ProcessSimulationEngine
}

interface DesignerModalsProps {
  showHelpDocs: boolean
  showSearch: boolean
  showShortcuts: boolean
  processVariablesDialog: DesignerLayoutProps['processVariablesDialog']
}

interface ContextMenuActions {
  onBreakpoint: () => void
  onClose: () => void
  onCopy: () => void
  onDelete: () => void
  onEdit: () => void
  onPaste: () => void
  onSelectAll: () => void
}

interface GraphHighlightHandlers {
  onDebugHighlightConnection: (connectionId: string | null) => void
  onDebugHighlightNode: (nodeId: string | null) => void
  onHighlightValidationConnections: (connectionIds: string[]) => void
  onHighlightValidationNodes: (nodeIds: string[]) => void
}

const requestIdle =
  typeof requestIdleCallback !== 'undefined'
    ? requestIdleCallback
    : (callback: IdleRequestCallback) => window.setTimeout(callback, 0)

const cancelIdle =
  typeof cancelIdleCallback !== 'undefined'
    ? cancelIdleCallback
    : (id: IdleHandle) => window.clearTimeout(id)

const resetNodeStyle = (graph: Graph, nodeId: string) => {
  const cell = graph.getCellById(nodeId)
  if (cell?.isNode()) {
    cell.setAttrs({ body: { stroke: 'var(--color-border-light, #d9d9d9)', strokeWidth: 1 } })
  }
}

const resetEdgeStyle = (graph: Graph, edgeId: string) => {
  const edge = graph.getCellById(edgeId)
  if (edge?.isEdge()) {
    edge.setAttrs({ line: { stroke: 'var(--color-border-light, #d9d9d9)', strokeWidth: 1 } })
  }
}

const highlightNodeStyle = (graph: Graph, nodeId: string, color: string) => {
  const cell = graph.getCellById(nodeId)
  if (cell?.isNode()) {
    cell.setAttrs({ body: { stroke: color, strokeWidth: 2 } })
    return cell
  }
  return null
}

const highlightEdgeStyle = (graph: Graph, edgeId: string, color: string) => {
  const edge = graph.getCellById(edgeId)
  if (edge?.isEdge()) {
    edge.setAttrs({ line: { stroke: color, strokeWidth: 2 } })
  }
}

function requireCurrentProcess(
  currentProcess: UnifiedProcessDefinition | null
): UnifiedProcessDefinition {
  if (!currentProcess) throw new Error('Designer layout requires an initialized process')
  return currentProcess
}

const resolveSegmentedValue = (rightPanelTab: RightPanelTab): RightTab => {
  if (rightPanelTab === 'validation') return 'validation'
  if (rightPanelTab === 'debug') return 'debug'
  return 'properties'
}

function useLayoutState(): LayoutState {
  const currentProcess = useAppSelector(selectCurrentProcess)
  const selectedEdgeId = useAppSelector(selectSelectedEdgeId)
  return {
    contextMenu: useAppSelector(selectContextMenu),
    currentProcess,
    leftPanelCollapsed: useAppSelector(selectLeftPanelCollapsed),
    rightPanelCollapsed: useAppSelector(selectRightPanelCollapsed),
    rightPanelTab: useAppSelector(selectRightPanelTab),
    selectedEdge:
      currentProcess?.connections.find((connection) => connection.id === selectedEdgeId) ?? null,
    selectedNodeId: useAppSelector(selectSelectedNodeId),
    showHelpDocs: useAppSelector(selectShowHelpDocs),
    showSearch: useAppSelector(selectShowSearch),
    showShortcuts: useAppSelector(selectShowShortcuts),
  }
}

function useSimulationEngine(flowDefinition: UnifiedProcessDefinition) {
  return useMemo(() => createSimulationEngine(flowDefinition), [flowDefinition])
}

function useRightPanelTabs(dispatch: AppDispatch) {
  const { t } = useTranslation()
  const tabs = useMemo(
    (): { label: ReactNode; value: RightTab; icon: ReactNode }[] => [
      {
        label: t('designer.layout.tabProperties'),
        value: 'properties',
        icon: <AppstoreOutlined />,
      },
      {
        label: t('designer.layout.tabValidation'),
        value: 'validation',
        icon: <SafetyCertificateOutlined />,
      },
      { label: t('designer.layout.tabDebug'), value: 'debug', icon: <CodeOutlined /> },
    ],
    [t]
  )

  const handleTabChange = useCallback(
    (value: RightTab) => dispatch(setRightPanelTab(value)),
    [dispatch]
  )

  return { handleTabChange, tabs }
}

function useGraphHighlights(
  graphRef: GraphRef,
  flowDefinition: UnifiedProcessDefinition,
  tab: RightPanelTab
): GraphHighlightHandlers {
  const prevHighlightedNodesRef = useRef<Set<string>>(new Set())
  const prevHighlightedEdgesRef = useRef<Set<string>>(new Set())
  const prevDebugHighlightedNodeRef = useRef<string | null>(null)
  const prevDebugHighlightedEdgeRef = useRef<string | null>(null)
  const highlightNodesIdleHandle = useRef<IdleHandle>(0)
  const highlightEdgesIdleHandle = useRef<IdleHandle>(0)

  const onHighlightValidationNodes = useCallback(
    (nodeIds: string[]) => {
      const graph = graphRef.current
      if (!graph) return
      if (highlightNodesIdleHandle.current) cancelIdle(highlightNodesIdleHandle.current)

      const highlightSet = new Set(nodeIds)
      highlightNodesIdleHandle.current = requestIdle(() => {
        const currentGraph = graphRef.current
        if (currentGraph !== graph) return
        currentGraph.startBatch('highlight-nodes')
        prevHighlightedNodesRef.current.forEach((id) => resetNodeStyle(currentGraph, id))
        nodeIds.forEach((nodeId) =>
          highlightNodeStyle(currentGraph, nodeId, 'var(--color-error, #ff4d4f)')
        )
        currentGraph.stopBatch('highlight-nodes')
        prevHighlightedNodesRef.current = highlightSet

        const firstCell = nodeIds.length ? currentGraph.getCellById(nodeIds[0]) : null
        if (firstCell) currentGraph.centerCell(firstCell)
      })
    },
    [graphRef]
  )

  const onHighlightValidationConnections = useCallback(
    (connectionIds: string[]) => {
      const graph = graphRef.current
      if (!graph) return
      if (highlightEdgesIdleHandle.current) cancelIdle(highlightEdgesIdleHandle.current)

      const highlightSet = new Set(connectionIds)
      highlightEdgesIdleHandle.current = requestIdle(() => {
        const currentGraph = graphRef.current
        if (currentGraph !== graph) return
        currentGraph.startBatch('highlight-edges')
        prevHighlightedEdgesRef.current.forEach((id) => resetEdgeStyle(currentGraph, id))
        connectionIds.forEach((connectionId) =>
          highlightEdgeStyle(currentGraph, connectionId, 'var(--color-error, #ff4d4f)')
        )
        currentGraph.stopBatch('highlight-edges')
        prevHighlightedEdgesRef.current = highlightSet
      })
    },
    [graphRef]
  )

  const onDebugHighlightNode = useCallback(
    (nodeId: string | null) => {
      const graph = graphRef.current
      if (!graph) return
      const previousNodeId = prevDebugHighlightedNodeRef.current
      if (previousNodeId && previousNodeId !== nodeId) resetNodeStyle(graph, previousNodeId)
      if (nodeId) {
        const cell = highlightNodeStyle(graph, nodeId, 'var(--color-primary, #6b57ff)')
        if (cell) graph.centerCell(cell)
      }
      prevDebugHighlightedNodeRef.current = nodeId
    },
    [graphRef]
  )

  const onDebugHighlightConnection = useCallback(
    (connectionId: string | null) => {
      const graph = graphRef.current
      if (!graph) return
      const previousConnectionId = prevDebugHighlightedEdgeRef.current
      if (previousConnectionId && previousConnectionId !== connectionId)
        resetEdgeStyle(graph, previousConnectionId)
      if (connectionId) highlightEdgeStyle(graph, connectionId, 'var(--color-primary, #6b57ff)')
      prevDebugHighlightedEdgeRef.current = connectionId
    },
    [graphRef]
  )

  useLayoutEffect(
    () => () => {
      cancelIdle(highlightNodesIdleHandle.current)
      cancelIdle(highlightEdgesIdleHandle.current)
      const graph = graphRef.current
      if (graph) {
        prevHighlightedNodesRef.current.forEach((id) => resetNodeStyle(graph, id))
        prevHighlightedEdgesRef.current.forEach((id) => resetEdgeStyle(graph, id))
        if (prevDebugHighlightedNodeRef.current)
          resetNodeStyle(graph, prevDebugHighlightedNodeRef.current)
        if (prevDebugHighlightedEdgeRef.current)
          resetEdgeStyle(graph, prevDebugHighlightedEdgeRef.current)
      }
      prevHighlightedNodesRef.current.clear()
      prevHighlightedEdgesRef.current.clear()
      prevDebugHighlightedNodeRef.current = null
      prevDebugHighlightedEdgeRef.current = null
    },
    [graphRef, flowDefinition, tab]
  )

  return {
    onDebugHighlightConnection,
    onDebugHighlightNode,
    onHighlightValidationConnections,
    onHighlightValidationNodes,
  }
}

function useContextMenuActions({
  collapseLeftWhenOpeningPanel,
  contextMenu,
  currentProcess,
  dispatch,
  graphRef,
  onCopy,
  onDelete,
  onPaste,
  simulationEngine,
}: {
  collapseLeftWhenOpeningPanel: boolean
  contextMenu: ContextMenuState
  currentProcess: UnifiedProcessDefinition
  dispatch: AppDispatch
  graphRef: GraphRef
  onCopy: () => void
  onDelete: () => void
  onPaste: () => void
  simulationEngine: ProcessSimulationEngine
}): ContextMenuActions {
  const { message } = App.useApp()
  const { t } = useTranslation()

  const onEdit = useCallback(() => {
    if (contextMenu.type === 'node' && contextMenu.targetId) {
      dispatch(selectNode(contextMenu.targetId))
      dispatch(openRightPanelTab({ tab: 'properties', collapseLeft: collapseLeftWhenOpeningPanel }))
      return
    }

    if (contextMenu.type === 'edge' && contextMenu.targetId) {
      const edge = currentProcess.connections.find(
        (connection) => connection.id === contextMenu.targetId
      )
      if (edge) {
        dispatch(selectNode(null))
        dispatch(selectEdge(edge.id))
        dispatch(openRightPanelTab({ tab: 'edge', collapseLeft: collapseLeftWhenOpeningPanel }))
      }
    }
  }, [
    collapseLeftWhenOpeningPanel,
    contextMenu.targetId,
    contextMenu.type,
    currentProcess,
    dispatch,
  ])

  const onBreakpoint = useCallback(() => {
    if (contextMenu.type !== 'node' || !contextMenu.targetId) return
    simulationEngine.addBreakpoint(contextMenu.targetId)
    message.success(t('designer.layout.breakpointSet', { nodeId: contextMenu.targetId }))
    dispatch(openRightPanelTab({ tab: 'debug', collapseLeft: collapseLeftWhenOpeningPanel }))
  }, [
    collapseLeftWhenOpeningPanel,
    contextMenu.targetId,
    contextMenu.type,
    dispatch,
    message,
    simulationEngine,
    t,
  ])

  const onSelectAll = useCallback(() => {
    const graph = graphRef.current
    if (graph) graph.select(graph.getNodes())
  }, [graphRef])

  const onDeleteTarget = useCallback(() => {
    if (contextMenu.targetId) onDelete()
  }, [contextMenu.targetId, onDelete])

  const onClose = useCallback(() => {
    dispatch(hideContextMenu())
  }, [dispatch])

  return {
    onBreakpoint,
    onClose,
    onCopy,
    onDelete: onDeleteTarget,
    onEdit,
    onPaste,
    onSelectAll,
  }
}

function CollapseTrigger({
  collapsed,
  direction,
  onClick,
  title,
}: {
  collapsed: boolean
  direction: 'left' | 'right'
  onClick: () => void
  title: string
}) {
  return (
    <button
      type="button"
      className={`sider-collapse-trigger sider-collapse-trigger--${direction}`}
      onClick={onClick}
      aria-label={title}
      title={title}
    >
      {direction === 'left' ? (
        collapsed ? (
          <RightOutlined />
        ) : (
          <LeftOutlined />
        )
      ) : collapsed ? (
        <LeftOutlined />
      ) : (
        <RightOutlined />
      )}
    </button>
  )
}

function DesignerLeftSider({ collapsed, layoutClassName, onToggle, palette }: LeftSiderProps) {
  const { t } = useTranslation()
  const { theme } = useTheme()
  const title = collapsed
    ? t('designer.layout.expandPalette')
    : t('designer.layout.collapsePalette')

  return (
    <Sider
      width={260}
      collapsedWidth={0}
      collapsed={collapsed}
      collapsible
      trigger={null}
      theme={theme === 'dark' ? 'dark' : 'light'}
      className={`${layoutClassName}-left-sider${collapsed ? '' : ' sider-visible'}`}
      aria-hidden={collapsed}
      inert={collapsed ? true : undefined}
    >
      {palette}
      <CollapseTrigger collapsed={collapsed} direction="left" onClick={onToggle} title={title} />
    </Sider>
  )
}

function FloatingSiderTriggers({
  leftPanelCollapsed,
  onToggleLeftPanel,
  onToggleRightPanel,
  rightPanelCollapsed,
}: Pick<
  ContentAreaProps,
  'leftPanelCollapsed' | 'onToggleLeftPanel' | 'onToggleRightPanel' | 'rightPanelCollapsed'
>) {
  const { t } = useTranslation()

  return (
    <>
      {leftPanelCollapsed && (
        <button
          type="button"
          className="sider-float-trigger sider-float-trigger--left"
          onClick={onToggleLeftPanel}
          aria-label={t('designer.layout.expandPalette')}
          title={t('designer.layout.expandPalette')}
        >
          <RightOutlined />
        </button>
      )}
      {rightPanelCollapsed && (
        <button
          type="button"
          className="sider-float-trigger sider-float-trigger--right"
          onClick={onToggleRightPanel}
          aria-label={t('designer.layout.expandProperties')}
          title={t('designer.layout.expandProperties')}
        >
          <LeftOutlined />
        </button>
      )}
    </>
  )
}

function DesignerContextMenu({
  actions,
  contextMenu,
}: {
  actions: ContextMenuActions
  contextMenu: ContextMenuState
}) {
  return (
    contextMenu.visible &&
    contextMenu.position && (
      <ContextMenu
        visible={contextMenu.visible}
        position={contextMenu.position}
        menuType={contextMenu.type}
        onClose={actions.onClose}
        onEdit={actions.onEdit}
        onCopy={actions.onCopy}
        onPaste={actions.onPaste}
        onDelete={actions.onDelete}
        onBreakpoint={actions.onBreakpoint}
        onSelectAll={actions.onSelectAll}
      />
    )
  )
}

function ContentArea({
  canvas,
  contextMenuActions,
  contextMenuState,
  layoutClassName,
  leftPanelCollapsed,
  onToggleLeftPanel,
  onToggleRightPanel,
  rightPanelCollapsed,
}: ContentAreaProps) {
  return (
    <div className={`${layoutClassName}-content`}>
      <FloatingSiderTriggers
        leftPanelCollapsed={leftPanelCollapsed}
        rightPanelCollapsed={rightPanelCollapsed}
        onToggleLeftPanel={onToggleLeftPanel}
        onToggleRightPanel={onToggleRightPanel}
      />
      {canvas}
      <DesignerContextMenu actions={contextMenuActions} contextMenu={contextMenuState} />
    </div>
  )
}

function RightPanelTabs({
  onChange,
  tabs,
  value,
}: {
  onChange: (value: RightTab) => void
  tabs: { label: ReactNode; value: RightTab; icon: ReactNode }[]
  value: RightTab
}) {
  const { t } = useTranslation()
  return (
    <div className="right-panel-tabs">
      <Segmented<RightTab>
        size="small"
        value={value}
        onChange={onChange}
        aria-label={t('designer.layout.rightPanelTabs')}
        options={tabs.map((tab) => ({
          label: (
            <Tooltip title={tab.label} mouseEnterDelay={0.5}>
              <span className="right-panel-tab-label">
                {tab.icon}
                <span className="right-panel-tab-text">{tab.label}</span>
              </span>
            </Tooltip>
          ),
          value: tab.value,
        }))}
      />
    </div>
  )
}

function EmptyPanel({ layoutClassName }: { layoutClassName: string }) {
  const { t } = useTranslation()
  return (
    <div className={`${layoutClassName}-empty-panel`}>
      <div className={`${layoutClassName}-empty-panel-icon`}>
        <NodeIndexOutlined style={{ fontSize: 28, color: 'var(--color-text-tertiary)' }} />
      </div>
      <div>
        <div className={`${layoutClassName}-empty-panel-title`}>
          {t('designer.layout.emptyTitle')}
        </div>
        <div className={`${layoutClassName}-empty-panel-sub`}>{t('designer.layout.emptySub')}</div>
        <div className={`${layoutClassName}-empty-panel-hint`}>
          {t('designer.layout.emptyHint')}
        </div>
      </div>
    </div>
  )
}

function RightPanelContent({
  emptyPanel,
  flowDefinition,
  graphHighlights,
  layoutClassName,
  propertiesPanel,
  rightPanelTab,
  selectedEdge,
  selectedNodeId,
  simulationEngine,
}: Omit<RightSiderProps, 'onToggleRightPanel' | 'rightPanelCollapsed' | 'segmentedValue'>) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const showEdgeProperties =
    selectedEdge && !selectedNodeId && (rightPanelTab === 'edge' || rightPanelTab === 'properties')

  return (
    <Suspense fallback={<SuspenseFallback text={t('designer.loading.rightPanel')} />}>
      <div className="right-panel-content">
        {selectedNodeId && rightPanelTab === 'properties' && propertiesPanel}
        {showEdgeProperties && (
          <EdgePropertiesPanel edge={selectedEdge} onClose={() => dispatch(selectEdge(null))} />
        )}
        {rightPanelTab === 'validation' && (
          <ValidationResultPanel
            flowDefinition={flowDefinition}
            onHighlightNodes={graphHighlights.onHighlightValidationNodes}
            onHighlightConnections={graphHighlights.onHighlightValidationConnections}
          />
        )}
        {rightPanelTab === 'debug' && (
          <ProcessDebuggerPanel
            flowDefinition={flowDefinition}
            simulationEngine={simulationEngine}
            onHighlightNode={graphHighlights.onDebugHighlightNode}
            onHighlightConnection={graphHighlights.onDebugHighlightConnection}
          />
        )}
        {emptyPanel && <EmptyPanel layoutClassName={layoutClassName} />}
      </div>
    </Suspense>
  )
}

function DesignerRightSider({
  emptyPanel,
  flowDefinition,
  graphHighlights,
  layoutClassName,
  onToggleRightPanel,
  propertiesPanel,
  rightPanelCollapsed,
  rightPanelTab,
  selectedEdge,
  selectedNodeId,
  segmentedValue,
  simulationEngine,
}: RightSiderProps) {
  const { t } = useTranslation()
  const { theme } = useTheme()
  const dispatch = useAppDispatch()
  const { handleTabChange, tabs } = useRightPanelTabs(dispatch)
  const collapseTitle = rightPanelCollapsed
    ? t('designer.layout.expandProperties')
    : t('designer.layout.collapseProperties')

  return (
    <Sider
      width={360}
      collapsedWidth={0}
      collapsed={rightPanelCollapsed}
      collapsible
      trigger={null}
      theme={theme === 'dark' ? 'dark' : 'light'}
      className={`${layoutClassName}-right-sider${rightPanelCollapsed ? '' : ' sider-visible'}`}
      aria-hidden={rightPanelCollapsed}
      inert={rightPanelCollapsed ? true : undefined}
    >
      <CollapseTrigger
        collapsed={rightPanelCollapsed}
        direction="right"
        onClick={onToggleRightPanel}
        title={collapseTitle}
      />
      <RightPanelTabs tabs={tabs} value={segmentedValue} onChange={handleTabChange} />
      <RightPanelContent
        emptyPanel={emptyPanel}
        flowDefinition={flowDefinition}
        graphHighlights={graphHighlights}
        layoutClassName={layoutClassName}
        propertiesPanel={propertiesPanel}
        rightPanelTab={rightPanelTab}
        selectedEdge={selectedEdge}
        selectedNodeId={selectedNodeId}
        simulationEngine={simulationEngine}
      />
    </Sider>
  )
}

function DesignerModals({
  showHelpDocs,
  showSearch,
  showShortcuts,
  processVariablesDialog,
}: DesignerModalsProps) {
  const dispatch = useAppDispatch()

  return (
    <Suspense fallback={null}>
      <DeferredMount active={showShortcuts}>
        <KeyboardShortcutsModal
          open={showShortcuts}
          onClose={() => dispatch(togglePanel('showShortcuts'))}
        />
      </DeferredMount>
      <DeferredMount active={processVariablesDialog.open}>
        <ProcessVariablesDialog
          open={processVariablesDialog.open}
          onClose={processVariablesDialog.onClose}
        />
      </DeferredMount>
      <DeferredMount active={showHelpDocs}>
        <HelpDocumentation
          open={showHelpDocs}
          onClose={() => dispatch(togglePanel('showHelpDocs'))}
        />
      </DeferredMount>
      <DeferredMount active={showSearch}>
        <NodeSearch />
      </DeferredMount>
    </Suspense>
  )
}

function DeferredMount({ active, children }: { active: boolean; children: ReactNode }) {
  const [mounted, setMounted] = useState(active)

  useEffect(() => {
    if (active) setMounted(true)
  }, [active])

  return mounted ? children : null
}

export function DesignerLayout({
  layoutClassName,
  palette,
  canvas,
  propertiesPanel,
  onCopy,
  onPaste,
  onDelete,
  processVariablesDialog,
}: DesignerLayoutProps) {
  const dispatch = useAppDispatch()
  const { containerRef, compact: isMobile } = useCompactContainer(960)
  const wasMobileRef = useRef<boolean | null>(null)
  const previousSelectionRef = useRef<string | null>(null)
  const { graphRef } = useDesignerContext()
  const layoutState = useLayoutState()
  const flowDefinition = requireCurrentProcess(layoutState.currentProcess)
  const simulationEngine = useSimulationEngine(flowDefinition)
  const graphHighlights = useGraphHighlights(graphRef, flowDefinition, layoutState.rightPanelTab)
  const segmentedValue = resolveSegmentedValue(layoutState.rightPanelTab)
  const emptyPanel =
    !layoutState.selectedNodeId &&
    !layoutState.selectedEdge &&
    layoutState.rightPanelTab !== 'validation' &&
    layoutState.rightPanelTab !== 'debug'
  const contextMenuActions = useContextMenuActions({
    collapseLeftWhenOpeningPanel: isMobile,
    contextMenu: layoutState.contextMenu,
    currentProcess: flowDefinition,
    dispatch,
    graphRef,
    onCopy,
    onDelete,
    onPaste,
    simulationEngine,
  })
  useEffect(() => {
    if (isMobile && wasMobileRef.current !== true) {
      dispatch(setSidePanelsCollapsed({ left: true, right: true }))
    } else if (!isMobile && wasMobileRef.current === true) {
      dispatch(setSidePanelsCollapsed({ left: false, right: false }))
    }
    wasMobileRef.current = isMobile
  }, [dispatch, isMobile])

  useEffect(() => {
    const selection = layoutState.selectedNodeId
      ? `node:${layoutState.selectedNodeId}`
      : layoutState.selectedEdge
        ? `edge:${layoutState.selectedEdge.id}`
        : null
    const previousSelection = previousSelectionRef.current
    previousSelectionRef.current = selection
    if (!isMobile) return

    if (
      selection !== null &&
      (selection !== previousSelection ||
        (!layoutState.leftPanelCollapsed && !layoutState.rightPanelCollapsed))
    ) {
      dispatch(setSidePanelsCollapsed({ left: true, right: layoutState.rightPanelCollapsed }))
    } else if (
      selection === null &&
      previousSelection !== null &&
      layoutState.rightPanelTab === 'none'
    ) {
      dispatch(setSidePanelsCollapsed({ left: layoutState.leftPanelCollapsed, right: true }))
    }
  }, [
    dispatch,
    isMobile,
    layoutState.leftPanelCollapsed,
    layoutState.rightPanelCollapsed,
    layoutState.rightPanelTab,
    layoutState.selectedEdge?.id,
    layoutState.selectedNodeId,
  ])

  const handleToggleLeftPanel = useCallback(() => {
    if (isMobile && layoutState.leftPanelCollapsed) {
      dispatch(setSidePanelsCollapsed({ left: false, right: true }))
      return
    }
    dispatch(toggleLeftPanel())
  }, [dispatch, isMobile, layoutState.leftPanelCollapsed])
  const handleToggleRightPanel = useCallback(() => {
    if (isMobile && layoutState.rightPanelCollapsed) {
      dispatch(setSidePanelsCollapsed({ left: true, right: false }))
      return
    }
    dispatch(toggleRightPanel())
  }, [dispatch, isMobile, layoutState.rightPanelCollapsed])

  return (
    <Layout ref={containerRef} className={layoutClassName} style={{ height: '100%' }}>
      <DesignerLeftSider
        collapsed={layoutState.leftPanelCollapsed}
        layoutClassName={layoutClassName}
        onToggle={handleToggleLeftPanel}
        palette={palette}
      />
      <ContentArea
        canvas={canvas}
        contextMenuActions={contextMenuActions}
        contextMenuState={layoutState.contextMenu}
        layoutClassName={layoutClassName}
        leftPanelCollapsed={layoutState.leftPanelCollapsed}
        rightPanelCollapsed={layoutState.rightPanelCollapsed}
        onToggleLeftPanel={handleToggleLeftPanel}
        onToggleRightPanel={handleToggleRightPanel}
      />
      <DesignerRightSider
        emptyPanel={emptyPanel}
        flowDefinition={flowDefinition}
        graphHighlights={graphHighlights}
        layoutClassName={layoutClassName}
        onToggleRightPanel={handleToggleRightPanel}
        propertiesPanel={propertiesPanel}
        rightPanelCollapsed={layoutState.rightPanelCollapsed}
        rightPanelTab={layoutState.rightPanelTab}
        selectedEdge={layoutState.selectedEdge}
        selectedNodeId={layoutState.selectedNodeId}
        segmentedValue={segmentedValue}
        simulationEngine={simulationEngine}
      />
      <DesignerModals
        showHelpDocs={layoutState.showHelpDocs}
        showSearch={layoutState.showSearch}
        showShortcuts={layoutState.showShortcuts}
        processVariablesDialog={processVariablesDialog}
      />
    </Layout>
  )
}
