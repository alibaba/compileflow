import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { App } from 'antd'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { loadOperateProcess } from '../../store/editorSlice'
import type { BpmnNode } from '../../types/flowDefinition'
import SubProcessPropertiesTab from '../properties/SubProcessPropertiesTab'

import { store } from '@/app/store'
import i18n, { i18nReady } from '@/shared/i18n'

const nested: BpmnNode = {
  id: 'nested',
  parentId: 'outer',
  type: 'bpmn:SubProcess',
  name: 'Nested',
  position: { x: 20, y: 20 },
  properties: {},
}

describe('SubProcessPropertiesTab', () => {
  beforeEach(async () => {
    await i18nReady
    await i18n.changeLanguage('zh')
    store.dispatch(loadOperateProcess.pending('document', 'flow'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow: {
            type: 'BPMN',
            id: 'flow',
            code: 'flow',
            name: 'Flow',
            nodes: [
              {
                id: 'outer',
                type: 'bpmn:SubProcess',
                name: 'Outer',
                position: { x: 0, y: 0 },
                properties: {},
              },
              nested,
              {
                id: 'sibling',
                parentId: 'outer',
                type: 'bpmn:ServiceTask',
                name: 'Sibling',
                position: { x: 200, y: 20 },
                properties: {},
              },
            ],
            connections: [],
          },
          warnings: [],
          operateProcessCode: 'flow',
          revision: 1,
        },
        'document',
        'flow'
      )
    )
  })

  it('offers sibling nodes from the same parent scope as nested subprocess children', async () => {
    render(
      <Provider store={store}>
        <App>
          <SubProcessPropertiesTab node={nested} onUpdate={vi.fn()} />
        </App>
      </Provider>
    )

    await userEvent.click(screen.getByRole('combobox', { name: '直属子节点' }))
    expect(await screen.findByText('Sibling (sibling)')).toBeInTheDocument()
  })
})
