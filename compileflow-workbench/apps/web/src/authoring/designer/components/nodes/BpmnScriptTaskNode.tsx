import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './bpmnNodes.css'

interface BpmnScriptTaskNodeProps {
  node: Node
}

export function BpmnScriptTaskNode({ node }: BpmnScriptTaskNodeProps) {
  const data = node.getData()
  const name = useNodeDisplayLabel('bpmn:ScriptTask', data.name || data.label)
  const scriptFormat = typeof data.scriptFormat === 'string' ? data.scriptFormat : undefined

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
        <g transform="translate(9, 9)" fill="none" stroke="#722ed1" strokeWidth="1.5">
          <path d="M2 1h12l4 4v15H2z" />
          <path d="M14 1v5h4M6 10h8M6 14h8M6 18h5" />
        </g>
      </svg>
      <div className="bpmn-node-label">
        <div className="bpmn-node-name">{name}</div>
        {scriptFormat && <div className="bpmn-node-hint">{scriptFormat}</div>}
      </div>
    </div>
  )
}
