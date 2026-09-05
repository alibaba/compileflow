import { safeParseTbbpmNode } from '../schemas/tbbpmSchemas'
import type { ActionDefinition, VariableMapping } from '../types/action'
import type { ProcessVariable, TbbpmProcessDefinition } from '../types/flowDefinition'
import { isJavaClassName, requireGeneratedJavaIdentifier } from '../types/javaIdentifiers'
import { isValidLoopIterationLimit, MAX_LOOP_ITERATIONS } from '../types/loopLimits'
import { findInapplicableTbbpmNodeProperties } from '../types/nodePropertyContracts'
import type { TbbpmConnection, TbbpmNode, TbbpmNodeType, TbbpmVar } from '../types/tbbpm'
import {
  getTbbpmChildNodeTypes,
  isTbbpmNodeType,
  TBBPM_ROOT_NODE_TYPES,
  TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES,
} from '../types/tbbpm'
import { normalizeJavaConditionExpression } from '../validation/javaConditionExpression'

import {
  generateActionElementXml,
  generateMappedVariableXml,
  parseActionElement,
  parseMappedVariableElement,
} from './actionXml'
import { escapeXml } from './xmlEscaping'
import { validateXmlInput } from './xmlInputValidation'
import type { GenerateOptions, ParseOptions, ParseResult, ParseWarning } from './xmlTypes'

import { toError } from '@/shared/errors'

// ==================== XML Parsing ====================

type BpmRootResult =
  | { element: Element; error?: never }
  | { element?: never; error: NonNullable<ParseResult<TbbpmProcessDefinition>['error']> }

interface TbbpmNodeElement {
  element: Element
  id: string
  parentId?: string
  tagName: TbbpmNodeType
}

export function parseTbbpmXml(
  xml: string,
  options: ParseOptions = {}
): ParseResult<TbbpmProcessDefinition> {
  const warnings: ParseWarning[] = []

  const validationResult = validateXmlInput(xml)
  if (!validationResult.success) {
    return { success: false, error: validationResult.error }
  }
  const validatedXml = validationResult.data!

  try {
    const rootResult = parseBpmRoot(validatedXml)
    if (rootResult.error) {
      return { success: false, error: rootResult.error }
    }

    const bpmElement = rootResult.element
    requireOnlyAttributes(bpmElement, ['code', 'name', 'description'])
    const code = requireNonBlankAttribute(bpmElement, 'code')
    const name = bpmElement.getAttribute('name') || code
    const description = bpmElement.getAttribute('description') || undefined
    const vars: TbbpmVar[] = parseProcessVars(bpmElement)
    const nodeElements = getAllTbbpmNodeElements(bpmElement)
    const nodes = parseTbbpmNodes(nodeElements)
    const connections = parseTbbpmConnections(nodeElements)
    validateTbbpmVariableMappings(nodes, requireUniqueProcessVariableNames(vars))

    if (options.validate) {
      validateNodeReferences(nodes, connections, warnings)
    }
    if (options.schemaValidate) {
      validateTbbpmNodeSchemas(nodes, warnings)
    }

    const flowDefinition: TbbpmProcessDefinition = {
      id: code,
      code,
      name,
      description,
      type: 'TBBPM',
      nodes,
      connections,
      variables: vars.map((v) => ({
        name: v.name,
        type: v.dataType,
        defaultValue: v.defaultValue,
        description: v.description,
        inOutType: v.inOutType,
      })),
      properties: {},
    }

    return {
      success: true,
      data: flowDefinition,
      warnings: warnings.length > 0 ? warnings : undefined,
    }
  } catch (error) {
    return {
      success: false,
      error: { code: 'UNKNOWN_ERROR', message: toError(error).message, details: error },
    }
  }
}

function parseBpmRoot(xml: string): BpmRootResult {
  const doc = new DOMParser().parseFromString(xml, 'text/xml')
  const parseError = doc.querySelector('parsererror')
  if (parseError) {
    return {
      error: {
        code: 'PARSE_ERROR',
        message: 'Failed to parse XML',
        details: parseError.textContent,
      },
    }
  }
  const bpmElement = doc.documentElement
  if (!bpmElement || bpmElement.tagName !== 'bpm') {
    return { error: { code: 'INVALID_ROOT', message: 'Root element must be <bpm>' } }
  }
  return { element: bpmElement }
}

function parseTbbpmNodes(nodeElements: TbbpmNodeElement[]): TbbpmNode[] {
  const nodes: TbbpmNode[] = []
  nodeElements.forEach((entry, index) => {
    const node = parseTbbpmNode(entry, index)
    if (node) nodes.push(node)
  })
  return nodes
}

function parseTbbpmConnections(nodeElements: TbbpmNodeElement[]): TbbpmConnection[] {
  const connections: TbbpmConnection[] = []
  nodeElements.forEach(({ element, id: sourceId }) => {
    element.querySelectorAll(':scope > transition').forEach((trans, index) => {
      const connection = parseTbbpmTransition(trans, sourceId, index)
      if (connection) connections.push(connection)
    })
  })
  return connections
}

function validateTbbpmNodeSchemas(nodes: TbbpmNode[], warnings: ParseWarning[]): void {
  nodes.forEach((node) => {
    const result = safeParseTbbpmNode(node)
    if (!result.success) {
      const detail = result.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('; ')
      warnings.push({
        code: 'SCHEMA_VALIDATION_WARNING',
        message: `Node ${node.id} schema validation: ${detail}`,
        location: node.id,
      })
    }
  })
}

function parseProcessVars(bpmElement: Element): TbbpmVar[] {
  return Array.from(bpmElement.children)
    .filter((el) => el.tagName === 'var')
    .map(parseTbbpmVar)
}

function parseTbbpmVar(el: Element): TbbpmVar {
  requireOnlyAttributes(el, ['name', 'description', 'dataType', 'defaultValue', 'inOutType'])
  const inOutType = requireNonBlankAttribute(el, 'inOutType')
  if (inOutType !== 'param' && inOutType !== 'return' && inOutType !== 'inner') {
    throw new Error(`var inOutType must be param, return, or inner: ${inOutType}`)
  }
  const dataType = requireNonBlankAttribute(el, 'dataType')
  if (dataType !== dataType.trim()) {
    throw new Error('TBBPM process variable dataType must not contain surrounding whitespace')
  }
  const variable: TbbpmVar = {
    name: requireNonBlankAttribute(el, 'name'),
    inOutType,
    dataType,
    description: el.getAttribute('description') || undefined,
    defaultValue: el.hasAttribute('defaultValue')
      ? (el.getAttribute('defaultValue') ?? undefined)
      : undefined,
  }
  requireGeneratedJavaIdentifier(variable.name, 'TBBPM process variable name')
  return variable
}

