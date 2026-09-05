import { Alert, Form, Select } from 'antd'
import type { ReactNode } from 'react'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import { selectBpmnConnections, selectBpmnNodes } from '../../store/editorSlice'

import { useAppSelector } from '@/app/hooks'

interface GatewayRoutingPropertiesProps {
  nodeId: string
  defaultConnectionId: string | undefined
  onDefaultConnectionChange: (value: string | undefined) => void
  conditionHelp?: ReactNode
}

export function GatewayRoutingProperties({
  nodeId,
  defaultConnectionId,
  onDefaultConnectionChange,
  conditionHelp,
}: GatewayRoutingPropertiesProps) {
  const labels = usePropertyLabels()
  const connections = useAppSelector(selectBpmnConnections)
  const nodes = useAppSelector(selectBpmnNodes)
  const incoming = connections.filter((connection) => connection.targetId === nodeId)
  const outgoing = connections.filter((connection) => connection.sourceId === nodeId)
  const isJoin = incoming.length > 1 && outgoing.length === 1
  const nodesById = new Map(nodes.map((node) => [node.id, node]))
  const options = outgoing.map((connection) => {
    const target = nodesById.get(connection.targetId)
    const flowLabel = connection.name?.trim() || connection.id
    const targetLabel = target?.name?.trim() || target?.id || connection.targetId
    return {
      value: connection.id,
      label: `${flowLabel} -> ${targetLabel}`,
    }
  })

  if (isJoin) {
    return <Alert title={labels.gatewayJoinNoConfig} type="info" showIcon />
  }

  return (
    <>
      <Form.Item label={labels.defaultEdgeId} help={labels.defaultEdgeIdHelp}>
        <Select
          allowClear
          showSearch
          optionFilterProp="label"
          value={defaultConnectionId || undefined}
          options={options}
          onChange={onDefaultConnectionChange}
          placeholder={labels.phDefaultProcess}
          notFoundContent={labels.noOutgoingEdges}
        />
      </Form.Item>
      {conditionHelp}
    </>
  )
}
