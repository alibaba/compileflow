import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

export function ExclusiveNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('exclusive', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-exclusive">
      <div className="node-diamond">
        <div className="node-icon">?</div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}