function getAllTbbpmNodeElements(bpmElement: Element): TbbpmNodeElement[] {
  const result: TbbpmNodeElement[] = []

  const collect = (container: Element, parentId?: string, containerType?: TbbpmNodeType) => {
    Array.from(container.children).forEach((element) => {
      if (element.localName === 'var' && !parentId) return
      if (element.localName === 'transition' && parentId) return
      if (element.localName === 'output' && container.localName === 'foreach') return
      if (!isTbbpmNodeType(element.localName)) {
        throw new Error(`${container.localName} contains unsupported child "${element.localName}"`)
      }

      const tagName = element.localName
      const allowedChildren = containerType ? getTbbpmChildNodeTypes(containerType) : undefined
      if (parentId && !allowedChildren?.has(tagName)) {
        throw new Error(`${tagName} is not allowed inside ${container.localName} ${parentId}`)
      }
      const id = requireNonBlankAttribute(element, 'id')
      result.push({ element, id, parentId, tagName })
      if (tagName === 'while' || tagName === 'foreach' || tagName === 'subBpm') {
        collect(element, id, tagName)
      }
    })
  }

  collect(bpmElement)
  return result
}

function parseTbbpmNode(
  { element, id, parentId, tagName }: TbbpmNodeElement,
  layoutIndex: number
): TbbpmNode | null {
  validateNodeAttributes(element, tagName)
  validateNodeChildren(element, tagName)
  const { x, y, width, height } = parseNodeGeometry(element, layoutIndex)
  const properties: TbbpmNode['properties'] = {}
  appendNodeSpecificProperties(tagName, element, properties)

  return {
    id,
    parentId,
    type: tagName,
    name: parseNodeDisplayName(element, tagName, id),
    documentation: element.getAttribute('description') || undefined,
    position: { x: x || 0, y: y || 0 },
    size: { width: width || 100, height: height || 80 },
    properties,
    metadata: { editable: true, deletable: tagName !== 'start' },
  }
}

function parseNodeDisplayName(element: Element, tagName: TbbpmNodeType, id: string): string {
  if (tagName === 'note') {
    return element.getAttribute('name') || element.getAttribute('comment') || id
  }
  return element.getAttribute('name') || id
}

function parseNodeGeometry(
  element: Element,
  layoutIndex: number
): {
  x: number
  y: number
  width: number
  height: number
} {
  const value = element.getAttribute('g')?.trim()
  if (!value) {
    return { x: 80 + layoutIndex * 160, y: 80, width: 100, height: 80 }
  }
  const geometry = value.split(',')
  if (geometry.length !== 4) {
    throw new Error(`${element.localName} g must contain x,y,width,height`)
  }
  const [x, y, width, height] = geometry.map(Number)
  if (![x, y, width, height].every(Number.isFinite) || width <= 0 || height <= 0) {
    throw new Error(`${element.localName} g must contain finite values and positive dimensions`)
  }
  return { x, y, width, height }
}

function appendNodeSpecificProperties(
  tagName: TbbpmNodeType,
  element: Element,
  properties: TbbpmNode['properties']
): void {
  switch (tagName) {
    case 'autoTask':
    case 'scriptTask':
      appendActionNodeProperties(element, properties)
      break
    case 'waitTask':
    case 'waitEventTask':
      appendWaitNodeProperties(element, properties)
      break
    case 'timerTask':
      appendTimerTaskProperties(element, properties)
      break
    case 'bpmCall':
      appendBpmCallProperties(element, properties)
      break
    case 'subBpm':
      break
    case 'while':
      appendWhileProperties(element, properties)
      break
    case 'foreach':
      appendForEachProperties(element, properties)
      break
    case 'break':
    case 'continue':
      properties.condition = normalizeOptionalJavaCondition(
        optionalNonBlankAttribute(element, 'condition')
      )
      break
    case 'note':
      appendNoteProperties(element, properties)
      break
  }
}

function appendActionNodeProperties(element: Element, properties: TbbpmNode['properties']): void {
  const actionEl = optionalDirectChild(element, 'action')
  if (actionEl) {
    properties.action = parseAction(actionEl)
  }
}

function appendWaitNodeProperties(element: Element, properties: TbbpmNode['properties']): void {
  properties.timeout = element.getAttribute('timeout') || undefined
  if (element.localName === 'waitEventTask') {
    properties.event = requireNonBlankAttribute(element, 'event')
  }
}

function appendTimerTaskProperties(element: Element, properties: TbbpmNode['properties']): void {
  const schedules = ['duration', 'durationExpression', 'wakeAtExpression'] as const
  const configured = schedules.filter((attribute) => element.hasAttribute(attribute))
  if (configured.length !== 1) {
    throw new Error(
      'timerTask must declare exactly one of duration, durationExpression, or wakeAtExpression'
    )
  }
  const attribute = configured[0]
  properties[attribute] = requireNonBlankAttribute(element, attribute)
}

function optionalDirectChild(parent: Element, name: string): Element | undefined {
  const matches = Array.from(parent.children).filter((child) => child.localName === name)
  if (matches.length > 1) {
    throw new Error(`${parent.localName} must declare at most one ${name} element`)
  }
  return matches[0]
}

function appendBpmCallProperties(element: Element, properties: TbbpmNode['properties']): void {
  properties.code = requireNonBlankAttribute(element, 'code')
  const hasResource = element.hasAttribute('classpath')
  const hasVersion = element.hasAttribute('version')
  if (hasResource === hasVersion) {
    throw new Error('bpmCall must declare exactly one of classpath or version')
  }
  if (hasResource) properties.classpath = requireNonBlankAttribute(element, 'classpath')
  if (hasVersion) properties.version = requireNonBlankAttribute(element, 'version')

  const callMappings = Array.from(element.querySelectorAll(':scope > input, :scope > output')).map(
    parseMappedTbbpmVar
  )
  if (callMappings.length > 0) properties.callMappings = callMappings
}

