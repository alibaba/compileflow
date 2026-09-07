import { beforeEach, describe, expect, it } from 'vitest'

import { importXml, loadOperateProcess } from '../../store/editorSlice'

import { store } from '@/app/store'

describe('XML editor metadata import', () => {
  beforeEach(() => {
    store.dispatch(loadOperateProcess.pending('document', 'flow'))
    store.dispatch(
      loadOperateProcess.fulfilled(
        {
          flow: {
            id: 'flow',
            code: 'flow',
            name: 'Flow',
            description: 'Old description',
            type: 'TBBPM',
            nodes: [],
            connections: [],
          },
          operateProcessCode: 'flow',
          revision: 1,
          warnings: [],
        },
        'document',
        'flow'
      )
    )
  })

  it.each(['New description', undefined])(
    'applies XML description %s instead of restoring old document text',
    async (description) => {
      await store
        .dispatch(
          importXml({
            type: 'TBBPM',
            xml: `<bpm code="flow" name="Flow"${description ? ` description="${description}"` : ''}/>`,
          })
        )
        .unwrap()
      expect(store.getState().editor.present.currentProcess?.description).toBe(description)
    }
  )
})
