import type { ActionDefinition, VariableMapping } from '../types/action'
import { validateBpmnLoopCharacteristics } from '../types/bpmnLoop'
import type { MultiInstanceLoopCharacteristics } from '../types/bpmnNodeTypes'
import type {
  BpmnConnection,
  BpmnMessageDefinition,
  BpmnNode,
  ProcessVariable,
} from '../types/flowDefinition'
import { validateInvocationPolicy } from '../types/invocationPolicy'
import { findInapplicableBpmnNodeProperties } from '../types/nodePropertyContracts'

import { actionFindings } from './actionValidation'
import { findDirectJavaMutation } from './javaConditionExpression'
import { mappingFindings } from './mappingValidation'
import { processCallReferenceIssue } from './processCallReference'

import { toError } from '@/shared/errors'

export interface BpmnValidationError {
  id: string
  elementId: string
  code: string
  params?: Record<string, string | number>
  severity: 'error' | 'warning' | 'info'
  category: 'structure' | 'property' | 'bpmn'
}

export class BpmnValidator {
  private nodes: BpmnNode[]
  private connections: BpmnConnection[]
  private processVariableNames: Set<string>
  private processVariables: ReadonlyMap<string, ProcessVariable>
  private nodeById: ReadonlyMap<string, BpmnNode>
  private messages: BpmnMessageDefinition[] | undefined
  private errors: BpmnValidationError[] = []

  constructor(
    nodes: BpmnNode[],
    connections: BpmnConnection[],
    variables: ProcessVariable[] = [],
    messages?: BpmnMessageDefinition[]
  ) {
    this.nodes = nodes
    this.connections = connections
    this.processVariableNames = new Set(variables.map((variable) => variable.name))
    this.processVariables = new Map(variables.map((variable) => [variable.name, variable]))
    const nodeById = new Map<string, BpmnNode>()
    nodes.forEach((node) => {
      if (!nodeById.has(node.id)) nodeById.set(node.id, node)
    })
    this.nodeById = nodeById
    this.messages = messages
  }

  /** Format-specific rules only; shared topology checks run separately. */
  validateNodeRules(): BpmnValidationError[] {
    this.errors = []
    this.validateHierarchy()
    this.validateMessages()
    this.nodes.forEach((node) => this.validateNode(node))
    this.connections.forEach((conn) => this.validateConnection(conn))
    return this.errors
  }

  private validateHierarchy(): void {
    const nodesById = new Map<string, BpmnNode>()
    this.nodes.forEach((node) => {
      if (nodesById.has(node.id)) {
        this.addError(node.id, 'bpmn.process.duplicateNodeId', 'error', 'structure')
      } else {
        nodesById.set(node.id, node)
      }
    })

    this.nodes.forEach((node) => {
      if (!node.parentId) return
      const parent = nodesById.get(node.parentId)
      if (!parent) {
        this.addError(node.id, 'bpmn.subProcess.unknownParent', 'error', 'structure', {
          parentId: node.parentId,
        })
        return
      }
      if (parent.type !== 'bpmn:SubProcess') {
        this.addError(node.id, 'bpmn.subProcess.invalidParent', 'error', 'structure', {
          parentId: node.parentId,
        })
      }
      if (node.type === 'bpmn:ReceiveTask') {
        this.addError(node.id, 'bpmn.subProcess.triggerEntryChild', 'error', 'structure')
      }

      const ancestors = new Set([node.id])
      let ancestorId: string | undefined = node.parentId
      while (ancestorId) {
        if (ancestors.has(ancestorId)) {
          this.addError(node.id, 'bpmn.subProcess.parentCycle', 'error', 'structure')
          break
        }
        ancestors.add(ancestorId)
        ancestorId = nodesById.get(ancestorId)?.parentId
      }
    })
  }

