import { CloseOutlined, InboxOutlined } from '@ant-design/icons'
import { App, Button, Card, Space, Tabs } from 'antd'
import React, { useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { selectNodes, updateNode } from '../store/editorSlice'
import { selectNode, selectSelectedNodeId } from '../store/uiSlice'
import type { BaseNode, ProcessNode } from '../types/flowDefinition'

import { GeneralPropertiesTab } from './properties/GeneralPropertiesTab'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

interface PropertyTabConfig<TNode extends ProcessNode> {
  key: string
  labelKey: string
  component: React.ComponentType<{
    node: TNode
    onUpdate: (nodeId: string, updates: Partial<TNode['properties']>) => void
  }>
}

export interface BasePropertiesPanelProps<TNode extends ProcessNode> {
  /** Panel title shown in the Card header. */
  title: string
  getPropertyConfig: (node: TNode) => PropertyTabConfig<TNode> | null | undefined
  isNode: (node: ProcessNode) => node is TNode
}

function BasePropertiesPanelInner<TNode extends ProcessNode>({
  title,
  getPropertyConfig,
  isNode,
}: BasePropertiesPanelProps<TNode>) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const rawNodes = useAppSelector(selectNodes)
  const selectedNodeId = useAppSelector(selectSelectedNodeId)

  const selectedNode = useMemo<TNode | null>(() => {
    if (!selectedNodeId) return null
    const node = rawNodes.find((candidate) => candidate.id === selectedNodeId)
    return node && isNode(node) ? node : null
  }, [isNode, rawNodes, selectedNodeId])

  const handleUpdate = useCallback(
    (values: Partial<Omit<BaseNode, 'id' | 'type'>>) => {
      if (!selectedNode) return
      try {
        dispatch(updateNode({ id: selectedNode.id, updates: values }))
      } catch {
        message.error(t('designer.properties.updateFailed'))
      }
    },
    [selectedNode, dispatch]
  )

  const handleClose = useCallback(() => {
    dispatch(selectNode(null))
  }, [dispatch])

  if (!selectedNode) {
    return (
      <Card title={title} size="small">
        <div className="properties-panel-empty">
          <InboxOutlined className="properties-panel-empty-icon" />
          <div className="properties-panel-empty-text">{t('designer.properties.selectNode')}</div>
          <div className="properties-panel-empty-hint">
            {t('designer.properties.selectNodeHint')}
          </div>
        </div>
      </Card>
    )
  }

  const tabItems: { key: string; label: string; children: React.ReactNode }[] = [
    {
      key: 'general',
      label: t('designer.properties.generalTab'),
      children: <GeneralPropertiesTab node={selectedNode} onUpdate={handleUpdate} />,
    },
  ]

  const propertyConfig = getPropertyConfig(selectedNode)
  if (propertyConfig) {
    const SpecificTabComponent = propertyConfig.component
    tabItems.push({
      key: propertyConfig.key,
      label: t(propertyConfig.labelKey),
      children: (
        <SpecificTabComponent
          node={selectedNode}
          onUpdate={(_nodeId, updates) => {
            handleUpdate({
              properties: { ...selectedNode.properties, ...updates },
            })
          }}
        />
      ),
    })
  }

  return (
    <Card
      title={title}
      size="small"
      extra={
        <Space>
          <span className="properties-panel-type-badge">{selectedNode.type}</span>
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            onClick={handleClose}
            aria-label={t('designer.props.common.close')}
          />
        </Space>
      }
    >
      <Tabs items={tabItems} size="small" tabBarStyle={{ marginBottom: 8 }} />
    </Card>
  )
}

// Wrap with React.memo.  The forwardRef-style naming is needed because the
// generic function can't be passed directly to React.memo without losing type params.
export const BasePropertiesPanel = React.memo(
  BasePropertiesPanelInner
) as typeof BasePropertiesPanelInner
