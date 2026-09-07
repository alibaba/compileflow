import { Dnd, Graph } from '@antv/x6'
import { type ReactNode, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import './DragPaletteItem.css'

interface DragPaletteItemConfig {
  shape: string
  width: number
  height: number
}

export interface DragPaletteItemProps {
  accessibleLabel?: string
  label: ReactNode
  description?: ReactNode
  icon?: string
  color: string
  graph: Graph | null
  dnd: Dnd | null
  getConfig: () => DragPaletteItemConfig | null | undefined
  getNodeData: () => Record<string, unknown>
  onKeyAddNode?: () => void
}

export function DragPaletteItem({
  accessibleLabel,
  label,
  description,
  icon,
  color,
  graph,
  dnd,
  getConfig,
  getNodeData,
  onKeyAddNode,
}: DragPaletteItemProps) {
  const { t } = useTranslation()
  const itemRef = useRef<HTMLButtonElement>(null)
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null)
  const pointerMovedRef = useRef(false)
  const suppressNextClickRef = useRef(false)
  const touchInputRef = useRef(false)
  const [isDragging, setIsDragging] = useState(false)

  useEffect(() => {
    if (!itemRef.current || !graph || !dnd) return

    const handlePointerDown = (event: PointerEvent) => {
      touchInputRef.current = event.pointerType !== 'mouse'
    }

    const handleMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return
      if (touchInputRef.current) return
      const config = getConfig()
      if (!config) return
      pointerStartRef.current = { x: e.clientX, y: e.clientY }
      pointerMovedRef.current = false
      setIsDragging(true)
      const node = graph.createNode({
        shape: config.shape,
        width: config.width,
        height: config.height,
        data: getNodeData(),
      })
      dnd.start(node, e)
    }

    const handleMouseMove = (e: MouseEvent) => {
      const start = pointerStartRef.current
      if (!start || pointerMovedRef.current) return
      pointerMovedRef.current = Math.hypot(e.clientX - start.x, e.clientY - start.y) > 4
    }

    const handleMouseUp = () => {
      const shouldAddNode = pointerStartRef.current !== null && !pointerMovedRef.current
      pointerStartRef.current = null
      pointerMovedRef.current = false
      setIsDragging(false)
      suppressNextClickRef.current = shouldAddNode
      if (shouldAddNode) onKeyAddNode?.()
    }

    const element = itemRef.current
    element.addEventListener('pointerdown', handlePointerDown)
    element.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseup', handleMouseUp, true)

    return () => {
      element.removeEventListener('pointerdown', handlePointerDown)
      element.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('mousemove', handleMouseMove, true)
      document.removeEventListener('mouseup', handleMouseUp, true)
    }
  }, [graph, dnd, getConfig, getNodeData, onKeyAddNode])

  return (
    <button
      type="button"
      ref={itemRef}
      disabled={!graph || !dnd}
      aria-label={t('designer.palette.addNode', {
        label: accessibleLabel ?? (typeof label === 'string' ? label : ''),
      })}
      className={`drag-palette-item${isDragging ? ' drag-palette-item--dragging' : ''}`}
      style={{ '--drag-accent-color': color } as React.CSSProperties}
      onClick={() => {
        if (suppressNextClickRef.current) {
          suppressNextClickRef.current = false
          return
        }
        touchInputRef.current = false
        onKeyAddNode?.()
      }}
    >
      {icon && <span className="drag-palette-item-icon">{icon}</span>}
      <span className="drag-palette-item-content">
        <span className="drag-palette-item-label">{label}</span>
        {description && <span className="drag-palette-item-desc">{description}</span>}
      </span>
    </button>
  )
}
