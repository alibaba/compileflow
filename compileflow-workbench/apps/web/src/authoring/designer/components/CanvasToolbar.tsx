import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  ColumnHeightOutlined,
  ColumnWidthOutlined,
  CopyOutlined,
  DeleteOutlined,
  FullscreenOutlined,
  LinkOutlined,
  MoreOutlined,
  ReloadOutlined,
  SelectOutlined,
  VerticalAlignBottomOutlined,
  VerticalAlignMiddleOutlined,
  VerticalAlignTopOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons'
import type { Graph } from '@antv/x6'
import { Button, Divider, Dropdown, Space, Tooltip } from 'antd'
import type { TFunction } from 'i18next'
import React, { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { fitGraphContent, resetGraphView } from '@/authoring/designer/canvas/graphViewport'
import { createMultiSelectionTools } from '@/authoring/designer/canvas/selectionTools'
import { useCompactContainer } from '@/shared/hooks/useCompactContainer'
import './CanvasToolbar.css'

interface CanvasToolbarProps {
  /** Graph instance controlled by the toolbar. */
  graph: Graph | null
  /** Duplicates the selected canonical model graph, including descendants and edges. */
  onDuplicateSelection?: () => void
  /** Deletes the selected canonical nodes and edges as one undoable graph mutation. */
  onDeleteSelection?: () => void
  /** Opens the keyboard- and click-operable connection creator. */
  onCreateConnection?: () => void
}

interface ToolbarAction {
  disabled?: boolean
  icon: React.ReactNode
  key: string
  onClick: () => void
  tooltip: string
}

interface ToolbarActions {
  align: ToolbarAction[]
  batch: ToolbarAction[]
  distribute: ToolbarAction[]
  view: ToolbarAction[]
}

function useViewActions(graph: Graph | null, t: TFunction): ToolbarAction[] {
  return useMemo(
    () => [
      {
        key: 'zoom-in',
        icon: <ZoomInOutlined />,
        tooltip: t('designer.toolbar.zoomIn'),
        onClick: () => graph?.zoom(0.1),
      },
      {
        key: 'zoom-out',
        icon: <ZoomOutOutlined />,
        tooltip: t('designer.toolbar.zoomOut'),
        onClick: () => graph?.zoom(-0.1),
      },
      {
        key: 'zoom-fit',
        icon: <FullscreenOutlined />,
        tooltip: t('designer.toolbar.zoomFit'),
        onClick: () => {
          if (graph) fitGraphContent(graph)
        },
      },
      {
        key: 'zoom-reset',
        icon: <ReloadOutlined />,
        tooltip: t('designer.toolbar.zoomReset'),
        onClick: () => {
          if (graph) resetGraphView(graph)
        },
      },
    ],
    [graph, t]
  )
}

function useToolbarActions(
  graph: Graph | null,
  onDuplicateSelection: (() => void) | undefined,
  onDeleteSelection: (() => void) | undefined,
  onCreateConnection: (() => void) | undefined,
  t: TFunction,
  tools: ReturnType<typeof createMultiSelectionTools> | null,
  selectedCellCount: number,
  selectedNodeCount: number,
  nodeCount: number,
  cellCount: number
): ToolbarActions {
  const view = useViewActions(graph, t)
  const align = useMemo(
    () => [
      {
        key: 'align-left',
        icon: <AlignLeftOutlined />,
        tooltip: t('designer.toolbar.alignLeft'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignLeft(),
      },
      {
        key: 'align-center-v',
        icon: <AlignCenterOutlined />,
        tooltip: t('designer.toolbar.alignCenterV'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignCenterVertical(),
      },
      {
        key: 'align-right',
        icon: <AlignRightOutlined />,
        tooltip: t('designer.toolbar.alignRight'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignRight(),
      },
      {
        key: 'align-top',
        icon: <VerticalAlignTopOutlined />,
        tooltip: t('designer.toolbar.alignTop'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignTop(),
      },
      {
        key: 'align-center-h',
        icon: <VerticalAlignMiddleOutlined />,
        tooltip: t('designer.toolbar.alignCenterH'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignCenterHorizontal(),
      },
      {
        key: 'align-bottom',
        icon: <VerticalAlignBottomOutlined />,
        tooltip: t('designer.toolbar.alignBottom'),
        disabled: selectedNodeCount < 2,
        onClick: () => tools?.align.alignBottom(),
      },
    ],
    [selectedNodeCount, t, tools]
  )

  const distribute = useMemo(
    () => [
      {
        key: 'distribute-h',
        icon: <ColumnWidthOutlined />,
        tooltip: t('designer.toolbar.distributeH'),
        disabled: selectedNodeCount < 3,
        onClick: () => tools?.distribute.distributeHorizontal(),
      },
      {
        key: 'distribute-v',
        icon: <ColumnHeightOutlined />,
        tooltip: t('designer.toolbar.distributeV'),
        disabled: selectedNodeCount < 3,
        onClick: () => tools?.distribute.distributeVertical(),
      },
    ],
    [selectedNodeCount, t, tools]
  )

  const batch = useMemo(
    () => [
      {
        key: 'connect',
        icon: <LinkOutlined />,
        tooltip: t('designer.toolbar.createConnection'),
        disabled: nodeCount < 2,
        onClick: () => onCreateConnection?.(),
      },
      {
        key: 'copy',
        icon: <CopyOutlined />,
        tooltip: t('designer.toolbar.copySelected'),
        disabled: selectedNodeCount < 1,
        onClick: () => {
          onDuplicateSelection?.()
        },
      },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        tooltip: t('designer.toolbar.deleteSelected'),
        disabled: selectedCellCount < 1,
        onClick: () => onDeleteSelection?.(),
      },
      {
        key: 'select-all',
        icon: <SelectOutlined />,
        tooltip: t('designer.toolbar.selectAll'),
        disabled: cellCount < 1,
        onClick: () => tools?.selection.selectAll(),
      },
    ],
    [
      cellCount,
      nodeCount,
      onCreateConnection,
      onDeleteSelection,
      onDuplicateSelection,
      selectedCellCount,
      selectedNodeCount,
      t,
      tools,
    ]
  )

  return { align, batch, distribute, view }
}

function ToolbarActionGroup({
  actions,
  disabled,
  label,
}: {
  actions: ToolbarAction[]
  disabled: boolean
  label: string
}) {
  return (
    <Space size="small" role="group" aria-label={label}>
      {actions.map((action) => (
        <Tooltip key={action.key} title={action.tooltip}>
          <Button
            type="text"
            size="small"
            icon={action.icon}
            onClick={action.onClick}
            disabled={disabled || action.disabled}
            aria-label={action.tooltip}
          />
        </Tooltip>
      ))}
    </Space>
  )
}

const CanvasToolbar = React.memo(function CanvasToolbar({
  graph,
  onDeleteSelection,
  onDuplicateSelection,
  onCreateConnection,
}: CanvasToolbarProps) {
  const { t } = useTranslation()
  const { containerRef, compact } = useCompactContainer()
  // Cache the tools object so its onClick references are stable across renders,
  // preserving React.memo effectiveness on downstream button components.
  const tools = useMemo(() => (graph ? createMultiSelectionTools(graph) : null), [graph])
  const [selectionState, setSelectionState] = useState({
    cellCount: 0,
    nodeCount: 0,
    selectedCellCount: 0,
    selectedNodeCount: 0,
  })
  useEffect(() => {
    if (!graph) {
      setSelectionState({ cellCount: 0, nodeCount: 0, selectedCellCount: 0, selectedNodeCount: 0 })
      return
    }
    const syncSelectionState = () => {
      const selectedCells = graph.getSelectedCells()
      setSelectionState({
        cellCount: graph.getCells().length,
        nodeCount: graph.getNodes().length,
        selectedCellCount: selectedCells.length,
        selectedNodeCount: selectedCells.filter((cell) => cell.isNode()).length,
      })
    }
    syncSelectionState()
    graph.on('selection:changed', syncSelectionState)
    graph.on('cell:added', syncSelectionState)
    graph.on('cell:removed', syncSelectionState)
    return () => {
      graph.off('selection:changed', syncSelectionState)
      graph.off('cell:added', syncSelectionState)
      graph.off('cell:removed', syncSelectionState)
    }
  }, [graph])
  const disabled = !graph
  const actions = useToolbarActions(
    graph,
    onDuplicateSelection,
    onDeleteSelection,
    onCreateConnection,
    t,
    tools,
    selectionState.selectedCellCount,
    selectionState.selectedNodeCount,
    selectionState.nodeCount,
    selectionState.cellCount
  )

  return (
    <div
      ref={containerRef}
      className={`x6-canvas-toolbar${compact ? ' x6-canvas-toolbar-compact' : ''}`}
      role="toolbar"
      aria-label={t('designer.toolbar.label')}
    >
      {/* Divider uses type="vertical" for direction; orientation controls text alignment. */}
      <Space wrap={compact} separator={<Divider vertical />}>
        {!compact && (
          <ToolbarActionGroup
            actions={actions.align}
            disabled={disabled}
            label={t('designer.toolbar.alignGroup')}
          />
        )}
        {!compact && (
          <ToolbarActionGroup
            actions={actions.distribute}
            disabled={disabled}
            label={t('designer.toolbar.distributeGroup')}
          />
        )}
        <ToolbarActionGroup
          actions={actions.batch}
          disabled={disabled}
          label={t('designer.toolbar.batchGroup')}
        />
        <ToolbarActionGroup
          actions={
            compact ? actions.view.filter((action) => action.key === 'zoom-fit') : actions.view
          }
          disabled={disabled}
          label={t('designer.toolbar.viewGroup')}
        />
        {compact && (
          <Dropdown
            trigger={['click']}
            popupRender={(menu) => <div className="canvas-toolbar-menu">{menu}</div>}
            menu={{
              items: [
                {
                  key: 'align',
                  label: t('designer.toolbar.alignGroup'),
                  children: actions.align,
                },
                {
                  key: 'distribute',
                  label: t('designer.toolbar.distributeGroup'),
                  children: actions.distribute,
                },
                {
                  key: 'view',
                  label: t('designer.toolbar.viewGroup'),
                  children: actions.view.filter((action) => action.key !== 'zoom-fit'),
                },
              ].map((group) => ({
                ...group,
                type: 'group' as const,
                children: group.children.map((action) => ({
                  key: action.key,
                  icon: action.icon,
                  label: action.tooltip,
                  disabled: disabled || action.disabled,
                  onClick: action.onClick,
                })),
              })),
            }}
          >
            <Button
              type="text"
              icon={<MoreOutlined />}
              disabled={disabled}
              aria-label={t('designer.toolbar.moreActions')}
            />
          </Dropdown>
        )}
      </Space>
    </div>
  )
})

CanvasToolbar.displayName = 'CanvasToolbar'

export default CanvasToolbar
