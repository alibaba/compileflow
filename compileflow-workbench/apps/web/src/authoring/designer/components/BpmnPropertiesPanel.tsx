import React from 'react'
import { useTranslation } from 'react-i18next'

import { getBpmnPropertyConfig } from '../config/bpmnPropertyConfig'
import type { BpmnNode } from '../types/flowDefinition'
import { isBpmnNode } from '../types/typeGuards'

import { BasePropertiesPanel } from './BasePropertiesPanel'

const BpmnPropertiesPanel = React.memo(function BpmnPropertiesPanel() {
  const { t } = useTranslation()
  return (
    <BasePropertiesPanel<BpmnNode>
      title={t('designer.properties.panel.bpmn')}
      getPropertyConfig={(node) => getBpmnPropertyConfig(node.type)}
      isNode={isBpmnNode}
    />
  )
})

export default BpmnPropertiesPanel
