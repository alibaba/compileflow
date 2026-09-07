import { describe, expect, test } from 'vitest'

import type { ProcessVariable } from '../../types/flowDefinition'
import { generateBpmnXml, parseBpmnXml } from '../bpmnXmlCodec'
import { generateTbbpmXml, parseTbbpmXml } from '../tbbpmXmlCodec'

function draft(variable: ProcessVariable) {
  return {
    id: 'variable-contract',
    code: 'variable-contract',
    name: 'Variable contract',
    nodes: [],
    connections: [],
    variables: [variable],
  }
}

describe('process-variable serialization contract', () => {
  test.each(['TBBPM', 'BPMN'] as const)(
    'preserves XML attribute whitespace for %s defaults',
    (type) => {
      const variable: ProcessVariable = {
        name: 'message',
        type: 'java.lang.String',
        inOutType: 'param',
        defaultValue: 'first\nsecond\tthird\rfourth',
      }
      const result =
        type === 'TBBPM'
          ? parseTbbpmXml(generateTbbpmXml({ ...draft(variable), type }))
          : parseBpmnXml(generateBpmnXml({ ...draft(variable), type }))

      expect(result.success).toBe(true)
      expect(result.data?.variables?.[0].defaultValue).toBe(variable.defaultValue)
    }
  )

  test('rejects invalid Java names when importing either model format', () => {
    const tbbpm = parseTbbpmXml(
      '<bpm code="invalid"><var name="order-id" dataType="java.lang.String" inOutType="param"/></bpm>'
    )
    const bpmn = parseBpmnXml(`<?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions
          xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
          xmlns:cf="http://www.compileflow.org"
          id="definitions"
          targetNamespace="urn:compileflow:test">
        <bpmn:process id="invalid" isExecutable="true">
          <bpmn:extensionElements>
            <cf:var name="order-id" dataType="java.lang.String" inOutType="param"/>
          </bpmn:extensionElements>
        </bpmn:process>
      </bpmn:definitions>`)

    expect(tbbpm.success).toBe(false)
    expect(tbbpm.error?.message).toContain('valid Java identifier')
    expect(bpmn.success).toBe(false)
    expect(bpmn.error?.message).toContain('valid Java identifier')
  })

  test.each(['_cf$state', '__cf_effect_id'])(
    'rejects the reserved namespace %s when exporting either model format',
    (name) => {
      const reservedVariable: ProcessVariable = {
        name,
        type: 'java.lang.String',
        inOutType: 'inner',
      }

      expect(() => generateTbbpmXml({ ...draft(reservedVariable), type: 'TBBPM' })).toThrow(
        'reserved CompileFlow identifier prefix'
      )
      expect(() => generateBpmnXml({ ...draft(reservedVariable), type: 'BPMN' })).toThrow(
        'reserved CompileFlow identifier prefix'
      )
    }
  )

  test('rejects surrounding whitespace in TBBPM variable data types', () => {
    const parsed = parseTbbpmXml(
      '<bpm code="invalid"><var name="order" dataType=" java.lang.String " inOutType="param"/></bpm>'
    )
    const variable: ProcessVariable = {
      name: 'order',
      type: ' java.lang.String ',
      inOutType: 'param',
    }

    expect(parsed.success).toBe(false)
    expect(parsed.error?.message).toContain('dataType must not contain surrounding whitespace')
    expect(() => generateTbbpmXml({ ...draft(variable), type: 'TBBPM' })).toThrow(
      'dataType must not contain surrounding whitespace'
    )
  })
})