function appendForEachProperties(element: Element, properties: TbbpmNode['properties']): void {
  const execution = element.getAttribute('execution')
  if (execution !== null) {
    if (execution !== 'sequential' && execution !== 'parallel') {
      throw new Error('foreach execution must be sequential or parallel')
    }
    properties.execution = execution
  }
  properties.collection = requireNonBlankAttribute(element, 'collection')
  properties.item = requireNonBlankAttribute(element, 'item')
  properties.itemType = requireNonBlankAttribute(element, 'itemType')
  properties.index = optionalNonBlankAttribute(element, 'index')
  const output = optionalDirectChild(element, 'output')
  if (output) {
    if (element.firstElementChild !== output) {
      throw new Error('foreach output must precede transitions and body nodes')
    }
    requireOnlyAttributes(output, ['target', 'source'])
    properties.output = {
      target: requireNonBlankAttribute(output, 'target'),
      source: requireNonBlankAttribute(output, 'source'),
    }
  }
}

function appendWhileProperties(element: Element, properties: TbbpmNode['properties']): void {
  properties.condition = normalizeJavaConditionExpression(
    requireNonBlankAttribute(element, 'condition')
  )
  properties.index = optionalNonBlankAttribute(element, 'index')
  const declaredLimit = requireNonBlankAttribute(element, 'maxIterations').trim()
  const maxIterations = /^\+?\d+$/.test(declaredLimit) ? Number(declaredLimit) : Number.NaN
  if (!isValidLoopIterationLimit(maxIterations)) {
    throw new Error(`while maxIterations must be between 1 and ${MAX_LOOP_ITERATIONS}`)
  }
  properties.maxIterations = maxIterations
}

function appendNoteProperties(element: Element, properties: TbbpmNode['properties']): void {
  rejectRemovedAttribute(element, 'visible')
  properties.comment = element.getAttribute('comment') || undefined
}

function rejectRemovedAttribute(element: Element, attribute: string): void {
  if (element.hasAttribute(attribute)) {
    throw new Error(`${element.tagName} attribute "${attribute}" is not part of the TBBPM contract`)
  }
}

function requireNonBlankAttribute(element: Element, attribute: string): string {
  const value = element.getAttribute(attribute)
  if (value === null || value.trim() === '') {
    throw new Error(`${element.localName} must declare a non-blank ${attribute} attribute`)
  }
  return value
}

function optionalNonBlankAttribute(element: Element, attribute: string): string | undefined {
  return element.hasAttribute(attribute) ? requireNonBlankAttribute(element, attribute) : undefined
}

function requireOnlyAttributes(element: Element, allowed: readonly string[]): void {
  const allowedSet = new Set(allowed)
  Array.from(element.attributes).forEach((attribute) => {
    if (!allowedSet.has(attribute.name)) {
      throw new Error(
        `${element.localName} attribute "${attribute.name}" is not part of the TBBPM contract`
      )
    }
  })
}

function validateNodeAttributes(element: Element, type: TbbpmNodeType): void {
  const common = ['id', 'name', 'description', 'g']
  switch (type) {
    case 'waitTask':
      requireOnlyAttributes(element, [...common, 'timeout'])
      break
    case 'waitEventTask':
      requireOnlyAttributes(element, [...common, 'event', 'timeout'])
      break
    case 'timerTask':
      requireOnlyAttributes(element, [
        ...common,
        'duration',
        'durationExpression',
        'wakeAtExpression',
      ])
      break
    case 'bpmCall':
      requireOnlyAttributes(element, [...common, 'code', 'classpath', 'version'])
      break
    case 'subBpm':
      requireOnlyAttributes(element, common)
      break
    case 'while':
      requireOnlyAttributes(element, [...common, 'condition', 'index', 'maxIterations'])
      break
    case 'foreach':
      requireOnlyAttributes(element, [
        ...common,
        'execution',
        'collection',
        'item',
        'index',
        'itemType',
      ])
      break
    case 'break':
    case 'continue':
      requireOnlyAttributes(element, [...common, 'condition'])
      break
    case 'note':
      requireOnlyAttributes(element, ['id', 'name', 'description', 'comment', 'g'])
      break
    default:
      requireOnlyAttributes(element, common)
      break
  }
}

function validateNodeChildren(element: Element, type: TbbpmNodeType): void {
  const allowed = TBBPM_NODE_CHILD_TYPES[type]
  Array.from(element.children).forEach((child) => {
    if (!allowed.has(child.localName)) {
      throw new Error(`${element.localName} contains unsupported child "${child.localName}"`)
    }
  })
}

function parseAction(actionEl: Element): ActionDefinition {
  return parseActionElement(actionEl)
}

function parseMappedTbbpmVar(el: Element): VariableMapping {
  return parseMappedVariableElement(el, undefined, true)
}

function requireUniqueProcessVariableNames(
  variables: ReadonlyArray<Pick<TbbpmVar, 'name'>>
): Set<string> {
  const names = new Set<string>()
  variables.forEach((variable) => {
    if (names.has(variable.name)) {
      throw new Error(`TBBPM process variable names must be unique: ${variable.name}`)
    }
    names.add(variable.name)
  })
  return names
}

function validateTbbpmVariableMappings(
  nodes: TbbpmNode[],
  processVariableNames: ReadonlySet<string>
): void {
  nodes.forEach((node) => {
    const properties = node.properties
    if (node.type === 'autoTask' || node.type === 'scriptTask') {
      validateMappedVariables(
        `${node.type} ${node.id} action`,
        properties.action?.mappings,
        processVariableNames,
        false,
        true,
        true
      )
    }
    if (node.type === 'bpmCall') {
      validateMappedVariables(
        `bpmCall ${node.id}`,
        properties.callMappings,
        processVariableNames,
        true,
        false,
        false
      )
    }
  })
}

function validateMappedVariables(
  location: string,
  variables: VariableMapping[] | undefined,
  processVariableNames: ReadonlySet<string>,
  processCall: boolean,
  singleOutput: boolean,
  outputDataTypeRequired: boolean
): void {
  if (!variables) return
  const state: MappingValidationState = {
    inputTargets: new Set(),
    outputSources: new Set(),
    outputTargets: new Set(),
  }
  let outputCount = 0
  for (const variable of variables) {
    if (variable.direction === 'input') {
      validateInputMapping(location, variable, processCall, state)
      continue
    }
    outputCount += 1
    validateOutputMapping(
      location,
      variable,
      processVariableNames,
      processCall,
      outputDataTypeRequired,
      state
    )
  }

  if (singleOutput && outputCount > 1) {
    throw new Error(`${location} must declare at most one output mapping`)
  }
}

interface MappingValidationState {
  inputTargets: Set<string>
  outputSources: Set<string>
  outputTargets: Set<string>
}

