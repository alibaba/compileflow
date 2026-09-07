import {
  type ActionDefinition,
  type EffectPolicy,
  type ActionInvocationDefinition,
  type ActionType,
  type ReconcileActionDefinition,
  type ReconcileInputMapping,
  type VariableMapping,
  mappingDefaultConflict,
  persistedEffectRequestFields,
} from '../types/action'
import { validateEffectPolicy } from '../types/effectPolicy'
import { type InvocationPolicy, validateInvocationPolicy } from '../types/invocationPolicy'
import { isJavaIdentifier, requireGeneratedJavaIdentifier } from '../types/javaIdentifiers'
import { actionFindings, reconcileActionFindings } from '../validation/actionValidation'

import { escapeXml } from './xmlEscaping'

export interface ActionXmlDialect {
  namespaceUri: string | null
  prefix: string
}

const TBBPM_ACTION_XML: ActionXmlDialect = {
  namespaceUri: null,
  prefix: '',
}

export const BPMN_ACTION_XML: ActionXmlDialect = {
  namespaceUri: 'http://www.compileflow.org',
  prefix: 'cf:',
}

export function parseActionElement(
  actionElement: Element,
  dialect: ActionXmlDialect = TBBPM_ACTION_XML
): ActionDefinition {
  return parseActionLikeElement(actionElement, 'action', dialect) as ActionDefinition
}

function parseActionLikeElement(
  actionElement: Element,
  elementName: 'action' | 'reconcileAction',
  dialect: ActionXmlDialect
): ActionDefinition | ReconcileActionDefinition {
  const reconcile = elementName === 'reconcileAction'
  assertElementDialect(actionElement, elementName, dialect)
  const actionType = parseActionType(actionElement)
  validateActionShape(actionType, actionElement, dialect, reconcile)
  const children = directChildren(actionElement)
  const policyElement = optionalSingleChild(actionElement, 'invocationPolicy', dialect)
  const effectPolicyElement = optionalSingleChild(actionElement, 'effectPolicy', dialect)

  const action: ActionInvocationDefinition = {
    actionType,
  }

  if (actionType === 'java') {
    action.className = optionalAttribute(actionElement, 'class')
    action.method = optionalAttribute(actionElement, 'method')
  } else if (actionType === 'spring-bean') {
    action.bean = optionalAttribute(actionElement, 'bean')
    action.className = optionalAttribute(actionElement, 'class')
    action.method = optionalAttribute(actionElement, 'method')
  } else if (actionType === 'script') {
    action.language = requireNonBlankAttribute(actionElement, 'language')
    action.source = parseActionCode(actionElement, dialect)
  }

  if (reconcile) {
    return {
      ...action,
      inputs: children
        .filter((element) => element.localName === 'input' && matchesDialect(element, dialect))
        .map((element) => parseReconcileInputElement(element, dialect)),
    }
  }

  const processAction: ActionDefinition = {
    ...action,
    execution: parseActionExecution(actionElement),
    mappings: children
      .filter(
        (element) =>
          (element.localName === 'input' || element.localName === 'output') &&
          matchesDialect(element, dialect)
      )
      .map((element) => parseMappedVariableElement(element, dialect)),
  }
  if (policyElement) processAction.invocationPolicy = parseInvocationPolicyElement(policyElement)
  if (effectPolicyElement) {
    processAction.effectPolicy = parseEffectPolicyElement(effectPolicyElement, dialect)
    validateEffectPolicy(processAction.effectPolicy, processAction.execution)
  }
  return processAction
}

function parseReconcileInputElement(
  element: Element,
  dialect: ActionXmlDialect
): ReconcileInputMapping {
  if (element.localName !== 'input' || !matchesDialect(element, dialect)) {
    throw new Error(`Expected ${dialect.prefix}input, received ${element.tagName}`)
  }
  requireOnlyAttributes(element, ['source', 'target', 'dataType'])
  if (element.children.length > 0) {
    throw new Error(`${element.tagName} must not contain child elements`)
  }
  return {
    source: requireNonBlankAttribute(element, 'source'),
    target: requireNonBlankAttribute(element, 'target'),
    dataType: requireNonBlankAttribute(element, 'dataType'),
  }
}

