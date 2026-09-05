import type { BaseNode, UnifiedProcessDefinition } from '../types/flowDefinition'

import { collectReachableNodes, findDirectedCycles } from './directedGraph'

export enum ValidationLevel {
  ERROR = 'error', // 错误 - 必须修复
  WARNING = 'warning', // 警告 - 建议修复
  INFO = 'info', // 信息 - 提示优化
}

type ValidationIssueType =
  | 'cycle'
  | 'isolated'
  | 'deadlock'
  | 'unreachable'
  | 'multi-start'
  | 'multi-end'
  | 'no-end'
  | 'orphan-edge'
  | 'property'

interface ValidationIssueBase {
  /** 问题级别 */
  level: ValidationLevel

  /** Interpolation values for i18n message templates. */
  params?: Record<string, string | number>

  /** 涉及的节点ID列表 */
  nodeIds: string[]

  /** 涉及的连接线ID列表 */
  connectionIds?: string[]
}

export type ValidationIssue =
  | (ValidationIssueBase & { type: 'property'; code: string })
  | (ValidationIssueBase & {
      type: Exclude<ValidationIssueType, 'property'>
      code?: never
    })

export interface TopologyValidationResult {
  /** 是否通过验证 */
  valid: boolean

  /** 问题列表 */
  issues: ValidationIssue[]

  /** 错误数量 */
  errorCount: number

  /** 警告数量 */
  warningCount: number

  /** 信息数量 */
  infoCount: number
}

interface GraphNode {
  id: string
  inDegree: number
  outDegree: number
  neighbors: string[]
  reverseNeighbors: string[]
}

class ProcessTopologyValidator {
  private flowDef: UnifiedProcessDefinition
  private nodeMap: Map<string, BaseNode>
  private graph: Map<string, GraphNode>
  private issues: ValidationIssue[]

  constructor(flowDef: UnifiedProcessDefinition) {
    this.flowDef = flowDef
    this.nodeMap = new Map()
    this.graph = new Map()
    this.issues = []
    this.buildGraph()
  }

  validate(): TopologyValidationResult {
    this.issues = []

    this.detectMultipleStartPoints()
    this.detectNoEndPoint()
    this.detectCycles()
    this.detectIsolatedNodes()
    this.detectUnreachableNodes()
    this.detectOrphanEdges()
    this.detectDeadlocks()

    const errorCount = this.issues.filter((i) => i.level === ValidationLevel.ERROR).length
    const warningCount = this.issues.filter((i) => i.level === ValidationLevel.WARNING).length
    const infoCount = this.issues.filter((i) => i.level === ValidationLevel.INFO).length

    return {
      valid: errorCount === 0,
      issues: this.issues,
      errorCount,
      warningCount,
      infoCount,
    }
  }

  private buildGraph() {
    // 构建节点映射
    this.flowDef.nodes.forEach((node) => {
      if (node.type === 'note') return
      this.nodeMap.set(node.id, node)
      this.graph.set(node.id, {
        id: node.id,
        inDegree: 0,
        outDegree: 0,
        neighbors: [],
        reverseNeighbors: [],
      })
    })

    // 构建邻接表
    this.flowDef.connections.forEach((conn) => {
      const sourceNode = this.graph.get(conn.sourceId)
      const targetNode = this.graph.get(conn.targetId)

      if (sourceNode && targetNode) {
        sourceNode.neighbors.push(conn.targetId)
        sourceNode.outDegree++
        targetNode.reverseNeighbors.push(conn.sourceId)
        targetNode.inDegree++
      }
    })
  }

