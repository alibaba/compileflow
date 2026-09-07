import type { ActionDefinition } from '../types/action'
import type { BaseConnection, BaseNode, UnifiedProcessDefinition } from '../types/flowDefinition'

import { evaluateSafeExpression, evaluateSafeJavaCondition } from './safeExpressionEvaluator'
import {
  SIM_ERROR_CALLED_PROCESS_UNSUPPORTED,
  SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED,
  SIM_ERROR_CONTINUE_NOT_PAUSED,
  SIM_ERROR_CYCLE,
  SIM_ERROR_DEAD_END,
  SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED,
  SIM_ERROR_EXPRESSION_EVALUATION_FAILED,
  SIM_ERROR_LOOP_UNSUPPORTED,
  SIM_ERROR_NO_BRANCH_MATCHED,
  SIM_ERROR_NO_START,
  SIM_ERROR_NODE_NOT_FOUND,
  SIM_ERROR_RUN_SUPERSEDED,
  SIM_ERROR_STEP_LIMIT,
  SIM_ERROR_STEP_NOT_PAUSED,
  SIM_ERROR_TIMER_UNSUPPORTED,
  SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED,
} from './simulationErrors'

import { toError } from '@/shared/errors'
import { logger } from '@/shared/logging/logger'

const SERVICE_TASK_TYPES = new Set(['autoTask', 'bpmn:ServiceTask'])
const SCRIPT_TASK_TYPES = new Set(['scriptTask', 'bpmn:ScriptTask'])
const TRIGGER_ENTRY_TYPES = new Set(['waitTask', 'waitEventTask', 'bpmn:ReceiveTask'])
const CALLED_PROCESS_TYPES = new Set(['bpmCall', 'bpmn:CallActivity'])
const LOOP_CONTROL_TYPES = new Set(['break', 'continue'])

export enum ExecutionState {
  READY = 'ready', // 准备就绪
  RUNNING = 'running', // 运行中
  PAUSED = 'paused', // 暂停（断点）
  COMPLETED = 'completed', // 已完成
  ERROR = 'error', // 错误
}

export interface ExecutionContext {
  /** 变量存储 */
  variables: Map<string, unknown>

  /** 执行路径 */
  path: string[]

  /** 当前节点ID */
  currentNodeId: string | null

  /** 开始时间 */
  startTime: number

  /** 结束时间 */
  endTime?: number
}

export interface ExecutionEvent {
  /** 事件类型 */
  type:
    | 'node-enter'
    | 'node-exit'
    | 'edge-traverse'
    | 'variable-change'
    | 'breakpoint-hit'
    | 'error'

  /** 时间戳 */
  timestamp: number

  /** 节点ID */
  nodeId?: string

  /** 连接线ID */
  connectionId?: string

  /** 变量名 */
  variableName?: string

  /** 变量值 */
  variableValue?: unknown

  /** 错误信息 */
  error?: string
}

export interface Breakpoint {
  /** 断点ID */
  id: string

  /** 节点ID */
  nodeId: string

  /** 是否启用 */
  enabled: boolean

  /** 条件表达式（可选） */
  condition?: string

  /** 命中次数 */
  hitCount: number
}

export interface SimulationResult {
  /** 执行状态 */
  state: ExecutionState

  /** 执行上下文 */
  context: ExecutionContext

  /** 执行事件列表 */
  events: ExecutionEvent[]

  /** 最终变量状态 */
  finalVariables: Record<string, unknown>

  /** 执行时长（ms） */
  duration: number

  /** 错误信息 */
  error?: string
}

interface PausedExecution {
  nodeId: string
  visited: Set<string>
}

export class ProcessSimulationEngine {
  // Stop malformed cyclic graphs from running forever.
  private static readonly MAX_VISITED = 1000
  private flowDef: UnifiedProcessDefinition
  private nodeMap: Map<string, BaseNode>
  private connectionMap: Map<string, BaseConnection[]>
  private context: ExecutionContext
  private events: ExecutionEvent[]
  private breakpoints: Map<string, Breakpoint>
  private state: ExecutionState
  private eventListeners: Array<(event: ExecutionEvent) => void>
  private pausedExecution: PausedExecution | null
  private generation = 0

