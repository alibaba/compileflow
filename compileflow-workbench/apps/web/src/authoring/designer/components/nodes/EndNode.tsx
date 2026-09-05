import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

interface EndNodeProps {
  node: Node
}

export function EndNode({ node }: EndNodeProps) {
  const data = node.getData()
  const label = useNodeDisplayLabel('end', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-end">
      <div className="node-icon">■</div>
      <div className="node-label">{label}</div>
    </div>
  )
}
