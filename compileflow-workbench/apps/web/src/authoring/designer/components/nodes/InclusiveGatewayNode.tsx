import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface InclusiveGatewayNodeProps {
  node: Node
}

export function InclusiveGatewayNode({ node }: InclusiveGatewayNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:InclusiveGateway', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-gateway bpmn-inclusive-gateway">
      <svg width="50" height="50" viewBox="0 0 50 50">
        <path d="M 25,2 L 48,25 L 25,48 L 2,25 Z" fill="#fff" stroke="#52c41a" strokeWidth="2" />
        <circle cx="25" cy="25" r="10" fill="none" stroke="#52c41a" strokeWidth="3" />
      </svg>
      {name && <div className="bpmn-node-label">{name}</div>}
    </div>
  )
}