function validateInputMapping(
  location: string,
  mapping: Extract<VariableMapping, { direction: 'input' }>,
  processCall: boolean,
  state: MappingValidationState
): void {
  requireGeneratedJavaIdentifier(mapping.target, `${location} input target`)
  if (state.inputTargets.has(mapping.target)) {
    throw new Error(`${location} has duplicate input target: ${mapping.target}`)
  }
  state.inputTargets.add(mapping.target)
  if (processCall) {
    if (mapping.dataType !== undefined) {
      throw new Error(`${location} called-process input must not declare dataType`)
    }
  } else {
    if (!mapping.dataType?.trim()) throw new Error(`${location} input dataType must not be blank`)
    if (mapping.dataType !== mapping.dataType.trim()) {
      throw new Error(`${location} input dataType must not contain surrounding whitespace`)
    }
  }
  if ((mapping.source === undefined) === (mapping.defaultValue === undefined)) {
    throw new Error(`${location} input must declare exactly one of source or defaultValue`)
  }
  if (mapping.source !== undefined && !mapping.source.trim()) {
    throw new Error(`${location} input source must not be blank`)
  }
}

function validateOutputMapping(
  location: string,
  mapping: Extract<VariableMapping, { direction: 'output' }>,
  processVariableNames: ReadonlySet<string>,
  processCall: boolean,
  outputDataTypeRequired: boolean,
  state: MappingValidationState
): void {
  const target = mapping.target
  if (!target) throw new Error(`${location} output mapping must declare target`)
  if (target !== target.trim()) {
    throw new Error(`${location} output target must not contain surrounding whitespace`)
  }
  if (!processVariableNames.has(target)) {
    throw new Error(
      `${location} output target must reference a declared process variable: ${target}`
    )
  }
  if (state.outputTargets.has(target)) {
    throw new Error(`${location} has duplicate output target: ${target}`)
  }
  state.outputTargets.add(target)
  if (processCall) {
    validateProcessCallOutput(location, mapping, state.outputSources)
  } else {
    validateActionOutput(location, mapping, outputDataTypeRequired)
  }
}

function validateProcessCallOutput(
  location: string,
  mapping: Extract<VariableMapping, { direction: 'output' }>,
  outputSources: Set<string>
): void {
  requireGeneratedJavaIdentifier(mapping.source || '', `${location} output source`)
  if (outputSources.has(mapping.source!)) {
    throw new Error(`${location} has duplicate output source: ${mapping.source}`)
  }
  outputSources.add(mapping.source!)
  if (mapping.dataType !== undefined) {
    throw new Error(`${location} called-process output must not declare dataType`)
  }
}

function validateActionOutput(
  location: string,
  mapping: Extract<VariableMapping, { direction: 'output' }>,
  outputDataTypeRequired: boolean
): void {
  if (mapping.source !== undefined) {
    throw new Error(`${location} action output must not declare source`)
  }
  if (outputDataTypeRequired && !mapping.dataType?.trim()) {
    throw new Error(`${location} output dataType must not be blank`)
  }
  if (mapping.dataType !== undefined && mapping.dataType !== mapping.dataType.trim()) {
    throw new Error(`${location} output dataType must not contain surrounding whitespace`)
  }
}

function parseTbbpmTransition(trans: Element, sourceId: string, index: number): TbbpmConnection {
  requireOnlyAttributes(trans, ['name', 'to', 'g', 'condition'])
  const to = requireNonBlankAttribute(trans, 'to')

  return {
    id: `transition_${sourceId}_to_${to}_${index}`,
    sourceId,
    targetId: to,
    name: trans.getAttribute('name') || undefined,
    condition: normalizeOptionalJavaCondition(trans.getAttribute('condition')),
    g: trans.getAttribute('g') || undefined,
    from: sourceId,
    to,
  }
}

function validateNodeReferences(
  nodes: TbbpmNode[],
  connections: TbbpmConnection[],
  warnings: ParseWarning[]
): void {
  const nodesById = new Map(nodes.map((node) => [node.id, node]))
  connections.forEach((conn, index) => {
    const source = nodesById.get(conn.sourceId)
    const target = nodesById.get(conn.targetId)
    if (!source) {
      warnings.push({
        code: 'INVALID_SOURCE_REF',
        message: `Connection ${index}: source "${conn.sourceId}" not found`,
        location: `Connection ${index}`,
      })
    }
    if (!target) {
      warnings.push({
        code: 'INVALID_TARGET_REF',
        message: `Connection ${index}: target "${conn.targetId}" not found`,
        location: `Connection ${index}`,
      })
    }
    if (source && target && source.parentId !== target.parentId) {
      warnings.push({
        code: 'CROSS_CONTAINER_REF',
        message: `Connection ${index}: transition crosses a container boundary`,
        location: `Connection ${index}`,
      })
    }
  })
}

// ==================== XML 生成 ====================

interface TbbpmGenerationContext {
  indentUnit: string
  childrenByParent: Map<string | undefined, TbbpmNode[]>
  connectionsBySource: Map<string, TbbpmConnection[]>
}

const TRANSITION_ONLY_CHILDREN: ReadonlySet<string> = new Set(['transition'])
const TBBPM_NODE_CHILD_TYPES: Readonly<Record<TbbpmNodeType, ReadonlySet<string>>> = {
  start: TRANSITION_ONLY_CHILDREN,
  end: new Set(),
  autoTask: new Set(['transition', 'action']),
  waitTask: TRANSITION_ONLY_CHILDREN,
  waitEventTask: TRANSITION_ONLY_CHILDREN,
  timerTask: TRANSITION_ONLY_CHILDREN,
  scriptTask: new Set(['transition', 'action']),
  exclusive: TRANSITION_ONLY_CHILDREN,
  parallel: TRANSITION_ONLY_CHILDREN,
  inclusive: TRANSITION_ONLY_CHILDREN,
  subBpm: new Set(['transition', ...TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES]),
  bpmCall: new Set(['transition', 'input', 'output']),
  while: new Set(['transition', ...TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES]),
  foreach: new Set(['transition', 'output', ...TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES]),
  continue: TRANSITION_ONLY_CHILDREN,
  break: TRANSITION_ONLY_CHILDREN,
  note: new Set(),
}

