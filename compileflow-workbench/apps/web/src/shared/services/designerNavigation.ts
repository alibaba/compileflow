import type { NavigateFunction, NavigateOptions } from 'react-router-dom'

import {
  buildDesignerRoute,
  type DesignerRouteProcessType,
  type DesignerRouteSource,
  normalizeDesignerRouteProcessType,
} from '@/shared/constants'

export interface DesignerEntryDescriptor {
  source: DesignerRouteSource
  modelType: DesignerRouteProcessType
  processId: string | null
  templateId: string | null
  exampleId: string | null
  processCode: string | null
}

export interface OpenNewDesignerOptions {
  modelType?: string | null
}

export interface OpenWorkspaceProcessOptions {
  processId: string
  modelType?: string | null
}

export interface OpenTemplateDesignerOptions {
  templateId: string
  modelType?: string | null
}

export interface OpenExampleDesignerOptions {
  exampleId: string
  modelType?: string | null
}

export interface OpenOperateProcessDesignerOptions {
  processCode: string
  modelType?: string | null
}

export interface DesignerCapabilityState {
  enabled: boolean
  reason?: string
  entryDescriptor: DesignerEntryDescriptor
}

function createDescriptor(
  source: DesignerRouteSource,
  modelType: string | null | undefined,
  fields: {
    processId?: string | null
    templateId?: string | null
    exampleId?: string | null
    processCode?: string | null
  } = {}
): DesignerEntryDescriptor {
  return {
    source,
    modelType: normalizeDesignerRouteProcessType(modelType),
    processId: fields.processId ?? null,
    templateId: fields.templateId ?? null,
    exampleId: fields.exampleId ?? null,
    processCode: fields.processCode ?? null,
  }
}

export function createNewDesignerEntry(
  options: OpenNewDesignerOptions = {}
): DesignerEntryDescriptor {
  return createDescriptor('new', options.modelType)
}

function createWorkspaceProcessDesignerEntry(
  options: OpenWorkspaceProcessOptions
): DesignerEntryDescriptor {
  return createDescriptor('workspaceProcess', options.modelType, { processId: options.processId })
}

function createTemplateDesignerEntry(
  options: OpenTemplateDesignerOptions
): DesignerEntryDescriptor {
  return createDescriptor('template', options.modelType, { templateId: options.templateId })
}

function createExampleDesignerEntry(options: OpenExampleDesignerOptions): DesignerEntryDescriptor {
  return createDescriptor('example', options.modelType, { exampleId: options.exampleId })
}

function createOperateProcessCodeDesignerEntry(
  options: OpenOperateProcessDesignerOptions
): DesignerEntryDescriptor {
  return createDescriptor('operateProcessCode', options.modelType, {
    processCode: options.processCode,
  })
}

export function parseDesignerEntryDescriptor(
  searchParams: URLSearchParams
): DesignerEntryDescriptor {
  const source = searchParams.get('source')
  const modelType = searchParams.get('modelType')
  const processId = searchParams.get('processId')
  const templateId = searchParams.get('templateId')
  const exampleId = searchParams.get('exampleId')
  const processCode = searchParams.get('processCode')

  if (source === 'workspaceProcess') {
    return createDescriptor('workspaceProcess', modelType, { processId })
  }
  if (source === 'template') {
    return createDescriptor('template', modelType, { templateId })
  }
  if (source === 'example') {
    return createDescriptor('example', modelType, { exampleId })
  }
  if (source === 'operateProcessCode') {
    return createDescriptor('operateProcessCode', modelType, { processCode })
  }
  if (source === 'new') {
    return createDescriptor('new', modelType)
  }
  if (source === null) {
    return createDescriptor('new', modelType)
  }
  throw new Error(`Unsupported designer entry source: ${source}`)
}

function createDesignerPath(entryDescriptor: DesignerEntryDescriptor): string {
  return buildDesignerRoute({
    modelType: entryDescriptor.modelType,
    processId: entryDescriptor.processId,
    templateId: entryDescriptor.templateId,
    exampleId: entryDescriptor.exampleId,
    processCode: entryDescriptor.processCode,
    source: entryDescriptor.source,
  })
}

function navigateWithDescriptor(
  navigate: NavigateFunction,
  entryDescriptor: DesignerEntryDescriptor,
  options?: NavigateOptions
): void {
  void navigate(createDesignerPath(entryDescriptor), options)
}

export function openNewDesigner(
  navigate: NavigateFunction,
  options: OpenNewDesignerOptions = {},
  navigationOptions?: NavigateOptions
) {
  navigateWithDescriptor(navigate, createNewDesignerEntry(options), navigationOptions)
}

export function openDesignerFromWorkspaceProcess(
  navigate: NavigateFunction,
  options: OpenWorkspaceProcessOptions,
  navigationOptions?: NavigateOptions
) {
  navigateWithDescriptor(navigate, createWorkspaceProcessDesignerEntry(options), navigationOptions)
}

export function openDesignerFromTemplate(
  navigate: NavigateFunction,
  options: OpenTemplateDesignerOptions,
  navigationOptions?: NavigateOptions
) {
  navigateWithDescriptor(navigate, createTemplateDesignerEntry(options), navigationOptions)
}

export function openDesignerFromExample(
  navigate: NavigateFunction,
  options: OpenExampleDesignerOptions,
  navigationOptions?: NavigateOptions
) {
  navigateWithDescriptor(navigate, createExampleDesignerEntry(options), navigationOptions)
}

export function openDesignerFromOperateProcessCode(
  navigate: NavigateFunction,
  options: OpenOperateProcessDesignerOptions,
  navigationOptions?: NavigateOptions
) {
  navigateWithDescriptor(
    navigate,
    createOperateProcessCodeDesignerEntry(options),
    navigationOptions
  )
}

export function getOperateProcessDesignerCapability(
  processCode: string,
  modelType?: string | null
): DesignerCapabilityState {
  const trimmedCode = processCode?.trim()
  return {
    enabled: Boolean(trimmedCode),
    entryDescriptor: createOperateProcessCodeDesignerEntry({ processCode: trimmedCode, modelType }),
  }
}
