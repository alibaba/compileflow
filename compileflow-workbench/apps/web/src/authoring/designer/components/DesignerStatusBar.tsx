import {
  ApartmentOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  LoadingOutlined,
  MinusOutlined,
  PlusOutlined,
  ShareAltOutlined,
  ZoomInOutlined,
} from '@ant-design/icons'
import type { Graph } from '@antv/x6'
import { Button, Tooltip } from 'antd'
import { memo, useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import './DesignerStatusBar.css'

interface DesignerStatusBarProps {
  graph: Graph | null
  isModified: boolean
  isSaving: boolean
  autoSavePending?: boolean
}

interface StatusState {
  zoom: number
  nodeCount: number
  edgeCount: number
  cursorX: number
  cursorY: number
}

const DesignerStatusBar = memo(function DesignerStatusBar({
  graph,
  isModified,
  isSaving,
  autoSavePending = false,
}: DesignerStatusBarProps) {
  const { t } = useTranslation()
  const [status, setStatus] = useState<StatusState>({
    zoom: 1,
    nodeCount: 0,
    edgeCount: 0,
    cursorX: 0,
    cursorY: 0,
  })

  const refresh = useCallback(() => {
    if (!graph) return
    setStatus((prev) => ({
      ...prev,
      zoom: graph.zoom(),
      nodeCount: graph.getNodes().length,
      edgeCount: graph.getEdges().length,
    }))
  }, [graph])

  useEffect(() => {
    if (!graph) return
    refresh()
    graph.on('node:added', refresh)
    graph.on('node:removed', refresh)
    graph.on('edge:added', refresh)
    graph.on('edge:removed', refresh)
    graph.on('scale', refresh)
    return () => {
      graph.off('node:added', refresh)
      graph.off('node:removed', refresh)
      graph.off('edge:added', refresh)
      graph.off('edge:removed', refresh)
      graph.off('scale', refresh)
    }
  }, [graph, refresh])

  const zoomIn = useCallback(() => graph?.zoom(0.1), [graph])
  const zoomOut = useCallback(() => graph?.zoom(-0.1), [graph])
  const zoomReset = useCallback(() => graph?.zoomTo(1), [graph])

  const zoomPct = Math.round(status.zoom * 100)

  const saveStatusClass = isSaving
    ? 'status-bar-save-status status-bar-save-status--saving'
    : isModified
      ? autoSavePending
        ? 'status-bar-save-status status-bar-save-status--pending'
        : 'status-bar-save-status status-bar-save-status--modified'
      : 'status-bar-save-status status-bar-save-status--saved'

  return (
    <div className="designer-status-bar">
      <Tooltip title={t('designer.statusBar.zoomOut')}>
        <Button
          type="text"
          size="small"
          icon={<MinusOutlined />}
          onClick={zoomOut}
          disabled={!graph}
          aria-label={t('designer.statusBar.zoomOut')}
        />
      </Tooltip>
      <Tooltip title={t('designer.statusBar.zoomReset')}>
        <button
          type="button"
          className="status-bar-zoom-btn"
          onClick={zoomReset}
          disabled={!graph}
          aria-label={t('designer.statusBar.zoomReset')}
        >
          <ZoomInOutlined />
          {zoomPct}%
        </button>
      </Tooltip>
      <Tooltip title={t('designer.statusBar.zoomIn')}>
        <Button
          type="text"
          size="small"
          icon={<PlusOutlined />}
          onClick={zoomIn}
          disabled={!graph}
          aria-label={t('designer.statusBar.zoomIn')}
        />
      </Tooltip>

      <div className="status-bar-divider" />

      <Tooltip title={t('designer.statusBar.nodeCount')}>
        <span className="status-bar-stat">
          <ApartmentOutlined />
          <span>{t('designer.statusBar.nodes', { count: status.nodeCount })}</span>
        </span>
      </Tooltip>

      <div className="status-bar-divider-sm" />

      <Tooltip title={t('designer.statusBar.edgeCount')}>
        <span className="status-bar-stat">
          <ShareAltOutlined />
          <span>{t('designer.statusBar.edges', { count: status.edgeCount })}</span>
        </span>
      </Tooltip>

      <div className="status-bar-spacer" />

      <span className={saveStatusClass}>
        {isSaving ? (
          <>
            <LoadingOutlined /> {t('designer.statusBar.saving')}
          </>
        ) : isModified ? (
          autoSavePending ? (
            <>
              <ClockCircleOutlined /> {t('designer.autoSave.pending')}
            </>
          ) : (
            <>
              <ClockCircleOutlined /> {t('designer.statusBar.unsaved')}
            </>
          )
        ) : (
          <>
            <CheckCircleOutlined /> {t('designer.statusBar.saved')}
          </>
        )}
      </span>
    </div>
  )
})

DesignerStatusBar.displayName = 'DesignerStatusBar'
export default DesignerStatusBar