export function generateTbbpmXml(
  definition: TbbpmProcessDefinition,
  options: GenerateOptions = {}
): string {
  const { indent = '  ', includeDeclaration = true, encoding = 'UTF-8' } = options
  const lines: string[] = []
  const { connections, nodes } = definition
  const vars = definition.variables || []
  const processVariableNames = validateGeneratedProcessVariables(vars)
  validateTbbpmVariableMappings(nodes, processVariableNames)
  validateLoopVariableContracts(
    nodes,
    processVariableNames,
    new Map(vars.map((variable) => [variable.name, variable]))
  )
  const context = createTbbpmGenerationContext(nodes, connections, indent)

  if (includeDeclaration) {
    lines.push(`<?xml version="1.0" encoding="${encoding}"?>`)
  }

  const code = definition.code || definition.id
  const name = definition.name

  const bpmAttrs = [`code="${escapeXml(code)}"`, `name="${escapeXml(name)}"`]
  if (definition.description) bpmAttrs.push(`description="${escapeXml(definition.description)}"`)
  lines.push(`<bpm ${bpmAttrs.join(' ')}>`)

  // 生成流程级 var
  vars.forEach((v) => {
    const attrs = [
      `name="${escapeXml(v.name)}"`,
      `dataType="${escapeXml(v.type)}"`,
      `inOutType="${v.inOutType}"`,
    ]
    if (v.description) attrs.push(`description="${escapeXml(v.description)}"`)
    if (v.defaultValue !== undefined) {
      attrs.push(`defaultValue="${escapeXml(String(v.defaultValue))}"`)
    }
    lines.push(`${indent}<var ${attrs.join(' ')}/>`)
  })

  const rootNodes = context.childrenByParent.get(undefined) || []
  rootNodes.forEach((node) => {
    lines.push(generateTbbpmNodeXml(node, indent, context))
  })

  lines.push('</bpm>')
  return lines.join('\n')
}

function validateGeneratedProcessVariables(variables: ProcessVariable[]): Set<string> {
  variables.forEach((variable) => {
    if (!variable.name.trim()) {
      throw new Error('TBBPM process variable name must not be blank')
    }
    if (variable.name !== variable.name.trim()) {
      throw new Error('TBBPM process variable name must not contain surrounding whitespace')
    }
    requireGeneratedJavaIdentifier(variable.name, 'TBBPM process variable name')
    if (!variable.type.trim()) {
      throw new Error(`TBBPM process variable dataType must not be blank: ${variable.name}`)
    }
    if (variable.type !== variable.type.trim()) {
      throw new Error(
        `TBBPM process variable dataType must not contain surrounding whitespace: ${variable.name}`
      )
    }
    if (
      variable.inOutType !== 'param' &&
      variable.inOutType !== 'return' &&
      variable.inOutType !== 'inner'
    ) {
      throw new Error(
        `TBBPM process variable inOutType must be param, return, or inner: ${variable.name}`
      )
    }
  })
  return requireUniqueProcessVariableNames(variables)
}

function createTbbpmGenerationContext(
  nodes: TbbpmNode[],
  connections: TbbpmConnection[],
  indentUnit: string
): TbbpmGenerationContext {
  const nodeById = new Map<string, TbbpmNode>()
  const childrenByParent = new Map<string | undefined, TbbpmNode[]>()
  const connectionsBySource = new Map<string, TbbpmConnection[]>()

  nodes.forEach((node) => {
    const inapplicableProperties = findInapplicableTbbpmNodeProperties(node)
    if (inapplicableProperties.length > 0) {
      throw new Error(
        `TBBPM node ${node.id} of type ${node.type} contains inapplicable properties: ` +
          inapplicableProperties.join(', ')
      )
    }
    if (nodeById.has(node.id)) {
      throw new Error(`TBBPM node ids must be globally unique: ${node.id}`)
    }
    nodeById.set(node.id, node)
    const siblings = childrenByParent.get(node.parentId) || []
    siblings.push(node)
    childrenByParent.set(node.parentId, siblings)
  })

  nodes.forEach((node) => {
    validateTbbpmNodeParent(node, nodeById)
    if (node.parentId) {
      const parent = nodeById.get(node.parentId)!
      if (!getTbbpmChildNodeTypes(parent.type)?.has(node.type)) {
        throw new Error(`Node type ${node.type} is not allowed inside ${parent.type} ${parent.id}`)
      }
    } else if (!TBBPM_ROOT_NODE_TYPES.has(node.type)) {
      throw new Error(`Node type ${node.type} is not allowed at the process root`)
    }
  })

  connections.forEach((connection) => {
    const source = nodeById.get(connection.sourceId)
    const target = nodeById.get(connection.targetId)
    if (!source || !target) {
      throw new Error(
        `Transition ${connection.id} references an unknown node: ` +
          `${connection.sourceId} -> ${connection.targetId}`
      )
    }
    if (source.type === 'note' || target.type === 'note') {
      throw new Error(`TBBPM note nodes cannot participate in transitions: ${connection.id}`)
    }
    if (source.parentId !== target.parentId) {
      throw new Error(
        `Transition ${connection.id} crosses a TBBPM container boundary: ` +
          `${connection.sourceId} -> ${connection.targetId}`
      )
    }
    const outgoing = connectionsBySource.get(connection.sourceId) || []
    outgoing.push(connection)
    connectionsBySource.set(connection.sourceId, outgoing)
  })

  nodes
    .filter((node) => node.type === 'while' || node.type === 'foreach')
    .forEach((node) =>
      validateLoopBody(node, childrenByParent.get(node.id) || [], connectionsBySource)
    )

  nodes
    .filter((node) => node.type === 'subBpm')
    .forEach((node) =>
      validateSubBpmBody(node, childrenByParent.get(node.id) || [], connectionsBySource)
    )

  return { indentUnit, childrenByParent, connectionsBySource }
}

function validateTbbpmNodeParent(node: TbbpmNode, nodeById: Map<string, TbbpmNode>): void {
  const visited = new Set([node.id])
  let parentId = node.parentId
  while (parentId) {
    if (visited.has(parentId)) {
      throw new Error(`TBBPM container hierarchy contains a cycle at node ${node.id}`)
    }
    visited.add(parentId)
    const parent = nodeById.get(parentId)
    if (!parent) {
      throw new Error(`Node ${node.id} references unknown parent ${parentId}`)
    }
    if (!getTbbpmChildNodeTypes(parent.type)) {
      throw new Error(`Node ${node.id} parent ${parentId} is not a TBBPM container`)
    }
    parentId = parent.parentId
  }
}

