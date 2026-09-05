import type { VariableMapping } from '../types/action'
import { findInapplicableTbbpmNodeProperties } from '../types/nodePropertyContracts'
import { getTbbpmChildNodeTypes, type TbbpmConnection, type TbbpmNode } from '../types/tbbpm'

import { findDirectJavaMutation } from './javaConditionExpression'
import { mappingFindings } from './mappingValidation'
import type { ValidationContext } from './NodeValidationStrategy'
import { TbbpmValidatorFactory } from './ValidatorFactory'

export interface ValidationError {
  id: string
  elementId: string
  code: string
  params?: Record<string, string | number>
  severity: 'error' | 'warning' | 'info'
  category: 'structure' | 'property' | 'compileflow'
}

export class TbbpmValidator {
  private nodes: TbbpmNode[]
  private connections: TbbpmConnection[]
  private errors: ValidationError[] = []
  private processVariableNames: ReadonlySet<string>
  private processVariables: ValidationContext['processVariables']
  private nodeById: ReadonlyMap<string, TbbpmNode>
  // Strategy registry — created once per validator instance.
  private factory = new TbbpmValidatorFactory()

  constructor(
    nodes: TbbpmNode[],
    connections: TbbpmConnection[],
    variables: Array<{
      name: string
      type?: string
      dataType?: string
      inOutType?: 'param' | 'return' | 'inner'
    }> = []
  ) {
    this.nodes = nodes
    this.connections = connections
    this.processVariableNames = new Set(variables.map((variable) => variable.name))
    this.processVariables = new Map(variables.map((variable) => [variable.name, variable]))
    this.nodeById = new Map(nodes.map((node) => [node.id, node]))
  }

  /** Format-specific rules only; shared topology checks run separately. */
  validateNodeRules(): ValidationError[] {
    this.errors = []
    this.validateHierarchy()
    this.nodes.forEach((node) => this.validateNode(node))
    this.connections.forEach((conn) => this.validateConnection(conn))
    return this.errors
  }

  private validateHierarchy() {
    const nodesById = new Map<string, TbbpmNode>()
    const duplicates = new Set<string>()
    this.nodes.forEach((node) => {
      if (nodesById.has(node.id)) duplicates.add(node.id)
      else nodesById.set(node.id, node)
    })
    duplicates.forEach((nodeId) => {
      this.addError(nodeId, 'process.duplicateNodeId', 'error', 'structure', { nodeId })
    })

    this.nodes.forEach((node) => {
      if (!node.parentId) {
        if (node.type === 'continue' || node.type === 'break') {
          this.addError(node.id, 'loop.rootOnlyChildType', 'error', 'structure')
        }
        return
      }

      const parent = nodesById.get(node.parentId)
      if (!parent) {
        this.addError(node.id, 'container.unknownParent', 'error', 'structure', {
          parentId: node.parentId,
        })
        return
      }
      const allowedChildren = getTbbpmChildNodeTypes(parent.type)
      if (!allowedChildren) {
        this.addError(node.id, 'container.invalidParent', 'error', 'structure', {
          parentId: node.parentId,
        })
        return
      }
      if (!allowedChildren.has(node.type)) {
        this.addError(node.id, 'container.invalidChildType', 'error', 'structure', {
          nodeType: node.type,
        })
      }
      if (
        (node.type === 'continue' || node.type === 'break') &&
        !hasEnclosingLoop(node, nodesById)
      ) {
        this.addError(node.id, 'loop.rootOnlyChildType', 'error', 'structure')
      }

      const visited = new Set([node.id])
      let ancestorId: string | undefined = node.parentId
      while (ancestorId) {
        if (visited.has(ancestorId)) {
          this.addError(node.id, 'container.parentCycle', 'error', 'structure')
          break
        }
        visited.add(ancestorId)
        ancestorId = nodesById.get(ancestorId)?.parentId
      }
    })
  }

  /** Delegates to the matching NodeValidationStrategy (complexity: 3). */
  private validateNode(node: TbbpmNode) {
    findInapplicableTbbpmNodeProperties(node).forEach((property) => {
      this.addError(node.id, 'node.inapplicableProperty', 'error', 'property', {
        nodeType: node.type,
        property,
      })
    })
    const context: ValidationContext = {
      nodes: this.nodes,
      connections: this.connections,
      processVariableNames: this.processVariableNames,
      processVariables: this.processVariables,
    }
    const strategy = this.factory.getStrategy(node.type)
    const issues = strategy.validate(node, context)
    issues.forEach((issue) => {
      this.addError(issue.elementId, issue.code, issue.severity, issue.category, issue.params)
    })
    this.validateNodeMappings(node)
    this.validateExplicitBranching(node)
  }

