import { describe, expect, it } from 'vitest'

import {
  getOperateProcessDesignerCapability,
  parseDesignerEntryDescriptor,
} from '@/shared/services/designerNavigation'

describe('parseDesignerEntryDescriptor', () => {
  it('parses an explicit workspace process entry', () => {
    const params = new URLSearchParams('source=workspaceProcess&processId=flow-1&modelType=tbbpm')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('workspaceProcess')
    expect(descriptor.processId).toBe('flow-1')
    expect(descriptor.modelType).toBe('tbbpm')
    expect(descriptor.exampleId).toBeNull()
    expect(descriptor.templateId).toBeNull()
  })

  it('parses an explicit example entry', () => {
    const params = new URLSearchParams('source=example&exampleId=ex-1&modelType=bpmn')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('example')
    expect(descriptor.exampleId).toBe('ex-1')
    expect(descriptor.modelType).toBe('bpmn')
    expect(descriptor.processId).toBeNull()
    expect(descriptor.templateId).toBeNull()
  })

  it('parses an explicit template entry', () => {
    const params = new URLSearchParams('source=template&templateId=tpl-1&modelType=bpmn')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('template')
    expect(descriptor.templateId).toBe('tpl-1')
    expect(descriptor.modelType).toBe('bpmn')
    expect(descriptor.processId).toBeNull()
    expect(descriptor.exampleId).toBeNull()
  })

  it('defaults to new source when no identifying params are present', () => {
    const params = new URLSearchParams('source=new&modelType=bpmn')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('new')
    expect(descriptor.processId).toBeNull()
    expect(descriptor.exampleId).toBeNull()
    expect(descriptor.templateId).toBeNull()
  })

  it('parses explicit operateProcessCode source', () => {
    const params = new URLSearchParams(
      'source=operateProcessCode&processCode=order-approval-bpmn&modelType=bpmn'
    )
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('operateProcessCode')
    expect(descriptor.processCode).toBe('order-approval-bpmn')
    expect(descriptor.modelType).toBe('bpmn')
  })

  it('does not infer an entry source from unrelated query fields', () => {
    const params = new URLSearchParams('processCode=payment-process-tbbpm&modelType=tbbpm')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('new')
    expect(descriptor.processCode).toBeNull()
  })

  it('ignores identifiers that do not belong to the explicit source', () => {
    const params = new URLSearchParams('source=new&processId=flow-1&exampleId=ex-1&modelType=bpmn')
    const descriptor = parseDesignerEntryDescriptor(params)

    expect(descriptor.source).toBe('new')
    expect(descriptor.processId).toBeNull()
    expect(descriptor.exampleId).toBeNull()
  })
})

describe('getOperateProcessDesignerCapability', () => {
  it('enables designer entry when process code is present', () => {
    const capability = getOperateProcessDesignerCapability('order-approval-bpmn', 'BPMN')

    expect(capability.enabled).toBe(true)
    expect(capability.entryDescriptor.source).toBe('operateProcessCode')
    expect(capability.entryDescriptor.processCode).toBe('order-approval-bpmn')
  })

  it('disables designer entry when process code is empty', () => {
    const capability = getOperateProcessDesignerCapability('   ', 'BPMN')
    expect(capability.enabled).toBe(false)
  })
})