  private validateNode(node: BpmnNode) {
    findInapplicableBpmnNodeProperties(node).forEach((property) => {
      this.addError(node.id, 'bpmn.node.inapplicableProperty', 'error', 'property', {
        nodeType: node.type,
        property,
      })
    })
    this.validateStartEvent(node)
    this.validateEndEvent(node)
    this.validateServiceTask(node)
    this.validateScriptTask(node)
    this.validateGateway(node)
    this.validateCallActivity(node)
    this.validateReceiveTask(node)
    this.validateLoop(node)
    this.validateInvocationPolicy(node)
    this.validateExplicitBranching(node)
  }

  private validateStartEvent(node: BpmnNode): void {
    if (node.type !== 'bpmn:StartEvent') return
    if (this.outgoingConnections(node.id).length !== 1) {
      this.addError(node.id, 'bpmn.start.mustHaveOutgoing', 'error', 'structure')
    }
    if (this.incomingConnections(node.id).length > 0) {
      this.addError(node.id, 'bpmn.start.shouldNotHaveIncoming', 'error', 'structure')
    }
  }

  private validateEndEvent(node: BpmnNode): void {
    if (node.type !== 'bpmn:EndEvent') return
    if (this.incomingConnections(node.id).length === 0) {
      this.addError(node.id, 'bpmn.end.mustHaveIncoming', 'error', 'structure')
    }
    if (this.outgoingConnections(node.id).length > 0) {
      this.addError(node.id, 'bpmn.end.shouldNotHaveOutgoing', 'error', 'structure')
    }
  }

  private validateServiceTask(node: BpmnNode): void {
    if (node.type !== 'bpmn:ServiceTask') return
    const action = node.properties.action
    if (!action) {
      this.addError(node.id, 'bpmn.serviceTask.missingAction', 'error', 'property')
      return
    }
    if (action.actionType === 'script') {
      this.addError(node.id, 'bpmn.serviceTask.unsupportedActionType', 'error', 'property', {
        actionType: action.actionType,
      })
    }
    this.validateAction(node.id, action)
  }

  private validateScriptTask(node: BpmnNode): void {
    if (node.type !== 'bpmn:ScriptTask') return
    if (!node.properties.scriptFormat) {
      this.addError(node.id, 'bpmn.scriptTask.missingScriptFormat', 'error', 'property')
    }
    if (!node.properties.script) {
      this.addError(node.id, 'bpmn.scriptTask.missingScript', 'error', 'property')
    }
    this.validateAction(node.id, {
      actionType: 'script',
      language: node.properties.scriptFormat,
      source: node.properties.script,
      execution: node.properties.execution || 'replayable',
      mappings: node.properties.mappings,
      invocationPolicy: node.properties.invocationPolicy,
      effectPolicy: node.properties.effectPolicy,
    })
  }

  private validateGateway(node: BpmnNode): void {
    if (!isGateway(node)) return
    const incoming = this.incomingConnections(node.id)
    const outgoing = this.outgoingConnections(node.id)
    const split = incoming.length === 1 && outgoing.length > 1
    const join = incoming.length > 1 && outgoing.length === 1
    this.validateGatewayShape(node, incoming.length, outgoing.length, split, join)
    this.validateNestedConcurrentGateway(node, split)

    const defaultConnectionId =
      typeof node.properties.default === 'string' ? node.properties.default.trim() : ''
    if (node.type === 'bpmn:ParallelGateway') {
      this.validateParallelGateway(node, incoming, outgoing, join, defaultConnectionId)
      return
    }
    if (join) {
      this.validateJoinGateway(node, outgoing, defaultConnectionId)
      return
    }
    if (split) {
      this.validateSplitGateway(node, outgoing, defaultConnectionId)
    }
  }

  private validateGatewayShape(
    node: BpmnNode,
    incomingCount: number,
    outgoingCount: number,
    split: boolean,
    join: boolean
  ): void {
    if (!split && !join) {
      this.addError(node.id, 'bpmn.gateway.invalidShape', 'error', 'structure', {
        incoming: incomingCount,
        outgoing: outgoingCount,
      })
    }
  }

