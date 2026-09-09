import { persistedEffectRequestFields } from '../types/action'
import { validateBpmnLoopCharacteristics } from '../types/bpmnLoop'
import type {
  LoopCharacteristics,
  MultiInstanceLoopCharacteristics,
  StandardLoopCharacteristics,
} from '../types/bpmnNodeTypes'
import type {
  BpmnConnection,
  BpmnMessageDefinition,
  BpmnNode,
  BpmnNodeType,
  BpmnProcessDefinition,
  ProcessVariable,
} from '../types/flowDefinition'
import { requireGeneratedJavaIdentifier } from '../types/javaIdentifiers'
import { findInapplicableBpmnNodeProperties } from '../types/nodePropertyContracts'
import { normalizeJavaConditionExpression } from '../validation/javaConditionExpression'

import {
  BPMN_ACTION_XML,
  generateActionElementXml,
  generateEffectPolicyXml,
  generateInvocationPolicyXml,
  generateMappedVariableXml,
  parseActionElement,
  parseEffectPolicyElement,
  parseInvocationPolicyElement,
  parseMappedVariableElement,
} from './actionXml'
import { readConnectionGeometry, writeConnectionGeometry } from './connectionGeometry'
import { escapeXml } from './xmlEscaping'
import { validateXmlInput } from './xmlInputValidation'
import type { GenerateOptions, ParseResult, ParseWarning } from './xmlTypes'

import { toError } from '@/shared/errors'

const BPMN_NS = 'http://www.omg.org/spec/BPMN/20100524/MODEL'
const BPMNDI_NS = 'http://www.omg.org/spec/BPMN/20100524/DI'
const DC_NS = 'http://www.omg.org/spec/DD/20100524/DC'
const DI_NS = 'http://www.omg.org/spec/DD/20100524/DI'
const CF_NS = 'http://www.compileflow.org'
const XSI_NS = 'http://www.w3.org/2001/XMLSchema-instance'
const XMLNS_NS = 'http://www.w3.org/2000/xmlns/'
const COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE = 'urn:compileflow:java'
const ROOT_CONTAINER = ''

const SUPPORTED_NODE_NAMES = [
  'startEvent',
  'endEvent',
  'serviceTask',
  'scriptTask',
  'receiveTask',
  'exclusiveGateway',
  'parallelGateway',
  'inclusiveGateway',
  'callActivity',
  'subProcess',
] as const

type SupportedNodeName = (typeof SUPPORTED_NODE_NAMES)[number]

const NODE_TYPE_BY_NAME: Record<SupportedNodeName, BpmnNodeType> = {
  startEvent: 'bpmn:StartEvent',
  endEvent: 'bpmn:EndEvent',
  serviceTask: 'bpmn:ServiceTask',
  scriptTask: 'bpmn:ScriptTask',
  receiveTask: 'bpmn:ReceiveTask',
  exclusiveGateway: 'bpmn:ExclusiveGateway',
  parallelGateway: 'bpmn:ParallelGateway',
  inclusiveGateway: 'bpmn:InclusiveGateway',
  callActivity: 'bpmn:CallActivity',
  subProcess: 'bpmn:SubProcess',
}

const NODE_NAME_BY_TYPE = Object.fromEntries(
  Object.entries(NODE_TYPE_BY_NAME).map(([name, type]) => [type, name])
) as Record<BpmnNodeType, SupportedNodeName>

const NODE_EXTENSION_NAMES: Partial<Record<BpmnNodeType, readonly string[]>> = {
  'bpmn:ServiceTask': ['action'],
  'bpmn:ScriptTask': ['input', 'output', 'invocationPolicy', 'effectPolicy'],
  'bpmn:CallActivity': ['input', 'output', 'processRef'],
  'bpmn:ReceiveTask': [],
}

interface NodeGeometry {
  x: number
  y: number
  width: number
  height: number
}

export function parseBpmnXml(xml: string): ParseResult<BpmnProcessDefinition> {
  const warnings: ParseWarning[] = []
  const validationResult = validateXmlInput(xml)
  if (!validationResult.success) {
    return {
      success: false,
      error: validationResult.error,
      warnings: validationResult.warnings,
    }
  }
  if (typeof validationResult.data !== 'string') {
    return failure('PARSE_ERROR', 'Validated BPMN XML is missing')
  }

  try {
    const document = new DOMParser().parseFromString(validationResult.data, 'text/xml')
    const parseError = document.querySelector('parsererror')
    if (parseError) {
      return failure('PARSE_ERROR', `Failed to parse XML: ${parseError.textContent || ''}`)
    }

    const definitions = document.documentElement
    if (definitions.localName !== 'definitions' || definitions.namespaceURI !== BPMN_NS) {
      return failure(
        'INVALID_ROOT',
        `Invalid BPMN XML: root element must be {${BPMN_NS}}definitions`
      )
    }
    requireOnlyAttributes(
      definitions,
      [
        'id',
        'targetNamespace',
        'exporter',
        'exporterVersion',
        'typeLanguage',
        'expressionLanguage',
      ],
      [{ namespace: XSI_NS, localName: 'schemaLocation' }]
    )
    validateDefinitionsChildren(definitions)

    const processes = directChildrenNamed(definitions, BPMN_NS, 'process')
    if (processes.length !== 1) {
      return failure(
        'INVALID_PROCESS_COUNT',
        `CompileFlow BPMN requires exactly one process, received ${processes.length}`
      )
    }
    const process = processes[0]
    validateProcessChildren(process)
    if (!xmlBooleanAttribute(process, 'isExecutable', false)) {
      throw new Error('BPMN process must declare isExecutable="true"')
    }

    const processId = requiredAttribute(process, 'id')
    const targetNamespace = requiredAttribute(definitions, 'targetNamespace')
    const geometry = buildGeometryMap(definitions)
    const waypoints = buildWaypointMap(definitions)
    const nodes = parseNodes(process, geometry)
    const connections = parseConnections(process, waypoints)
    const messages = parseMessages(definitions)
    validateUniqueIds(optionalAttribute(definitions, 'id'), processId, nodes, connections, messages)
    validateContainerHierarchy(nodes, new Map(nodes.map((node) => [node.id, node])))
    validateConnectionContainers(nodes, connections)

    const flow: BpmnProcessDefinition = {
      id: processId,
      code: processId,
      name: optionalAttribute(process, 'name') || processId,
      description: directChildText(process, BPMN_NS, 'documentation') || undefined,
      type: 'BPMN',
      namespace: targetNamespace,
      nodes,
      connections,
      variables: parseProcessVariables(process),
      messages,
      properties: { definitionAttributes: readDefinitionAttributes(definitions) },
    }
    return {
      success: true,
      data: flow,
      warnings: warnings.length > 0 ? warnings : undefined,
    }
  } catch (error) {
    return failure('UNSUPPORTED_BPMN', toError(error).message, error)
  }
}

const DEFINITION_METADATA = [
  'id',
  'exporter',
  'exporterVersion',
  'typeLanguage',
  'expressionLanguage',
  'xsi:schemaLocation',
] as const

