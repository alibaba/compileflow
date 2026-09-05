import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface CallActivityNodeProps {
  node: Node
}

export function CallActivityNode({ node }: CallActivityNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:CallActivity', data.name || data.label)
  const calledElement = data.calledElement

  return (
    <div className="bpmn-node bpmn-call-activity">
      <svg width="140" height="100" viewBox="0 0 140 100">
        <rect
          x="1"
          y="1"
          width="138"
          height="98"
          rx="8"
          ry="8"
          fill="#fff"
          stroke="#722ed1"
          strokeWidth="4"
        />
      </svg>

      <div className="bpmn-node-label">
        <div className="bpmn-node-name">{name}</div>
        {calledElement && <div className="bpmn-node-hint">→ {calledElement}</div>}
      </div>
    </div>
  )
}
