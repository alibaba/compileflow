import {
  ApartmentOutlined,
  BranchesOutlined,
  ContainerOutlined,
  ControlOutlined,
  SettingOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { Graph } from '@antv/x6'
import type { TFunction } from 'i18next'
import { memo, useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { BasePalette, type PaletteCategoryConfig } from './BasePalette'
import { getNodeConfig } from './nodes/registerNodes'

import { getNodeColor } from '@/shared/styles/design-tokens'

interface NodePaletteProps {
  graph: Graph | null
}

function buildTbbpmCategories(t: TFunction): readonly PaletteCategoryConfig[] {
  return [
    {
      key: 'flow',
      title: t('designer.palette.tbbpm.cat.flow'),
      icon: <ApartmentOutlined />,
      color: 'var(--success-main)',
      nodes: [
        {
          type: 'start',
          label: t('designer.palette.tbbpm.node.start'),
          description: t('designer.palette.tbbpm.node.startDesc'),
        },
        {
          type: 'end',
          label: t('designer.palette.tbbpm.node.end'),
          description: t('designer.palette.tbbpm.node.endDesc'),
        },
      ],
    },
    {
      key: 'task',
      title: t('designer.palette.tbbpm.cat.task'),
      icon: <SettingOutlined />,
      color: 'var(--color-primary)',
      nodes: [
        {
          type: 'autoTask',
          label: t('designer.palette.tbbpm.node.autoTask'),
          description: t('designer.palette.tbbpm.node.autoTaskDesc'),
        },
        {
          type: 'waitTask',
          label: t('designer.palette.tbbpm.node.waitTask'),
          description: t('designer.palette.tbbpm.node.waitTaskDesc'),
        },
        {
          type: 'waitEventTask',
          label: t('designer.palette.tbbpm.node.waitEventTask'),
          description: t('designer.palette.tbbpm.node.waitEventTaskDesc'),
        },
        {
          type: 'timerTask',
          label: t('designer.palette.tbbpm.node.timerTask'),
          description: t('designer.palette.tbbpm.node.timerTaskDesc'),
        },
        {
          type: 'scriptTask',
          label: t('designer.palette.tbbpm.node.scriptTask'),
          description: t('designer.palette.tbbpm.node.scriptTaskDesc'),
        },
      ],
    },
    {
      key: 'gateway',
      title: t('designer.palette.tbbpm.cat.gateway'),
      icon: <BranchesOutlined />,
      color: 'var(--warning-main)',
      nodes: [
        {
          type: 'exclusive',
          label: t('designer.palette.tbbpm.node.exclusive'),
          description: t('designer.palette.tbbpm.node.exclusiveDesc'),
        },
        {
          type: 'parallel',
          label: t('designer.palette.tbbpm.node.parallel'),
          description: t('designer.palette.tbbpm.node.parallelDesc'),
        },
        {
          type: 'inclusive',
          label: t('designer.palette.tbbpm.node.inclusive'),
          description: t('designer.palette.tbbpm.node.inclusiveDesc'),
        },
      ],
    },
    {
      key: 'subprocess',
      title: t('designer.palette.tbbpm.cat.subprocess'),
      icon: <ContainerOutlined />,
      color: 'var(--node-subprocess-main)',
      nodes: [
        {
          type: 'subBpm',
          label: t('designer.palette.tbbpm.node.subBpm'),
          description: t('designer.palette.tbbpm.node.subBpmDesc'),
        },
        {
          type: 'bpmCall',
          label: t('designer.palette.tbbpm.node.bpmCall'),
          description: t('designer.palette.tbbpm.node.bpmCallDesc'),
        },
      ],
    },
    {
      key: 'loop',
      title: t('designer.palette.tbbpm.cat.loop'),
      icon: <SyncOutlined />,
      color: 'var(--node-loop-main)',
      nodes: [
        {
          type: 'while',
          label: t('designer.palette.tbbpm.node.while'),
          description: t('designer.palette.tbbpm.node.whileDesc'),
        },
        {
          type: 'foreach',
          label: t('designer.palette.tbbpm.node.foreach'),
          description: t('designer.palette.tbbpm.node.foreachDesc'),
        },
      ],
    },
    {
      key: 'control',
      title: t('designer.palette.tbbpm.cat.control'),
      icon: <ControlOutlined />,
      color: 'var(--error-main)',
      nodes: [
        {
          type: 'continue',
          label: t('designer.palette.tbbpm.node.continue'),
          description: t('designer.palette.tbbpm.node.continueDesc'),
        },
        {
          type: 'break',
          label: t('designer.palette.tbbpm.node.break'),
          description: t('designer.palette.tbbpm.node.breakDesc'),
        },
      ],
    },
    {
      key: 'annotation',
      title: t('designer.palette.tbbpm.cat.annotation'),
      icon: <ContainerOutlined />,
      color: 'var(--gray-500)',
      nodes: [
        {
          type: 'note',
          label: t('designer.palette.tbbpm.node.note'),
          description: t('designer.palette.tbbpm.node.noteDesc'),
        },
      ],
    },
  ]
}

const NodePalette = memo(function NodePalette({ graph }: NodePaletteProps) {
  const { t } = useTranslation()
  const categories = useMemo(() => buildTbbpmCategories(t), [t])

  return (
    <BasePalette
      graph={graph}
      title={t('designer.palette.tbbpmTitle')}
      categories={categories}
      getNodeDragConfig={(type) => {
        const config = getNodeConfig(type)
        if (!config) return null
        return { shape: config.shape, width: config.width, height: config.height }
      }}
      getNodeColor={getNodeColor}
      getNodeData={(type, label) => ({ label, type })}
      defaultActiveKeys={['flow', 'task', 'gateway']}
      loggerName="NodePalette"
    />
  )
})

export default NodePalette
