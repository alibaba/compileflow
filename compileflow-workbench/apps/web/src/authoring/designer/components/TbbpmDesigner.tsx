import { lazy } from 'react'

import { BaseDesignerCore, type BaseDesignerProps, withDesignerWrapper } from './BaseDesigner'
import NodePalette from './NodePalette'
import TbbpmCanvas from './TbbpmCanvas'

import '../theme/design-tokens.css'
import './TbbpmDesigner.css'

const TbbpmPropertiesPanel = lazy(() => import('./TbbpmPropertiesPanel'))

function TbbpmDesignerInner(props: BaseDesignerProps) {
  return (
    <BaseDesignerCore
      {...props}
      layoutClassName="tbbpm-designer"
      renderPalette={(graph) => <NodePalette graph={graph} />}
      renderCanvas={(onGraphReady) => <TbbpmCanvas onGraphReady={onGraphReady} />}
      renderPropertiesPanel={() => <TbbpmPropertiesPanel />}
    />
  )
}

export default withDesignerWrapper(TbbpmDesignerInner)
