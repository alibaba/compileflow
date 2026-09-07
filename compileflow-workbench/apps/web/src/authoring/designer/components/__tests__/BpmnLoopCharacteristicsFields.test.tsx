import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it } from 'vitest'

import { BpmnLoopCharacteristicsFields } from '../properties/BpmnLoopCharacteristicsFields'

import { store } from '@/app/store'
import type { LoopCharacteristics } from '@/authoring/designer/types/bpmnNodeTypes'
import i18n, { i18nReady } from '@/shared/i18n'

function Harness() {
  const [value, setValue] = useState<LoopCharacteristics>({
    type: 'multiInstance',
    isSequential: true,
    collection: '',
    item: 'item',
  })
  return <BpmnLoopCharacteristicsFields value={value} onChange={(next) => next && setValue(next)} />
}

describe('BpmnLoopCharacteristicsFields', () => {
  beforeEach(async () => {
    await i18nReady
    await i18n.changeLanguage('zh')
  })

  it('accepts an enclosing loop variable that is not a process-variable suggestion', async () => {
    render(
      <Provider store={store}>
        <Harness />
      </Provider>
    )

    const collection = screen.getByRole('combobox', { name: '集合变量名' })
    await userEvent.type(collection, 'outerItem')

    expect(collection).toHaveValue('outerItem')
  })
})