export function parseMappedVariableElement(
  element: Element,
  dialect: ActionXmlDialect = TBBPM_ACTION_XML,
  processCall = false
): VariableMapping {
  const input = element.localName === 'input'
  const output = element.localName === 'output'
  if ((!input && !output) || !matchesDialect(element, dialect)) {
    throw new Error(
      `Expected ${dialect.prefix}input or ${dialect.prefix}output, received ${element.tagName}`
    )
  }
  requireOnlyAttributes(
    element,
    input
      ? processCall
        ? ['source', 'target', 'defaultValue']
        : ['source', 'target', 'dataType', 'defaultValue']
      : processCall
        ? ['source', 'target']
        : ['target', 'dataType']
  )
  if (element.children.length > 0) {
    throw new Error(`${element.tagName} must not contain child elements`)
  }
  const mapping: VariableMapping = input
    ? {
        direction: 'input',
        target: requireNonBlankAttribute(element, 'target'),
        dataType: processCall ? undefined : requireNonBlankAttribute(element, 'dataType'),
        source: optionalAttribute(element, 'source'),
        defaultValue: optionalAttributePreservingEmpty(element, 'defaultValue'),
      }
    : {
        direction: 'output',
        source: processCall ? requireNonBlankAttribute(element, 'source') : undefined,
        target: requireNonBlankAttribute(element, 'target'),
        dataType: processCall ? undefined : requireNonBlankAttribute(element, 'dataType'),
      }
  requireUsableMappingDefault(mapping, element.tagName)
  return mapping
}

export function generateActionElementXml(
  elementName: 'action',
  action: ActionDefinition,
  indent: string,
  indentUnit: string,
  dialect: ActionXmlDialect = TBBPM_ACTION_XML
): string {
  requireSerializableAction(action)
  const childIndent = indent + indentUnit
  const children = (action.mappings || []).map((mapping) =>
    generateMappedVariableXml(mapping, childIndent, dialect)
  )
  if (action.actionType === 'script' && action.source !== undefined) {
    children.push(generateActionCodeXml(action.source, childIndent, dialect))
  }
  if (action.invocationPolicy) {
    children.push(generateInvocationPolicyXml(action.invocationPolicy, childIndent, dialect))
  }
  if (action.effectPolicy) {
    children.push(
      generateEffectPolicyXml(
        action.effectPolicy,
        childIndent,
        indentUnit,
        dialect,
        persistedEffectRequestFields(action.mappings)
      )
    )
  }
  const open = `${indent}<${dialect.prefix}${elementName}${actionElementAttributes(action)}`
  if (children.length === 0) return `${open}/>`
  return [open + '>', ...children, `${indent}</${dialect.prefix}${elementName}>`].join('\n')
}

function generateReconcileActionXml(
  action: ReconcileActionDefinition,
  indent: string,
  indentUnit: string,
  dialect: ActionXmlDialect,
  requestFields: ReadonlySet<string>
): string {
  requireSerializableReconcileAction(action, requestFields)
  const childIndent = indent + indentUnit
  const children = (action.inputs ?? []).map((input) => {
    const attributes = [
      `source="${escapeXml(input.source)}"`,
      `target="${escapeXml(input.target)}"`,
      `dataType="${escapeXml(input.dataType)}"`,
    ]
    return `${childIndent}<${dialect.prefix}input ${attributes.join(' ')}/>`
  })
  if (action.actionType === 'script' && action.source !== undefined) {
    children.push(generateActionCodeXml(action.source, childIndent, dialect))
  }
  const open = `${indent}<${dialect.prefix}reconcileAction${actionElementAttributes(action)}`
  if (children.length === 0) return `${open}/>`
  return [open + '>', ...children, `${indent}</${dialect.prefix}reconcileAction>`].join('\n')
}

