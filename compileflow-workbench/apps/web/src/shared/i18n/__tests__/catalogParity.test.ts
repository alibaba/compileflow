import { describe, expect, it } from 'vitest'

import enAuthoring from '../en/authoring'
import enCommon from '../en/common'
import enLearn from '../en/learn'
import enOperate from '../en/operate'
import zhAuthoring from '../zh/authoring'
import zhCommon from '../zh/common'
import zhLearn from '../zh/learn'
import zhOperate from '../zh/operate'

describe.each([
  ['common', zhCommon, enCommon],
  ['learn', zhLearn, enLearn],
  ['operate', zhOperate, enOperate],
  ['authoring', zhAuthoring, enAuthoring],
])('%s translation catalog', (_name, zh, en) => {
  it('has identical Chinese and English keys', () => {
    expect(Object.keys(en).sort()).toEqual(Object.keys(zh).sort())
  })
})