  private validateNestedConcurrentGateway(node: BpmnNode, split: boolean): void {
    if (
      node.parentId &&
      split &&
      (node.type === 'bpmn:ParallelGateway' || node.type === 'bpmn:InclusiveGateway')
    ) {
      this.addError(node.id, 'bpmn.gateway.nestedConcurrencyUnsupported', 'error', 'structure')
    }
  }

  private validateParallelGateway(
    node: BpmnNode,
    incoming: BpmnConnection[],
    outgoing: BpmnConnection[],
    join: boolean,
    defaultConnectionId: string
  ): void {
    if (defaultConnectionId) {
      this.addError(node.id, 'bpmn.gateway.defaultUnsupported', 'error', 'property')
    }
    if (outgoing.some(hasCondition)) {
      this.addError(node.id, 'bpmn.gateway.parallelConditionUnsupported', 'error', 'property')
    }
    if (join && incoming.some(hasCondition)) {
      this.addError(node.id, 'bpmn.gateway.parallelJoinConditionUnsupported', 'error', 'property')
    }
  }

  private validateJoinGateway(
    node: BpmnNode,
    outgoing: BpmnConnection[],
    defaultConnectionId: string
  ): void {
    if (defaultConnectionId) {
      this.addError(node.id, 'bpmn.gateway.defaultOnJoin', 'error', 'property')
    }
    if (outgoing.some(hasCondition)) {
      this.addError(node.id, 'bpmn.gateway.joinConditionUnsupported', 'error', 'property')
    }
  }

  private validateSplitGateway(
    node: BpmnNode,
    outgoing: BpmnConnection[],
    defaultConnectionId: string
  ): void {
    const defaultConnection = defaultConnectionId
      ? outgoing.find((connection) => connection.id === defaultConnectionId)
      : undefined
    if (defaultConnectionId && !defaultConnection) {
      this.addError(node.id, 'bpmn.gateway.invalidDefault', 'error', 'property', {
        connectionId: defaultConnectionId,
      })
    }
    if (defaultConnection && hasCondition(defaultConnection)) {
      this.addError(node.id, 'bpmn.gateway.defaultHasCondition', 'error', 'property', {
        connectionId: defaultConnection.id,
      })
    }
    this.validateConditionalGatewayBranches(node, outgoing, defaultConnection)
    this.validateExclusiveConditions(node, outgoing)
  }

  private validateConditionalGatewayBranches(
    node: BpmnNode,
    outgoing: BpmnConnection[],
    defaultConnection: BpmnConnection | undefined
  ): void {
    outgoing
      .filter((connection) => connection !== defaultConnection)
      .filter((connection) => !hasCondition(connection))
      .forEach((connection) => {
        this.addError(node.id, 'bpmn.gateway.branchMissingCondition', 'error', 'property', {
          connectionId: connection.id,
        })
      })
  }

  private validateExclusiveConditions(node: BpmnNode, outgoing: BpmnConnection[]): void {
    if (node.type !== 'bpmn:ExclusiveGateway') return
    const conditions = outgoing
      .map((connection) => connection.condition?.trim())
      .filter((condition): condition is string => Boolean(condition))
    if (new Set(conditions).size !== conditions.length) {
      this.addError(node.id, 'bpmn.gateway.duplicateCondition', 'error', 'property')
    }
  }

  private validateCallActivity(node: BpmnNode): void {
    if (node.type !== 'bpmn:CallActivity') return
    const { mappings, calledElement, classpath, version } = node.properties
    if (!calledElement) {
      this.addError(node.id, 'bpmn.callActivity.missingCalledElement', 'error', 'property')
    } else {
      const referenceIssue = processCallReferenceIssue({
        code: calledElement,
        classpath,
        version,
      })
      if (referenceIssue) {
        this.addError(node.id, `processCall.${referenceIssue}`, 'error', 'property')
      }
    }
    this.validateMappings(node.id, mappings, true, false, false)
  }

