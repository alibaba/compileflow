import { describe, expect, test } from 'vitest'

import { isBpmnNodeType } from '../bpmnNodeTypes'
import { isTbbpmNodeType } from '../tbbpm'

describe('designer node type guards', () => {
  test.each(['bpmn:StartEvent', 'bpmn:SubProcess'])('accepts supported BPMN type %s', (type) => {
    expect(isBpmnNodeType(type)).toBe(true)
  })

  test.each(['bpmn:Unknown', 'StartEvent', ''])('rejects unsupported BPMN type %s', (type) => {
    expect(isBpmnNodeType(type)).toBe(false)
  })

  test.each(['start', 'while', 'foreach'])('accepts supported TBBPM type %s', (type) => {
    expect(isTbbpmNodeType(type)).toBe(true)
  })

  test.each(['loop', 'serviceTask', ''])('rejects unsupported TBBPM type %s', (type) => {
    expect(isTbbpmNodeType(type)).toBe(false)
  })
})
