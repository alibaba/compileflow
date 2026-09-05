import { Alert, Form } from 'antd'
import { useTranslation } from 'react-i18next'

import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { GatewayRoutingProperties } from './GatewayRoutingProperties'

export default function InclusiveGatewayPropertiesTab({
  node,
  onUpdate,
}: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const p = node.properties

  const update = (field: string, value: unknown) => {
    onUpdate(node.id, { ...p, [field]: value })
  }

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Form layout="vertical" size="small">
        <Alert
          title={t('designer.props.node.inclusiveGateway.title')}
          description={t('designer.props.node.inclusiveGateway.desc')}
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <GatewayRoutingProperties
          nodeId={node.id}
          defaultConnectionId={p.default as string | undefined}
          onDefaultConnectionChange={(value) => update('default', value)}
        />
      </Form>
    </div>
  )
}