  private validateReceiveTask(node: BpmnNode): void {
    if (node.type !== 'bpmn:ReceiveTask') return
    const messageRef = node.properties.messageRef?.trim()
    if (!messageRef) {
      this.addError(node.id, 'bpmn.receiveTask.missingMessageRef', 'error', 'property')
    } else if (this.messages) {
      const matchingMessages = this.messages.filter((message) => message.id === messageRef)
      if (matchingMessages.length !== 1) {
        this.addError(node.id, 'bpmn.receiveTask.unknownMessageRef', 'error', 'property', {
          messageRef,
        })
      }
    }
  }

  private validateMessages(): void {
    if (!this.messages) return
    const messageIds = new Set<string>()
    const executableElementIds = new Set([
      ...this.nodes.map((node) => node.id),
      ...this.connections.map((connection) => connection.id),
    ])
    this.messages.forEach((message) => {
      if (!message.id.trim()) {
        this.addError('process', 'bpmn.message.missingId', 'error', 'property')
        return
      }
      if (messageIds.has(message.id)) {
        this.addError(message.id, 'bpmn.message.duplicateId', 'error', 'property', {
          messageId: message.id,
        })
      }
      messageIds.add(message.id)
      if (executableElementIds.has(message.id)) {
        this.addError(message.id, 'bpmn.message.idCollision', 'error', 'property', {
          messageId: message.id,
        })
      }
      if (!message.name.trim()) {
        this.addError(message.id, 'bpmn.message.missingName', 'error', 'property', {
          messageId: message.id,
        })
      }
    })
  }

  private validateLoop(node: BpmnNode): void {
    const loop = node.properties.loopCharacteristics
    if (!loop) return
    if (
      node.type !== 'bpmn:ServiceTask' &&
      node.type !== 'bpmn:ScriptTask' &&
      node.type !== 'bpmn:CallActivity' &&
      node.type !== 'bpmn:SubProcess'
    ) {
      this.addError(node.id, 'bpmn.loop.unsupportedNode', 'error', 'property')
      return
    }
    try {
      validateBpmnLoopCharacteristics(loop)
    } catch (error) {
      this.addError(node.id, 'bpmn.loop.invalid', 'error', 'property', {
        message: errorMessage(error),
      })
      return
    }
    if (loop.type === 'standard') {
      this.validateConditionExpression(node.id, loop.loopCondition)
      return
    }
    const enclosingVariables = this.enclosingMultiInstanceVariables(node)
    if (
      !this.processVariableNames.has(loop.collection) &&
      !enclosingVariables.has(loop.collection)
    ) {
      this.addError(node.id, 'bpmn.loop.unknownCollection', 'error', 'property', {
        name: loop.collection,
      })
    }
    this.validateMultiInstanceOutput(node.id, loop)
    const localVariableNames = [loop.item, loop.index]
    localVariableNames
      .filter((name): name is string => Boolean(name))
      .filter((name) => this.processVariableNames.has(name) || enclosingVariables.has(name))
      .forEach((name) => {
        this.addError(node.id, 'bpmn.loop.variableShadowing', 'error', 'property', {
          name,
        })
      })
  }

  private validateMultiInstanceOutput(
    nodeId: string,
    loop: MultiInstanceLoopCharacteristics
  ): void {
    for (const name of [loop.target, loop.source]) {
      if (name && !this.processVariableNames.has(name)) {
        this.addError(nodeId, 'bpmn.loop.unknownOutputReference', 'error', 'property', { name })
      }
    }
    const outputSource = loop.source ? this.processVariables.get(loop.source) : undefined
    if (outputSource && outputSource.inOutType !== 'inner') {
      this.addError(nodeId, 'bpmn.loop.outputSourceNotInner', 'error', 'property')
    }
  }

  private enclosingMultiInstanceVariables(node: BpmnNode): ReadonlySet<string> {
    const variables = new Set<string>()
    const visited = new Set<string>()
    let parentId = node.parentId
    while (parentId && !visited.has(parentId)) {
      visited.add(parentId)
      const parent = this.nodeById.get(parentId)
      if (!parent) break
      const loop = parent.properties.loopCharacteristics
      if (loop?.type === 'multiInstance') {
        variables.add(loop.item)
        if (loop.index) variables.add(loop.index)
      }
      parentId = parent.parentId
    }
    return variables
  }

