import {
  AppstoreOutlined,
  BlockOutlined,
  BorderOuterOutlined,
  GatewayOutlined,
} from '@ant-design/icons'
import { Graph } from '@antv/x6'
import type { TFunction } from 'i18next'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { BasePalette, type PaletteCategoryConfig } from './BasePalette'
import { getBpmnNodeConfig } from './nodes/registerBpmnNodes'

interface BpmnNodePaletteProps {
  graph: Graph | null
}

function buildBpmnCategories(t: TFunction): readonly PaletteCategoryConfig[] {
  return [
    {
      key: 'events',
      title: t('designer.palette.bpmn.cat.events'),
      icon: <BorderOuterOutlined />,
      color: 'var(--success-main)',
      nodes: [
        {
          type: 'bpmn:StartEvent',
          label: t('designer.palette.bpmn.node.startEvent'),
          icon: '●',
          description: t('designer.palette.bpmn.node.startEventDesc'),
        },
        {
          type: 'bpmn:EndEvent',
          label: t('designer.palette.bpmn.node.endEvent'),
          icon: '■',
          description: t('designer.palette.bpmn.node.endEventDesc'),
        },
      ],
    },
    {
      key: 'tasks',
      title: t('designer.palette.bpmn.cat.tasks'),
      icon: <AppstoreOutlined />,
      color: 'var(--color-primary)',
      nodes: [
        {
          type: 'bpmn:ServiceTask',
          label: t('designer.palette.bpmn.node.serviceTask'),
          description: t('designer.palette.bpmn.node.serviceTaskDesc'),
        },
        {
          type: 'bpmn:ScriptTask',
          label: t('designer.palette.bpmn.node.scriptTask'),
          description: t('designer.palette.bpmn.node.scriptTaskDesc'),
        },
        {
          type: 'bpmn:ReceiveTask',
          label: t('designer.palette.bpmn.node.receiveTask'),
          description: t('designer.palette.bpmn.node.receiveTaskDesc'),
        },
      ],
    },
    {
      key: 'gateways',
      title: t('designer.palette.bpmn.cat.gateways'),
      icon: <GatewayOutlined />,
      color: 'var(--warning-main)',
      nodes: [
        {
          type: 'bpmn:ExclusiveGateway',
          label: t('designer.palette.bpmn.node.exclusiveGateway'),
          icon: '✕',
          description: t('designer.palette.bpmn.node.exclusiveGatewayDesc'),
        },
        {
          type: 'bpmn:ParallelGateway',
          label: t('designer.palette.bpmn.node.parallelGateway'),
          icon: '＋',
          description: t('designer.palette.bpmn.node.parallelGatewayDesc'),
        },
        {
          type: 'bpmn:InclusiveGateway',
          label: t('designer.palette.bpmn.node.inclusiveGateway'),
          icon: '○',
          description: t('designer.palette.bpmn.node.inclusiveGatewayDesc'),
        },
      ],
    },
    {
      key: 'composition',
      title: t('designer.palette.bpmn.cat.composition'),
      icon: <BlockOutlined />,
      color: 'var(--node-subprocess-main)',
      nodes: [
        {
          type: 'bpmn:CallActivity',
          label: t('designer.palette.bpmn.node.callActivity'),
          description: t('designer.palette.bpmn.node.callActivityDesc'),
        },
        {
          type: 'bpmn:SubProcess',
          label: t('designer.palette.bpmn.node.subProcess'),
          description: t('designer.palette.bpmn.node.subProcessDesc'),
        },
      ],
    },
  ]
}

function getBpmnNodeColor(type: string): string {
  if (type.includes('Event')) return 'var(--success-main)'
  if (type.includes('Task')) return 'var(--color-primary)'
  if (type.includes('Gateway')) return 'var(--warning-main)'
  return 'var(--node-subprocess-main)'
}

export default function BpmnNodePalette({ graph }: BpmnNodePaletteProps) {
  const { t } = useTranslation()
  const categories = useMemo(() => buildBpmnCategories(t), [t])

  return (
    <BasePalette
      graph={graph}
      title={t('designer.palette.bpmnTitle')}
      categories={categories}
      getNodeDragConfig={(type) => {
        const config = getBpmnNodeConfig(type)
        if (!config) return null
        return { shape: config.shape, width: config.width, height: config.height }
      }}
      getNodeColor={getBpmnNodeColor}
      getNodeData={(type, label) => ({ label, type })}
      defaultActiveKeys={['events', 'tasks', 'gateways']}
      loggerName="BpmnNodePalette"
    />
  )
}
