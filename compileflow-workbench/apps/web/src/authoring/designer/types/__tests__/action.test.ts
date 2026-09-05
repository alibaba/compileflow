import { describe, expect, it } from 'vitest'

import {
  type InputMapping,
  mappingSourceUpdate,
  mappingDefaultConflict,
  mappingDefaultUpdate,
  mappingDirectionUpdate,
} from '../action'

const INPUT_WITH_SOURCE: InputMapping = {
  target: 'quantity',
  dataType: 'java.lang.Integer',
  direction: 'input',
  source: 'order.quantity',
}

describe('variable mapping source', () => {
  it('keeps source and literal default mutually exclusive', () => {
    expect(mappingDefaultUpdate(INPUT_WITH_SOURCE, '')).toEqual({
      defaultValue: '',
      source: undefined,
    })

    expect(
      mappingSourceUpdate(
        { ...INPUT_WITH_SOURCE, source: undefined, defaultValue: '1' },
        'order.quantity'
      )
    ).toEqual({
      source: 'order.quantity',
      defaultValue: undefined,
    })
  })

  it('distinguishes an empty String default from an absent default', () => {
    const withEmptyDefault = {
      ...INPUT_WITH_SOURCE,
      source: undefined,
      defaultValue: '',
    }

    expect(mappingDefaultConflict(withEmptyDefault)).toBeUndefined()
    expect(mappingDefaultUpdate(withEmptyDefault, undefined)).toEqual({
      defaultValue: undefined,
      source: undefined,
    })
  })

  it('removes defaults and invalid targets when switching to an output mapping', () => {
    expect(
      mappingDirectionUpdate(
        {
          ...INPUT_WITH_SOURCE,
          defaultValue: '1',
        },
        'output',
        ['result'],
        false
      )
    ).toEqual({
      direction: 'output',
      source: undefined,
      target: '',
      dataType: 'java.lang.Integer',
      defaultValue: undefined,
    })
  })

  it('preserves a declared process target for an output mapping', () => {
    expect(mappingDirectionUpdate(INPUT_WITH_SOURCE, 'output', ['order.quantity'], false)).toEqual({
      direction: 'output',
      source: undefined,
      target: 'order.quantity',
      dataType: 'java.lang.Integer',
      defaultValue: undefined,
    })
  })
})