function readDefinitionAttributes(definitions: Element): Record<string, string> {
  const attributes: Record<string, string> = {}
  for (const name of DEFINITION_METADATA) {
    const value =
      name === 'xsi:schemaLocation'
        ? definitions.getAttributeNS(XSI_NS, 'schemaLocation')
        : definitions.getAttribute(name)
    if (value !== null) attributes[name] = value
  }
  return attributes
}

function definitionAttributes(definition: BpmnProcessDefinition): Record<string, string> {
  const raw = definition.properties?.definitionAttributes
  if (!raw || typeof raw !== 'object') return {}
  const attributes: Record<string, string> = {}
  for (const name of DEFINITION_METADATA) {
    const value: unknown = Reflect.get(raw, name)
    if (typeof value === 'string') attributes[name] = value
  }
  return attributes
}

function appendDefinitionMetadata(
  lines: string[],
  metadata: Record<string, string>,
  indent: string
): void {
  for (const [name, value] of Object.entries(metadata)) {
    if (name !== 'id') lines.push(`${indent}${name}="${escapeXml(value)}"`)
  }
}

function validateDefinitionsChildren(definitions: Element): void {
  directChildren(definitions).forEach((child) => {
    const supported =
      (child.namespaceURI === BPMN_NS &&
        (child.localName === 'process' || child.localName === 'message')) ||
      (child.namespaceURI === BPMNDI_NS && child.localName === 'BPMNDiagram')
    if (!supported) {
      throw new Error(
        `Unsupported BPMN definitions child {${child.namespaceURI || ''}}${child.localName}`
      )
    }
  })
}

function validateProcessChildren(process: Element): void {
  requireOnlyAttributes(process, ['id', 'name', 'isExecutable'])
  requireAtMostOneDirectChild(process, BPMN_NS, 'documentation')
  requireAtMostOneDirectChild(process, BPMN_NS, 'extensionElements')
  directChildren(process).forEach((child) => {
    if (child.namespaceURI !== BPMN_NS) {
      throw new Error(
        `Unsupported process child namespace for ${child.tagName}: ${child.namespaceURI || ''}`
      )
    }
    const supported =
      child.localName === 'documentation' ||
      child.localName === 'extensionElements' ||
      child.localName === 'sequenceFlow' ||
      isSupportedNodeName(child.localName)
    if (!supported) {
      throw new Error(
        `Workbench cannot safely edit BPMN element <${child.tagName}>; use a supported node or callActivity`
      )
    }
  })
}

function parseNodes(
  container: Element,
  geometry: Map<string, NodeGeometry>,
  parentId?: string
): BpmnNode[] {
  const nodes: BpmnNode[] = []
  const elements = directChildren(container).filter(
    (element): element is Element & { localName: SupportedNodeName } =>
      element.namespaceURI === BPMN_NS && isSupportedNodeName(element.localName)
  )
  let fallbackX = elements.reduce((right, element) => {
    const dimensions = geometry.get(requiredAttribute(element, 'id'))
    return dimensions ? Math.max(right, dimensions.x + dimensions.width + 80) : right
  }, 80)

  elements.forEach((element) => {
    const id = requiredAttribute(element, 'id')
    const type = NODE_TYPE_BY_NAME[element.localName]
    const explicitGeometry = geometry.get(id)
    const dimensions = explicitGeometry || { ...defaultGeometry(type), x: fallbackX, y: 80 }
    if (!explicitGeometry) fallbackX += dimensions.width + 80

    const node = parseNode(element, dimensions, parentId)
    nodes.push(node)
    if (node.type === 'bpmn:SubProcess') {
      nodes.push(...parseNodes(element, geometry, node.id))
    }
  })
  return nodes
}

function parseNode(
  element: Element & { localName: SupportedNodeName },
  dimensions: NodeGeometry,
  parentId?: string
): BpmnNode {
  const id = requiredAttribute(element, 'id')
  const type = NODE_TYPE_BY_NAME[element.localName]
  validateNodeAttributes(element)
  validateNodeChildren(element)

  const properties: BpmnNode['properties'] = {}
  parseNodeSpecificAttributes(element, type, properties)
  parseNodeExtensions(element, type, properties)
  const loopCharacteristics = parseLoopCharacteristics(element)
  if (loopCharacteristics) properties.loopCharacteristics = loopCharacteristics
  if (type === 'bpmn:ScriptTask') {
    properties.scriptFormat = optionalAttribute(element, 'scriptFormat') || ''
    properties.script = directChildText(element, BPMN_NS, 'script')
  }

  return {
    id,
    parentId,
    type,
    name: optionalAttribute(element, 'name'),
    documentation: directChildText(element, BPMN_NS, 'documentation') || undefined,
    position: { x: dimensions.x, y: dimensions.y },
    size: { width: dimensions.width, height: dimensions.height },
    properties,
    metadata: {
      editable: true,
      deletable: type !== 'bpmn:StartEvent',
    },
  }
}

function validateNodeAttributes(element: Element): void {
  const common = ['id', 'name']
  switch (element.localName) {
    case 'startEvent':
      requireOnlyAttributes(element, [...common, 'isInterrupting'])
      break
    case 'scriptTask':
      requireOnlyAttributes(
        element,
        [...common, 'scriptFormat'],
        [{ namespace: CF_NS, localName: 'execution' }]
      )
      break
    case 'receiveTask':
      requireOnlyAttributes(element, [...common, 'messageRef', 'implementation', 'operationRef'])
      break
    case 'callActivity':
      requireOnlyAttributes(
        element,
        [...common, 'calledElement'],
        [
          { namespace: CF_NS, localName: 'classpath' },
          { namespace: CF_NS, localName: 'version' },
        ]
      )
      break
    case 'subProcess':
      requireOnlyAttributes(element, [...common, 'triggeredByEvent'])
      break
    case 'exclusiveGateway':
    case 'inclusiveGateway':
      requireOnlyAttributes(element, [...common, 'default'])
      break
    default:
      requireOnlyAttributes(element, common)
  }
}

function validateNodeChildren(element: Element): void {
  const common = new Set(['documentation', 'extensionElements', 'incoming', 'outgoing'])
  if (isActivityName(element.localName)) {
    common.add('standardLoopCharacteristics')
    common.add('multiInstanceLoopCharacteristics')
  }
  if (element.localName === 'scriptTask') common.add('script')
  if (element.localName === 'subProcess') {
    common.add('sequenceFlow')
    SUPPORTED_NODE_NAMES.forEach((name) => common.add(name))
  }

  requireAtMostOneDirectChild(element, BPMN_NS, 'documentation')
  requireAtMostOneDirectChild(element, BPMN_NS, 'extensionElements')
  requireAtMostOneDirectChild(element, BPMN_NS, 'standardLoopCharacteristics')
  requireAtMostOneDirectChild(element, BPMN_NS, 'multiInstanceLoopCharacteristics')
  if (element.localName === 'scriptTask') {
    requireAtMostOneDirectChild(element, BPMN_NS, 'script')
  }

  directChildren(element).forEach((child) => {
    if (child.namespaceURI !== BPMN_NS || !common.has(child.localName)) {
      throw new Error(`Unsupported child <${child.tagName}> on BPMN ${element.localName}`)
    }
  })
}

