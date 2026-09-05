import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface ScriptTaskNodeProps {
  node: Node
}

export function ScriptTaskNode({ node }: ScriptTaskNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ScriptTask', data.name || data.label)
  const scriptFormat = data.scriptFormat || 'qlexpress'

  return (
    <div className="bpmn-node bpmn-task bpmn-script-task">
      <svg width="100" height="80" viewBox="0 0 100 80">
        <rect
          x="1"
          y="1"
          width="98"
          height="78"
          rx="8"
          ry="8"
          fill="#fff"
          stroke="#722ed1"
          strokeWidth="2"
        />

        <g transform="translate(8, 8)">
          <path
            d="M 2,0 L 14,0 L 14,2 L 16,2 L 16,16 L 2,16 Z"
            fill="none"
            stroke="#722ed1"
            strokeWidth="1.5"
          />
          <path d="M 4,5 L 7,8 L 4,11" fill="none" stroke="#722ed1" strokeWidth="1" />
          <path d="M 10,5 L 13,8 L 10,11" fill="none" stroke="#722ed1" strokeWidth="1" />
        </g>
      </svg>

      <div className="bpmn-node-label">
        <div className="bpmn-node-name">{name}</div>
        <div className="bpmn-node-hint">{scriptFormat}</div>
      </div>
    </div>
  )
}
