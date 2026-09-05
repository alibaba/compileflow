import { Alert } from 'antd'
import { useTranslation } from 'react-i18next'

import type { BpmnNodePropertyTabProps } from '../../types/propertyTabs'

export default function ParallelGatewayPropertiesTab(_: BpmnNodePropertyTabProps) {
  const { t } = useTranslation()

  return (
    <div style={{ padding: '16px 16px 24px' }}>
      <Alert
        title={t('designer.props.node.parallelGateway.title')}
        description={t('designer.props.node.parallelGateway.desc')}
        type="info"
        showIcon
      />
    </div>
  )
}