export function parseEffectPolicyElement(
  element: Element,
  dialect: ActionXmlDialect = TBBPM_ACTION_XML
): EffectPolicy {
  requireOnlyAttributes(element, [
    'recovery',
    'recoveryPlanVariable',
    'maxAttempts',
    'maxReconcileAttempts',
    'recoveryDelay',
    'maxRecoveryDuration',
  ])
  const recovery = optionalAttributePreservingEmpty(element, 'recovery') as EffectPolicy['recovery']
  if (recovery !== undefined && !['manual', 'retry', 'reconcile'].includes(recovery)) {
    throw new Error(`${element.tagName} recovery must be one of: manual, retry, reconcile`)
  }
  const reconcileActionElement = optionalSingleChild(element, 'reconcileAction', dialect)
  const policy: EffectPolicy = {
    recovery,
    recoveryPlanVariable: optionalAttributePreservingEmpty(element, 'recoveryPlanVariable'),
    maxAttempts: optionalNumberAttribute(element, 'maxAttempts'),
    maxReconcileAttempts: optionalNumberAttribute(element, 'maxReconcileAttempts'),
    recoveryDelay: optionalAttributePreservingEmpty(element, 'recoveryDelay'),
    maxRecoveryDuration: optionalAttributePreservingEmpty(element, 'maxRecoveryDuration'),
    reconcileAction: reconcileActionElement
      ? (parseActionLikeElement(
          reconcileActionElement,
          'reconcileAction',
          dialect
        ) as ReconcileActionDefinition)
      : undefined,
  }
  directChildren(element).forEach((child) => {
    if (child !== reconcileActionElement) {
      throw new Error(`${element.tagName} contains unsupported child "${child.tagName}"`)
    }
  })
  return policy
}

export function generateEffectPolicyXml(
  policy: EffectPolicy,
  indent: string,
  indentUnit: string,
  dialect: ActionXmlDialect,
  requestFields: ReadonlySet<string>
): string {
  validateEffectPolicy(policy, 'effect')
  const attributes: string[] = []
  appendOptionalAttribute(attributes, 'recovery', policy.recovery)
  appendOptionalAttribute(attributes, 'recoveryPlanVariable', policy.recoveryPlanVariable)
  appendOptionalAttribute(attributes, 'maxAttempts', policy.maxAttempts)
  appendOptionalAttribute(attributes, 'maxReconcileAttempts', policy.maxReconcileAttempts)
  appendOptionalAttribute(attributes, 'recoveryDelay', policy.recoveryDelay)
  appendOptionalAttribute(attributes, 'maxRecoveryDuration', policy.maxRecoveryDuration)
  const open = `${indent}<${dialect.prefix}effectPolicy${attributes.length ? ` ${attributes.join(' ')}` : ''}`
  if (!policy.reconcileAction) return `${open}/>`
  return [
    open + '>',
    generateReconcileActionXml(
      policy.reconcileAction,
      indent + indentUnit,
      indentUnit,
      dialect,
      requestFields
    ),
    `${indent}</${dialect.prefix}effectPolicy>`,
  ].join('\n')
}

export function parseInvocationPolicyElement(element: Element): InvocationPolicy {
  requireOnlyAttributes(element, [
    'timeout',
    'attemptTimeout',
    'maxAttempts',
    'initialBackoff',
    'backoffMultiplier',
    'maxBackoff',
    'jitter',
    'retryOn',
    'onFailure',
  ])
  if (element.children.length > 0) throw new Error(`${element.tagName} must not contain children`)
  const policy: InvocationPolicy = {
    timeout: optionalAttributePreservingEmpty(element, 'timeout'),
    attemptTimeout: optionalAttributePreservingEmpty(element, 'attemptTimeout'),
    maxAttempts: optionalNumberAttribute(element, 'maxAttempts'),
    initialBackoff: optionalAttributePreservingEmpty(element, 'initialBackoff'),
    backoffMultiplier: optionalNumberAttribute(element, 'backoffMultiplier'),
    maxBackoff: optionalAttributePreservingEmpty(element, 'maxBackoff'),
    jitter: optionalAttributePreservingEmpty(element, 'jitter') as InvocationPolicy['jitter'],
    retryOn: optionalAttributePreservingEmpty(element, 'retryOn'),
    onFailure: optionalAttributePreservingEmpty(element, 'onFailure'),
  }
  validateInvocationPolicy(policy)
  return policy
}

