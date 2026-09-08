import { fireEvent, render, screen } from '@testing-library/react'
import { App } from 'antd'
import { Provider } from 'react-redux'
import { beforeEach, describe, expect, it } from 'vitest'

import { loadOperateProcess, selectCurrentProcess } from '../../store/editorSlice'
import EdgePropertiesPanel from '../EdgePropertiesPanel'

import { useAppSelector } from '@/app/hooks'
import { store, UndoActionCreators } from '@/app/store'
import i18n, { i18nReady } from '@/shared/i18n'

const selectedEdge = {
  id: 'conditional',
  sourceId: 'gateway',
  targetId: 'accepted',
}

function CanonicalEdgePanel() {
  const process = useAppSelector(selectCurrentProcess)
  return <EdgePropertiesPanel edge={process?.connections[0] ?? null} />
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
    store.dispatch(UndoActionCreators.clearHistory())
  })

  it('offers condition editing for TBBPM inclusive-gateway branches', () => {
    render(
      <Provider store={store}>
        <App>
          <CanonicalEdgePanel />
        </App>
      </Provider>
    )

    expect(screen.getByRole('textbox', { name: '条件表达式' })).toBeInTheDocument()
  })

  it('commits a complete field edit as one undoable change on blur', () => {
    render(
      <Provider store={store}>
        <App>
          <CanonicalEdgePanel />
        </App>
      </Provider>
    )

    const input = screen.getByRole('textbox', { name: '连接名称' })
    fireEvent.change(input, { target: { value: '通过' } })
    expect(store.getState().editor.present.currentProcess?.connections[0].name).toBeUndefined()

    const pastCount = store.getState().editor.past.length
    fireEvent.blur(input)
    expect(store.getState().editor.present.currentProcess?.connections[0].name).toBe('通过')
    expect(store.getState().editor.past).toHaveLength(pastCount + 1)
    const past = store.getState().editor.past
    expect(past[past.length - 1]?.currentProcess?.connections[0].name).toBeUndefined()
    store.dispatch(UndoActionCreators.undo())
    expect(store.getState().editor.present.currentProcess?.connections[0].name).toBeUndefined()
  })
})