function parseNodeSpecificAttributes(
  element: Element,
  type: BpmnNodeType,
  properties: BpmnNode['properties']
): void {
  switch (type) {
    case 'bpmn:StartEvent':
      parseStartEventAttributes(element)
      break
    case 'bpmn:ExclusiveGateway':
    case 'bpmn:InclusiveGateway':
      properties.default = optionalAttribute(element, 'default')
      break
    case 'bpmn:ReceiveTask':
      parseReceiveTaskAttributes(element, properties)
      break
    case 'bpmn:CallActivity':
      parseCallActivityAttributes(element, properties)
      break
    case 'bpmn:ScriptTask':
      parseScriptTaskAttributes(element, properties)
      break
    case 'bpmn:SubProcess':
      parseSubProcessAttributes(element)
  }
}

function parseScriptTaskAttributes(element: Element, properties: BpmnNode['properties']): void {
  const execution = namespacedAttribute(element, CF_NS, 'execution')
  if (execution !== undefined && execution !== 'replayable' && execution !== 'effect') {
    throw new Error('BPMN scriptTask cf:execution must be replayable or effect')
  }
  properties.execution = execution || 'replayable'
}

function parseStartEventAttributes(element: Element): void {
  if (
    element.hasAttribute('isInterrupting') &&
    !xmlBooleanAttribute(element, 'isInterrupting', true)
  ) {
    throw new Error(
      'Non-interrupting start events require an event subprocess, which CompileFlow does not support'
    )
  }
}

function parseReceiveTaskAttributes(element: Element, properties: BpmnNode['properties']): void {
  properties.messageRef = optionalAttribute(element, 'messageRef')
  if (
    optionalAttribute(element, 'implementation')?.trim() ||
    optionalAttribute(element, 'operationRef')?.trim()
  ) {
    throw new Error(
      'BPMN receiveTask implementation and operationRef are not supported; CompileFlow trigger entries route by messageRef'
    )
  }
}

function parseCallActivityAttributes(element: Element, properties: BpmnNode['properties']): void {
  properties.calledElement = optionalAttribute(element, 'calledElement')
  properties.classpath = namespacedAttribute(element, CF_NS, 'classpath')
  properties.version = namespacedAttribute(element, CF_NS, 'version')
  if (Boolean(properties.classpath) === Boolean(properties.version)) {
    throw new Error('BPMN callActivity must declare exactly one of cf:classpath or cf:version')
  }
}

function parseSubProcessAttributes(element: Element): void {
  if (
    element.hasAttribute('triggeredByEvent') &&
    xmlBooleanAttribute(element, 'triggeredByEvent', false)
  ) {
    throw new Error('Event subprocesses are not supported')
  }
}

function parseNodeExtensions(
  element: Element,
  type: BpmnNodeType,
  properties: BpmnNode['properties']
): void {
  const extensions = extensionChildren(element)
  assertAllowedExtensions(element, extensions, new Set(NODE_EXTENSION_NAMES[type] || []))
  parseServiceActionExtension(element, extensions, properties)
  parseMappedVariableExtensions(type, extensions, properties)
  parseScriptTaskPolicies(type, element, extensions, properties)
  rejectCallActivityProcessRef(type, extensions)
}

function parseScriptTaskPolicies(
  type: BpmnNodeType,
  owner: Element,
  extensions: Element[],
  properties: BpmnNode['properties']
): void {
  if (type !== 'bpmn:ScriptTask') return
  const invocationPolicy = singleExtension(extensions, 'invocationPolicy', owner.localName)
  const effectPolicy = singleExtension(extensions, 'effectPolicy', owner.localName)
  if (invocationPolicy) {
    properties.invocationPolicy = parseInvocationPolicyElement(invocationPolicy)
  }
  if (effectPolicy) {
    properties.effectPolicy = parseEffectPolicyElement(effectPolicy, BPMN_ACTION_XML)
  }
}

function parseServiceActionExtension(
  owner: Element,
  extensions: Element[],
  properties: BpmnNode['properties']
): void {
  const action = singleExtension(extensions, 'action', owner.localName)
  if (action) {
    properties.action = parseActionElement(action, BPMN_ACTION_XML)
  }
}

function parseMappedVariableExtensions(
  type: BpmnNodeType,
  extensions: Element[],
  properties: BpmnNode['properties']
): void {
  if (type !== 'bpmn:ScriptTask' && type !== 'bpmn:CallActivity') return
  properties.mappings = extensions
    .filter((extension) => extension.localName === 'input' || extension.localName === 'output')
    .map((extension) =>
      parseMappedVariableElement(extension, BPMN_ACTION_XML, type === 'bpmn:CallActivity')
    )
}

function rejectCallActivityProcessRef(type: BpmnNodeType, extensions: Element[]): void {
  if (type !== 'bpmn:CallActivity') return
  const processRef = singleExtension(extensions, 'processRef', 'callActivity')
  if (processRef) throw new Error('cf:processRef is not supported in process definitions')
}

function singleExtension(
  extensions: Element[],
  name: string,
  ownerName: string
): Element | undefined {
  const matches = extensions.filter((extension) => extension.localName === name)
  if (matches.length > 1) {
    throw new Error(`BPMN ${ownerName} must declare at most one cf:${name}`)
  }
  return matches[0]
}

function parseProcessVariables(process: Element): ProcessVariable[] {
  const extensions = extensionChildren(process)
  assertAllowedExtensions(process, extensions, new Set(['var']))
  const names = new Set<string>()
  return extensions.map((element) => {
    requireOnlyAttributes(element, ['name', 'description', 'dataType', 'defaultValue', 'inOutType'])
    if (element.children.length > 0) throw new Error('cf:var must not contain child elements')
    const name = requiredAttribute(element, 'name')
    requireGeneratedJavaIdentifier(name, 'BPMN process variable name')
    if (names.has(name)) throw new Error(`Duplicate BPMN process variable name: ${name}`)
    names.add(name)
    const inOutType = requiredAttribute(element, 'inOutType')
    if (inOutType !== 'param' && inOutType !== 'return' && inOutType !== 'inner') {
      throw new Error(`Invalid BPMN process variable direction: ${inOutType}`)
    }
    return {
      name,
      type: requiredAttribute(element, 'dataType'),
      description: optionalAttribute(element, 'description'),
      defaultValue: element.hasAttribute('defaultValue')
        ? (element.getAttribute('defaultValue') ?? undefined)
        : undefined,
      inOutType,
    }
  })
}

function parseMessages(definitions: Element): BpmnMessageDefinition[] {
  const ids = new Set<string>()
  return directChildrenNamed(definitions, BPMN_NS, 'message').map((message) => {
    requireOnlyAttributes(message, ['id', 'name'])
    if (message.children.length > 0) throw new Error('BPMN message must not contain child elements')
    const id = requiredAttribute(message, 'id')
    const name = requiredAttribute(message, 'name')
    if (ids.has(id)) throw new Error(`Duplicate BPMN message id: ${id}`)
    ids.add(id)
    return { id, name }
  })
}

function extensionChildren(element: Element): Element[] {
  const containers = directChildrenNamed(element, BPMN_NS, 'extensionElements')
  if (containers.length > 1) {
    throw new Error(`BPMN ${element.localName} must contain at most one extensionElements`)
  }
  const container = containers[0]
  if (!container) return []
  requireOnlyAttributes(container, [])
  return directChildren(container).map((extension) => {
    if (extension.namespaceURI !== CF_NS) {
      throw new Error(
        `Unsupported BPMN extension namespace on ${element.localName}: ${extension.namespaceURI || ''}`
      )
    }
    return extension
  })
}

