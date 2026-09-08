import { Dnd, Graph } from '@antv/x6'
import type { CSSProperties as CssProperties, ReactNode, RefObject } from 'react'
import { useEffect, useRef, useState } from 'react'
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
  onTouchDrop?: (clientX: number, clientY: number) => void
}

function containsPoint(bounds: DOMRect | undefined, x: number, y: number) {
  return Boolean(
    bounds && x >= bounds.left && x <= bounds.right && y >= bounds.top && y <= bounds.bottom
  )
}

interface PaletteButtonProps {
  accessibleLabel?: string
  color: string
  description?: ReactNode
  disabled: boolean
  icon?: string
  isDragging: boolean
  itemRef: RefObject<HTMLButtonElement | null>
  label: ReactNode
  onKeyAddNode?: () => void
  suppressNextClickRef: RefObject<boolean>
  touchInputRef: RefObject<boolean>
}

function PaletteButton({
  accessibleLabel,
  color,
  description,
  disabled,
  icon,
  isDragging,
  itemRef,
  label,
  onKeyAddNode,
  suppressNextClickRef,
  touchInputRef,
}: PaletteButtonProps) {
  const { t } = useTranslation()
  return (
    <button
      type="button"
      ref={itemRef}
      disabled={disabled}
      aria-label={t('designer.palette.addNode', {
        label: accessibleLabel ?? (typeof label === 'string' ? label : ''),
      })}
      className={`drag-palette-item${isDragging ? ' drag-palette-item--dragging' : ''}`}
      style={{ '--drag-accent-color': color } as CssProperties}
      onClick={(event) => {
        if (suppressNextClickRef.current && event.detail > 0) {
          suppressNextClickRef.current = false
          return
        }
        suppressNextClickRef.current = false
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
  onTouchDrop,
}: DragPaletteItemProps) {
  const itemRef = useRef<HTMLButtonElement>(null)
  const pointerStartRef = useRef<{ x: number; y: number } | null>(null)
  const pointerMovedRef = useRef(false)
  const suppressNextClickRef = useRef(false)
  const touchInputRef = useRef(false)
  const touchPointerRef = useRef<number | null>(null)
  const touchGestureRef = useRef<'pending' | 'dragging' | 'scrolling' | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  useEffect(() => {
    if (!itemRef.current || !graph || !dnd) return
    let pendingMouseDown: { event: MouseEvent; config: DragPaletteItemConfig } | null = null

    const handlePointerDown = (event: PointerEvent) => {
      suppressNextClickRef.current = false
      touchInputRef.current = event.pointerType !== 'mouse'
      if (!touchInputRef.current || event.button !== 0) return
      touchPointerRef.current = event.pointerId
      touchGestureRef.current = 'pending'
      pointerStartRef.current = { x: event.clientX, y: event.clientY }
      pointerMovedRef.current = false
    }

    const handlePointerMove = (event: PointerEvent) => {
      if (event.pointerId !== touchPointerRef.current) return
      const start = pointerStartRef.current
      if (!start) return
      const deltaX = Math.abs(event.clientX - start.x)
      const deltaY = Math.abs(event.clientY - start.y)
      if (touchGestureRef.current === 'pending' && Math.max(deltaX, deltaY) > 8) {
        touchGestureRef.current = deltaX > deltaY ? 'dragging' : 'scrolling'
        pointerMovedRef.current = touchGestureRef.current === 'dragging'
        if (pointerMovedRef.current) {
          try {
            element.setPointerCapture?.(event.pointerId)
          } catch {
            // Synthetic pointer streams used by assistive tooling do not create an active pointer.
          }
        }
      }
      if (touchGestureRef.current === 'dragging') {
        event.preventDefault()
        setIsDragging(true)
      }
    }

    const finishTouchPointer = (event: PointerEvent, cancelled: boolean) => {
      if (event.pointerId !== touchPointerRef.current) return
      const gesture = touchGestureRef.current
      const shouldDrop = !cancelled && gesture === 'dragging'
      const shouldSuppressClick = gesture === 'dragging' || gesture === 'scrolling'
      touchPointerRef.current = null
      touchGestureRef.current = null
      pointerStartRef.current = null
      pointerMovedRef.current = false
      setIsDragging(false)
      if (element.hasPointerCapture?.(event.pointerId))
        element.releasePointerCapture(event.pointerId)
      if (shouldSuppressClick) suppressNextClickRef.current = true
      if (shouldDrop) {
        const paletteBounds = element.closest('.node-palette')?.getBoundingClientRect()
        if (!containsPoint(paletteBounds, event.clientX, event.clientY))
          onTouchDrop?.(event.clientX, event.clientY)
      }
    }

    const handlePointerUp = (event: PointerEvent) => finishTouchPointer(event, false)
    const handlePointerCancel = (event: PointerEvent) => finishTouchPointer(event, true)

    const handleMouseDown = (e: MouseEvent) => {
      if (e.button !== 0) return
      if (touchInputRef.current) return
      const config = getConfig()
      if (!config) return
      pointerStartRef.current = { x: e.clientX, y: e.clientY }
      pointerMovedRef.current = false
      pendingMouseDown = { event: e, config }
    }

    const handleMouseMove = (e: MouseEvent) => {
      const start = pointerStartRef.current
      if (!start || pointerMovedRef.current) return
      pointerMovedRef.current = Math.hypot(e.clientX - start.x, e.clientY - start.y) > 4
      if (!pointerMovedRef.current || !pendingMouseDown) return
      const { event, config } = pendingMouseDown
      pendingMouseDown = null
      setIsDragging(true)
      const node = graph.createNode({
        shape: config.shape,
        width: config.width,
        height: config.height,
        data: getNodeData(),
      })
      dnd.start(node, event)
    }

    const handleMouseUp = () => {
      const shouldSuppressClick = pointerStartRef.current !== null && pointerMovedRef.current
      pendingMouseDown = null
      pointerStartRef.current = null
      pointerMovedRef.current = false
      setIsDragging(false)
      suppressNextClickRef.current = shouldSuppressClick
    }

    const element = itemRef.current
    element.addEventListener('pointerdown', handlePointerDown)
    element.addEventListener('pointermove', handlePointerMove)
    element.addEventListener('pointerup', handlePointerUp)
    element.addEventListener('pointercancel', handlePointerCancel)
    element.addEventListener('mousedown', handleMouseDown)
    document.addEventListener('mousemove', handleMouseMove, true)
    document.addEventListener('mouseup', handleMouseUp, true)

    return () => {
      element.removeEventListener('pointerdown', handlePointerDown)
      element.removeEventListener('pointermove', handlePointerMove)
      element.removeEventListener('pointerup', handlePointerUp)
      element.removeEventListener('pointercancel', handlePointerCancel)
      element.removeEventListener('mousedown', handleMouseDown)
      document.removeEventListener('mousemove', handleMouseMove, true)
      document.removeEventListener('mouseup', handleMouseUp, true)
    }
  }, [graph, dnd, getConfig, getNodeData, onKeyAddNode, onTouchDrop])

  return (
    <PaletteButton
      accessibleLabel={accessibleLabel}
      color={color}
      description={description}
      disabled={!graph || !dnd}
      icon={icon}
      isDragging={isDragging}
      itemRef={itemRef}
      label={label}
      onKeyAddNode={onKeyAddNode}
      suppressNextClickRef={suppressNextClickRef}
      touchInputRef={touchInputRef}
    />
  )
}