export function generateInvocationPolicyXml(
  policy: InvocationPolicy,
  indent: string,
  dialect: ActionXmlDialect
): string {
  validateInvocationPolicy(policy)
  const attributes: string[] = []
  appendOptionalAttribute(attributes, 'timeout', policy.timeout)
  appendOptionalAttribute(attributes, 'attemptTimeout', policy.attemptTimeout)
  appendOptionalAttribute(attributes, 'maxAttempts', policy.maxAttempts)
  appendOptionalAttribute(attributes, 'initialBackoff', policy.initialBackoff)
  appendOptionalAttribute(attributes, 'backoffMultiplier', policy.backoffMultiplier)
  appendOptionalAttribute(attributes, 'maxBackoff', policy.maxBackoff)
  appendOptionalAttribute(attributes, 'jitter', policy.jitter)
  appendOptionalAttribute(attributes, 'retryOn', policy.retryOn)
  appendOptionalAttribute(attributes, 'onFailure', policy.onFailure)
  return `${indent}<${dialect.prefix}invocationPolicy${attributes.length ? ` ${attributes.join(' ')}` : ''}/>`
}

function requireSerializableAction(action: ActionDefinition): void {
  validateActionPolicyBoundary(action)
  const actionType = action.actionType
  const findings = actionFindings(action)
  if (findings.length > 0) {
    throw new Error(
      `Invalid ${actionType} action: ${findings.map((finding) => finding.code).join(', ')}`
    )
  }
  validateSerializableMappings(action.mappings)
}

function validateActionPolicyBoundary(action: ActionDefinition): void {
  if (action.execution && action.execution !== 'replayable' && action.execution !== 'effect') {
    throw new Error('Action execution must be one of: replayable, effect')
  }
  if (action.effectPolicy) {
    validateEffectPolicy(action.effectPolicy, action.execution)
  }
}

function requireSerializableReconcileAction(
  action: ReconcileActionDefinition,
  requestFields: ReadonlySet<string>
): void {
  const findings = reconcileActionFindings(action, requestFields)
  if (findings.length > 0) {
    throw new Error(
      `Invalid ${action.actionType} reconcile action: ${findings
        .map((finding) => finding.code)
        .join(', ')}`
    )
  }
  const targets = new Set<string>()
  for (const input of action.inputs ?? []) {
    if (input.source !== input.source.trim() || !isJavaIdentifier(input.source)) {
      throw new Error(`Reconcile input source must be a field name: ${input.source}`)
    }
    if (input.target !== input.target.trim()) {
      throw new Error(`Reconcile input target must be canonical: ${input.target}`)
    }
    requireGeneratedJavaIdentifier(input.target, 'Reconcile input target')
    if (!input.dataType.trim() || input.dataType !== input.dataType.trim()) {
      throw new Error(`Reconcile input dataType must be canonical: ${input.dataType}`)
    }
    if (!targets.add(input.target)) {
      throw new Error(`Reconcile action has duplicate input target: ${input.target}`)
    }
  }
}

function validateSerializableMappings(variables: VariableMapping[] | undefined): void {
  const mappings = new Set<string>()
  let outputCount = 0
  for (const mapping of variables || []) {
    validateSerializableMapping(mapping, mappings)
    outputCount += mapping.direction === 'output' ? 1 : 0
  }
  if (outputCount > 1) {
    throw new Error('Action must declare at most one output mapping')
  }
}

function validateSerializableMapping(mapping: VariableMapping, mappings: Set<string>): void {
  if (!mapping.target?.trim() || !mapping.dataType?.trim()) {
    throw new Error('Action mapping target and dataType must not be blank')
  }
  if (mapping.target !== mapping.target.trim()) {
    throw new Error(`Action mapping target must be canonical: ${mapping.target}`)
  }
  if (mapping.dataType !== mapping.dataType.trim()) {
    throw new Error(`Action mapping dataType must be canonical: ${mapping.dataType}`)
  }
  requireGeneratedJavaIdentifier(mapping.target, 'Action mapping target')
  const mappingKey = `${mapping.direction}\u0000${mapping.target}`
  if (mappings.has(mappingKey)) {
    throw new Error(`Action has duplicate ${mapping.direction} mapping target: ${mapping.target}`)
  }
  mappings.add(mappingKey)
}

