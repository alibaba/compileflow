import { describe, expect, test } from 'vitest'

import { safeParseTbbpmNode } from '../../schemas/tbbpmSchemas'
import type { BpmnNode } from '../flowDefinition'
import {
  findInapplicableBpmnNodeProperties,
  findInapplicableTbbpmNodeProperties,
} from '../nodePropertyContracts'
import type { TbbpmNode } from '../tbbpm'

describe('node property ownership', () => {
  test('uses one nested action contract for TBBPM tasks', () => {
    const task: TbbpmNode = {
      id: 'task',
      type: 'autoTask',
      position: { x: 0, y: 0 },
      properties: {
        action: {
          actionType: 'java',
          className: 'com.example.Task',
          method: 'run',
        },
      },
    }

    expect(findInapplicableTbbpmNodeProperties(task)).toEqual([])
    expect(safeParseTbbpmNode(task).success).toBe(true)
    expect(
      safeParseTbbpmNode({
        ...task,
        properties: { actionType: 'java', className: 'com.example.Task' },
      }).success
    ).toBe(false)
  })

  test('rejects task properties on pure TBBPM gateways', () => {
    const gateway: TbbpmNode = {
      id: 'route',
      type: 'exclusive',
      position: { x: 0, y: 0 },
      properties: {
        action: {
          actionType: 'java',
          className: 'com.example.Router',
          method: undefined,
        },
      },
    }

    expect(findInapplicableTbbpmNodeProperties(gateway)).toEqual(['action'])
    expect(safeParseTbbpmNode(gateway).success).toBe(false)
  })

  test('rejects execution policy and loops on BPMN gateways', () => {
    const gateway: BpmnNode = {
      id: 'route',
      type: 'bpmn:ExclusiveGateway',
      position: { x: 0, y: 0 },
      properties: {
        default: 'fallback',
        invocationPolicy: { timeout: 'PT1S' },
        loopCharacteristics: undefined,
      } as unknown as BpmnNode['properties'],
    }

    expect(findInapplicableBpmnNodeProperties(gateway)).toEqual(['invocationPolicy'])
  })

  test('rejects unmodeled script properties instead of silently dropping them from XML', () => {
    const script: BpmnNode = {
      id: 'script',
      type: 'bpmn:ScriptTask',
      position: { x: 0, y: 0 },
      properties: {
        scriptFormat: 'qlexpress',
        script: 'return true',
        concurrency: 4,
      } as unknown as BpmnNode['properties'],
    }

    expect(findInapplicableBpmnNodeProperties(script)).toEqual(['concurrency'])
  })
})
