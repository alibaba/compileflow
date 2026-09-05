import { Alert, Divider, Input, Table } from 'antd'
import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import { selectTbbpmConnections, updateConnection } from '../../store/editorSlice'
import type { NodePropertyTabProps } from '../../types/propertyTabs'
import type { TbbpmConnection } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

export function InclusivePropertiesTab({ node }: NodePropertyTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const dispatch = useAppDispatch()
  const allConnections = useAppSelector(selectTbbpmConnections)
  const outgoingConnections = allConnections.filter((c) => c.sourceId === node.id)

  const columns = useMemo(
    () => [
      {
        title: labels.colSeq,
        key: 'index',
        width: 55,
        render: (_: unknown, __: unknown, index: number) => index + 1,
      },
      {
        title: labels.colTargetNode,
        dataIndex: 'targetId',
        key: 'targetId',
        width: 110,
      },
      {
        title: labels.colConditionExpr,
        dataIndex: 'condition',
        key: 'condition',
        render: (condition: string, record: TbbpmConnection) => (
          <Input
            aria-label={`${labels.colConditionExpr}: ${record.id}`}
            value={condition || ''}
            onChange={(e) =>
              dispatch(updateConnection({ id: record.id, updates: { condition: e.target.value } }))
            }
            placeholder={labels.phCondition}
            style={{ fontFamily: 'Monaco, Consolas, monospace', fontSize: 12 }}
          />
        ),
      },
    ],
    [dispatch, labels]
  )

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.inclusive.title')}
      description={t('designer.props.node.inclusive.desc')}
    >
      <Divider style={{ margin: '0 0 16px 0' }}>{labels.sectionOutgoingConditions}</Divider>

      {outgoingConnections.length > 0 ? (
        <Table
          dataSource={outgoingConnections}
          columns={columns}
          pagination={false}
          rowKey="id"
          size="small"
          bordered
        />
      ) : (
        <Alert
          title={labels.noOutgoingEdges}
          description={labels.noOutgoingEdgesGatewayDesc}
          type="warning"
          showIcon
        />
      )}
    </PropertiesTabLayout>
  )
}
