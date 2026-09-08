import { Alert, Form } from 'antd'
import { useTranslation } from 'react-i18next'

import { setBpmnDefaultConnection } from '../../store/editorSlice'
import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { GatewayRoutingProperties } from './GatewayRoutingProperties'

import { useAppDispatch } from '@/app/hooks'

export default function InclusiveGatewayPropertiesTab({ node }: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const p = node.properties

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
          onDefaultConnectionChange={(connectionId) =>
            dispatch(setBpmnDefaultConnection({ nodeId: node.id, connectionId }))
          }
        />
      </Form>
    </div>
  )
}
