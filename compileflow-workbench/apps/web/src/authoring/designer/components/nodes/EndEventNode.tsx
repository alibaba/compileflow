import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface EndEventNodeProps {
  node: Node
}

export function EndEventNode({ node }: EndEventNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:EndEvent', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-event bpmn-end-event">
      <svg width="36" height="36" viewBox="0 0 36 36">
        {/* 外圆 - 粗线 */}
        <circle cx="18" cy="18" r="16" fill="#fff" stroke="#ff4d4f" strokeWidth="4" />
      </svg>
      {/* 节点名称 */}
      {name && <div className="bpmn-node-label">{name}</div>}
    </div>
  )
}
