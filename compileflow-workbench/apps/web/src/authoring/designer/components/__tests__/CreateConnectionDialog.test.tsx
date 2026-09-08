import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, expect, it, vi } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import CreateConnectionDialog from '../CreateConnectionDialog'

import i18n, { i18nReady } from '@/shared/i18n'

beforeEach(async () => {
  await i18nReady
  await i18n.changeLanguage('zh')
})

it('preserves the chosen target when autosave updates the process', async () => {
  const process: UnifiedProcessDefinition = {
    type: 'TBBPM',
    id: 'flow',
    code: 'flow',
    name: 'Flow',
    nodes: ['source', 'first', 'chosen'].map((id) => ({
      id,
      name: id,
      type: 'autoTask',
      position: { x: 0, y: 0 },
      properties: {},
    })),
    connections: [],
  }
  const props = {
    initialNodeIds: ['source', 'first'],
    onCancel: vi.fn(),
    onCreate: vi.fn(),
  }
  const { rerender } = render(<CreateConnectionDialog {...props} process={process} />)
  fireEvent.mouseDown(screen.getAllByRole('combobox')[1]!)
  fireEvent.click(await screen.findByText('chosen · chosen'))
  rerender(<CreateConnectionDialog {...props} process={{ ...process, updatedAt: Date.now() }} />)
  fireEvent.click(screen.getByRole('button', { name: /创\s*建/ }))
  await waitFor(() => expect(props.onCreate).toHaveBeenCalledWith('source', 'chosen'))
})
