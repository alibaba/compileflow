import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import ValidationResultPanel from '../ValidationResultPanel'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
const flow: UnifiedProcessDefinition = {
  id: 'flow',
  code: 'flow',
  name: 'Flow',
  type: 'TBBPM',
  nodes: [],
  connections: [],
}

describe('validation result ownership', () => {
  it('clears obsolete results when the document disappears', () => {
    const view = render(<ValidationResultPanel flowDefinition={flow} />)
    view.rerender(<ValidationResultPanel flowDefinition={null} />)
    expect(screen.getByText('designer.validation.notRun')).toBeInTheDocument()
  })
  it('clears highlights of the other kind when activating an issue', () => {
    const nodes = vi.fn()
    const edges = vi.fn()
    const view = render(
      <ValidationResultPanel
        flowDefinition={flow}
        onHighlightNodes={nodes}
        onHighlightConnections={edges}
      />
    )
    const issue = view.container.querySelector('.validation-issue-item')!
    fireEvent.click(issue)
    expect(nodes).toHaveBeenLastCalledWith([])
    expect(edges).toHaveBeenLastCalledWith([])
  })
})