function assertAllowedExtensions(
  owner: Element,
  extensions: Element[],
  allowed: ReadonlySet<string>
): void {
  extensions.forEach((extension) => {
    if (!allowed.has(extension.localName)) {
      throw new Error(`Unsupported cf:${extension.localName} extension on BPMN ${owner.localName}`)
    }
  })
}

function parseLoopCharacteristics(element: Element): LoopCharacteristics | undefined {
  const multi = directChildrenNamed(element, BPMN_NS, 'multiInstanceLoopCharacteristics')
  const standard = directChildrenNamed(element, BPMN_NS, 'standardLoopCharacteristics')
  if (multi.length + standard.length > 1) {
    throw new Error(`BPMN ${element.localName} must declare at most one loop characteristic`)
  }
  if (multi[0]) return parseMultiInstanceLoop(multi[0])
  if (standard[0]) return parseStandardLoop(standard[0])
  return undefined
}

function parseMultiInstanceLoop(loop: Element): MultiInstanceLoopCharacteristics {
  requireOnlyAttributes(
    loop,
    ['id', 'isSequential'],
    [
      { namespace: CF_NS, localName: 'collection' },
      { namespace: CF_NS, localName: 'item' },
      { namespace: CF_NS, localName: 'itemType' },
      { namespace: CF_NS, localName: 'index' },
      { namespace: CF_NS, localName: 'target' },
      { namespace: CF_NS, localName: 'source' },
    ]
  )
  if (loop.children.length > 0) {
    throw new Error('BPMN multiInstanceLoopCharacteristics must not contain child elements')
  }
  const parsed: MultiInstanceLoopCharacteristics = {
    type: 'multiInstance',
    isSequential: xmlBooleanAttribute(loop, 'isSequential', false),
    collection: namespacedAttribute(loop, CF_NS, 'collection') || '',
    item: namespacedAttribute(loop, CF_NS, 'item') || '',
    itemType: optionalNonBlankNamespacedAttribute(loop, CF_NS, 'itemType'),
    index: optionalNonBlankNamespacedAttribute(loop, CF_NS, 'index'),
    target: optionalNonBlankNamespacedAttribute(loop, CF_NS, 'target'),
    source: optionalNonBlankNamespacedAttribute(loop, CF_NS, 'source'),
  }
  validateBpmnLoopCharacteristics(parsed)
  return parsed
}

function parseStandardLoop(loop: Element): StandardLoopCharacteristics {
  requireOnlyAttributes(loop, ['id', 'testBefore', 'loopMaximum'])
  const conditions = directChildrenNamed(loop, BPMN_NS, 'loopCondition')
  if (conditions.length > 1) {
    throw new Error('BPMN standardLoopCharacteristics must contain at most one loopCondition')
  }
  directChildren(loop).forEach((child) => {
    if (child.namespaceURI !== BPMN_NS || child.localName !== 'loopCondition') {
      throw new Error(`Unsupported child <${child.tagName}> on BPMN standardLoopCharacteristics`)
    }
  })
  if (conditions[0]) validateLoopCondition(conditions[0])

  const maximum = optionalAttribute(loop, 'loopMaximum')
  const parsed: StandardLoopCharacteristics = {
    type: 'standard',
    testBefore: xmlBooleanAttribute(loop, 'testBefore', false),
    loopMaximum: maximum === undefined ? undefined : parseLoopMaximum(maximum),
    loopCondition: conditions[0]
      ? normalizeJavaConditionExpression(conditions[0].textContent || '')
      : undefined,
  }
  validateBpmnLoopCharacteristics(parsed)
  return parsed
}

function parseLoopMaximum(value: string): number {
  if (!/^[+-]?\d+$/.test(value)) {
    throw new Error('BPMN standard loopMaximum must use integer syntax')
  }
  return Number(value)
}

function validateLoopCondition(condition: Element): void {
  validateJavaFormalExpression(condition, 'loopCondition')
}

function parseConnections(
  container: Element,
  waypointMap: Map<string, Array<{ x: number; y: number }>>
): BpmnConnection[] {
  const connections: BpmnConnection[] = directChildrenNamed(container, BPMN_NS, 'sequenceFlow').map(
    (element) => {
      requireOnlyAttributes(element, ['id', 'name', 'sourceRef', 'targetRef', 'isImmediate'])
      const id = requiredAttribute(element, 'id')
      const condition = directChildrenNamed(element, BPMN_NS, 'conditionExpression')
      if (condition.length > 1) {
        throw new Error(`BPMN sequenceFlow ${id} must contain at most one conditionExpression`)
      }
      directChildren(element).forEach((child) => {
        if (child.namespaceURI !== BPMN_NS || child.localName !== 'conditionExpression') {
          throw new Error(`Unsupported child <${child.tagName}> on BPMN sequenceFlow`)
        }
      })
      if (condition[0]) validateConditionExpression(condition[0])
      const body = condition[0]
        ? normalizeJavaConditionExpression(condition[0].textContent || '')
        : undefined
      if (element.hasAttribute('isImmediate')) {
        if (!xmlBooleanAttribute(element, 'isImmediate', true)) {
          throw new Error(`Executable BPMN sequenceFlow ${id} cannot declare isImmediate="false"`)
        }
      }
      const waypoints = waypointMap.get(id)
      return {
        id,
        sourceId: requiredAttribute(element, 'sourceRef'),
        targetId: requiredAttribute(element, 'targetRef'),
        name: optionalAttribute(element, 'name'),
        condition: body,
        waypoints,
        ...readConnectionGeometry(element),
      }
    }
  )
  directChildrenNamed(container, BPMN_NS, 'subProcess').forEach((subProcess) => {
    connections.push(...parseConnections(subProcess, waypointMap))
  })
  return connections
}

function buildGeometryMap(definitions: Element): Map<string, NodeGeometry> {
  const geometry = new Map<string, NodeGeometry>()
  Array.from(definitions.getElementsByTagNameNS(BPMNDI_NS, 'BPMNShape')).forEach((shape) => {
    const elementId = optionalAttribute(shape, 'bpmnElement')
    const bounds = directChildrenNamed(shape, DC_NS, 'Bounds')[0]
    if (!elementId || !bounds) return
    geometry.set(elementId, {
      x: finiteNumberAttribute(bounds, 'x'),
      y: finiteNumberAttribute(bounds, 'y'),
      width: finiteNumberAttribute(bounds, 'width'),
      height: finiteNumberAttribute(bounds, 'height'),
    })
  })
  return geometry
}

function buildWaypointMap(definitions: Element): Map<string, Array<{ x: number; y: number }>> {
  const waypoints = new Map<string, Array<{ x: number; y: number }>>()
  Array.from(definitions.getElementsByTagNameNS(BPMNDI_NS, 'BPMNEdge')).forEach((edge) => {
    const elementId = optionalAttribute(edge, 'bpmnElement')
    if (!elementId) return
    const points = directChildrenNamed(edge, DI_NS, 'waypoint').map((point) => ({
      x: finiteNumberAttribute(point, 'x'),
      y: finiteNumberAttribute(point, 'y'),
    }))
    if (points.length >= 2) waypoints.set(elementId, points)
  })
  return waypoints
}