function validateSubBpmBody(
  subBpm: TbbpmNode,
  children: TbbpmNode[],
  connectionsBySource: ReadonlyMap<string, readonly TbbpmConnection[]>
): void {
  const starts = children.filter((child) => child.type === 'start')
  const ends = children.filter((child) => child.type === 'end')
  if (starts.length !== 1 || ends.length !== 1) {
    throw new Error(`subBpm ${subBpm.id} must contain exactly one start and one end node`)
  }
  const endNodeId = ends[0].id
  if ((connectionsBySource.get(endNodeId) || []).length > 0) {
    throw new Error(`subBpm ${subBpm.id} end node must not have outgoing transitions`)
  }
  const reachable = collectReachableNodes(starts[0].id, connectionsBySource)
  const unreachable = children
    .filter((child) => child.type !== 'note' && !reachable.has(child.id))
    .map((child) => child.id)
  if (unreachable.length > 0) {
    throw new Error(
      `subBpm ${subBpm.id} contains unreachable child nodes: ${unreachable.join(', ')}`
    )
  }
}

function collectReachableNodes(
  startNodeId: string,
  connectionsBySource: ReadonlyMap<string, readonly TbbpmConnection[]>
): Set<string> {
  const reachable = new Set<string>()
  const pending = [startNodeId]
  while (pending.length > 0) {
    const currentId = pending.shift()!
    if (!reachable.add(currentId)) continue
    ;(connectionsBySource.get(currentId) || []).forEach((connection) =>
      pending.push(connection.targetId)
    )
  }
  return reachable
}

function validateLoopBody(
  loop: TbbpmNode,
  children: TbbpmNode[],
  connectionsBySource: ReadonlyMap<string, readonly TbbpmConnection[]>
): void {
  if (children.length === 0) {
    throw new Error(`${loop.type} ${loop.id} must contain a body`)
  }
  const executableChildren = children.filter((child) => child.type !== 'note')
  const starts = executableChildren.filter((child) => child.type === 'start')
  const ends = executableChildren.filter((child) => child.type === 'end')
  if (starts.length !== 1 || ends.length !== 1) {
    throw new Error(`${loop.type} ${loop.id} must contain exactly one start and one end node`)
  }
  const startNodeId = starts[0].id
  const endNodeId = ends[0].id

  if ((connectionsBySource.get(endNodeId) || []).length > 0) {
    throw new Error(`${loop.type} ${loop.id} end node must not have outgoing transitions`)
  }

  const reachable = collectReachableLoopNodes(
    startNodeId,
    endNodeId,
    new Map(executableChildren.map((child) => [child.id, child])),
    connectionsBySource
  )
  const unreachableIds = executableChildren
    .map((child) => child.id)
    .filter((childId) => !reachable.has(childId))
    .sort()
  if (unreachableIds.length > 0) {
    throw new Error(
      `${loop.type} ${loop.id} contains child nodes unreachable from its start node: ` +
        unreachableIds.join(', ')
    )
  }
}

function collectReachableLoopNodes(
  startNodeId: string,
  endNodeId: string,
  childrenById: ReadonlyMap<string, TbbpmNode>,
  connectionsBySource: ReadonlyMap<string, readonly TbbpmConnection[]>
): Set<string> {
  const reachable = new Set<string>()
  const pending = [startNodeId]
  while (pending.length > 0) {
    const currentId = pending.shift()!
    if (!reachable.add(currentId)) continue

    const outgoing = connectionsBySource.get(currentId) || []
    const current = childrenById.get(currentId)
    if (isImplicitLoopExit(current, currentId, endNodeId, outgoing)) {
      pending.push(endNodeId)
    }
    outgoing.forEach((connection) => pending.push(connection.targetId))
  }
  return reachable
}

function isImplicitLoopExit(
  node: TbbpmNode | undefined,
  nodeId: string,
  endNodeId: string,
  outgoing: readonly TbbpmConnection[]
): boolean {
  return (
    outgoing.length === 0 &&
    nodeId !== endNodeId &&
    (node?.type === 'break' || node?.type === 'continue')
  )
}

function validateLoopVariableContracts(
  nodes: TbbpmNode[],
  processVariableNames: ReadonlySet<string>,
  processVariables: ReadonlyMap<string, ProcessVariable>
): void {
  const nodeById = new Map(nodes.map((node) => [node.id, node]))
  nodes
    .filter((node) => node.type === 'while' || node.type === 'foreach')
    .forEach((loop) =>
      validateLoopVariableContract(loop, nodeById, processVariableNames, processVariables)
    )
}

function validateLoopVariableContract(
  loop: TbbpmNode,
  nodeById: ReadonlyMap<string, TbbpmNode>,
  processVariableNames: ReadonlySet<string>,
  processVariables: ReadonlyMap<string, ProcessVariable>
): void {
  const visibleVariables = collectVisibleLoopVariables(loop, nodeById)
  if (loop.type === 'foreach') {
    validateForEachVariables(loop, processVariableNames, processVariables, visibleVariables)
    return
  }
  if (loop.type === 'while') {
    validateWhileVariables(loop, processVariableNames, visibleVariables)
    return
  }
  throw new Error(`Node ${loop.id} is not a loop`)
}

function collectVisibleLoopVariables(
  loop: TbbpmNode,
  nodeById: ReadonlyMap<string, TbbpmNode>
): Set<string> {
  const visibleVariables = new Set<string>()
  let parentId = loop.parentId
  while (parentId) {
    const parent = nodeById.get(parentId)
    if (!parent) break
    if (parent.type === 'foreach') {
      const variable = optionalProperty(parent.properties.item)
      if (variable) visibleVariables.add(variable)
      const index = optionalProperty(parent.properties.index)
      if (index) visibleVariables.add(index)
    } else if (parent.type === 'while') {
      const iteration = optionalProperty(parent.properties.index)
      if (iteration) visibleVariables.add(iteration)
    }
    parentId = parent.parentId
  }
  return visibleVariables
}

