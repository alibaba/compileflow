import { describe, expect, test } from 'vitest'

import type { ActionDefinition } from '../../types/action'
import { actionFindings } from '../actionValidation'

describe('actionFindings', () => {
  test('validates every built-in implementation shape with one shared contract', () => {
    expect(actionFindings({ actionType: 'java' }).map(({ code }) => code)).toEqual([
      'action.missingClass',
    ])
    expect(
      actionFindings({
        actionType: 'spring-bean',
        bean: ' ',
        className: 'invalid/class',
        method: '1run',
      }).map(({ code }) => code)
    ).toEqual(['action.missingBean', 'action.invalidClass', 'action.invalidMethod'])
    expect(
      actionFindings({ actionType: 'script', language: 'java', source: ' ' }).map(
        ({ code }) => code
      )
    ).toEqual(['action.missingScriptSource'])
    expect(
      actionFindings({ actionType: 'script', source: 'return 1;' }).map(({ code }) => code)
    ).toEqual(['action.missingScriptLanguage'])
  })

  test('rejects action types outside the stable three-variant surface', () => {
    expect(actionFindings({ actionType: 'groovy' } as unknown as ActionDefinition)).toEqual([
      { code: 'action.unsupportedType', params: { actionType: 'groovy' } },
    ])
  })

  test('rejects a Spring bean identity with surrounding whitespace', () => {
    expect(
      actionFindings({
        actionType: 'spring-bean',
        bean: ' orderService ',
        className: 'java.lang.Runnable',
      }).map(({ code }) => code)
    ).toEqual(['action.invalidBean'])
  })

  test('enforces the canonical script language length boundary', () => {
    expect(
      actionFindings({ actionType: 'script', language: 'x'.repeat(257), source: 'return 1;' }).map(
        ({ code }) => code
      )
    ).toEqual(['action.invalidScriptLanguage'])
  })

  test('restricts reconcile inputs to the persisted Effect request', () => {
    const findings = actionFindings({
      actionType: 'java',
      className: 'com.example.Send',
      execution: 'effect',
      mappings: [
        { direction: 'input', source: 'order.id', target: 'orderId', dataType: 'java.lang.String' },
      ],
      effectPolicy: {
        recovery: 'reconcile',
        maxAttempts: 1,
        maxReconcileAttempts: 1,
        recoveryDelay: 'PT1S',
        reconcileAction: {
          actionType: 'java',
          className: 'com.example.Query',
          inputs: [
            { source: 'missing', target: 'requestId', dataType: 'java.lang.String' },
            { source: '__cf_effect_id', target: '__cf_effect_id', dataType: 'java.lang.String' },
          ],
        },
      },
    })

    expect(findings).toEqual([
      {
        code: 'effectPolicy.invalid',
        params: { message: 'Reconcile input references unknown Effect request field: missing' },
      },
      {
        code: 'effectPolicy.invalid',
        params: { message: 'Invalid reconcile input target: __cf_effect_id' },
      },
    ])
  })

  test('does not treat injected Effect metadata targets as persisted request fields', () => {
    const findings = actionFindings({
      actionType: 'java',
      className: 'com.example.Send',
      execution: 'effect',
      mappings: [
        {
          direction: 'input',
          source: '__cf_effect_id',
          target: 'effectId',
          dataType: 'java.lang.String',
        },
      ],
      effectPolicy: {
        recovery: 'reconcile',
        maxAttempts: 1,
        maxReconcileAttempts: 1,
        recoveryDelay: 'PT1S',
        reconcileAction: {
          actionType: 'java',
          className: 'com.example.Query',
          inputs: [{ source: 'effectId', target: 'effectId', dataType: 'java.lang.String' }],
        },
      },
    })

    expect(findings).toEqual([
      {
        code: 'effectPolicy.invalid',
        params: {
          message: 'Reconcile input references unknown Effect request field: effectId',
        },
      },
    ])
  })

  test('rejects Effect attempt as a business input source', () => {
    const findings = actionFindings({
      actionType: 'java',
      className: 'com.example.Send',
      execution: 'effect',
      mappings: [
        {
          direction: 'input',
          source: '__cf_effect_attempt',
          target: 'attempt',
          dataType: 'java.lang.Integer',
        },
      ],
    })

    expect(findings).toEqual([
      {
        code: 'effectPolicy.invalid',
        params: { message: 'Unsupported Effect metadata input source: __cf_effect_attempt' },
      },
    ])
  })
})
