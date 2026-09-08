import type { Graph } from '@antv/x6'
import React, { ReactNode, Suspense, useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { fitGraphContent } from '../canvas/graphViewport'
import { DesignerProvider } from '../context'
import { useClipboard } from '../hooks/useClipboard'
import { useKeyboardShortcuts } from '../hooks/useKeyboardShortcuts'
import {
  deleteConnection as deleteConnectionAction,
  deleteGraph,
  deleteNode as deleteNodeAction,
  addConnection,
  selectCurrentProcess,
} from '../store/editorSlice'
import {
  selectContextMenu,
  selectEdge,
  selectNode,
  selectSelectedNodeId,
  togglePanel,
} from '../store/uiSlice'

import CanvasToolbar from './CanvasToolbar'
import CreateConnectionDialog from './CreateConnectionDialog'
import { DesignerErrorBoundary } from './DesignerErrorBoundary'
import { DesignerLayout } from './DesignerLayout'
import { SuspenseFallback } from './LoadingFeedback'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { generateId } from '@/authoring/designer/identifiers'

export interface BaseDesignerProps {
  onGraphReady?: (graph: Graph | null) => void
  processVariablesDialog: {
    open: boolean
    onClose: () => void
  }
}

export interface BaseDesignerCoreProps extends BaseDesignerProps {
  layoutClassName: string
  renderPalette: (graph: Graph | null) => ReactNode
  renderCanvas: (onGraphReady: (graph: Graph) => void) => ReactNode
  renderPropertiesPanel: () => ReactNode
}

function useDesignerGraphActions(graph: Graph | null) {
  const dispatch = useAppDispatch()
  const { copyNodes, duplicateNodes, pasteNodes } = useClipboard()
  const selectedNodeId = useAppSelector(selectSelectedNodeId)
  const contextMenu = useAppSelector(selectContextMenu)
  const [connectionDialog, setConnectionDialog] = useState({
    open: false,
    initialNodeIds: [] as string[],
  })

  const duplicateSelection = useCallback(() => {
    duplicateNodes(
      graph
        ?.getSelectedCells()
        .filter((cell) => cell.isNode())
        .map((cell) => cell.id) ?? []
    )
  }, [duplicateNodes, graph])

  const deleteSelection = useCallback(() => {
    const selectedCells = graph?.getSelectedCells() ?? []
    if (selectedCells.length === 0) {
      if (selectedNodeId) dispatch(deleteNodeAction(selectedNodeId))
      return
    }
    dispatch(
      deleteGraph({
        nodeIds: selectedCells.filter((cell) => cell.isNode()).map((cell) => cell.id),
        connectionIds: selectedCells.filter((cell) => cell.isEdge()).map((cell) => cell.id),
      })
    )
    dispatch(selectNode(null))
    dispatch(selectEdge(null))
  }, [dispatch, graph, selectedNodeId])

  const openConnectionDialog = useCallback(() => {
    const initialNodeIds =
      graph
        ?.getSelectedCells()
        .filter((cell) => cell.isNode())
        .map((cell) => cell.id) ?? []
    setConnectionDialog({ open: true, initialNodeIds })
  }, [graph])

  const createConnection = useCallback(
    (sourceId: string, targetId: string) => {
      const connection = { id: generateId(), sourceId, targetId, name: '' }
      dispatch(addConnection(connection))
      graph?.cleanSelection()
      dispatch(selectEdge(connection.id))
      setConnectionDialog({ open: false, initialNodeIds: [] })
    },
    [dispatch, graph]
  )

  const copyContextTarget = useCallback(() => {
    if (contextMenu.targetId) copyNodes([contextMenu.targetId])
  }, [contextMenu.targetId, copyNodes])

  const deleteContextTarget = useCallback(() => {
    if (!contextMenu.targetId) return
    if (contextMenu.type === 'node') dispatch(deleteNodeAction(contextMenu.targetId))
    if (contextMenu.type === 'edge') dispatch(deleteConnectionAction(contextMenu.targetId))
  }, [contextMenu.targetId, contextMenu.type, dispatch])

  return {
    closeConnectionDialog: () => setConnectionDialog({ open: false, initialNodeIds: [] }),
    connectionDialog,
    copyContextTarget,
    createConnection,
    deleteContextTarget,
    deleteSelection,
    duplicateSelection,
    openConnectionDialog,
    pasteNodes,
  }
}

export function BaseDesignerCore({
  onGraphReady,
  layoutClassName,
  renderPalette,
  renderCanvas,
  renderPropertiesPanel,
  processVariablesDialog,
}: BaseDesignerCoreProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const currentProcess = useAppSelector(selectCurrentProcess)

  const [graphInstance, setGraphInstance] = useState<Graph | null>(null)
  const actions = useDesignerGraphActions(graphInstance)

  const handleGraphReady = useCallback(
    (graph: Graph) => {
      setGraphInstance(graph)
      onGraphReady?.(graph)
    },
    [onGraphReady]
  )

  // Copy/paste shortcuts are registered at UnifiedDesigner page level to avoid duplicate handlers.
  useKeyboardShortcuts([
    {
      id: 'delete',
      key: 'Delete',
      handler: actions.deleteSelection,
      description: t('designer.shortcuts.deleteNode'),
    },
    {
      id: 'duplicate',
      key: 'd',
      ctrl: true,
      handler: actions.duplicateSelection,
      description: t('designer.toolbar.copySelected'),
    },
    {
      id: 'search',
      key: 'f',
      ctrl: true,
      handler: () => dispatch(togglePanel('showSearch')),
      description: t('designer.shortcuts.searchNodes'),
    },
    {
      id: 'help',
      key: '/',
      ctrl: true,
      handler: () => dispatch(togglePanel('showShortcuts')),
      description: t('designer.shortcuts.help'),
    },
    {
      id: 'select-all',
      key: 'a',
      ctrl: true,
      handler: () => graphInstance?.select(graphInstance.getCells()),
      description: t('designer.shortcuts.item.selectAll'),
    },
    {
      id: 'zoom-in-plus',
      key: '+',
      ctrl: true,
      handler: () => graphInstance?.zoom(0.1),
      description: t('designer.shortcuts.item.zoomIn'),
    },
    {
      id: 'zoom-in-equal',
      key: '=',
      ctrl: true,
      handler: () => graphInstance?.zoom(0.1),
      description: t('designer.shortcuts.item.zoomIn'),
    },
    {
      id: 'zoom-out',
      key: '-',
      ctrl: true,
      handler: () => graphInstance?.zoom(-0.1),
      description: t('designer.shortcuts.item.zoomOut'),
    },
    {
      id: 'zoom-fit',
      key: '0',
      ctrl: true,
      handler: () => {
        if (graphInstance) fitGraphContent(graphInstance)
      },
      description: t('designer.shortcuts.item.zoomFit'),
    },
  ])

  return (
    <>
      <DesignerLayout
        layoutClassName={layoutClassName}
        palette={renderPalette(graphInstance)}
        canvas={
          <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
            <CanvasToolbar
              graph={graphInstance}
              onCreateConnection={actions.openConnectionDialog}
              onDuplicateSelection={actions.duplicateSelection}
              onDeleteSelection={actions.deleteSelection}
            />
            <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
              {renderCanvas(handleGraphReady)}
            </div>
          </div>
        }
        propertiesPanel={
          <Suspense fallback={<SuspenseFallback text={t('designer.loading.propertiesPanel')} />}>
            {renderPropertiesPanel()}
          </Suspense>
        }
        onCopy={actions.copyContextTarget}
        onPaste={actions.pasteNodes}
        onDelete={actions.deleteContextTarget}
        processVariablesDialog={processVariablesDialog}
      />
      {currentProcess && actions.connectionDialog.open && (
        <CreateConnectionDialog
          process={currentProcess}
          initialNodeIds={actions.connectionDialog.initialNodeIds}
          onCancel={actions.closeConnectionDialog}
          onCreate={actions.createConnection}
        />
      )}
    </>
  )
}

export function withDesignerWrapper(Core: React.ComponentType<BaseDesignerProps>) {
  return function DesignerWithErrorBoundary(props: BaseDesignerProps) {
    return (
      <DesignerErrorBoundary>
        <DesignerProvider>
          <Core {...props} />
        </DesignerProvider>
      </DesignerErrorBoundary>
    )
  }
}