export function generateMappedVariableXml(
  mapping: VariableMapping,
  indent: string,
  dialect: ActionXmlDialect = TBBPM_ACTION_XML,
  processCall = false
): string {
  const input = mapping.direction === 'input'
  const tag = input ? 'input' : 'output'
  requireUsableMappingDefault(mapping, `${dialect.prefix}${tag}`)
  const attributes: string[] = []
  if (input && mapping.source) {
    attributes.push(`source="${escapeXml(mapping.source)}"`)
  }
  if (!input && processCall) {
    attributes.push(`source="${escapeXml(mapping.source!)}"`)
  }
  attributes.push(`target="${escapeXml(mapping.target)}"`)
  if (!processCall) {
    attributes.push(`dataType="${escapeXml(mapping.dataType!)}"`)
  }
  if (input && mapping.defaultValue !== undefined) {
    attributes.push(`defaultValue="${escapeXml(mapping.defaultValue)}"`)
  }
  return `${indent}<${dialect.prefix}${tag} ${attributes.join(' ')}/>`
}

function requireUsableMappingDefault(mapping: VariableMapping, location: string): void {
  const conflict = mappingDefaultConflict(mapping)
  if (conflict === 'output') {
    throw new Error(`${location} output mapping must not declare defaultValue`)
  }
  if (conflict === 'source') {
    throw new Error(`${location} input mapping must not declare both source and defaultValue`)
  }
}

function validateActionShape(
  actionType: string,
  actionElement: Element,
  dialect: ActionXmlDialect,
  reconcile: boolean
): void {
  const allowedAttributes = new Set(['type'])
  const allowedChildren = new Set(['input'])
  if (!reconcile) {
    allowedChildren.add('output')
    allowedAttributes.add('execution')
    allowedChildren.add('invocationPolicy')
    allowedChildren.add('effectPolicy')
  }

  if (actionType === 'java') {
    allowedAttributes.add('class')
    allowedAttributes.add('method')
  } else if (actionType === 'spring-bean') {
    allowedAttributes.add('bean')
    allowedAttributes.add('class')
    allowedAttributes.add('method')
  } else if (actionType === 'script') {
    allowedAttributes.add('language')
    allowedChildren.add('code')
  } else {
    throw new Error(`Unsupported action type "${actionType}"`)
  }

  Array.from(actionElement.attributes).forEach((attribute) => {
    if (attribute.namespaceURI || !allowedAttributes.has(attribute.localName)) {
      throw new Error(
        `action attribute "${attribute.name}" is not valid for action type "${actionType}"`
      )
    }
  })
  directChildren(actionElement).forEach((child) => {
    if (!matchesDialect(child, dialect) || !allowedChildren.has(child.localName)) {
      throw new Error(
        `action child "${child.tagName}" is not valid for action type "${actionType}"`
      )
    }
  })
  validateActionChildOrder(actionElement, reconcile)

  if (directChildrenNamed(actionElement, 'code', dialect).length > 1) {
    throw new Error(`action type "${actionType}" must contain at most one code element`)
  }
}

function validateActionChildOrder(actionElement: Element, reconcile: boolean): void {
  const ranks: Record<string, number> = {
    input: 0,
    output: 1,
    code: 2,
    invocationPolicy: 3,
    effectPolicy: 4,
  }
  let previous = -1
  directChildren(actionElement).forEach((child) => {
    const rank = ranks[child.localName]
    if (rank === undefined || (reconcile && rank > 2)) return
    if (rank < previous) {
      throw new Error(`${actionElement.tagName} children are not in canonical protocol order`)
    }
    previous = rank
  })
}

function parseActionCode(actionElement: Element, dialect: ActionXmlDialect): string | undefined {
  const codeElement = directChildrenNamed(actionElement, 'code', dialect)[0]
  if (!codeElement) return undefined
  requireOnlyAttributes(codeElement, [])
  return codeElement.textContent ?? undefined
}

