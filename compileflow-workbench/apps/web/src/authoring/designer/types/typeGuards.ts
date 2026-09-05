import { isBpmnNodeType } from './bpmnNodeTypes'
import type { BaseNode, BpmnNode } from './flowDefinition'
import { isTbbpmNodeType, type TbbpmNode } from './tbbpm'

// ==================== 跨类型守卫 ====================

export function isTbbpmNode(node: BaseNode): node is TbbpmNode {
  return isTbbpmNodeType(node.type)
}

export function isBpmnNode(node: BaseNode): node is BpmnNode {
  return isBpmnNodeType(node.type)
}

// ==================== 节点类型守卫 ====================

function isStartNode(node: TbbpmNode): node is TbbpmNode {
  return node.type === 'start'
}

function isEndNode(node: TbbpmNode): node is TbbpmNode {
  return node.type === 'end'
}

// ==================== 复合类型判断 ====================

export function isNodeCopyable(node: TbbpmNode): boolean {
  return !isStartNode(node) && !isEndNode(node)
}
