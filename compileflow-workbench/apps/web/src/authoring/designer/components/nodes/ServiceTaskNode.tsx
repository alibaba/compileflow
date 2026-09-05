import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'
import type { ActionDefinition } from '../../types/action'

import './bpmnNodes.css'

interface ServiceTaskNodeProps {
  node: Node
}

export function ServiceTaskNode({ node }: ServiceTaskNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ServiceTask', data.name || data.label)
  const actionType = (data.action as ActionDefinition | undefined)?.actionType

  return (
    <div className="bpmn-node bpmn-task bpmn-service-task">
      <svg width="100" height="80" viewBox="0 0 100 80">
        {/* 圆角矩形 */}
        <rect
          x="1"
          y="1"
          width="98"
          height="78"
          rx="8"
          ry="8"
          fill="#fff"
          stroke="#1890ff"
          strokeWidth="2"
        />

        {/* 齿轮图标 (左上角) */}
        <g transform="translate(8, 8)">
          <path
            d="M 8,2 L 10,2 L 10,0 L 14,0 L 14,2 L 16,2 L 16,4 L 18,4 L 18,8 L 16,8 L 16,10 L 14,10 L 14,12 L 10,12 L 10,10 L 8,10 L 8,8 L 6,8 L 6,4 L 8,4 Z M 12,5 A 3,3 0 1,1 12,11 A 3,3 0 1,1 12,5 Z"
            fill="none"
            stroke="#1890ff"
            strokeWidth="1"
          />
          <circle cx="12" cy="8" r="2" fill="none" stroke="#1890ff" strokeWidth="1" />
        </g>
      </svg>

      {/* 节点名称 */}
      <div className="bpmn-node-label">
        <div className="bpmn-node-name">{name}</div>
        {actionType && <div className="bpmn-node-hint">{actionType}</div>}
      </div>
    </div>
  )
}
