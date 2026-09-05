import { SettingOutlined } from '@ant-design/icons'
import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

export function AutoTaskNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('autoTask', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-autotask">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <SettingOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}