  constructor(flowDef: UnifiedProcessDefinition) {
    this.flowDef = flowDef
    this.nodeMap = new Map()
    this.connectionMap = new Map()
    this.events = []
    this.breakpoints = new Map()
    this.state = ExecutionState.READY
    this.eventListeners = []
    this.pausedExecution = null

    this.context = {
      variables: new Map(),
      path: [],
      currentNodeId: null,
      startTime: 0,
    }

    this.buildGraph()
  }

  addBreakpoint(nodeId: string, condition?: string): string {
    const id = `bp_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
    this.breakpoints.set(id, {
      id,
      nodeId,
      enabled: true,
      condition,
      hitCount: 0,
    })
    return id
  }

  removeBreakpoint(id: string) {
    this.breakpoints.delete(id)
  }

  toggleBreakpoint(id: string) {
    const bp = this.breakpoints.get(id)
    if (bp) {
      bp.enabled = !bp.enabled
    }
  }

  on(listener: (event: ExecutionEvent) => void) {
    this.eventListeners.push(listener)
  }

  off(listener: (event: ExecutionEvent) => void) {
    const index = this.eventListeners.indexOf(listener)
    if (index > -1) {
      this.eventListeners.splice(index, 1)
    }
  }

  async start(initialVariables: Record<string, unknown> = {}): Promise<SimulationResult> {
    // Guard against re-entrant starts.  Calling start() while RUNNING or PAUSED
    // would spawn a second concurrent traversal, producing duplicate events and
    // corrupting the visited-set logic.  Callers must reset() first.
    if (this.state === ExecutionState.RUNNING || this.state === ExecutionState.PAUSED) {
      logger.warn(
        '[ProcessSimulationEngine] start() ignored: engine is already running or paused. Call reset() first.'
      )
      return this.result(
        this.state,
        this.context,
        this.events,
        'Engine is already running or paused'
      )
    }
    this.initializeRun(initialVariables)
    const generation = this.generation
    const context = this.context
    const events = this.events

    // 找到起点节点
    const startNode = this.flowDef.nodes.find(
      (node) => !node.parentId && (node.type === 'start' || node.type === 'bpmn:StartEvent')
    )

    if (!startNode) {
      this.failRun(new Error(SIM_ERROR_NO_START))
      return this.result(ExecutionState.ERROR, context, events, SIM_ERROR_NO_START)
    }

    try {
      await this.executeNode(startNode.id, generation)
      this.requireGeneration(generation)
      return this.result(this.state, context, events)
    } catch (error) {
      if (generation !== this.generation) {
        return this.result(ExecutionState.ERROR, context, events, SIM_ERROR_RUN_SUPERSEDED)
      }
      const failure = toError(error)
      this.failRun(failure)
      return this.result(ExecutionState.ERROR, context, events, failure.message)
    }
  }

  async stepNext(): Promise<void> {
    await this.resumePausedExecution(true, SIM_ERROR_STEP_NOT_PAUSED)
  }

  async continue(): Promise<void> {
    await this.resumePausedExecution(false, SIM_ERROR_CONTINUE_NOT_PAUSED)
  }

  getState(): ExecutionState {
    return this.state
  }

  getContext(): ExecutionContext {
    return structuredClone(this.context)
  }

  getEvents(): ExecutionEvent[] {
    return structuredClone(this.events)
  }

  getBreakpoints(): Breakpoint[] {
    return structuredClone(Array.from(this.breakpoints.values()))
  }

  reset() {
    this.generation++
    this.state = ExecutionState.READY
    this.context = {
      variables: new Map(),
      path: [],
      currentNodeId: null,
      startTime: 0,
    }
    this.events = []
    this.pausedExecution = null
  }

  private buildGraph() {
    // 构建节点映射
    this.flowDef.nodes.forEach((node) => {
      this.nodeMap.set(node.id, node)
    })

    // 构建连接线映射（source -> targets）
    this.flowDef.connections.forEach((conn) => {
      const outgoing = this.connectionMap.get(conn.sourceId)
      if (outgoing) {
        outgoing.push(conn)
      } else {
        this.connectionMap.set(conn.sourceId, [conn])
      }
    })
  }

  private emitEvent(event: ExecutionEvent) {
    const generation = this.generation
    this.events.push(structuredClone(event))
    for (const listener of [...this.eventListeners]) {
      if (generation !== this.generation) break
      listener(structuredClone(event))
    }
  }

  private async executeNode(
    nodeId: string,
    generation: number,
    visited = new Set<string>(),
    pauseBeforeExecution = false
  ): Promise<void> {
    this.requireGeneration(generation)
    this.visitNode(nodeId, visited)

    const node = this.nodeMap.get(nodeId)
    if (!node) {
      throw new Error(`${SIM_ERROR_NODE_NOT_FOUND}:${nodeId}`)
    }

    if (pauseBeforeExecution) {
      this.state = ExecutionState.PAUSED
    }
    this.enterNode(nodeId)
    this.requireGeneration(generation)

    if (pauseBeforeExecution) {
      this.pausedExecution = { nodeId, visited: new Set(visited) }
      return
    }
    if (this.checkBreakpoint(nodeId)) {
      this.pauseAtBreakpoint(nodeId, visited)
      return
    }

    await this.executeNodeLogic(node)
    this.requireGeneration(generation)
    this.exitNode(nodeId)
    this.requireGeneration(generation)

    if (this.isEndNode(node)) {
      this.completeRun()
      return
    }

    await this.executeOutgoingConnections(
      node,
      this.connectionMap.get(nodeId) || [],
      visited,
      generation
    )
  }

  private visitNode(nodeId: string, visited: Set<string>): void {
    if (visited.has(nodeId)) {
      throw new Error(`${SIM_ERROR_CYCLE}:${nodeId}`)
    }
    if (visited.size >= ProcessSimulationEngine.MAX_VISITED) {
      throw new Error(`${SIM_ERROR_STEP_LIMIT}:${nodeId}`)
    }
    visited.add(nodeId)
  }

  private enterNode(nodeId: string): void {
    this.context.currentNodeId = nodeId
    this.context.path.push(nodeId)
    this.emitEvent({
      type: 'node-enter',
      timestamp: Date.now(),
      nodeId,
    })
  }

  private exitNode(nodeId: string): void {
    this.emitEvent({
      type: 'node-exit',
      timestamp: Date.now(),
      nodeId,
    })
  }

  private isEndNode(node: BaseNode): boolean {
    return node.type === 'end' || node.type === 'bpmn:EndEvent'
  }

  private async executeOutgoingConnections(
    node: BaseNode,
    outgoingConnections: BaseConnection[],
    visited: Set<string>,
    generation: number,
    pauseAtTarget = false
  ): Promise<void> {
    if (node.type === 'exclusive' || node.type === 'bpmn:ExclusiveGateway') {
      await this.executeExclusiveBranch(
        node.id,
        outgoingConnections,
        visited,
        generation,
        pauseAtTarget
      )
      return
    }
    if (node.type === 'parallel' || node.type === 'bpmn:ParallelGateway') {
      await this.executeParallelBranches(
        node.id,
        outgoingConnections,
        visited,
        generation,
        pauseAtTarget
      )
      return
    }
    if (node.type === 'inclusive' || node.type === 'bpmn:InclusiveGateway') {
      await this.executeConcurrentGateway(
        node.id,
        outgoingConnections,
        visited,
        generation,
        pauseAtTarget
      )
      return
    }
    await this.executeFirstOutgoingConnection(
      node.id,
      outgoingConnections,
      visited,
      generation,
      pauseAtTarget
    )
  }

  private async executeExclusiveBranch(
    nodeId: string,
    connections: BaseConnection[],
    visited: Set<string>,
    generation: number,
    pauseAtTarget: boolean
  ): Promise<void> {
    const selectedConnection = this.evaluateExclusive(connections)
    if (!selectedConnection) {
      throw new Error(`${SIM_ERROR_NO_BRANCH_MATCHED}:${nodeId}`)
    }
    this.traverseEdge(selectedConnection)
    await this.executeNode(selectedConnection.targetId, generation, visited, pauseAtTarget)
  }

  private async executeParallelBranches(
    nodeId: string,
    connections: BaseConnection[],
    visited: Set<string>,
    generation: number,
    pauseAtTarget: boolean
  ): Promise<void> {
    await this.executeConcurrentGateway(nodeId, connections, visited, generation, pauseAtTarget)
  }

  private async executeConcurrentGateway(
    nodeId: string,
    connections: BaseConnection[],
    visited: Set<string>,
    generation: number,
    pauseAtTarget: boolean
  ): Promise<void> {
    if (connections.length > 1) {
      throw new Error(SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED)
    }
    await this.executeFirstOutgoingConnection(
      nodeId,
      connections,
      visited,
      generation,
      pauseAtTarget
    )
  }

  private async executeFirstOutgoingConnection(
    nodeId: string,
    connections: BaseConnection[],
    visited: Set<string>,
    generation: number,
    pauseAtTarget: boolean
  ): Promise<void> {
    const connection = connections[0]
    if (!connection) {
      throw new Error(`${SIM_ERROR_DEAD_END}:${nodeId}`)
    }
    this.traverseEdge(connection)
    await this.executeNode(connection.targetId, generation, visited, pauseAtTarget)
  }

  private async executeNodeLogic(node: BaseNode): Promise<void> {
    if (node.properties?.loopCharacteristics || node.type === 'while' || node.type === 'foreach') {
      throw new Error(`${SIM_ERROR_LOOP_UNSUPPORTED}:${node.id}`)
    }
    if (SERVICE_TASK_TYPES.has(node.type)) {
      await this.simulateServiceTask(node)
      return
    }
    if (SCRIPT_TASK_TYPES.has(node.type)) {
      await this.executeScript(node)
      return
    }
    const unsupportedError = simulationUnsupportedError(node.type)
    if (unsupportedError) throw new Error(`${unsupportedError}:${node.id}`)
  }

  private async simulateServiceTask(node: BaseNode): Promise<void> {
    // 简单模拟：生成随机结果
    const resultVar = `${node.id}_result`
    const result = {
      success: true,
      data: `Mock result from ${node.name || node.id}`,
      timestamp: Date.now(),
    }

    this.setVariable(resultVar, result)
  }

  private async executeScript(node: BaseNode): Promise<void> {
    const action = node.properties?.action as ActionDefinition | undefined
    const rawScript = action?.source ?? node.properties?.script

    try {
      // This local calculator previews one assignment over process variables only.
      // Providers, mappings and Effect execution require the real engine.
      requireAssignmentPreview(action)
      if (typeof rawScript !== 'string') throw new Error('Script source is required')
      const assignmentMatch = rawScript.trim().match(/^([A-Za-z_$][\w$]*)\s*=\s*(.+?)\s*;?$/s)
      if (!assignmentMatch) {
        throw new Error('Only one variable assignment can be simulated')
      }
      const varName = assignmentMatch[1]
      const condition = assignmentMatch[2]

      const value = this.evaluateScriptPreview(condition)
      this.setVariable(varName, value)
    } catch (_error) {
      throw new Error(`${SIM_ERROR_EXPRESSION_EVALUATION_FAILED}:${node.id}`)
    }
  }

  private evaluateExclusive(connections: BaseConnection[]): BaseConnection | null {
    for (const connection of connections) {
      if (!connection.condition) continue
      try {
        if (this.evaluateCondition(connection.condition)) {
          return connection
        }
      } catch (_error) {
        throw new Error(`${SIM_ERROR_EXPRESSION_EVALUATION_FAILED}:${connection.id}`)
      }
    }

    return connections.find((connection) => !connection.condition) ?? null
  }

  private evaluateScriptPreview(condition: string): unknown {
    return evaluateSafeExpression(condition, this.context.variables)
  }

  private evaluateCondition(condition: string): boolean {
    return evaluateSafeJavaCondition(condition, this.context.variables)
  }

  private traverseEdge(connection: BaseConnection): void {
    this.emitEvent({
      type: 'edge-traverse',
      timestamp: Date.now(),
      connectionId: connection.id,
    })
  }

  private checkBreakpoint(nodeId: string): boolean {
    for (const bp of this.breakpoints.values()) {
      if (bp.nodeId === nodeId && bp.enabled) {
        // 条件断点：评估条件
        if (bp.condition) {
          try {
            const conditionMet = this.evaluateCondition(bp.condition)
            if (!conditionMet) continue
          } catch (_error) {
            throw new Error(`${SIM_ERROR_EXPRESSION_EVALUATION_FAILED}:${nodeId}`)
          }
        }

        // 断点命中
        bp.hitCount++
        return true
      }
    }

    return false
  }

  private setVariable(name: string, value: unknown) {
    this.context.variables.set(name, value)
    this.emitEvent({
      type: 'variable-change',
      timestamp: Date.now(),
      variableName: name,
      variableValue: value,
    })
  }

  private initializeRun(initialVariables: Record<string, unknown>): void {
    const variables = new Map(Object.entries(structuredClone(initialVariables)))
    this.generation++
    this.state = ExecutionState.RUNNING
    this.context = {
      variables,
      path: [],
      currentNodeId: null,
      startTime: Date.now(),
    }
    this.events = []
    this.pausedExecution = null
  }

  private async resumePausedExecution(
    pauseAtNextNode: boolean,
    invalidStateError: string
  ): Promise<void> {
    const paused = this.pausedExecution
    const generation = this.generation
    if (this.state !== ExecutionState.PAUSED || !paused) {
      throw new Error(invalidStateError)
    }

    const node = this.nodeMap.get(paused.nodeId)
    if (!node) {
      throw new Error(`${SIM_ERROR_NODE_NOT_FOUND}:${paused.nodeId}`)
    }

    this.state = ExecutionState.RUNNING
    this.pausedExecution = null
    try {
      await this.executeNodeLogic(node)
      this.requireGeneration(generation)
      this.exitNode(node.id)
      this.requireGeneration(generation)
      if (!this.isEndNode(node)) {
        await this.executeOutgoingConnections(
          node,
          this.connectionMap.get(node.id) || [],
          paused.visited,
          generation,
          pauseAtNextNode
        )
      } else {
        this.completeRun()
      }
      this.requireGeneration(generation)
    } catch (error) {
      this.requireGeneration(generation)
      const failure = toError(error)
      this.failRun(failure)
      throw error
    }
  }

  private pauseAtBreakpoint(nodeId: string, visited: Set<string>): void {
    this.state = ExecutionState.PAUSED
    this.pausedExecution = { nodeId, visited: new Set(visited) }
    this.emitEvent({
      type: 'breakpoint-hit',
      timestamp: Date.now(),
      nodeId,
    })
  }

  private completeRun(): void {
    this.state = ExecutionState.COMPLETED
    this.context.endTime = Date.now()
  }

  private failRun(error: Error): void {
    this.state = ExecutionState.ERROR
    this.context.endTime = Date.now()
    this.pausedExecution = null
    this.emitEvent({
      type: 'error',
      timestamp: Date.now(),
      error: error.message,
    })
  }

  private requireGeneration(generation: number): void {
    if (generation !== this.generation) throw new Error(SIM_ERROR_RUN_SUPERSEDED)
  }

  private result(
    state: ExecutionState,
    context: ExecutionContext,
    events: ExecutionEvent[],
    error?: string
  ): SimulationResult {
    return {
      state,
      context: structuredClone(context),
      events: structuredClone(events),
      finalVariables: Object.fromEntries(structuredClone(context.variables)),
      duration: context.startTime === 0 ? 0 : (context.endTime ?? Date.now()) - context.startTime,
      ...(error === undefined ? {} : { error }),
    }
  }
}

function requireAssignmentPreview(action: ActionDefinition | undefined): void {
  if (
    action &&
    (action.actionType !== 'script' ||
      !['java', 'qlexpress'].includes(action.language ?? '') ||
      action.mappings?.length ||
      action.execution === 'effect')
  ) {
    throw new Error('Script action semantics are outside the assignment preview subset')
  }
  if (Object.values(action?.invocationPolicy ?? {}).some((value) => value !== undefined)) {
    throw new Error('Invocation policy requires the real engine')
  }
}

function simulationUnsupportedError(nodeType: string): string | undefined {
  if (TRIGGER_ENTRY_TYPES.has(nodeType)) return SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED
  if (nodeType === 'timerTask') return SIM_ERROR_TIMER_UNSUPPORTED
  if (CALLED_PROCESS_TYPES.has(nodeType)) return SIM_ERROR_CALLED_PROCESS_UNSUPPORTED
  if (nodeType === 'subBpm' || nodeType === 'bpmn:SubProcess') {
    return SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED
  }
  if (LOOP_CONTROL_TYPES.has(nodeType)) return SIM_ERROR_LOOP_UNSUPPORTED
  return undefined
}

export function createSimulationEngine(flowDef: UnifiedProcessDefinition): ProcessSimulationEngine {
  return new ProcessSimulationEngine(flowDef)
}