  private validateInvocationPolicy(node: BpmnNode): void {
    const policy =
      node.type === 'bpmn:ServiceTask'
        ? node.properties.action?.invocationPolicy
        : node.type === 'bpmn:ScriptTask'
          ? node.properties.invocationPolicy
          : undefined
    if (!policy) return
    try {
      validateInvocationPolicy(policy)
    } catch (error) {
      this.addError(node.id, 'bpmn.invocationPolicy.invalid', 'error', 'property', {
        message: errorMessage(error),
      })
    }
  }

  private validateExplicitBranching(node: BpmnNode): void {
    if (isGateway(node) || node.type === 'bpmn:EndEvent') return
    const outgoing = this.outgoingConnections(node.id)
    if (outgoing.length > 1) {
      this.addError(node.id, 'bpmn.node.requiresExplicitGateway', 'error', 'structure')
    }
    if (outgoing.some(hasCondition)) {
      this.addError(node.id, 'bpmn.node.conditionRequiresGateway', 'error', 'property')
    }
  }

  private incomingConnections(nodeId: string): BpmnConnection[] {
    return this.connections.filter((c) => c.targetId === nodeId)
  }

  private outgoingConnections(nodeId: string): BpmnConnection[] {
    return this.connections.filter((c) => c.sourceId === nodeId)
  }

  private validateAction(nodeId: string, action: ActionDefinition): void {
    actionFindings(action).forEach((finding) => {
      this.addError(nodeId, finding.code, 'error', 'property', finding.params)
    })
    this.validateMappings(nodeId, action.mappings, false, true, true)
  }

  private validateMappings(
    nodeId: string,
    variables: VariableMapping[] | undefined,
    processCall: boolean,
    singleOutput: boolean,
    outputDataTypeRequired: boolean
  ): void {
    mappingFindings({
      dialect: 'bpmn',
      mappings: variables,
      processCall,
      singleOutput,
      outputDataTypeRequired,
      processVariableNames: this.processVariableNames,
    }).forEach((finding) => {
      this.addError(nodeId, finding.code, 'error', 'property', finding.params)
    })
  }

  private validateConnection(conn: BpmnConnection) {
    this.validateConditionExpression(`conn_${conn.id}`, conn.condition)
    const source = this.nodes.find((node) => node.id === conn.sourceId)
    const target = this.nodes.find((node) => node.id === conn.targetId)
    if (!source) {
      this.addError(`conn_${conn.id}`, 'conn.missingSource', 'error', 'structure', {
        nodeId: conn.sourceId,
      })
    }
    if (!target) {
      this.addError(`conn_${conn.id}`, 'conn.missingTarget', 'error', 'structure', {
        nodeId: conn.targetId,
      })
    }
    if (conn.sourceId === conn.targetId) {
      this.addError(`conn_${conn.id}`, 'conn.selfLoop', 'error', 'structure')
    }
    if (source && target && source.parentId !== target.parentId) {
      this.addError(
        `conn_${conn.id}`,
        'bpmn.subProcess.crossContainerTransition',
        'error',
        'structure'
      )
    }
  }

  private validateConditionExpression(elementId: string, condition: string | undefined): void {
    const mutation = findDirectJavaMutation(condition)
    if (mutation) {
      this.addError(elementId, 'condition.directMutation', 'error', 'property', {
        operator: mutation,
      })
    }
  }

  private addError(
    elementId: string,
    code: string,
    severity: 'error' | 'warning' | 'info',
    category: 'structure' | 'property' | 'bpmn',
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

function isGateway(node: BpmnNode): boolean {
  return (
    node.type === 'bpmn:ExclusiveGateway' ||
    node.type === 'bpmn:InclusiveGateway' ||
    node.type === 'bpmn:ParallelGateway'
  )
}

function hasCondition(connection: BpmnConnection): boolean {
  return Boolean(connection.condition?.trim())
}

function errorMessage(error: unknown): string {
  return toError(error).message
}
