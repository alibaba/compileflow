import { NodeIndexOutlined, SearchOutlined } from '@ant-design/icons'
import type { InputRef } from 'antd'
import { Empty, Input, Space, Tag } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { useDesignerContext } from '../context'
import { selectNodes } from '../store/editorSlice'
import { selectNode, selectShowSearch, togglePanel } from '../store/uiSlice'
import type { BaseNode } from '../types/flowDefinition'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

import './DesignerSurfaces.css'
import './NodeSearch.css'

const { Search } = Input

const NODE_TYPE_COLORS: Record<string, string> = {
  start: 'green',
  end: 'red',
  autoTask: 'blue',
  scriptTask: 'purple',
  exclusive: 'orange',
  parallel: 'cyan',
  inclusive: 'geekblue',
  subBpm: 'purple',
  bpmCall: 'magenta',
  while: 'volcano',
  foreach: 'magenta',
  waitTask: 'gold',
  waitEventTask: 'lime',
  timerTask: 'gold',
  break: 'volcano',
  continue: 'volcano',
  note: 'default',
}

interface NodeSearchTitleProps {
  count: number
}

interface NodeSearchResultsProps {
  nodes: BaseNode[]
  query: string
  onSelectNode: (nodeId: string) => void
}

const matchesNodeQuery = (node: BaseNode, query: string) =>
  (node.name || '').toLowerCase().includes(query) ||
  node.id.toLowerCase().includes(query) ||
  node.type.toLowerCase().includes(query)

const getNodeTypeColor = (type: string): string => NODE_TYPE_COLORS[type] || 'default'

function NodeSearchTitle({ count }: NodeSearchTitleProps) {
  const { t } = useTranslation()

  return (
    <Space>
      <SearchOutlined />
      <span>{t('designer.nodeSearch.title')}</span>
      <span style={{ fontSize: 12, color: 'var(--color-text-tertiary)', fontWeight: 'normal' }}>
        {t('designer.nodeSearch.total', { count })}
      </span>
    </Space>
  )
}

function NodeSearchTips() {
  const { t } = useTranslation()

  return (
    <div className="node-search-tips">
      <p className="node-search-tips-title">{t('designer.nodeSearch.tipsTitle')}</p>
      <ul style={{ marginLeft: 20, marginBottom: 0 }}>
        <li>{t('designer.nodeSearch.tip1')}</li>
        <li>{t('designer.nodeSearch.tip2')}</li>
        <li>{t('designer.nodeSearch.tip3')}</li>
        <li>{t('designer.nodeSearch.tip4')}</li>
      </ul>
    </div>
  )
}

function NodeSearchIdleState() {
  const { t } = useTranslation()

  return (
    <div className="node-search-empty">
      <SearchOutlined style={{ fontSize: 48, marginBottom: 16 }} />
      <div>{t('designer.nodeSearch.startHint')}</div>
      <div className="node-search-empty-hint">{t('designer.nodeSearch.startSubhint')}</div>
    </div>
  )
}

function NodeSearchResultItem({
  node,
  onSelectNode,
}: {
  node: BaseNode
  onSelectNode: (nodeId: string) => void
}) {
  const { t } = useTranslation()
  const getNodeTypeName = useCallback(
    (type: string): string => {
      const key = `designer.nodeSearch.type.${type}` as const
      const translated = t(key)
      return translated === key ? type : translated
    },
    [t]
  )

  return (
    <li>
      <button
        type="button"
        className="node-search-result-item"
        onClick={() => onSelectNode(node.id)}
      >
        <NodeIndexOutlined
          className="node-search-result-icon"
          style={{ fontSize: 20, color: 'var(--color-primary)' }}
        />
        <div className="node-search-result-content">
          <div className="node-search-result-title">
            <Space>
              <span style={{ fontWeight: 500 }}>{node.name}</span>
              <Tag color={getNodeTypeColor(node.type)}>{getNodeTypeName(node.type)}</Tag>
            </Space>
          </div>
          <div className="node-search-result-description">
            <Space vertical size={4} style={{ width: '100%' }}>
              <div className="node-search-meta-text">
                {t('designer.nodeSearch.nodeId')}:{' '}
                <code className="node-search-meta-code">{node.id}</code>
              </div>
              <div className="node-search-meta-text">
                {t('designer.nodeSearch.position')}: ({node.position.x}, {node.position.y})
              </div>
            </Space>
          </div>
        </div>
      </button>
    </li>
  )
}

function NodeSearchResults({ nodes, query, onSelectNode }: NodeSearchResultsProps) {
  const { t } = useTranslation()

  if (!query) return <NodeSearchIdleState />
  if (nodes.length === 0) {
    return (
      <Empty
        description={t('designer.nodeSearch.noResults')}
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  return (
    <ul className="node-search-result-list">
      {nodes.map((node) => (
        <NodeSearchResultItem key={node.id} node={node} onSelectNode={onSelectNode} />
      ))}
    </ul>
  )
}

function NodeSearch() {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const { graphRef } = useDesignerContext()
  const nodes = useAppSelector(selectNodes)
  const showSearch = useAppSelector(selectShowSearch)
  const [searchQuery, setSearchQuery] = useState('')
  const inputRef = useRef<InputRef | null>(null)
  const focusTimer = useRef<number | undefined>(undefined)
  const handleClose = useCallback(() => dispatch(togglePanel('showSearch')), [dispatch])
  useEscapeToClose(showSearch, handleClose)

  const normalizedQuery = searchQuery.trim().toLowerCase()
  const filteredNodes = useMemo(
    () => (normalizedQuery ? nodes.filter((node) => matchesNodeQuery(node, normalizedQuery)) : []),
    [nodes, normalizedQuery]
  )

  useEffect(() => {
    if (focusTimer.current !== undefined) clearTimeout(focusTimer.current)
    if (showSearch) {
      focusTimer.current = window.setTimeout(() => {
        focusTimer.current = undefined
        inputRef.current?.focus()
      }, 100)
    } else {
      setSearchQuery('')
    }
    return () => {
      if (focusTimer.current !== undefined) clearTimeout(focusTimer.current)
    }
  }, [showSearch])

  const handleSelectNode = useCallback(
    (nodeId: string) => {
      dispatch(selectNode(nodeId))

      const cell = graphRef.current?.getCellById(nodeId)
      if (cell) graphRef.current?.centerCell(cell)

      dispatch(togglePanel('showSearch'))
    },
    [dispatch, graphRef]
  )

  return (
    <Modal
      title={<NodeSearchTitle count={nodes?.length || 0} />}
      open={showSearch}
      onCancel={handleClose}
      footer={null}
      width={600}
      className="designer-surface-modal"
      destroyOnHidden
    >
      <div style={{ marginBottom: 16 }}>
        <Search
          ref={inputRef}
          placeholder={t('designer.nodeSearch.placeholder')}
          value={searchQuery}
          onChange={(event) => setSearchQuery(event.target.value)}
          allowClear
          size="large"
          enterButton
          aria-label={t('designer.nodeSearch.title')}
        />
      </div>

      <div className="node-search-results-container">
        <NodeSearchResults
          nodes={filteredNodes}
          query={normalizedQuery}
          onSelectNode={handleSelectNode}
        />
      </div>

      <NodeSearchTips />
    </Modal>
  )
}

export default NodeSearch
