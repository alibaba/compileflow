import { BuildOutlined } from '@ant-design/icons'
import { Alert, Button, Divider, Input, Space, Table } from 'antd'
import { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import {
  selectCurrentProcess,
  selectTbbpmConnections,
  updateConnection,
} from '../../store/editorSlice'
import type { NodePropertyTabProps } from '../../types/propertyTabs'
import type { TbbpmConnection } from '../../types/tbbpm'
import { ExpressionBuilder } from '../ExpressionBuilder'
import { toExpressionVariables } from '../expressionVariables'

import { PropertiesTabLayout } from './PropertiesTabLayout'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

function useProcessVariables() {
  const currentProcess = useAppSelector(selectCurrentProcess)
  return toExpressionVariables(currentProcess?.variables)
}

export function ExclusivePropertiesTab({ node }: NodePropertyTabProps) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const dispatch = useAppDispatch()
  const allConnections = useAppSelector(selectTbbpmConnections)
  const outgoingConnections = allConnections.filter((c) => c.sourceId === node.id)
  const variables = useProcessVariables()
  const [builderForConnection, setBuilderForConnection] = useState<TbbpmConnection | null>(null)

  const openBuilder = useCallback((conn: TbbpmConnection) => setBuilderForConnection(conn), [])
  const closeBuilder = useCallback(() => setBuilderForConnection(null), [])
  useEscapeToClose(builderForConnection !== null, closeBuilder)

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
          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={condition || ''}
              onChange={(e) =>
                dispatch(
                  updateConnection({ id: record.id, updates: { condition: e.target.value } })
                )
              }
              placeholder={labels.phCondition}
              aria-label={labels.colConditionExpr}
              style={{ fontFamily: 'Monaco, Consolas, monospace', fontSize: 12 }}
            />
            <Button
              icon={<BuildOutlined />}
              onClick={() => openBuilder(record)}
              title={labels.exprBuilderTooltip}
              aria-label={labels.exprBuilderTooltip}
            />
          </Space.Compact>
        ),
      },
    ],
    [dispatch, labels, openBuilder]
  )

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.exclusive.title')}
      description={t('designer.props.node.exclusive.desc')}
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
          description={labels.noOutgoingEdgesExclusiveDesc}
          type="warning"
          showIcon
        />
      )}

      <Modal
        title={`${labels.exprBuilderTooltip} — ${labels.colTargetNode}: ${builderForConnection?.targetId ?? ''}`}
        open={builderForConnection !== null}
        onCancel={closeBuilder}
        onOk={closeBuilder}
        width={760}
        okText={labels.done}
        cancelText={labels.close}
        destroyOnHidden
      >
        {builderForConnection && (
          <ExpressionBuilder
            value={builderForConnection.condition || ''}
            variables={variables}
            onChange={(value) => {
              dispatch(
                updateConnection({
                  id: builderForConnection.id,
                  updates: { condition: value },
                })
              )
              setBuilderForConnection({ ...builderForConnection, condition: value })
            }}
          />
        )}
      </Modal>
    </PropertiesTabLayout>
  )
}
