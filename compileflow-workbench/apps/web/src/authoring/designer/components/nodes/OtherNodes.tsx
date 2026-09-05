import {
  ArrowRightOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  FormOutlined,
  RadarChartOutlined,
  SyncOutlined,
} from '@ant-design/icons'
import { Node } from '@antv/x6'

import { useNodeDisplayLabel } from '../../hooks/useNodeDisplayLabel'

import './nodes.css'

export function WaitEventTaskNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('waitEventTask', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-waiteventtask">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <RadarChartOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function TimerTaskNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('timerTask', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-waittask">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <ClockCircleOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function ParallelNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('parallel', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-parallel">
      <div className="node-diamond">
        <div className="node-icon">+</div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function InclusiveNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('inclusive', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-inclusive">
      <div className="node-diamond">
        <div className="node-icon">○</div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function BpmCallNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('bpmCall', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-subbpm">
      <div className="node-header">
        <div className="node-icon">◇</div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function SubBpmNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('subBpm', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-subbpm">
      <div className="node-header">
        <div className="node-icon">⊞</div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function WhileNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('while', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-while">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          <SyncOutlined />
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function ForEachNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('foreach', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-foreach">
      <div className="node-header">
        <div className="node-icon" aria-hidden="true">
          ∀
        </div>
        <div className="node-label">{label}</div>
      </div>
    </div>
  )
}

export function ContinueNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('continue', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-continue">
      <div className="node-icon" aria-hidden="true">
        <ArrowRightOutlined />
      </div>
      <div className="node-label">{label}</div>
    </div>
  )
}

export function BreakNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('break', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-break">
      <div className="node-icon" aria-hidden="true">
        <CloseOutlined />
      </div>
      <div className="node-label">{label}</div>
    </div>
  )
}

export function NoteNode({ node }: { node: Node }) {
  const data = node.getData()
  const label = useNodeDisplayLabel('note', data.label)
  return (
    <div className="tbbpm-node tbbpm-node-note">
      <div className="node-icon" aria-hidden="true">
        <FormOutlined />
      </div>
      <div className="node-label">{label}</div>
      {data.comment && <div className="node-comment">{data.comment}</div>}
    </div>
  )
}
