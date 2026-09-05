import { CloseOutlined, EditOutlined, InboxOutlined } from '@ant-design/icons'
import { Button, Card, Form, Input, Space } from 'antd'
import React, { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { selectCurrentProcess, updateConnection } from '../store/editorSlice'
import type { ProcessConnection, UnifiedProcessDefinition } from '../types/flowDefinition'

import ConditionExpressionEditor from './ConditionExpressionEditor'
import { toExpressionVariables } from './expressionVariables'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

interface EdgePropertiesPanelProps {
  edge: ProcessConnection | null
  onClose?: () => void
}

function useExpressionVariables() {
  const currentProcess = useAppSelector(selectCurrentProcess)
  return toExpressionVariables(currentProcess?.variables)
}

function isExpressionGateway(nodeType: string | undefined): boolean {
  return ['exclusive', 'bpmn:ExclusiveGateway', 'bpmn:InclusiveGateway'].includes(nodeType || '')
}

function resolveDefaultConnectionId(
  sourceNode: UnifiedProcessDefinition['nodes'][number] | undefined
): string | undefined {
  const properties = sourceNode?.properties
  if (!properties || !('default' in properties)) return undefined
  return typeof properties.default === 'string' ? properties.default : undefined
}

function resolveEdgeCapabilities(
  currentProcess: UnifiedProcessDefinition,
  edge: ProcessConnection
) {
  const sourceNode = currentProcess.nodes.find((node) => node.id === edge.sourceId)
  const sourceOutgoingCount = currentProcess.connections.filter(
    (connection) => connection.sourceId === edge.sourceId
  ).length
  const supportsExpression = sourceOutgoingCount > 1 && isExpressionGateway(sourceNode?.type)
  const defaultConnectionId = resolveDefaultConnectionId(sourceNode)
  const isBpmnDefault =
    currentProcess.type === 'BPMN' &&
    typeof defaultConnectionId === 'string' &&
    defaultConnectionId === edge.id
  return { modelType: currentProcess.type, supportsExpression, isBpmnDefault }
}

function EmptyEdgeProperties() {
  const { t } = useTranslation()
  return (
    <div className="properties-panel-empty">
      <InboxOutlined className="properties-panel-empty-icon" />
      <div className="properties-panel-empty-text">{t('designer.properties.selectEdge')}</div>
      <div className="properties-panel-empty-hint">{t('designer.properties.selectEdgeHint')}</div>
    </div>
  )
}

function EdgeFormFields({
  expressionEnabled,
  onOpenExpressionEditor,
}: {
  expressionEnabled: boolean
  onOpenExpressionEditor: () => void
}) {
  const { t } = useTranslation()
  return (
    <>
      <Form.Item
        label={t('designer.edge.name')}
        name="name"
        tooltip={t('designer.edge.nameTooltip')}
      >
        <Input
          aria-label={t('designer.edge.name')}
          placeholder={t('designer.edge.namePlaceholder')}
        />
      </Form.Item>

      {expressionEnabled && (
        <Form.Item
          label={
            <Space size={4}>
              <span>{t('designer.edge.condition')}</span>
              <Button
                type="link"
                size="small"
                icon={<EditOutlined />}
                onClick={onOpenExpressionEditor}
                style={{ padding: '0 4px', height: 'auto', fontSize: 12 }}
              >
                {t('designer.edge.advancedEdit')}
              </Button>
            </Space>
          }
          name="condition"
          tooltip={t('designer.edge.expressionTooltip')}
          help={t('designer.edge.expressionHelp')}
        >
          <Input.TextArea
            aria-label={t('designer.edge.condition')}
            rows={3}
            placeholder={t('designer.edge.expressionPlaceholder')}
            style={{ fontFamily: 'Monaco, Consolas, monospace', fontSize: 12 }}
          />
        </Form.Item>
      )}
    </>
  )
}

function EdgeInfo({ edge }: { edge: ProcessConnection }) {
  const { t } = useTranslation()
  return (
    <div className="edge-info-block">
      <div>
        <strong>{t('designer.edge.sourceNode')}:</strong> {edge.sourceId}
      </div>
      <div>
        <strong>{t('designer.edge.targetNode')}:</strong> {edge.targetId}
      </div>
    </div>
  )
}

const EdgePropertiesPanel = React.memo(function EdgePropertiesPanel({
  edge,
  onClose,
}: EdgePropertiesPanelProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const [form] = Form.useForm()
  const [exprEditorOpen, setExprEditorOpen] = useState(false)
  const variables = useExpressionVariables()
  const currentProcess = useAppSelector(selectCurrentProcess)
  const capabilities =
    currentProcess && edge ? resolveEdgeCapabilities(currentProcess, edge) : undefined
  const expressionEnabled = Boolean(capabilities?.supportsExpression && !capabilities.isBpmnDefault)

  useEffect(() => {
    if (edge) {
      form.setFieldsValue({
        name: edge.name || '',
        condition: expressionEnabled ? edge.condition || '' : '',
      })
    }
  }, [edge, expressionEnabled, form])

  const handleValuesChange = (_: unknown, allValues: { name?: string; condition?: string }) => {
    if (!edge) return
    const condition = expressionEnabled ? allValues.condition?.trim() || undefined : undefined
    dispatch(
      updateConnection({
        id: edge.id,
        updates: {
          name: allValues.name || undefined,
          condition,
        },
      })
    )
  }

  if (!edge || !capabilities) {
    return <EmptyEdgeProperties />
  }

  return (
    <Card
      size="small"
      title={
        <Space>
          <span>{t('designer.edge.title')}</span>
          <span className="properties-panel-type-badge">{edge.id}</span>
        </Space>
      }
      extra={
        onClose && (
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined />}
            onClick={onClose}
            aria-label={t('common.cancel')}
          />
        )
      }
      styles={{ body: { padding: 16 } }}
    >
      <Form form={form} layout="vertical" onValuesChange={handleValuesChange} autoComplete="off">
        <EdgeFormFields
          expressionEnabled={expressionEnabled}
          onOpenExpressionEditor={() => setExprEditorOpen(true)}
        />
        <EdgeInfo edge={edge} />
      </Form>

      {expressionEnabled && (
        <ConditionExpressionEditor
          visible={exprEditorOpen}
          condition={edge.condition || ''}
          type={capabilities.modelType}
          variables={variables}
          onOk={(condition) => {
            dispatch(updateConnection({ id: edge.id, updates: { condition } }))
            form.setFieldValue('condition', condition)
            setExprEditorOpen(false)
          }}
          onCancel={() => setExprEditorOpen(false)}
        />
      )}
    </Card>
  )
})

EdgePropertiesPanel.displayName = 'EdgePropertiesPanel'

export default EdgePropertiesPanel
