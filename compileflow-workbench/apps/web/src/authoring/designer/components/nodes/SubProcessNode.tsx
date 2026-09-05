import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface SubProcessNodeProps {
  node: Node
}

export function SubProcessNode({ node }: SubProcessNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:SubProcess', data.name || data.label)

  return (
    <div className="bpmn-node bpmn-sub-process">
      <svg width="100%" height="100%" viewBox="0 0 320 220" preserveAspectRatio="none">
        <rect
          x="1"
          y="1"
          width="318"
          height="218"
          rx="8"
          ry="8"
          fill="rgba(255, 255, 255, 0.86)"
          stroke="#1677ff"
          strokeWidth="2"
        />
        <rect x="145" y="199" width="30" height="14" fill="#fff" stroke="#1677ff" />
        <path d="M151 206h18M160 201v10" stroke="#1677ff" strokeWidth="2" />
      </svg>
      <div className="bpmn-sub-process-label">{name}</div>
    </div>
  )
}
