import { Alert, Divider, Form } from 'antd'
import { useTranslation } from 'react-i18next'

import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

import { GatewayRoutingProperties } from './GatewayRoutingProperties'

export default function ExclusiveGatewayPropertiesTab({
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
          title={t('designer.props.node.exclusiveGateway.title')}
          description={t('designer.props.node.exclusiveGateway.desc')}
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
        />

        <GatewayRoutingProperties
          nodeId={node.id}
          defaultConnectionId={p.default as string | undefined}
          onDefaultConnectionChange={(value) => update('default', value)}
          conditionHelp={
            <>
              <Divider style={{ fontSize: 12 }}>
                {t('designer.props.section.edgeConditionNote')}
              </Divider>
              <div
                style={{
                  padding: 8,
                  background: 'var(--color-fill-secondary)',
                  border: '1px solid var(--color-border-light)',
                  borderRadius: 4,
                  fontSize: 12,
                  fontFamily: 'Monaco, Consolas, monospace',
                }}
              >
                <div style={{ color: 'var(--color-text-tertiary)', marginBottom: 6 }}>
                  {t('designer.props.node.edgeConditionHint')}
                </div>
                <div>amount &gt; 1000</div>
                <div>status == &quot;approved&quot;</div>
                <div>true</div>
              </div>
            </>
          }
        />
      </Form>
    </div>
  )
}
