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
  const [isDragging, setIsDragging] = useState(false)

  useEffect(() => {
    if (!itemRef.current || !graph || !dnd) return

    const handleMouseDown = (e: MouseEvent) => {
      const config = getConfig()
      if (!config) return
      setIsDragging(true)
      const node = graph.createNode({
        shape: config.shape,
        width: config.width,
        height: config.height,
        data: getNodeData(),
      })
      dnd.start(node, e)
    }

    const handleMouseUp = () => setIsDragging(false)

    const element = itemRef.current
    element.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('mouseup', handleMouseUp)

    return () => {
      element.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('mouseup', handleMouseUp)
    }
  }, [graph, dnd, getConfig, getNodeData])

  return (
    <button
      type="button"
      ref={itemRef}
      disabled={!graph || !dnd}
      aria-label={t('designer.palette.dragToAdd', {
        label: accessibleLabel ?? (typeof label === 'string' ? label : ''),
      })}
      className={`drag-palette-item${isDragging ? ' drag-palette-item--dragging' : ''}`}
      style={{ '--drag-accent-color': color } as React.CSSProperties}
      onKeyDown={(e) => {
        if ((e.key === 'Enter' || e.key === ' ') && graph && onKeyAddNode) {
          e.preventDefault()
          onKeyAddNode()
        }
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
