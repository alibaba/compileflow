import { CodeOutlined } from '@ant-design/icons'
import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

interface ScriptTaskNodeProps {
  node: Node
}

export function ScriptTaskNode({ node }: ScriptTaskNodeProps) {
  const data = node.getData()
  const label = useNodeDisplayLabel('scriptTask', data.label)

  return (
    <div className="tbbpm-node tbbpm-node-scripttask">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <CodeOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}
