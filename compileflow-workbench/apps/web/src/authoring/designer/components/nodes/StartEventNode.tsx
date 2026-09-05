import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface StartEventNodeProps {
  node: Node
}

export function StartEventNode({ node }: StartEventNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:StartEvent', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-event bpmn-start-event">
      <svg width="36" height="36" viewBox="0 0 36 36">
        {/* 外圆 - 单线 */}
        <circle cx="18" cy="18" r="16" fill="#fff" stroke="#52c41a" strokeWidth="2" />
      </svg>
      {/* 节点名称 */}
      {name && <div className="bpmn-node-label">{name}</div>}
    </div>
  )
}