export function generateBpmnXml(
  definition: BpmnProcessDefinition,
  options: GenerateOptions = {}
): string {
  const { indent = '  ', includeDeclaration = true, encoding = 'UTF-8' } = options
  const processId = requiredText(definition.code || definition.id, 'BPMN process id')
  const metadata = definitionAttributes(definition)
  const definitionsId = metadata.id
    ? requiredText(metadata.id, 'definitions id')
    : `Definitions_${processId}`
  const { connections, nodes } = definition
  const messages = resolveMessages(definition.messages || [])
  validateGeneratedGraph(processId, definitionsId, nodes, connections, messages)
  const nodesByContainer = groupNodesByContainer(nodes)
  const connectionsByContainer = groupConnectionsByContainer(nodes, connections)

  const lines: string[] = []
  if (includeDeclaration) {
    lines.push(`<?xml version="1.0" encoding="${escapeXml(encoding)}"?>`)
  }
  lines.push('<bpmn:definitions')
  lines.push(`${indent}xmlns:bpmn="${BPMN_NS}"`)
  lines.push(`${indent}xmlns:bpmndi="${BPMNDI_NS}"`)
  lines.push(`${indent}xmlns:dc="${DC_NS}"`)
  lines.push(`${indent}xmlns:di="${DI_NS}"`)
  lines.push(`${indent}xmlns:cf="${CF_NS}"`)
  lines.push(`${indent}xmlns:xsi="${XSI_NS}"`)
  lines.push(`${indent}targetNamespace="${escapeXml(definition.namespace || CF_NS)}"`)
  appendDefinitionMetadata(lines, metadata, indent)
  lines.push(`${indent}id="${escapeXml(definitionsId)}">`)

  messages.forEach((message) => {
    lines.push(
      `${indent}<bpmn:message id="${escapeXml(message.id)}" name="${escapeXml(message.name)}"/>`
    )
  })

  const processAttributes = [
    `id="${escapeXml(processId)}"`,
    `name="${escapeXml(definition.name || processId)}"`,
    'isExecutable="true"',
  ]
  lines.push(`${indent}<bpmn:process ${processAttributes.join(' ')}>`)
  if (definition.description) {
    lines.push(
      `${indent}${indent}<bpmn:documentation>${escapeXml(definition.description)}</bpmn:documentation>`
    )
  }
  appendProcessVariables(lines, definition.variables || [], indent.repeat(2), indent)
  ;(nodesByContainer.get(ROOT_CONTAINER) || []).forEach((node) =>
    lines.push(
      generateBpmnNodeXml(node, indent.repeat(2), indent, nodesByContainer, connectionsByContainer)
    )
  )
  ;(connectionsByContainer.get(ROOT_CONTAINER) || []).forEach((connection) => {
    lines.push(generateBpmnConnectionXml(connection, indent.repeat(2)))
  })
  lines.push(`${indent}</bpmn:process>`)

  lines.push(`${indent}<bpmndi:BPMNDiagram id="BPMNDiagram_${escapeXml(processId)}">`)
  lines.push(
    `${indent}${indent}<bpmndi:BPMNPlane id="BPMNPlane_${escapeXml(processId)}" bpmnElement="${escapeXml(processId)}">`
  )
  nodes.forEach((node) => lines.push(generateBpmnShapeXml(node, indent.repeat(3))))
  const nodesById = new Map(nodes.map((node) => [node.id, node]))
  connections.forEach((connection) => {
    lines.push(generateBpmnEdgeXml(connection, indent.repeat(3), nodesById))
  })
  lines.push(`${indent}${indent}</bpmndi:BPMNPlane>`)
  lines.push(`${indent}</bpmndi:BPMNDiagram>`)
  lines.push('</bpmn:definitions>')
  return lines.join('\n')
}

function appendProcessVariables(
  lines: string[],
  variables: ProcessVariable[],
  childIndent: string,
  indentUnit: string
): void {
  if (variables.length === 0) return
  const names = new Set<string>()
  lines.push(`${childIndent}<bpmn:extensionElements>`)
  variables.forEach((variable) => {
    const name = requiredText(variable.name, 'BPMN process variable name')
    requireGeneratedJavaIdentifier(name, 'BPMN process variable name')
    if (names.has(name)) throw new Error(`Duplicate BPMN process variable name: ${name}`)
    names.add(name)
    const direction = variable.inOutType
    if (direction !== 'param' && direction !== 'return' && direction !== 'inner') {
      throw new Error(`Invalid BPMN process variable direction: ${direction}`)
    }
    const attributes = [
      `name="${escapeXml(name)}"`,
      `dataType="${escapeXml(requiredText(variable.type, `data type for ${name}`))}"`,
      `inOutType="${direction}"`,
    ]
    if (variable.defaultValue !== undefined) {
      attributes.push(`defaultValue="${escapeXml(String(variable.defaultValue))}"`)
    }
    if (variable.description) {
      attributes.push(`description="${escapeXml(variable.description)}"`)
    }
    lines.push(`${childIndent}${indentUnit}<cf:var ${attributes.join(' ')}/>`)
  })
  lines.push(`${childIndent}</bpmn:extensionElements>`)
}

function generateBpmnNodeXml(
  node: BpmnNode,
  indent: string,
  indentUnit: string,
  nodesByContainer: ReadonlyMap<string, BpmnNode[]>,
  connectionsByContainer: ReadonlyMap<string, BpmnConnection[]>
): string {
  const elementName = NODE_NAME_BY_TYPE[node.type]
  if (!elementName) throw new Error(`Unsupported Workbench BPMN node type: ${node.type}`)
  const attributes = [`id="${escapeXml(requiredText(node.id, 'BPMN node id'))}"`]
  if (node.name) attributes.push(`name="${escapeXml(node.name)}"`)
  appendNodeAttributes(attributes, node)

  const childLines: string[] = []
  if (node.documentation) {
    childLines.push(
      `${indent}${indentUnit}<bpmn:documentation>${escapeXml(node.documentation)}</bpmn:documentation>`
    )
  }
  appendNodeExtensions(childLines, node, indent + indentUnit, indentUnit)
  if (node.properties.loopCharacteristics) {
    childLines.push(
      generateLoopCharacteristicsXml(node.properties.loopCharacteristics, indent + indentUnit)
    )
  }
  if (node.type === 'bpmn:ScriptTask' && node.properties.script) {
    const script = String(node.properties.script).replace(/\]\]>/g, ']]>]]<![CDATA[>')
    childLines.push(`${indent}${indentUnit}<bpmn:script><![CDATA[${script}]]></bpmn:script>`)
  }
  if (node.type === 'bpmn:SubProcess') {
    ;(nodesByContainer.get(node.id) || []).forEach((child) => {
      childLines.push(
        generateBpmnNodeXml(
          child,
          indent + indentUnit,
          indentUnit,
          nodesByContainer,
          connectionsByContainer
        )
      )
    })
    ;(connectionsByContainer.get(node.id) || []).forEach((connection) => {
      childLines.push(generateBpmnConnectionXml(connection, indent + indentUnit))
    })
  }

  if (childLines.length === 0) {
    return `${indent}<bpmn:${elementName} ${attributes.join(' ')}/>`
  }
  return [
    `${indent}<bpmn:${elementName} ${attributes.join(' ')}>`,
    ...childLines,
    `${indent}</bpmn:${elementName}>`,
  ].join('\n')
}