  private detectMultipleStartPoints() {
    const startNodes = this.startNodes()

    if (startNodes.length === 0) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'multi-start',
        nodeIds: [],
      })
    } else if (startNodes.length > 1) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'multi-start',
        nodeIds: startNodes.map((n) => n.id),
      })
    }
  }

  private detectNoEndPoint() {
    const endNodes = this.endNodes()

    if (endNodes.length === 0) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'no-end',
        nodeIds: [],
      })
    } else if (endNodes.length > 1) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'multi-end',
        params: { count: endNodes.length },
        nodeIds: endNodes.map((node) => node.id),
      })
    }
  }

  private detectCycles() {
    const cycles = findDirectedCycles(
      this.graph.keys(),
      (nodeId) => this.graph.get(nodeId)?.neighbors ?? []
    )
    cycles.forEach((cycle, index) => {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'cycle',
        params: {
          index: index + 1,
          path: cycle.map((id) => this.getNodeName(id)).join(' → '),
        },
        nodeIds: cycle,
      })
    })
  }

  private detectIsolatedNodes() {
    const isolatedNodes: string[] = []

    this.graph.forEach((graphNode, nodeId) => {
      if (graphNode.inDegree === 0 && graphNode.outDegree === 0) {
        const node = this.nodeMap.get(nodeId)
        // Start和End节点允许单向连接
        if (
          node &&
          node.type !== 'start' &&
          node.type !== 'bpmn:StartEvent' &&
          node.type !== 'end' &&
          node.type !== 'bpmn:EndEvent'
        ) {
          isolatedNodes.push(nodeId)
        }
      }
    })

    if (isolatedNodes.length > 0) {
      this.issues.push({
        level: ValidationLevel.WARNING,
        type: 'isolated',
        params: { count: isolatedNodes.length },
        nodeIds: isolatedNodes,
      })
    }
  }

  private detectUnreachableNodes() {
    const startNodes = this.startNodes()

    if (startNodes.length === 0) return // 没有起点，跳过

    const reachable = collectReachableNodes(
      startNodes.map((node) => node.id),
      (nodeId) => this.graph.get(nodeId)?.neighbors ?? []
    )

    // 找出不可达节点
    const unreachableNodes: string[] = []
    this.graph.forEach((_, nodeId) => {
      if (!reachable.has(nodeId)) {
        unreachableNodes.push(nodeId)
      }
    })

    if (unreachableNodes.length > 0) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'unreachable',
        params: { count: unreachableNodes.length },
        nodeIds: unreachableNodes,
      })
    }
  }

  private detectOrphanEdges() {
    const orphanEdges: string[] = []

    this.flowDef.connections.forEach((conn) => {
      if (!this.nodeMap.has(conn.sourceId) || !this.nodeMap.has(conn.targetId)) {
        orphanEdges.push(conn.id)
      }
    })

    if (orphanEdges.length > 0) {
      this.issues.push({
        level: ValidationLevel.ERROR,
        type: 'orphan-edge',
        params: { count: orphanEdges.length },
        nodeIds: [],
        connectionIds: orphanEdges,
      })
    }
  }

  private detectDeadlocks() {
    const concurrentGateways = this.flowDef.nodes.filter(
      (node) =>
        node.type === 'parallel' ||
        node.type === 'inclusive' ||
        node.type === 'bpmn:ParallelGateway' ||
        node.type === 'bpmn:InclusiveGateway'
    )

    concurrentGateways.forEach((gateway) => {
      const graphNode = this.graph.get(gateway.id)
      if (!graphNode) return
      const incomingCount = graphNode.inDegree

      // Fork Gateway（分支）: outDegree > 1, inDegree = 1
      const isFork = graphNode.outDegree > 1 && incomingCount === 1

      // Join Gateway（合并）: inDegree > 1, outDegree = 1
      const isJoin = incomingCount > 1 && graphNode.outDegree === 1

      if (!isFork && !isJoin) {
        this.issues.push({
          level: ValidationLevel.ERROR,
          type: 'deadlock',
          params: {
            name: this.getNodeName(gateway.id),
            inDegree: incomingCount,
            outDegree: graphNode.outDegree,
          },
          nodeIds: [gateway.id],
        })
      }

      if (isFork) {
        const hasConvergence = this.findMatchingConvergence(gateway.id, gateway.type)
        if (!hasConvergence) {
          this.issues.push({
            level: ValidationLevel.ERROR,
            type: 'deadlock',
            params: {
              variant: 'missingJoin',
              name: this.getNodeName(gateway.id),
            },
            nodeIds: [gateway.id],
          })
        }
      }
    })
  }

  private findMatchingConvergence(forkGatewayId: string, gatewayType: string): boolean {
    const graphNode = this.graph.get(forkGatewayId)
    if (!graphNode) return false

    const branches = graphNode.neighbors
    if (branches.length < 2) return false

    const reachableByBranch = branches.map((branchStart) =>
      collectReachableNodes([branchStart], (nodeId) => this.graph.get(nodeId)?.neighbors ?? [])
    )
    const [firstBranch, ...remainingBranches] = reachableByBranch

    for (const nodeId of firstBranch) {
      if (!remainingBranches.every((reachable) => reachable.has(nodeId))) continue
      if (this.isMatchingConvergence(nodeId, gatewayType)) return true
    }

    return false
  }

  private isMatchingConvergence(nodeId: string, gatewayType: string): boolean {
    const node = this.nodeMap.get(nodeId)
    if (node?.type === 'end' || node?.type === 'bpmn:EndEvent') return true
    if (node?.type !== gatewayType) return false
    const graphNode = this.graph.get(nodeId)
    return Boolean(graphNode && graphNode.inDegree > 1 && graphNode.outDegree === 1)
  }

  private getNodeName(nodeId: string): string {
    const node = this.nodeMap.get(nodeId)
    return node?.name || nodeId
  }

  private startNodes(): BaseNode[] {
    return this.flowDef.nodes.filter(
      (node) => node.type === 'start' || node.type === 'bpmn:StartEvent'
    )
  }

  private endNodes(): BaseNode[] {
    return this.flowDef.nodes.filter((node) => node.type === 'end' || node.type === 'bpmn:EndEvent')
  }
}

