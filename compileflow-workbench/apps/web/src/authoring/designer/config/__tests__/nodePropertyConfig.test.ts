import { describe, expect, it } from 'vitest'

import { getBpmnPropertyConfig } from '../bpmnPropertyConfig'
import { getNodePropertyConfig } from '../nodePropertyConfig'

describe('node property configuration', () => {
  it('maps every editable TBBPM node to a specific property panel', () => {
    const editableTypes = [
      'note',
      'autoTask',
      'scriptTask',
      'exclusive',
      'parallel',
      'inclusive',
      'subBpm',
      'bpmCall',
      'waitTask',
      'waitEventTask',
      'timerTask',
      'while',
      'foreach',
      'break',
      'continue',
    ] as const

    for (const type of editableTypes) {
      expect(getNodePropertyConfig(type)).not.toBeNull()
    }
    expect(getNodePropertyConfig('start')).toBeNull()
    expect(getNodePropertyConfig('end')).toBeNull()
  })

  it('maps every editable BPMN node to a specific property panel', () => {
    const editableTypes = [
      'bpmn:ServiceTask',
      'bpmn:ScriptTask',
      'bpmn:ReceiveTask',
      'bpmn:ExclusiveGateway',
      'bpmn:ParallelGateway',
      'bpmn:InclusiveGateway',
      'bpmn:CallActivity',
      'bpmn:SubProcess',
    ] as const

    for (const type of editableTypes) {
      expect(getBpmnPropertyConfig(type)).not.toBeNull()
    }
    expect(getBpmnPropertyConfig('bpmn:StartEvent')).toBeNull()
    expect(getBpmnPropertyConfig('bpmn:EndEvent')).toBeNull()
  })
})
