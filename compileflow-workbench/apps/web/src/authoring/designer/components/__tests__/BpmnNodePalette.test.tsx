import { fireEvent, render, screen } from '@testing-library/react'
import { App } from 'antd'
import { vi } from 'vitest'

import BpmnNodePalette from '../BpmnNodePalette'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

vi.mock('@antv/x6', () => ({
  Graph: vi.fn(),
  Dnd: vi.fn().mockImplementation(() => ({ start: vi.fn() })),
}))

describe('BpmnNodePalette', () => {
  it('renders every category and supported node type', () => {
    render(
      <App>
        <BpmnNodePalette graph={null} />
      </App>
    )

    for (const category of ['events', 'tasks', 'gateways', 'composition']) {
      expect(screen.getByText(`designer.palette.bpmn.cat.${category}`)).toBeInTheDocument()
    }
    fireEvent.click(screen.getByText('designer.palette.bpmn.cat.composition'))

    for (const type of [
      'startEvent',
      'endEvent',
      'serviceTask',
      'scriptTask',
      'receiveTask',
      'exclusiveGateway',
      'parallelGateway',
      'inclusiveGateway',
      'callActivity',
      'subProcess',
    ]) {
      expect(screen.getByText(`designer.palette.bpmn.node.${type}`)).toBeInTheDocument()
    }
  })
})
