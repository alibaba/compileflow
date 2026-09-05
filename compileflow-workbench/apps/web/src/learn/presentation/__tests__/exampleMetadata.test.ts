import type { TFunction } from 'i18next'
import { describe, expect, it } from 'vitest'

import { findMockExample } from '@/shared/api/exampleMockData'
import { localizeExample } from '@/shared/examples/localizeExample'

const sourceExample = findMockExample('learn.tbbpm.greeting')!

describe('localizeExample', () => {
  it('uses canonical source content for English even when i18next would fall back to Chinese', () => {
    const t = (() => '声明流程输入变量和返回变量') as unknown as TFunction

    const localized = localizeExample(sourceExample, t, 'en-US')

    expect(localized.whatYouWillLearn[0]).toBe('Declare process input and return variables')
    expect(localized).toBe(sourceExample)
  })

  it('uses translated tutorial content for Chinese without mutating the source example', () => {
    const t = ((key: string) =>
      key.endsWith('whatYouWillLearn.0') ? '声明流程输入变量和返回变量' : key) as TFunction

    const localized = localizeExample(sourceExample, t, 'zh-CN')

    expect(localized.whatYouWillLearn[0]).toBe('声明流程输入变量和返回变量')
    expect(sourceExample.whatYouWillLearn[0]).toBe('Declare process input and return variables')
  })
})
