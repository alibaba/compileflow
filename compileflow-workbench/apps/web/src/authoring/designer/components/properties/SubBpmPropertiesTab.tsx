import { Alert, Form, Select } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { replaceContainerChildren, selectTbbpmNodes } from '../../store/editorSlice'
import { isNodeAncestor } from '../../types/nodeHierarchy'
import type { NodePropertyTabProps } from '../../types/propertyTabs'
import { TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES, type TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

export function SubBpmPropertiesTab({ node }: NodePropertyTabProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const nodes = useAppSelector(selectTbbpmNodes)
  const nodesById = useMemo(
    () => new Map(nodes.map((candidate) => [candidate.id, candidate])),
    [nodes]
  )
  const selectedIds = useMemo(
    () => nodes.filter((candidate) => candidate.parentId === node.id).map(({ id }) => id),
    [node.id, nodes]
  )
  const insideLoop = useMemo(() => hasEnclosingLoop(node, nodesById), [node, nodesById])
  const candidates = useMemo(
    () =>
      nodes.filter(
        (candidate) =>
          candidate.id !== node.id &&
          TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES.has(candidate.type) &&
          (!isLoopControl(candidate) || insideLoop || candidate.parentId === node.id) &&
          (candidate.parentId === node.parentId || candidate.parentId === node.id) &&
          !isNodeAncestor(candidate.id, node, nodesById)
      ),
    [insideLoop, node, nodes, nodesById]
  )

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.subBpm.title')}
      description={t('designer.props.node.subBpm.desc')}
    >
      <Alert
        type="info"
        showIcon
        title={t('designer.props.node.subBpm.boundaryHint')}
        style={{ marginBottom: 16 }}
      />
      <Form.Item
        label={t('designer.props.node.subBpm.children')}
        required
        help={t('designer.props.node.subBpm.childrenHelp')}
      >
        <Select
          mode="multiple"
          value={selectedIds}
          onChange={(childIds) =>
            dispatch(replaceContainerChildren({ parentId: node.id, childIds }))
          }
          options={candidates.map((candidate) => ({
            value: candidate.id,
            label: `${candidate.name || candidate.id} (${candidate.id})`,
          }))}
          optionFilterProp="label"
          showSearch
          aria-label={t('designer.props.node.subBpm.children')}
        />
      </Form.Item>
    </PropertiesTabLayout>
  )
}

function isLoopControl(node: TbbpmNode): boolean {
  return node.type === 'break' || node.type === 'continue'
}

function hasEnclosingLoop(node: TbbpmNode, nodesById: ReadonlyMap<string, TbbpmNode>): boolean {
  const visited = new Set<string>()
  let parentId = node.parentId
  while (parentId && !visited.has(parentId)) {
    visited.add(parentId)
    const parent = nodesById.get(parentId)
    if (!parent) return false
    if (parent.type === 'while' || parent.type === 'foreach') return true
    parentId = parent.parentId
  }
  return false
}
