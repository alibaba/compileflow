import React from 'react'
import { useTranslation } from 'react-i18next'

import { getNodePropertyConfig } from '../config/nodePropertyConfig'
import type { TbbpmNode } from '../types/tbbpm'
import { isTbbpmNode } from '../types/typeGuards'

import { BasePropertiesPanel } from './BasePropertiesPanel'

const TbbpmPropertiesPanel = React.memo(function TbbpmPropertiesPanel() {
  const { t } = useTranslation()
  return (
    <BasePropertiesPanel<TbbpmNode>
      title={t('designer.properties.panel.tbbpm')}
      getPropertyConfig={(node) => getNodePropertyConfig(node.type)}
      isNode={isTbbpmNode}
    />
  )
})

export default TbbpmPropertiesPanel
