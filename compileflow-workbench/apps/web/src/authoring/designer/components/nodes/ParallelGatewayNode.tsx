import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface ParallelGatewayNodeProps {
  node: Node
}

export function ParallelGatewayNode({ node }: ParallelGatewayNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ParallelGateway', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-gateway bpmn-parallel-gateway">
      <svg width="50" height="50" viewBox="0 0 50 50">
        <path d="M 25,2 L 48,25 L 25,48 L 2,25 Z" fill="#fff" stroke="#1890ff" strokeWidth="2" />
        <path
          d="M 25,12 L 25,38 M 12,25 L 38,25"
          stroke="#1890ff"
          strokeWidth="3"
          strokeLinecap="round"
        />
      </svg>
      {name && <div className="bpmn-node-label">{name}</div>}
    </div>
  )
}
