import { describe, expect, it } from 'vitest'

import { BPMN_ACTION_XML, generateActionElementXml } from '../actionXml'

describe('action XML mapping identity', () => {
  it('rejects duplicate BPMN action input targets before serialization', () => {
    expect(() =>
      generateActionElementXml(
        'action',
        {
          actionType: 'java',
          className: 'example.Task',
          method: 'run',
          mappings: [
            {
              direction: 'input',
              target: 'argument',
              dataType: 'java.lang.String',
              source: 'first',
            },
            {
              direction: 'input',
              target: 'argument',
              dataType: 'java.lang.String',
              source: 'second',
            },
          ],
        },
        '',
        '  ',
        BPMN_ACTION_XML
      )
    ).toThrow(/duplicate input mapping target/)
  })
})