  private validateExplicitBranching(node: TbbpmNode): void {
    if (
      node.type === 'exclusive' ||
      node.type === 'parallel' ||
      node.type === 'inclusive' ||
      node.type === 'end'
    ) {
      return
    }
    const outgoing = this.connections.filter((connection) => connection.sourceId === node.id)
    if (outgoing.length > 1) {
      this.addError(node.id, 'node.requiresExplicitGateway', 'error', 'structure')
    }
    if (outgoing.some((connection) => Boolean(connection.condition?.trim()))) {
      this.addError(node.id, 'node.conditionRequiresGateway', 'error', 'property')
    }
  }

  private validateNodeMappings(node: TbbpmNode): void {
    const properties = node.properties
    if (node.type === 'autoTask' || node.type === 'scriptTask') {
      this.validateMappings(node.id, properties.action?.mappings, false, true, true)
    }
    if (node.type === 'bpmCall') {
      this.validateMappings(node.id, properties.callMappings, true, false, false)
    }
  }

  private validateMappings(
    nodeId: string,
    variables: VariableMapping[] | undefined,
    processCall: boolean,
    singleOutput: boolean,
    outputDataTypeRequired: boolean
  ): void {
    mappingFindings({
      dialect: 'tbbpm',
      mappings: variables,
      processCall,
      singleOutput,
      outputDataTypeRequired,
      processVariableNames: this.processVariableNames,
    }).forEach((finding) => {
      this.addError(nodeId, finding.code, 'error', 'property', finding.params)
    })
  }

  private validateConnection(conn: TbbpmConnection) {
    const mutation = findDirectJavaMutation(conn.condition)
    if (mutation) {
      this.addError(
        `conn_${conn.sourceId}_${conn.targetId}`,
        'condition.directMutation',
        'error',
        'property',
        { operator: mutation }
      )
    }
    const fromNode = this.nodeById.get(conn.sourceId)
    const toNode = this.nodeById.get(conn.targetId)

    if (!fromNode) {
      this.addError(
        `conn_${conn.sourceId}_${conn.targetId}`,
        'conn.missingSource',
        'error',
        'structure',
        { nodeId: conn.sourceId }
      )
    }
    if (!toNode) {
      this.addError(
        `conn_${conn.sourceId}_${conn.targetId}`,
        'conn.missingTarget',
        'error',
        'structure',
        { nodeId: conn.targetId }
      )
    }
    if (conn.sourceId === conn.targetId) {
      this.addError(`conn_${conn.sourceId}_${conn.targetId}`, 'conn.selfLoop', 'error', 'structure')
    }
    if (fromNode?.type === 'note' || toNode?.type === 'note') {
      this.addError(
        `conn_${conn.sourceId}_${conn.targetId}`,
        'note.transitionNotAllowed',
        'error',
        'structure'
      )
    }
    if (fromNode && toNode && fromNode.parentId !== toNode.parentId) {
      this.addError(
        `conn_${conn.sourceId}_${conn.targetId}`,
        'container.crossBoundaryTransition',
        'error',
        'structure'
      )
    }
  }

  private addError(
    elementId: string,
    code: string,
    severity: 'error' | 'warning' | 'info',
    category: 'structure' | 'property' | 'compileflow',
    params?: Record<string, string | number>
  ) {
    this.errors.push({
      id: `${elementId}_${this.errors.length}`,
      elementId,
      code,
      params,
      severity,
      category,
    })
  }
}

function hasEnclosingLoop(node: TbbpmNode, nodesById: ReadonlyMap<string, TbbpmNode>): boolean {
  const visited = new Set<string>()
  let parentId = node.parentId
  while (parentId && !visited.has(parentId)) {
    visited.add(parentId)
    const parent = nodesById.get(parentId)
    if (!parent) return false
    if (parent.type === 'while' || parent.type === 'foreach') return true
    parentId = parent.parentId
  }
  return false
}
