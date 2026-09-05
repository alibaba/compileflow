import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface ExclusiveGatewayNodeProps {
  node: Node
}

export function ExclusiveGatewayNode({ node }: ExclusiveGatewayNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ExclusiveGateway', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-gateway bpmn-exclusive-gateway">
      <svg width="50" height="50" viewBox="0 0 50 50">
        <path d="M 25,2 L 48,25 L 25,48 L 2,25 Z" fill="#fff" stroke="#faad14" strokeWidth="2" />
        <path
          d="M 15,15 L 35,35 M 35,15 L 15,35"
          stroke="#faad14"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </svg>
      {name && <div className="bpmn-node-label">{name}</div>}
    </div>
  )
}