export function validateProcessTopology(
  flowDef: UnifiedProcessDefinition
): TopologyValidationResult {
  const containers = [flowContainer(flowDef, undefined)]
  flowDef.nodes
    .filter(
      (node) =>
        node.type === 'bpmn:SubProcess' ||
        node.type === 'subBpm' ||
        node.type === 'while' ||
        node.type === 'foreach'
    )
    .forEach((container) => {
      containers.push(flowContainer(flowDef, container.id))
    })
  const issues = containers.flatMap(
    (definition) => new ProcessTopologyValidator(definition).validate().issues
  )
  const errorCount = issues.filter((issue) => issue.level === ValidationLevel.ERROR).length
  const warningCount = issues.filter((issue) => issue.level === ValidationLevel.WARNING).length
  const infoCount = issues.filter((issue) => issue.level === ValidationLevel.INFO).length
  return {
    valid: errorCount === 0,
    issues,
    errorCount,
    warningCount,
    infoCount,
  }
}

function flowContainer(
  flowDef: UnifiedProcessDefinition,
  parentId: string | undefined
): UnifiedProcessDefinition {
  if (flowDef.type === 'BPMN') {
    const nodes = flowDef.nodes.filter((node) => node.parentId === parentId)
    const nodeIds = new Set(nodes.map((node) => node.id))
    return {
      ...flowDef,
      nodes,
      connections: flowDef.connections.filter(
        (connection) => nodeIds.has(connection.sourceId) && nodeIds.has(connection.targetId)
      ),
    }
  }

  const nodes = flowDef.nodes.filter((node) => node.parentId === parentId)
  const nodeIds = new Set(nodes.map((node) => node.id))
  return {
    ...flowDef,
    nodes,
    connections: flowDef.connections.filter(
      (connection) => nodeIds.has(connection.sourceId) && nodeIds.has(connection.targetId)
    ),
  }
}