function appendNodeAttributes(attributes: string[], node: BpmnNode): void {
  const properties = node.properties
  if (
    (node.type === 'bpmn:ExclusiveGateway' || node.type === 'bpmn:InclusiveGateway') &&
    properties.default
  ) {
    attributes.push(`default="${escapeXml(String(properties.default))}"`)
  }
  if (node.type === 'bpmn:ScriptTask') {
    attributes.push(`scriptFormat="${escapeXml(String(properties.scriptFormat || ''))}"`)
    if (properties.execution && properties.execution !== 'replayable') {
      attributes.push(`cf:execution="${properties.execution}"`)
    }
  }
  if (node.type === 'bpmn:ReceiveTask') {
    appendOptionalAttribute(attributes, 'messageRef', properties.messageRef)
  }
  if (node.type === 'bpmn:CallActivity') {
    appendOptionalAttribute(attributes, 'calledElement', properties.calledElement)
    appendOptionalAttribute(attributes, 'cf:classpath', properties.classpath)
    appendOptionalAttribute(attributes, 'cf:version', properties.version)
  }
}

function appendNodeExtensions(
  lines: string[],
  node: BpmnNode,
  indent: string,
  indentUnit: string
): void {
  const extensions: string[] = []
  const extensionIndent = indent + indentUnit
  appendServiceAction(extensions, node, extensionIndent, indentUnit)
  appendMappedVariables(extensions, node, extensionIndent)
  appendScriptTaskPolicies(extensions, node, extensionIndent, indentUnit)
  if (extensions.length === 0) return
  lines.push(`${indent}<bpmn:extensionElements>`)
  lines.push(...extensions)
  lines.push(`${indent}</bpmn:extensionElements>`)
}

function appendScriptTaskPolicies(
  extensions: string[],
  node: BpmnNode,
  indent: string,
  indentUnit: string
): void {
  if (node.type !== 'bpmn:ScriptTask') return
  if (node.properties.invocationPolicy) {
    extensions.push(
      generateInvocationPolicyXml(node.properties.invocationPolicy, indent, BPMN_ACTION_XML)
    )
  }
  if (node.properties.effectPolicy) {
    const requestFields = persistedEffectRequestFields(node.properties.mappings)
    extensions.push(
      generateEffectPolicyXml(
        node.properties.effectPolicy,
        indent,
        indentUnit,
        BPMN_ACTION_XML,
        requestFields
      )
    )
  }
}

function appendServiceAction(
  extensions: string[],
  node: BpmnNode,
  indent: string,
  indentUnit: string
): void {
  if (node.type !== 'bpmn:ServiceTask' || !node.properties.action) return
  extensions.push(
    generateActionElementXml('action', node.properties.action, indent, indentUnit, BPMN_ACTION_XML)
  )
}

function appendMappedVariables(extensions: string[], node: BpmnNode, indent: string): void {
  if (node.type !== 'bpmn:ScriptTask' && node.type !== 'bpmn:CallActivity') return
  const mappings = node.properties.mappings || []
  for (const mapping of mappings) {
    extensions.push(
      generateMappedVariableXml(mapping, indent, BPMN_ACTION_XML, node.type === 'bpmn:CallActivity')
    )
  }
}

function generateLoopCharacteristicsXml(loop: LoopCharacteristics, indent: string): string {
  validateBpmnLoopCharacteristics(loop)
  return loop.type === 'multiInstance'
    ? generateMultiInstanceLoopXml(loop, indent)
    : generateStandardLoopXml(loop, indent)
}

function generateMultiInstanceLoopXml(
  loop: MultiInstanceLoopCharacteristics,
  indent: string
): string {
  const collection = requiredText(loop.collection, 'cf:collection')
  const item = requiredText(loop.item, 'cf:item')
  const index = loop.index ? requiredText(loop.index, 'cf:index') : undefined
  const attributes = [
    `isSequential="${loop.isSequential}"`,
    `cf:collection="${escapeXml(collection)}"`,
    `cf:item="${escapeXml(item)}"`,
  ]
  appendOptionalAttribute(attributes, 'cf:itemType', loop.itemType)
  appendOptionalAttribute(attributes, 'cf:index', index)
  appendOptionalAttribute(attributes, 'cf:target', loop.target)
  appendOptionalAttribute(attributes, 'cf:source', loop.source)
  return `${indent}<bpmn:multiInstanceLoopCharacteristics ${attributes.join(' ')}/>`
}

function generateStandardLoopXml(loop: StandardLoopCharacteristics, indent: string): string {
  const loopCondition = loop.loopCondition
    ? requiredText(normalizeJavaConditionExpression(loop.loopCondition), 'BPMN loopCondition')
    : undefined
  const attributes: string[] = []
  if (loop.testBefore !== undefined) {
    attributes.push(`testBefore="${loop.testBefore}"`)
  }
  appendOptionalAttribute(attributes, 'loopMaximum', loop.loopMaximum)
  const suffix = attributes.length ? ` ${attributes.join(' ')}` : ''
  if (!loopCondition) {
    return `${indent}<bpmn:standardLoopCharacteristics${suffix}/>`
  }
  return [
    `${indent}<bpmn:standardLoopCharacteristics${suffix}>`,
    `${indent}  <bpmn:loopCondition xsi:type="bpmn:tFormalExpression" language="java">${escapeXml(loopCondition)}</bpmn:loopCondition>`,
    `${indent}</bpmn:standardLoopCharacteristics>`,
  ].join('\n')
}

function generateBpmnConnectionXml(connection: BpmnConnection, indent: string): string {
  const attributes = [
    `id="${escapeXml(requiredText(connection.id, 'sequenceFlow id'))}"`,
    `sourceRef="${escapeXml(requiredText(connection.sourceId, 'sequenceFlow sourceRef'))}"`,
    `targetRef="${escapeXml(requiredText(connection.targetId, 'sequenceFlow targetRef'))}"`,
  ]
  if (connection.name) attributes.push(`name="${escapeXml(connection.name)}"`)
  const condition =
    connection.condition === undefined
      ? undefined
      : normalizeJavaConditionExpression(connection.condition)
  const geometry = writeConnectionGeometry(connection)
  if ((condition === undefined || condition === '') && !geometry) {
    return `${indent}<bpmn:sequenceFlow ${attributes.join(' ')}/>`
  }
  return [
    `${indent}<bpmn:sequenceFlow ${attributes.join(' ')}>`,
    ...(geometry ? [`${indent}  ${geometry}`] : []),
    ...(condition
      ? [
          `${indent}  <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression" language="java">${escapeXml(condition)}</bpmn:conditionExpression>`,
        ]
      : []),
    `${indent}</bpmn:sequenceFlow>`,
  ].join('\n')
}

function generateBpmnShapeXml(node: BpmnNode, indent: string): string {
  const geometry = {
    ...defaultGeometry(node.type),
    ...node.position,
    ...node.size,
  }
  return [
    `${indent}<bpmndi:BPMNShape id="BPMNShape_${escapeXml(node.id)}" bpmnElement="${escapeXml(node.id)}">`,
    `${indent}  <dc:Bounds x="${geometry.x}" y="${geometry.y}" width="${geometry.width}" height="${geometry.height}"/>`,
    `${indent}</bpmndi:BPMNShape>`,
  ].join('\n')
}

