import { Alert, Divider, Form, Select } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { replaceContainerChildren, selectBpmnNodes } from '../../store/editorSlice'
import { isNodeAncestor } from '../../types/nodeHierarchy'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { BpmnLoopCharacteristicsFields } from './BpmnLoopCharacteristicsFields'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

export default function SubProcessPropertiesTab({ node, onUpdate }: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const nodes = useAppSelector(selectBpmnNodes)
  const properties = node.properties
  const nodesById = useMemo(
    () => new Map(nodes.map((candidate) => [candidate.id, candidate])),
    [nodes]
  )
  const selectedIds = useMemo(
    () =>
      nodes.filter((candidate) => candidate.parentId === node.id).map((candidate) => candidate.id),
    [node.id, nodes]
  )
  const candidates = useMemo(
    () =>
      nodes.filter(
        (candidate) =>
          candidate.id !== node.id &&
          (candidate.parentId === node.parentId || candidate.parentId === node.id) &&
          !isNodeAncestor(candidate.id, node, nodesById)
      ),
    [node, nodes, nodesById]
  )

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...properties, [field]: value })
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Alert
          type="info"
          showIcon
          title={t('designer.props.subProcess.boundaryHint')}
          style={{ marginBottom: 16 }}
        />
        <Divider style={{ fontSize: 12 }}>{t('designer.props.subProcess.body')}</Divider>
        <Form.Item
          label={t('designer.props.subProcess.children')}
          required
          help={t('designer.props.subProcess.childrenHelp')}
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
            aria-label={t('designer.props.subProcess.children')}
          />
        </Form.Item>
        <BpmnLoopCharacteristicsFields
          value={properties.loopCharacteristics}
          onChange={(loopCharacteristics) => update('loopCharacteristics', loopCharacteristics)}
        />
      </Form>
    </div>
  )
}
