import { CaretRightOutlined } from '@ant-design/icons'
import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

interface StartNodeProps {
  node: Node
}

export function StartNode({ node }: StartNodeProps) {
  const data = node.getData()
  const label = useNodeDisplayLabel('start', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-start">
      <div className="node-icon" aria-hidden="true">
        <CaretRightOutlined />
      </div>
      <div className="node-label">{label}</div>
    </div>
  )
}