function generateBpmnEdgeXml(
  connection: BpmnConnection,
  indent: string,
  nodesById: ReadonlyMap<string, BpmnNode>
): string {
  const configured = connection.waypoints
  const endpoint = (id: string, port?: string) => {
    const node = requiredMapValue(nodesById, id)
    const { width, height } = { ...defaultGeometry(node.type), ...node.size }
    const { x, y } = node.position
    switch (port) {
      case 'top':
        return { x: x + width / 2, y: y + 6 }
      case 'bottom':
        return { x: x + width / 2, y: y + height - 6 }
      case 'left':
        return { x: x + 6, y: y + height / 2 }
      case 'right':
        return { x: x + width - 6, y: y + height / 2 }
      default:
        return nodeCenter(node)
    }
  }
  const points =
    connection.sourcePort || connection.targetPort
      ? [
          endpoint(connection.sourceId, connection.sourcePort),
          ...(configured ?? []),
          endpoint(connection.targetId, connection.targetPort),
        ]
      : configured && configured.length >= 2
        ? configured
        : [
            nodeCenter(requiredMapValue(nodesById, connection.sourceId)),
            nodeCenter(requiredMapValue(nodesById, connection.targetId)),
          ]
  return [
    `${indent}<bpmndi:BPMNEdge id="BPMNEdge_${escapeXml(connection.id)}" bpmnElement="${escapeXml(connection.id)}">`,
    ...points.map((point) => `${indent}  <di:waypoint x="${point.x}" y="${point.y}"/>`),
    `${indent}</bpmndi:BPMNEdge>`,
  ].join('\n')
}

function resolveMessages(configured: BpmnMessageDefinition[]): BpmnMessageDefinition[] {
  const messages = new Map<string, BpmnMessageDefinition>()
  configured.forEach((message) => {
    const id = requiredText(message.id, 'BPMN message id')
    if (messages.has(id)) throw new Error(`Duplicate BPMN message id: ${id}`)
    messages.set(id, {
      id,
      name: requiredText(message.name, `BPMN message ${id} name`),
    })
  })
  return Array.from(messages.values())
}

function validateGeneratedGraph(
  processId: string,
  definitionsId: string,
  nodes: BpmnNode[],
  connections: BpmnConnection[],
  messages: BpmnMessageDefinition[]
): void {
  const ids = new Set([definitionsId])
  addUniqueId(ids, processId)
  const messageIds = new Set(messages.map((message) => message.id))
  messages.forEach((message) => addUniqueId(ids, message.id))
  const nodesById = new Map<string, BpmnNode>()
  nodes.forEach((node) => {
    const inapplicableProperties = findInapplicableBpmnNodeProperties(node)
    if (inapplicableProperties.length > 0) {
      throw new Error(
        `BPMN node ${node.id} of type ${node.type} contains inapplicable properties: ` +
          inapplicableProperties.join(', ')
      )
    }
    if (!NODE_NAME_BY_TYPE[node.type]) {
      throw new Error(`Unsupported Workbench BPMN node type: ${node.type}`)
    }
    const nodeId = requiredText(node.id, 'BPMN node id')
    if (node.type === 'bpmn:ReceiveTask') {
      const messageRef = requiredText(
        node.properties.messageRef || '',
        `BPMN receiveTask ${nodeId} messageRef`
      )
      if (!messageIds.has(messageRef)) {
        throw new Error(
          `BPMN receiveTask ${nodeId} messageRef "${messageRef}" does not resolve to a message`
        )
      }
    }
    addUniqueId(ids, nodeId)
    nodesById.set(nodeId, node)
  })
  validateContainerHierarchy(nodes, nodesById)
  connections.forEach((connection) => {
    addUniqueId(ids, requiredText(connection.id, 'BPMN sequenceFlow id'))
  })
  validateConnectionContainers(nodes, connections)
}

function validateConnectionContainers(nodes: BpmnNode[], connections: BpmnConnection[]): void {
  const nodesById = new Map(nodes.map((node) => [node.id, node]))
  connections.forEach((connection) => {
    const source = nodesById.get(connection.sourceId)
    const target = nodesById.get(connection.targetId)
    if (!source) {
      throw new Error(
        `BPMN sequenceFlow ${connection.id} references unknown source ${connection.sourceId}`
      )
    }
    if (!target) {
      throw new Error(
        `BPMN sequenceFlow ${connection.id} references unknown target ${connection.targetId}`
      )
    }
    if (source.parentId !== target.parentId) {
      throw new Error(`BPMN sequenceFlow ${connection.id} crosses process container boundaries`)
    }
  })
}

function validateContainerHierarchy(
  nodes: BpmnNode[],
  nodesById: ReadonlyMap<string, BpmnNode>
): void {
  nodes.forEach((node) => {
    if (!node.parentId) return
    const parent = nodesById.get(node.parentId)
    if (!parent) {
      throw new Error(`BPMN node ${node.id} references unknown parent ${node.parentId}`)
    }
    if (parent.type !== 'bpmn:SubProcess') {
      throw new Error(`BPMN node ${node.id} parent ${node.parentId} is not a subProcess`)
    }
    const ancestors = new Set([node.id])
    let parentId: string | undefined = node.parentId
    while (parentId) {
      if (ancestors.has(parentId)) {
        throw new Error(`BPMN subprocess hierarchy contains a cycle at node ${node.id}`)
      }
      ancestors.add(parentId)
      parentId = nodesById.get(parentId)?.parentId
    }
  })

  nodes
    .filter((node) => node.type === 'bpmn:SubProcess')
    .forEach((subProcess) => {
      const children = nodes.filter((node) => node.parentId === subProcess.id)
      const startCount = children.filter((node) => node.type === 'bpmn:StartEvent').length
      const endCount = children.filter((node) => node.type === 'bpmn:EndEvent').length
      if (startCount !== 1 || endCount !== 1) {
        throw new Error(
          `BPMN subProcess ${subProcess.id} must contain exactly one direct startEvent and one direct endEvent`
        )
      }
    })
}

function groupNodesByContainer(nodes: BpmnNode[]): Map<string, BpmnNode[]> {
  const grouped = new Map<string, BpmnNode[]>()
  nodes.forEach((node) => {
    const key = node.parentId || ROOT_CONTAINER
    const children = grouped.get(key) || []
    children.push(node)
    grouped.set(key, children)
  })
  return grouped
}

function groupConnectionsByContainer(
  nodes: BpmnNode[],
  connections: BpmnConnection[]
): Map<string, BpmnConnection[]> {
  const nodesById = new Map(nodes.map((node) => [node.id, node]))
  const grouped = new Map<string, BpmnConnection[]>()
  connections.forEach((connection) => {
    const source = requiredMapValue(nodesById, connection.sourceId)
    const key = source.parentId || ROOT_CONTAINER
    const localConnections = grouped.get(key) || []
    localConnections.push(connection)
    grouped.set(key, localConnections)
  })
  return grouped
}

