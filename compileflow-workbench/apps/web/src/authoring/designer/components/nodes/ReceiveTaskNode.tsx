import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface ReceiveTaskNodeProps {
  node: Node
}

export function ReceiveTaskNode({ node }: ReceiveTaskNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ReceiveTask', data.name || data.label)
  const messageRef = data.messageRef

  return (
    <div className="bpmn-node bpmn-task bpmn-receive-task">
      <svg width="100" height="80" viewBox="0 0 100 80">
        <rect
          x="1"
          y="1"
          width="98"
          height="78"
          rx="8"
          ry="8"
          fill="#fff"
          stroke="#13c2c2"
          strokeWidth="2"
        />

        <g transform="translate(8, 8)">
          <rect
            x="2"
            y="4"
            width="14"
            height="10"
            rx="1"
            fill="none"
            stroke="#13c2c2"
            strokeWidth="1.5"
          />
          <path d="M 2,4 L 9,9 L 16,4" fill="none" stroke="#13c2c2" strokeWidth="1.5" />
        </g>
      </svg>

      <div className="bpmn-node-label">
        <div className="bpmn-node-name">{name}</div>
        {messageRef && <div className="bpmn-node-hint">msg: {messageRef}</div>}
      </div>
    </div>
  )
}