function validateForEachVariables(
  loop: TbbpmNode,
  processVariableNames: ReadonlySet<string>,
  processVariables: ReadonlyMap<string, ProcessVariable>,
  visibleVariables: ReadonlySet<string>
): void {
  const properties = loop.properties
  const collection = requiredLoopProperty(loop.id, 'collection', properties.collection)
  const variable = requiredLoopProperty(loop.id, 'item', properties.item)
  const index = validateOptionalLoopIndex(loop, processVariableNames, visibleVariables, 'variable')
  requireGeneratedJavaIdentifier(collection, 'collection')
  requireGeneratedJavaIdentifier(variable, 'item')
  if (!processVariableNames.has(collection) && !visibleVariables.has(collection)) {
    throw new Error(
      `foreach ${loop.id} collection must reference a declared process variable or enclosing loop variable: ${collection}`
    )
  }
  validateLoopVariableShadowing(loop, variable, processVariableNames, visibleVariables, 'variable')
  if (variable === index) {
    throw new Error(`foreach ${loop.id} item and index must be different`)
  }
  validateLoopVariableClass(loop)
  const execution = properties.execution ?? 'sequential'
  if (execution !== 'sequential' && execution !== 'parallel') {
    throw new Error(`foreach ${loop.id} execution must be sequential or parallel`)
  }
  validateForEachOutput(loop, processVariableNames, processVariables)
}

function validateWhileVariables(
  loop: TbbpmNode,
  processVariableNames: ReadonlySet<string>,
  visibleVariables: ReadonlySet<string>
): void {
  requiredLoopProperty(loop.id, 'condition', loop.properties.condition)
  if (!isValidLoopIterationLimit(loop.properties.maxIterations)) {
    throw new Error(`while ${loop.id} maxIterations must be between 1 and ${MAX_LOOP_ITERATIONS}`)
  }
  const iteration = optionalProperty(loop.properties.index)
  if (iteration) {
    requireGeneratedJavaIdentifier(iteration, 'index')
    validateLoopVariableShadowing(loop, iteration, processVariableNames, visibleVariables, 'index')
  }
}

function validateForEachOutput(
  loop: TbbpmNode,
  processVariableNames: ReadonlySet<string>,
  processVariables: ReadonlyMap<string, ProcessVariable>
): void {
  if (!loop.properties.output) return
  const target = optionalProperty(loop.properties.output.target)
  const source = optionalProperty(loop.properties.output.source)
  if (!target || !source) throw new Error(`foreach ${loop.id} output is incomplete`)
  if (!processVariableNames.has(target) || !processVariableNames.has(source)) {
    throw new Error(`foreach ${loop.id} output variables must reference declared process variables`)
  }
  if (target === source) {
    throw new Error(`foreach ${loop.id} output source and target variables must be different`)
  }
  if (processVariables.get(source)?.inOutType !== 'inner') {
    throw new Error(`foreach ${loop.id} output source must be inner: ${source}`)
  }
}

function validateOptionalLoopIndex(
  loop: TbbpmNode,
  processVariableNames: ReadonlySet<string>,
  visibleVariables: ReadonlySet<string>,
  shadowProperty: string
): string | undefined {
  const index = optionalProperty(loop.properties.index)
  if (!index) return undefined
  requireGeneratedJavaIdentifier(index, 'index')
  validateLoopVariableShadowing(loop, index, processVariableNames, visibleVariables, shadowProperty)
  return index
}

function validateLoopVariableShadowing(
  loop: TbbpmNode,
  variable: string,
  processVariableNames: ReadonlySet<string>,
  visibleVariables: ReadonlySet<string>,
  property: string
): void {
  if (processVariableNames.has(variable) || visibleVariables.has(variable)) {
    throw new Error(
      `${loop.type} ${loop.id} ${property} must not shadow process state or an enclosing loop variable: ${variable}`
    )
  }
}

function validateLoopVariableClass(loop: TbbpmNode): void {
  const itemType = optionalProperty(loop.properties.itemType)
  if (!itemType) {
    throw new Error(`foreach ${loop.id} itemType must not be blank`)
  }
  if (itemType && !isJavaClassName(itemType)) {
    throw new Error(`foreach ${loop.id} itemType must be a valid Java class name: ${itemType}`)
  }
}

function requiredLoopProperty(loopId: string, property: string, value: unknown): string {
  const normalized = optionalProperty(value)
  if (!normalized) {
    throw new Error(`Loop ${loopId} must declare ${property}`)
  }
  return normalized
}

function optionalProperty(value: unknown): string | undefined {
  if (typeof value !== 'string') return undefined
  const normalized = value.trim()
  return normalized || undefined
}

type TbbpmNodeXmlGenerator = (
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
) => string

const TBBPM_NODE_XML_GENERATORS: Partial<Record<TbbpmNodeType, TbbpmNodeXmlGenerator>> = {
  autoTask: generateActionNodeXml,
  scriptTask: generateActionNodeXml,
  waitTask: generateWaitNodeXml,
  waitEventTask: generateWaitNodeXml,
  timerTask: generateTimerTaskXml,
  bpmCall: generateBpmCallXml,
  subBpm: generateSubBpmXml,
  while: generateLoopXml,
  foreach: generateLoopXml,
  note: generateNoteXml,
}

function generateTbbpmNodeXml(
  node: TbbpmNode,
  indent: string,
  context: TbbpmGenerationContext
): string {
  const geometry = `${node.position.x},${node.position.y},${node.size?.width || 100},${node.size?.height || 80}`
  const connections = context.connectionsBySource.get(node.id) || []
  const generator = TBBPM_NODE_XML_GENERATORS[node.type] ?? generateSimpleNodeXml
  return generator(node, geometry, connections, indent, context)
}

function generateSubBpmXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const childIndent = indent + context.indentUnit
  const lines = [`${indent}<subBpm ${tbbpmNodeAttributes(node, geometry).join(' ')}>`]
  appendTransitions(lines, connections, childIndent)
  ;(context.childrenByParent.get(node.id) || []).forEach((child) => {
    lines.push(generateTbbpmNodeXml(child, childIndent, context))
  })
  lines.push(`${indent}</subBpm>`)
  return lines.join('\n')
}

function generateActionNodeXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const lines: string[] = []
  const childIndent = indent + context.indentUnit
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)

  lines.push(`${indent}<${node.type} ${nodeAttrs.join(' ')}>`)
  appendTransitions(lines, connections, childIndent)

  if (properties.action) {
    lines.push(
      generateActionElementXml('action', properties.action, childIndent, context.indentUnit)
    )
  }

  lines.push(`${indent}</${node.type}>`)
  return lines.join('\n')
}

function generateWaitNodeXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const lines: string[] = []
  const childIndent = indent + context.indentUnit
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  if (node.type === 'waitEventTask' && properties.event) {
    nodeAttrs.push(`event="${escapeXml(properties.event)}"`)
  }
  if (properties.timeout) nodeAttrs.push(`timeout="${escapeXml(properties.timeout)}"`)

  lines.push(`${indent}<${node.type} ${nodeAttrs.join(' ')}>`)
  appendTransitions(lines, connections, childIndent)

  lines.push(`${indent}</${node.type}>`)
  return lines.join('\n')
}

function generateTimerTaskXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const scheduleAttributes = [
    ['duration', properties.duration],
    ['durationExpression', properties.durationExpression],
    ['wakeAtExpression', properties.wakeAtExpression],
  ] as const
  const configured = scheduleAttributes.flatMap(([attribute, value]) =>
    value?.trim() ? [{ attribute, value }] : []
  )
  if (configured.length !== 1) {
    throw new Error(
      `timerTask ${node.id} must declare exactly one of duration, durationExpression, or wakeAtExpression`
    )
  }

  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  const schedule = configured[0]
  nodeAttrs.push(`${schedule.attribute}="${escapeXml(schedule.value)}"`)
  const childIndent = indent + context.indentUnit
  const lines = [`${indent}<timerTask ${nodeAttrs.join(' ')}>`]
  appendTransitions(lines, connections, childIndent)
  lines.push(`${indent}</timerTask>`)
  return lines.join('\n')
}

function generateBpmCallXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const childIndent = indent + context.indentUnit
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  if (properties.code) nodeAttrs.push(`code="${escapeXml(properties.code)}"`)
  if (Boolean(properties.classpath) === Boolean(properties.version)) {
    throw new Error(`bpmCall ${node.id} must declare exactly one of classpath or version`)
  }
  if (properties.classpath) nodeAttrs.push(`classpath="${escapeXml(properties.classpath)}"`)
  if (properties.version) nodeAttrs.push(`version="${escapeXml(properties.version)}"`)
  const variables = properties.callMappings || []
  const hasChildren = connections.length > 0 || variables.length > 0

  if (!hasChildren) {
    return `${indent}<bpmCall ${nodeAttrs.join(' ')}/>`
  }

  const lines = [`${indent}<bpmCall ${nodeAttrs.join(' ')}>`]
  appendTransitions(lines, connections, childIndent)
  variables.forEach((mapping) => {
    lines.push(generateMappedVariableXml(mapping, childIndent, undefined, true))
  })
  lines.push(`${indent}</bpmCall>`)
  return lines.join('\n')
}

function generateLoopXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const childIndent = indent + context.indentUnit
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  if (node.type === 'while') {
    if (properties.condition) {
      nodeAttrs.push(
        `condition="${escapeXml(normalizeJavaConditionExpression(properties.condition))}"`
      )
    }
    if (properties.index) {
      nodeAttrs.push(`index="${escapeXml(properties.index)}"`)
    }
    if (properties.maxIterations !== undefined) {
      nodeAttrs.push(`maxIterations="${properties.maxIterations}"`)
    }
  } else {
    if (properties.execution === 'parallel') nodeAttrs.push('execution="parallel"')
    if (properties.collection) {
      nodeAttrs.push(`collection="${escapeXml(properties.collection)}"`)
    }
    if (properties.item) nodeAttrs.push(`item="${escapeXml(properties.item)}"`)
    if (properties.itemType) {
      nodeAttrs.push(`itemType="${escapeXml(properties.itemType)}"`)
    }
    if (properties.index) nodeAttrs.push(`index="${escapeXml(properties.index)}"`)
  }
  const lines = [`${indent}<${node.type} ${nodeAttrs.join(' ')}>`]
  if (node.type === 'foreach' && properties.output) {
    lines.push(
      `${childIndent}<output target="${escapeXml(properties.output.target)}" source="${escapeXml(properties.output.source)}"/>`
    )
  }
  appendTransitions(lines, connections, childIndent)
  const childNodes = context.childrenByParent.get(node.id) || []
  childNodes.forEach((child) => {
    lines.push(generateTbbpmNodeXml(child, childIndent, context))
  })
  lines.push(`${indent}</${node.type}>`)
  return lines.join('\n')
}

function generateNoteXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  // The engine's AbstractTbbpmNodeWriter always writes name; keep it for round-trip fidelity.
  // For note nodes the canonical display text lives in comment; name mirrors it.
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  if (properties.comment) nodeAttrs.push(`comment="${escapeXml(properties.comment)}"`)
  if (connections.length === 0) {
    return `${indent}<note ${nodeAttrs.join(' ')}/>`
  }
  const lines = [`${indent}<note ${nodeAttrs.join(' ')}>`]
  appendTransitions(lines, connections, indent + context.indentUnit)
  lines.push(`${indent}</note>`)
  return lines.join('\n')
}

function generateSimpleNodeXml(
  node: TbbpmNode,
  geometry: string,
  connections: TbbpmConnection[],
  indent: string,
  context: TbbpmGenerationContext
): string {
  const properties = node.properties
  const nodeAttrs = tbbpmNodeAttributes(node, geometry)
  if ((node.type === 'break' || node.type === 'continue') && properties.condition) {
    nodeAttrs.push(
      `condition="${escapeXml(normalizeJavaConditionExpression(properties.condition))}"`
    )
  }
  if (connections.length === 0) {
    return `${indent}<${node.type} ${nodeAttrs.join(' ')}/>`
  }
  const lines = [`${indent}<${node.type} ${nodeAttrs.join(' ')}>`]
  appendTransitions(lines, connections, indent + context.indentUnit)
  lines.push(`${indent}</${node.type}>`)
  return lines.join('\n')
}

function tbbpmNodeAttributes(node: TbbpmNode, geometry: string): string[] {
  const attributes = [
    `id="${escapeXml(node.id)}"`,
    `name="${escapeXml(node.name || node.id)}"`,
    `g="${geometry}"`,
  ]
  if (node.documentation) {
    attributes.push(`description="${escapeXml(node.documentation)}"`)
  }
  return attributes
}

function appendTransitions(lines: string[], connections: TbbpmConnection[], indent: string): void {
  connections.forEach((conn) => {
    const attrs = [`to="${escapeXml(conn.targetId)}"`]
    if (conn.name) attrs.push(`name="${escapeXml(conn.name)}"`)
    if (conn.condition) {
      attrs.push(`condition="${escapeXml(normalizeJavaConditionExpression(conn.condition))}"`)
    }
    if (conn.g) attrs.push(`g="${escapeXml(conn.g)}"`)
    lines.push(`${indent}<transition ${attrs.join(' ')}/>`)
  })
}

function normalizeOptionalJavaCondition(expression: string | null | undefined): string | undefined {
  if (expression == null) return undefined
  const normalized = normalizeJavaConditionExpression(expression)
  return normalized || undefined
}
