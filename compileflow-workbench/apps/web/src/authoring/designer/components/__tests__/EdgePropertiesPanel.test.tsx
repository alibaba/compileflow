import { render, screen } from '@testing-library/react'
import { App } from 'antd'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it } from 'vitest'

import { loadOperateProcess } from '../../store/editorSlice'
import EdgePropertiesPanel from '../EdgePropertiesPanel'

import { store } from '@/app/store'
import i18n, { i18nReady } from '@/shared/i18n'

const selectedEdge = {
  id: 'conditional',
  sourceId: 'gateway',
  targetId: 'accepted',
}

describe('EdgePropertiesPanel', () => {
  beforeEach(async () => {
    await i18nReady
    await i18n.changeLanguage('zh')
    store.dispatch(loadOperateProcess.pending('document', 'flow'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow: {
            type: 'TBBPM',
            id: 'flow',
            code: 'flow',
            name: 'Flow',
            nodes: [
              {
                id: 'gateway',
                type: 'inclusive',
                name: 'Gateway',
                position: { x: 0, y: 0 },
                properties: {},
              },
              {
                id: 'accepted',
                type: 'autoTask',
                name: 'Accepted',
                position: { x: 200, y: 0 },
                properties: {},
              },
              {
                id: 'rejected',
                type: 'autoTask',
                name: 'Rejected',
                position: { x: 200, y: 120 },
                properties: {},
              },
            ],
            connections: [
              selectedEdge,
              { id: 'fallback', sourceId: 'gateway', targetId: 'rejected' },
            ],
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

  it('offers condition editing for TBBPM inclusive-gateway branches', () => {
    render(
      <Provider store={store}>
        <App>
          <EdgePropertiesPanel edge={selectedEdge} />
        </App>
      </Provider>
    )

    expect(screen.getByRole('textbox', { name: '条件表达式' })).toBeInTheDocument()
  })
})