function actionImplementationAttributes(action: ActionInvocationDefinition): string[] {
  switch (action.actionType) {
    case 'java':
      return optionalXmlAttributes([
        ['class', action.className],
        ['method', action.method],
      ])
    case 'spring-bean':
      return optionalXmlAttributes([
        ['bean', action.bean],
        ['class', action.className],
        ['method', action.method],
      ])
    case 'script':
      return optionalXmlAttributes([['language', action.language]])
  }
}

function optionalXmlAttributes(values: Array<[string, string | undefined]>): string[] {
  return values.flatMap(([name, value]) => (value ? [`${name}="${escapeXml(value)}"`] : []))
}

function parseActionExecution(actionElement: Element): ActionDefinition['execution'] {
  const value = actionElement.getAttribute('execution')
  if (value === null) return undefined
  if (value === 'replayable' || value === 'effect') {
    return value
  }
  throw new Error('action execution must be one of: replayable, effect')
}

function actionElementAttributes(action: ActionInvocationDefinition): string {
  const attributes = [`type="${escapeXml(action.actionType)}"`]
  if ('execution' in action && action.execution) attributes.push(`execution="${action.execution}"`)
  attributes.push(...actionImplementationAttributes(action))
  return ` ${attributes.join(' ')}`
}

function generateActionCodeXml(code: string, indent: string, dialect: ActionXmlDialect): string {
  const safeCode = code.replace(/\]\]>/g, ']]>]]<![CDATA[>')
  return `${indent}<${dialect.prefix}code><![CDATA[${safeCode}]]></${dialect.prefix}code>`
}

function parseActionType(element: Element): ActionType {
  const value = requireNonBlankAttribute(element, 'type')
  if (value === 'java' || value === 'spring-bean' || value === 'script') return value
  throw new Error(`Unsupported action type "${value}"`)
}

function directChildren(element: Element): Element[] {
  return Array.from(element.children)
}

function directChildrenNamed(
  element: Element,
  localName: string,
  dialect: ActionXmlDialect
): Element[] {
  return directChildren(element).filter(
    (child) => child.localName === localName && matchesDialect(child, dialect)
  )
}

function optionalSingleChild(
  element: Element,
  localName: string,
  dialect: ActionXmlDialect
): Element | undefined {
  const children = directChildrenNamed(element, localName, dialect)
  if (children.length > 1) {
    throw new Error(`${element.tagName} must contain at most one ${localName} element`)
  }
  return children[0]
}

function matchesDialect(element: Element, dialect: ActionXmlDialect): boolean {
  return element.namespaceURI === dialect.namespaceUri
}

function assertElementDialect(
  element: Element,
  localName: string,
  dialect: ActionXmlDialect
): void {
  if (element.localName !== localName || !matchesDialect(element, dialect)) {
    throw new Error(`Expected ${dialect.prefix}${localName}, received ${element.tagName}`)
  }
}

function requireOnlyAttributes(element: Element, allowedNames: string[]): void {
  const allowed = new Set(allowedNames)
  Array.from(element.attributes).forEach((attribute) => {
    if (attribute.namespaceURI || !allowed.has(attribute.localName)) {
      throw new Error(`${element.tagName} contains unsupported attribute "${attribute.name}"`)
    }
  })
}

function requireNonBlankAttribute(element: Element, name: string): string {
  const value = element.getAttribute(name)
  if (value === null || value.trim() === '') {
    throw new Error(`${element.tagName} must declare a non-blank ${name} attribute`)
  }
  return value
}

function optionalAttribute(element: Element, name: string): string | undefined {
  const value = element.getAttribute(name)
  return value === null || value === '' ? undefined : value
}

function optionalNumberAttribute(element: Element, name: string): number | undefined {
  const value = element.getAttribute(name)
  if (value === null) return undefined
  if (value === '') throw new Error(`${element.tagName} ${name} must be a number`)
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) throw new Error(`${element.tagName} ${name} must be a number`)
  return parsed
}

function appendOptionalAttribute(
  attributes: string[],
  name: string,
  value: string | number | undefined
): void {
  if (value !== undefined) attributes.push(`${name}="${escapeXml(String(value))}"`)
}

function optionalAttributePreservingEmpty(element: Element, name: string): string | undefined {
  const value = element.getAttribute(name)
  return value === null ? undefined : value
}
