import {
  AlignCenterOutlined,
  AlignLeftOutlined,
  AlignRightOutlined,
  ColumnHeightOutlined,
  ColumnWidthOutlined,
  CopyOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  FullscreenOutlined,
  ReloadOutlined,
  SelectOutlined,
  VerticalAlignBottomOutlined,
  VerticalAlignMiddleOutlined,
  VerticalAlignTopOutlined,
  ZoomInOutlined,
  ZoomOutOutlined,
} from '@ant-design/icons'
import type { Graph } from '@antv/x6'
import { Button, Divider, Space, Tooltip } from 'antd'
import type { TFunction } from 'i18next'
import React, { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { fitGraphContent, resetGraphView } from '@/authoring/designer/canvas/graphViewport'
import { createMultiSelectionTools } from '@/authoring/designer/canvas/selectionTools'
import './CanvasToolbar.css'

interface CanvasToolbarProps {
  /** Graph instance controlled by the toolbar. */
  graph: Graph | null
  /** Optional example loader exposed by the TBBPM designer. */
  onLoadExample?: () => void
  /** Duplicates the selected canonical model graph, including descendants and edges. */
  onDuplicateSelection?: () => void
  /** Deletes the selected canonical nodes and edges as one undoable graph mutation. */
  onDeleteSelection?: () => void
}

interface ToolbarAction {
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

function useToolbarActions(
  graph: Graph | null,
  onDuplicateSelection: (() => void) | undefined,
  onDeleteSelection: (() => void) | undefined,
  t: TFunction,
  tools: ReturnType<typeof createMultiSelectionTools> | null
): ToolbarActions {
  const align = useMemo(
    () => [
      {
        key: 'align-left',
        icon: <AlignLeftOutlined />,
        tooltip: t('designer.toolbar.alignLeft'),
        onClick: () => tools?.align.alignLeft(),
      },
      {
        key: 'align-center-v',
        icon: <AlignCenterOutlined />,
        tooltip: t('designer.toolbar.alignCenterV'),
        onClick: () => tools?.align.alignCenterVertical(),
      },
      {
        key: 'align-right',
        icon: <AlignRightOutlined />,
        tooltip: t('designer.toolbar.alignRight'),
        onClick: () => tools?.align.alignRight(),
      },
      {
        key: 'align-top',
        icon: <VerticalAlignTopOutlined />,
        tooltip: t('designer.toolbar.alignTop'),
        onClick: () => tools?.align.alignTop(),
      },
      {
        key: 'align-center-h',
        icon: <VerticalAlignMiddleOutlined />,
        tooltip: t('designer.toolbar.alignCenterH'),
        onClick: () => tools?.align.alignCenterHorizontal(),
      },
      {
        key: 'align-bottom',
        icon: <VerticalAlignBottomOutlined />,
        tooltip: t('designer.toolbar.alignBottom'),
        onClick: () => tools?.align.alignBottom(),
      },
    ],
    [t, tools]
  )

  const distribute = useMemo(
    () => [
      {
        key: 'distribute-h',
        icon: <ColumnWidthOutlined />,
        tooltip: t('designer.toolbar.distributeH'),
        onClick: () => tools?.distribute.distributeHorizontal(),
      },
      {
        key: 'distribute-v',
        icon: <ColumnHeightOutlined />,
        tooltip: t('designer.toolbar.distributeV'),
        onClick: () => tools?.distribute.distributeVertical(),
      },
    ],
    [t, tools]
  )

  const batch = useMemo(
    () => [
      {
        key: 'copy',
        icon: <CopyOutlined />,
        tooltip: t('designer.toolbar.copySelected'),
        onClick: () => {
          onDuplicateSelection?.()
        },
      },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        tooltip: t('designer.toolbar.deleteSelected'),
        onClick: () => onDeleteSelection?.(),
      },
      {
        key: 'select-all',
        icon: <SelectOutlined />,
        tooltip: t('designer.toolbar.selectAll'),
        onClick: () => tools?.selection.selectAll(),
      },
    ],
    [onDeleteSelection, onDuplicateSelection, t, tools]
  )

  const view = useMemo(
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
            disabled={disabled}
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
  onLoadExample,
}: CanvasToolbarProps) {
  const { t } = useTranslation()
  // Cache the tools object so its onClick references are stable across renders,
  // preserving React.memo effectiveness on downstream button components.
  const tools = useMemo(() => (graph ? createMultiSelectionTools(graph) : null), [graph])
  const disabled = !graph
  const actions = useToolbarActions(graph, onDuplicateSelection, onDeleteSelection, t, tools)

  return (
    <div className="x6-canvas-toolbar" role="toolbar" aria-label={t('designer.toolbar.label')}>
      {/* Divider uses type="vertical" for direction; orientation controls text alignment. */}
      <Space separator={<Divider vertical />}>
        <ToolbarActionGroup
          actions={actions.align}
          disabled={disabled}
          label={t('designer.toolbar.alignGroup')}
        />
        <ToolbarActionGroup
          actions={actions.distribute}
          disabled={disabled}
          label={t('designer.toolbar.distributeGroup')}
        />
        <ToolbarActionGroup
          actions={actions.batch}
          disabled={disabled}
          label={t('designer.toolbar.batchGroup')}
        />
        <ToolbarActionGroup
          actions={actions.view}
          disabled={disabled}
          label={t('designer.toolbar.viewGroup')}
        />

        {/* The load-example button is placed as a direct Space child so the Space
            separator prop automatically inserts exactly ONE Divider before it. */}
        {onLoadExample && (
          <Tooltip title={t('designer.toolbar.loadExampleHint')}>
            <Button
              type="dashed"
              size="small"
              icon={<FolderOpenOutlined />}
              onClick={onLoadExample}
              aria-label={t('designer.toolbar.loadExample')}
            >
              {t('designer.toolbar.loadExample')}
            </Button>
          </Tooltip>
        )}
      </Space>
    </div>
  )
})

CanvasToolbar.displayName = 'CanvasToolbar'

export default CanvasToolbar
