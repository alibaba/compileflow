import { HourglassOutlined } from '@ant-design/icons'
import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

export function WaitTaskNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('waitTask', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-waittask">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <HourglassOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}
