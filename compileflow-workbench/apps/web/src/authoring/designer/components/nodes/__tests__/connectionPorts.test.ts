import { describe, expect, it, vi } from 'vitest'

import { BPMN_NODE_TYPES } from '../../../types/bpmnNodeTypes'
import { CONNECTION_PORTS } from '../../../types/graphTypes'
import { TBBPM_NODE_TYPES } from '../../../types/tbbpm'
import { getBpmnNodeConfig } from '../registerBpmnNodes'
import { getNodeConfig } from '../registerNodes'

vi.mock('@antv/x6-react-shape', () => ({ register: vi.fn() }))

describe('connection directions for every registered node', () => {
  it.each(TBBPM_NODE_TYPES)('TBBPM %s exposes every geometric direction except notes', (type) => {
    expect(getNodeConfig(type)?.ports.map(({ id }) => id)).toEqual(
      type === 'note' ? [] : CONNECTION_PORTS
    )
  })
  it.each(BPMN_NODE_TYPES)('BPMN %s exposes every geometric direction', (type) => {
    expect(getBpmnNodeConfig(type)?.ports.map(({ id }) => id)).toEqual(CONNECTION_PORTS)
  })
})
