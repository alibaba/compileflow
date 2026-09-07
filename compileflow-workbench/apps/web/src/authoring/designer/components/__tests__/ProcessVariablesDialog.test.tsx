import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { Provider } from 'react-redux'
import { describe, expect, it, vi } from 'vitest'

import { loadOperateProcess } from '../../store/editorSlice'
import ProcessVariablesDialog from '../ProcessVariablesDialog'

import { store } from '@/app/store'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

function load(request: string, name: string) {
  store.dispatch(loadOperateProcess.pending(request, 'flow'))
  store.dispatch(
    loadOperateProcess.fulfilled(
      {
        flow: {
          id: 'flow',
          code: 'flow',
          name: 'Flow',
          type: 'TBBPM',
          nodes: [],
          connections: [],
          variables: [{ name, type: 'java.lang.String', inOutType: 'param' }],
        },
        operateProcessCode: 'flow',
        revision: 1,
        warnings: [],
      },
      request,
      'flow'
    )
  )
}

describe('variable form document ownership', () => {
  it.each(['before-save', 'during-validation'] as const)(
    'does not save old values after reload %s',
    async (when) => {
      load('old', 'oldValue')
      render(
        <Provider store={store}>
          <ProcessVariablesDialog open onClose={vi.fn()} />
        </Provider>
      )
      fireEvent.click(screen.getByRole('button', { name: 'common.edit' }))
      fireEvent.change(screen.getByLabelText('designer.variableManager.field.name'), {
        target: { value: 'obsoleteValue' },
      })
      if (when === 'before-save') act(() => load('new', 'newValue'))
      const save = screen.queryByRole('button', { name: 'common.save' })
      if (save) fireEvent.click(save)
      if (when === 'during-validation') act(() => load('new', 'newValue'))
      await waitFor(() =>
        expect(screen.queryByRole('button', { name: 'common.save' })).not.toBeInTheDocument()
      )
      expect(
        store.getState().editor.present.currentProcess?.variables?.map((variable) => variable.name)
      ).toEqual(['newValue'])
    }
  )
})
