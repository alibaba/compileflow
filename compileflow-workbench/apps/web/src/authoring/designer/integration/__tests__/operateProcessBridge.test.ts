import { describe, expect, it } from 'vitest'

import {
  buildOperateProcessId,
  isOperateBoundProcessId,
  mapDesignerToOperateUpdate,
  mapOperateDefinitionToUnified,
} from '@/authoring/designer/integration/operateProcessBridge'
import type { ProcessDefinition } from '@/shared/contracts'
import { DEFAULT_BPMN_WITH_EVENTS_XML } from '@/shared/processes/bpmnTemplates'
import { DEFAULT_TBBPM_WITH_NODES_XML } from '@/shared/processes/tbbpmTemplates'

describe('operateProcessBridge', () => {
  const sampleTbbpmProcess: ProcessDefinition = {
    code: 'payment-process-tbbpm',
    name: '支付处理流程',
    type: 'TBBPM',
    xml: DEFAULT_TBBPM_WITH_NODES_XML,
    tags: [],
    createdAt: '2026-01-20T09:00:00Z',
    updatedAt: '2026-02-09T16:45:00Z',
    revision: 3,
    createdBy: 'tester',
  }

  it('builds stable operate-bound designer ids', () => {
    expect(buildOperateProcessId('order-approval-bpmn')).toBe('operate:order-approval-bpmn')
    expect(isOperateBoundProcessId('operate:order-approval-bpmn')).toBe(true)
    expect(isOperateBoundProcessId('uuid-123')).toBe(false)
  })

  it('maps operate definitions into unified designer flows', () => {
    const { flow, warnings } = mapOperateDefinitionToUnified(sampleTbbpmProcess)

    expect(flow.id).toBe('operate:payment-process-tbbpm')
    expect(flow.code).toBe('payment-process-tbbpm')
    expect(flow.name).toBe('支付处理流程')
    expect(flow.type).toBe('TBBPM')
    expect(flow.nodes.length).toBeGreaterThan(0)
    expect(Array.isArray(warnings)).toBe(true)
  })

  it('maps designer state back to operate update payloads', () => {
    const { flow } = mapOperateDefinitionToUnified(sampleTbbpmProcess)
    const update = mapDesignerToOperateUpdate(
      flow,
      DEFAULT_TBBPM_WITH_NODES_XML,
      sampleTbbpmProcess.revision
    )

    expect(update.name).toBe(flow.name)
    expect(update.xml).toBe(DEFAULT_TBBPM_WITH_NODES_XML)
    expect(update.expectedRevision).toBe(3)
    expect(update).not.toHaveProperty('type')
    expect(update).not.toHaveProperty('status')
    expect(update).not.toHaveProperty('version')
  })

  it('rejects empty xml', () => {
    expect(() => mapOperateDefinitionToUnified({ ...sampleTbbpmProcess, xml: '  ' })).toThrow(
      'Process XML is empty'
    )
  })

  it('rejects invalid contract timestamps instead of inventing local values', () => {
    expect(() =>
      mapOperateDefinitionToUnified({ ...sampleTbbpmProcess, updatedAt: 'not-a-timestamp' })
    ).toThrow('Process updatedAt is not a valid timestamp')
  })

  it('maps BPMN operate definitions', () => {
    const { flow } = mapOperateDefinitionToUnified({
      code: 'order-approval-bpmn',
      name: '订单审批流程',
      type: 'BPMN',
      xml: DEFAULT_BPMN_WITH_EVENTS_XML,
      tags: [],
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-02T00:00:00Z',
      revision: 0,
      createdBy: 'tester',
    })

    expect(flow.type).toBe('BPMN')
    expect(flow.code).toBe('order-approval-bpmn')
    expect(flow.nodes.length).toBeGreaterThan(0)
  })
})