function validateUniqueIds(
  definitionsId: string | undefined,
  processId: string,
  nodes: BpmnNode[],
  connections: BpmnConnection[],
  messages: BpmnMessageDefinition[]
): void {
  const ids = new Set<string>()
  if (definitionsId) addUniqueId(ids, definitionsId)
  addUniqueId(ids, processId)
  messages.forEach((message) => addUniqueId(ids, message.id))
  nodes.forEach((node) => addUniqueId(ids, node.id))
  connections.forEach((connection) => addUniqueId(ids, connection.id))
}

function addUniqueId(ids: Set<string>, id: string): void {
  if (ids.has(id)) throw new Error(`Duplicate BPMN id: ${id}`)
  ids.add(id)
}

function defaultGeometry(type: BpmnNodeType): NodeGeometry {
  if (type === 'bpmn:StartEvent' || type === 'bpmn:EndEvent') {
    return { x: 100, y: 100, width: 36, height: 36 }
  }
  if (isGatewayType(type)) return { x: 100, y: 100, width: 50, height: 50 }
  if (type === 'bpmn:CallActivity') {
    return { x: 100, y: 100, width: 140, height: 100 }
  }
  if (type === 'bpmn:SubProcess') {
    return { x: 100, y: 100, width: 320, height: 220 }
  }
  return { x: 100, y: 100, width: 100, height: 80 }
}

function nodeCenter(node: BpmnNode): { x: number; y: number } {
  const geometry = { ...defaultGeometry(node.type), ...node.position, ...node.size }
  return {
    x: geometry.x + geometry.width / 2,
    y: geometry.y + geometry.height / 2,
  }
}

function isSupportedNodeName(name: string): name is SupportedNodeName {
  return (SUPPORTED_NODE_NAMES as readonly string[]).includes(name)
}

function isActivityName(name: string): boolean {
  return (
    name === 'serviceTask' ||
    name === 'scriptTask' ||
    name === 'callActivity' ||
    name === 'subProcess'
  )
}

function isGatewayType(type: BpmnNodeType): boolean {
  return (
    type === 'bpmn:ExclusiveGateway' ||
    type === 'bpmn:ParallelGateway' ||
    type === 'bpmn:InclusiveGateway'
  )
}

function directChildren(element: Element): Element[] {
  return Array.from(element.children)
}

function directChildrenNamed(element: Element, namespace: string, localName: string): Element[] {
  return directChildren(element).filter(
    (child) => child.namespaceURI === namespace && child.localName === localName
  )
}

function directChildText(element: Element, namespace: string, localName: string): string {
  return directChildrenNamed(element, namespace, localName)[0]?.textContent || ''
}

interface QualifiedAttribute {
  namespace: string
  localName: string
}

function requireOnlyAttributes(
  element: Element,
  allowedNames: string[],
  allowedQualified: QualifiedAttribute[] = []
): void {
  const allowed = new Set(allowedNames)
  const qualified = new Set(
    allowedQualified.map(({ namespace, localName }) => `${namespace}\u0000${localName}`)
  )
  Array.from(element.attributes).forEach((attribute) => {
    if (attribute.namespaceURI === XMLNS_NS) return
    const supported = attribute.namespaceURI
      ? qualified.has(`${attribute.namespaceURI}\u0000${attribute.localName}`)
      : allowed.has(attribute.localName)
    if (!supported) {
      throw new Error(`Unsupported attribute "${attribute.name}" on BPMN ${element.localName}`)
    }
  })
}

function requireAtMostOneDirectChild(element: Element, namespace: string, localName: string): void {
  if (directChildrenNamed(element, namespace, localName).length > 1) {
    throw new Error(`BPMN ${element.localName} must contain at most one ${localName}`)
  }
}

function validateConditionExpression(element: Element): void {
  validateJavaFormalExpression(element, 'conditionExpression')
}

function validateJavaFormalExpression(element: Element, elementName: string): void {
  requireOnlyAttributes(element, ['language'], [{ namespace: XSI_NS, localName: 'type' }])
  if (element.children.length > 0) {
    throw new Error(`BPMN ${elementName} must not contain child elements`)
  }
  const language = optionalAttribute(element, 'language')
  if (language && language !== 'java') {
    throw new Error(`BPMN ${elementName} language must be 'java': ${language}`)
  }
  if (!language && !inheritsJavaExpressionLanguage(element)) {
    throw new Error(
      `BPMN ${elementName} must declare language="java" or definitions expressionLanguage="${COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE}"`
    )
  }
  const type = namespacedAttribute(element, XSI_NS, 'type')
  if (type && type !== 'tFormalExpression' && !type.endsWith(':tFormalExpression')) {
    throw new Error(`BPMN ${elementName} xsi:type must be 'tFormalExpression': ${type}`)
  }
}

function inheritsJavaExpressionLanguage(element: Element): boolean {
  return (
    element.ownerDocument?.documentElement.getAttribute('expressionLanguage') ===
    COMPILEFLOW_JAVA_EXPRESSION_LANGUAGE
  )
}

function requiredAttribute(element: Element, name: string): string {
  return requiredText(element.getAttribute(name) || '', `${element.tagName}@${name}`)
}

function optionalAttribute(element: Element, name: string): string | undefined {
  const value = element.getAttribute(name)
  return value === null || value === '' ? undefined : value
}

function requiredText(value: string, label: string): string {
  if (!value.trim()) throw new Error(`${label} must not be blank`)
  if (value !== value.trim()) throw new Error(`${label} must not contain surrounding whitespace`)
  return value
}

function xmlBooleanAttribute(element: Element, name: string, defaultValue: boolean): boolean {
  const value = element.getAttribute(name)
  if (value === null || value === '') return defaultValue
  if (value === 'true' || value === '1') return true
  if (value === 'false' || value === '0') return false
  throw new Error(`Invalid ${name}: expected true, false, 1, or 0; received ${value}`)
}

function namespacedAttribute(
  element: Element,
  namespace: string,
  localName: string
): string | undefined {
  const value = element.getAttributeNS(namespace, localName)
  return value === null || value === '' ? undefined : value
}

function optionalNonBlankNamespacedAttribute(
  element: Element,
  namespace: string,
  localName: string
): string | undefined {
  const value = element.getAttributeNS(namespace, localName)
  if (value === null) return undefined
  if (value.trim().length === 0) {
    throw new Error(`cf:${localName} must not be blank when declared`)
  }
  return value
}

function finiteNumberAttribute(element: Element, name: string): number {
  const raw = requiredAttribute(element, name)
  const value = Number(raw)
  if (!Number.isFinite(value)) {
    throw new Error(`Invalid finite number for ${element.tagName}@${name}: ${raw}`)
  }
  return value
}

function appendOptionalAttribute(attributes: string[], name: string, value: unknown): void {
  if (value !== undefined && value !== null && value !== '') {
    attributes.push(`${name}="${escapeXml(String(value))}"`)
  }
}

function requiredMapValue<K, V>(map: ReadonlyMap<K, V>, key: K): V {
  const value = map.get(key)
  if (value === undefined) throw new Error(`Missing BPMN node geometry for ${String(key)}`)
  return value
}

function failure(
  code: string,
  message: string,
  details?: unknown
): ParseResult<BpmnProcessDefinition> {
  return {
    success: false,
    error: { code, message, details },
  }
}
