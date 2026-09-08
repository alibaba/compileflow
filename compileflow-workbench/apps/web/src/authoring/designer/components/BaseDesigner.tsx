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
} from '../store/editorSlice'
import {
  selectContextMenu,
  selectEdge,
  selectNode,
  selectSelectedNodeId,
  togglePanel,
} from '../store/uiSlice'

import CanvasToolbar from './CanvasToolbar'
import { DesignerErrorBoundary } from './DesignerErrorBoundary'
import { DesignerLayout } from './DesignerLayout'
import { SuspenseFallback } from './LoadingFeedback'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

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
  /** Optional callback wired to the "load example" button in CanvasToolbar (TBBPM only). */
  onLoadExample?: () => void
}

export function BaseDesignerCore({
  onGraphReady,
  layoutClassName,
  renderPalette,
  renderCanvas,
  renderPropertiesPanel,
  processVariablesDialog,
  onLoadExample,
}: BaseDesignerCoreProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const { copyNodes, duplicateNodes, pasteNodes } = useClipboard()

  const selectedNodeId = useAppSelector(selectSelectedNodeId)
  const contextMenu = useAppSelector(selectContextMenu)

  const [graphInstance, setGraphInstance] = useState<Graph | null>(null)

  const handleGraphReady = useCallback(
    (graph: Graph) => {
      setGraphInstance(graph)
      onGraphReady?.(graph)
    },
    [onGraphReady]
  )

  const handleDuplicateSelection = useCallback(() => {
    duplicateNodes(
      graphInstance
        ?.getSelectedCells()
        .filter((cell) => cell.isNode())
        .map((cell) => cell.id) ?? []
    )
  }, [duplicateNodes, graphInstance])

  const handleDeleteSelection = useCallback(() => {
    const selectedCells = graphInstance?.getSelectedCells() ?? []
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
  }, [dispatch, graphInstance, selectedNodeId])

  // Copy/paste shortcuts are registered at UnifiedDesigner page level to avoid duplicate handlers.
  useKeyboardShortcuts([
    {
      id: 'delete',
      key: 'Delete',
      handler: handleDeleteSelection,
      description: t('designer.shortcuts.deleteNode'),
    },
    {
      id: 'duplicate',
      key: 'd',
      ctrl: true,
      handler: handleDuplicateSelection,
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
    <DesignerLayout
      layoutClassName={layoutClassName}
      palette={renderPalette(graphInstance)}
      canvas={
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, minHeight: 0 }}>
          <CanvasToolbar
            graph={graphInstance}
            onDuplicateSelection={handleDuplicateSelection}
            onDeleteSelection={handleDeleteSelection}
            onLoadExample={onLoadExample}
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
      onCopy={() => {
        if (contextMenu.targetId) {
          copyNodes([contextMenu.targetId])
        }
      }}
      onPaste={() => pasteNodes()}
      onDelete={() => {
        if (contextMenu.type === 'node' && contextMenu.targetId) {
          dispatch(deleteNodeAction(contextMenu.targetId))
        } else if (contextMenu.type === 'edge' && contextMenu.targetId) {
          dispatch(deleteConnectionAction(contextMenu.targetId))
        }
      }}
      processVariablesDialog={processVariablesDialog}
    />
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
