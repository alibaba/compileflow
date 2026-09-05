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
      onKeyAddNode={() => {
        if (!graph) return
        const config = getNodeDragConfig(type)
        if (!config) return
        const existingNodes = graph.getNodes()
        let x = 100
        let y = 100
        if (existingNodes.length > 0) {
          const bbox = graph.getContentBBox()
          const viewH = graph.container?.clientHeight ?? 600
          x = bbox.x + 80
          y = bbox.y + bbox.height + 40
          if (y + config.height > bbox.y + Math.max(viewH, bbox.height) + 60) {
            x = bbox.x + bbox.width + 60
            y = bbox.y + 80
          }
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
        graph.select(node)
      }}
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
    if (!graph) {
      setDnd(null)
      return
    }
    logger.debug('Initializing Dnd plugin')
    try {
      const dndInstance = createUnifiedDnd(graph)
      setDnd(dndInstance)
      logger.info('Dnd plugin initialized successfully')
    } catch (error) {
      logger.error('Dnd initialization failed', toError(error))
      message.error(t('designer.palette.dndFailed'))
    }
    return () => setDnd(null)
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
    <div className="node-palette surface-panel">
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
