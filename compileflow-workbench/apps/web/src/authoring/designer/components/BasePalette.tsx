import { RightOutlined, SearchOutlined } from '@ant-design/icons'
import { Dnd, Graph } from '@antv/x6'
import { App, Badge, Card, Collapse, Empty, Input, Space } from 'antd'
import { debounce } from 'lodash-es'
import React, { ReactNode, useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { DragPaletteItem } from './DragPaletteItem'

import { createUnifiedDnd } from '@/authoring/designer/canvas/dragAndDrop'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

import './DesignerSurfaces.css'
import './NodePalette.css'

interface PaletteNodeConfig {
  type: string
  label: string
  icon?: string
  description?: string
}

export interface PaletteCategoryConfig {
  key: string
  title: string
  icon: ReactNode
  color: string
  nodes: readonly PaletteNodeConfig[]
}

interface NodeDragConfig {
  shape: string
  width: number
  height: number
}

function findVisibleNodePosition(graph: Graph, config: NodeDragConfig) {
  const surface = graph.container.closest('.x6-graph-scroller') ?? graph.container
  const bounds = surface.getBoundingClientRect()
  const topLeft = graph.clientToLocal({ x: bounds.left, y: bounds.top })
  const bottomRight = graph.clientToLocal({ x: bounds.right, y: bounds.bottom })
  const grid = graph.getGridSize()
  const minX = Math.min(topLeft.x, bottomRight.x)
  const minY = Math.min(topLeft.y, bottomRight.y)
  const maxX = Math.max(minX, Math.max(topLeft.x, bottomRight.x) - config.width)
  const maxY = Math.max(minY, Math.max(topLeft.y, bottomRight.y) - config.height)
  const centerX = Math.round((minX + maxX) / 2 / grid) * grid
  const centerY = Math.round((minY + maxY) / 2 / grid) * grid
  const existingBoxes = graph.getNodes().map((node) => node.getBBox())
  const stepX = config.width + grid
  const stepY = config.height + grid
  const maxRing = Math.ceil(Math.sqrt(existingBoxes.length + 1)) + 1

  for (let ring = 0; ring <= maxRing; ring += 1) {
    for (let row = -ring; row <= ring; row += 1) {
      for (let column = -ring; column <= ring; column += 1) {
        if (ring > 0 && Math.abs(row) !== ring && Math.abs(column) !== ring) continue
        const x = centerX + column * stepX
        const y = centerY + row * stepY
        if (x < minX || x > maxX || y < minY || y > maxY) continue
        const overlaps = existingBoxes.some(
          (box) =>
            x < box.x + box.width + grid &&
            x + config.width + grid > box.x &&
            y < box.y + box.height + grid &&
            y + config.height + grid > box.y
        )
        if (!overlaps) return { x, y }
      }
    }
  }

  return { x: centerX, y: centerY }
}

export interface BasePaletteProps {
  graph: Graph | null
  title: string
  categories: readonly PaletteCategoryConfig[]
  getNodeDragConfig: (type: string) => NodeDragConfig | null
  getNodeColor: (type: string) => string
  getNodeData: (type: string, label: string) => Record<string, unknown>
  defaultActiveKeys?: string[]
  loggerName?: string
}

function filterPaletteCategories(
  categories: readonly PaletteCategoryConfig[],
  searchText: string
): readonly PaletteCategoryConfig[] {
  if (!searchText.trim()) return categories
  const lowerSearch = searchText.toLowerCase()
  return categories
    .map((category) => ({
      ...category,
      nodes: category.nodes.filter(
        (node) =>
          node.label.toLowerCase().includes(lowerSearch) ||
          node.description?.toLowerCase().includes(lowerSearch) ||
          node.type.toLowerCase().includes(lowerSearch)
      ),
    }))
    .filter((category) => category.nodes.length > 0)
}

/** Highlight matching text portions with a yellow background. */
function highlightMatch(text: string, query: string): React.ReactNode {
  if (!query.trim()) return text
  const lowerText = text.toLowerCase()
  const lowerQuery = query.toLowerCase()
  const idx = lowerText.indexOf(lowerQuery)
  if (idx === -1) return text
  return (
    <>
      {text.slice(0, idx)}
      <span className="palette-search-highlight">{text.slice(idx, idx + query.length)}</span>
      {text.slice(idx + query.length)}
    </>
  )
}

const PaletteItem = React.memo(function PaletteItem({
  type,
  label,
  icon,
  description,
  graph,
  dnd,
  getNodeDragConfig,
  getNodeColor,
  getNodeData,
  searchQuery,
}: PaletteNodeConfig & {
  graph: Graph | null
  dnd: Dnd | null
  getNodeDragConfig: (type: string) => NodeDragConfig | null
  getNodeColor: (type: string) => string
  getNodeData: (type: string, label: string) => Record<string, unknown>
  searchQuery: string
}) {
  const color = getNodeColor(type)

  const addNode = (dropPoint?: { x: number; y: number }) => {
    if (!graph) return
    const config = getNodeDragConfig(type)
    if (!config) return

    let x: number
    let y: number
    if (dropPoint) {
      const topLayer = graph.container.ownerDocument.elementFromPoint(dropPoint.x, dropPoint.y)
      if (topLayer?.closest('.node-palette')) return
      const surface = graph.container.closest('.x6-graph-scroller') ?? graph.container
      const bounds = surface.getBoundingClientRect()
      if (
        dropPoint.x < bounds.left ||
        dropPoint.x > bounds.right ||
        dropPoint.y < bounds.top ||
        dropPoint.y > bounds.bottom
      ) {
        return
      }
      const local = graph.clientToLocal(dropPoint)
      const gridSize = graph.getGridSize()
      x = Math.round((local.x - config.width / 2) / gridSize) * gridSize
      y = Math.round((local.y - config.height / 2) / gridSize) * gridSize
    } else {
      const position = findVisibleNodePosition(graph, config)
      x = position.x
      y = position.y
    }

    const node = graph.createNode({
      shape: config.shape,
      width: config.width,
      height: config.height,
      x,
      y,
      data: getNodeData(type, label),
    })
    graph.addNode(node)
    graph.cleanSelection()
    graph.select(node)
  }

  return (
    <DragPaletteItem
      accessibleLabel={label}
      label={searchQuery ? highlightMatch(label, searchQuery) : label}
      description={
        description && searchQuery ? highlightMatch(description, searchQuery) : description
      }
      icon={icon}
      color={color}
      graph={graph}
      dnd={dnd}
      getConfig={() => {
        const config = getNodeDragConfig(type)
        if (!config) return null
        return { shape: config.shape, width: config.width, height: config.height }
      }}
      getNodeData={() => getNodeData(type, label)}
      onKeyAddNode={() => addNode()}
      onTouchDrop={(clientX, clientY) => addNode({ x: clientX, y: clientY })}
    />
  )
})

export const BasePalette = React.memo(function BasePalette({
  graph,
  title,
  categories,
  getNodeDragConfig,
  getNodeColor,
  getNodeData,
  defaultActiveKeys,
  loggerName = 'BasePalette',
}: BasePaletteProps) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const logger = useMemo(() => createLogger(loggerName), [loggerName])
  const paletteRef = React.useRef<HTMLDivElement>(null)
  const [searchText, setSearchText] = useState('')
  const [activeKeys, setActiveKeys] = useState<string[]>(
    defaultActiveKeys ?? categories.slice(0, 3).map((c) => c.key)
  )
  const [dnd, setDnd] = useState<Dnd | null>(null)

  const debouncedSearch = useMemo(() => {
    const debounced = debounce((value: string) => setSearchText(value), 300)
    return debounced
  }, [])

  useEffect(() => {
    return () => debouncedSearch.cancel?.()
  }, [debouncedSearch])

  useEffect(() => {
    if (!graph || !paletteRef.current) {
      setDnd(null)
      return
    }
    logger.debug('Initializing Dnd plugin')
    let dndInstance: Dnd
    try {
      dndInstance = createUnifiedDnd(graph, paletteRef.current)
      setDnd(dndInstance)
      logger.info('Dnd plugin initialized successfully')
    } catch (error) {
      logger.error('Dnd initialization failed', toError(error))
      message.error(t('designer.palette.dndFailed'))
      return
    }
    return () => {
      dndInstance.dispose()
      setDnd(null)
    }
  }, [graph, logger])

  const filteredCategories = useMemo(
    () => filterPaletteCategories(categories, searchText),
    [categories, searchText]
  )

  // When searching, expand all panels so results are visible
  const effectiveActiveKeys = useMemo(() => {
    if (searchText.trim()) return filteredCategories.map((c) => c.key)
    return activeKeys
  }, [searchText, filteredCategories, activeKeys])

  const totalNodeCount = useMemo(
    () => filteredCategories.reduce((sum, cat) => sum + cat.nodes.length, 0),
    [filteredCategories]
  )

  const handleCollapseChange = useCallback((keys: string | string[]) => {
    setActiveKeys(Array.isArray(keys) ? keys : [keys])
  }, [])

  return (
    <div ref={paletteRef} className="node-palette surface-panel">
      <Card
        title={
          <Space>
            <span>{title}</span>
            <Badge
              count={totalNodeCount}
              color={undefined}
              style={{ backgroundColor: 'var(--success-main)' }}
            />
          </Space>
        }
        variant="borderless"
        styles={{ body: { padding: 0 } }}
      >
        <div className="node-palette-search">
          <Input
            prefix={<SearchOutlined aria-hidden="true" />}
            placeholder={t('designer.palette.searchPlaceholder')}
            aria-label={t('designer.palette.searchPlaceholder')}
            onChange={(e) => debouncedSearch(e.target.value)}
            allowClear
            onClear={() => setSearchText('')}
          />
        </div>

        {filteredCategories.length > 0 ? (
          <Collapse
            activeKey={effectiveActiveKeys}
            onChange={handleCollapseChange}
            bordered={false}
            expandIcon={({ isActive }) => (
              <RightOutlined
                rotate={isActive ? 90 : 0}
                style={{
                  fontSize: 12,
                  color: 'var(--color-text-tertiary)',
                  transition: 'transform var(--transition-base)',
                }}
              />
            )}
            items={filteredCategories.map((category) => ({
              key: category.key,
              label: (
                <Space>
                  <span
                    className="palette-category-accent"
                    style={{ background: category.color }}
                  />
                  <span>{category.icon}</span>
                  <span className="palette-category-title">{category.title}</span>
                  <Badge
                    count={category.nodes.length}
                    style={{ backgroundColor: category.color }}
                  />
                </Space>
              ),
              children: (
                <Space vertical style={{ width: '100%' }} size={8}>
                  {category.nodes.map((node) => (
                    <PaletteItem
                      key={node.type}
                      {...node}
                      graph={graph}
                      dnd={dnd}
                      getNodeDragConfig={getNodeDragConfig}
                      getNodeColor={getNodeColor}
                      getNodeData={getNodeData}
                      searchQuery={searchText}
                    />
                  ))}
                </Space>
              ),
            }))}
          />
        ) : (
          <div style={{ padding: 24 }}>
            <Empty description={t('designer.palette.empty')} image={Empty.PRESENTED_IMAGE_SIMPLE}>
              <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                {t('designer.palette.emptyHint')}
              </span>
            </Empty>
          </div>
        )}
      </Card>
    </div>
  )
})

BasePalette.displayName = 'BasePalette'
