import { lazy } from 'react'

import { BaseDesignerCore, type BaseDesignerProps, withDesignerWrapper } from './BaseDesigner'
import BpmnCanvas from './BpmnCanvas'
import BpmnNodePalette from './BpmnNodePalette'

import '../theme/design-tokens.css'
import './BpmnDesigner.css'

const BpmnPropertiesPanel = lazy(() => import('./BpmnPropertiesPanel'))

function BpmnDesignerInner(props: BaseDesignerProps) {
  return (
    <BaseDesignerCore
      {...props}
      layoutClassName="bpmn-designer"
      renderPalette={(graph) => <BpmnNodePalette graph={graph} />}
      renderCanvas={(onGraphReady) => <BpmnCanvas onGraphReady={onGraphReady} />}
      renderPropertiesPanel={() => <BpmnPropertiesPanel />}
    />
  )
}

export default withDesignerWrapper(BpmnDesignerInner)
