import { Alert } from 'antd'
import { useTranslation } from 'react-i18next'

import type { NodePropertyTabProps } from '../../types/propertyTabs'

import { PropertiesTabLayout } from './PropertiesTabLayout'

export function ParallelPropertiesTab(_: NodePropertyTabProps) {
  const { t } = useTranslation()

  return (
    <PropertiesTabLayout
      title={t('designer.props.node.parallel.title')}
      description={t('designer.props.node.parallel.desc')}
    >
      <Alert title={t('designer.props.node.parallel.noConfig')} type="info" showIcon />
    </PropertiesTabLayout>
  )
}
