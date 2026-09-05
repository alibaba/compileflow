import { describe, expect, test } from 'vitest'

import type {
  BpmnConnection,
  BpmnNode,
  BpmnNodeType,
  UnifiedProcessDefinition,
} from '../../types/flowDefinition'
import { BpmnValidator } from '../BpmnValidator'
import { validateProcessTopology, ValidationLevel } from '../ProcessTopologyValidator'

function node(id: string, type: BpmnNodeType, properties: Record<string, unknown> = {}): BpmnNode {
  return {
    id,
    type,
    name: id,
    position: { x: 0, y: 0 },
    properties,
  }
}

function codes(nodes: BpmnNode[], connections: BpmnConnection[]): string[] {
  return new BpmnValidator(nodes, connections).validateNodeRules().map((error) => error.code)
}

describe('BpmnValidator executable control-flow contract', () => {
  test('rejects properties that do not belong to the node type', () => {
    const gateway = node('route', 'bpmn:ParallelGateway', {
      invocationPolicy: { timeout: 'PT1S' },
    })

    expect(codes([gateway], [])).toContain('bpmn.node.inapplicableProperty')
  })

  test('rejects unsupported attributes that are not part of the executable node model', () => {
    expect(codes([node('start', 'bpmn:StartEvent', { isInterrupting: false })], [])).toContain(
      'bpmn.node.inapplicableProperty'
    )
  })

  test('requires explicit gateways for branches and conditions', () => {
    const nodes = [
      node('start', 'bpmn:StartEvent'),
      node('task', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.Action', method: 'run' },
      }),
      node('left', 'bpmn:EndEvent'),
      node('right', 'bpmn:EndEvent'),
    ]
    const connections = [
      { id: 'to_task', sourceId: 'start', targetId: 'task' },
      { id: 'left_flow', sourceId: 'task', targetId: 'left', condition: 'enabled' },
      { id: 'right_flow', sourceId: 'task', targetId: 'right' },
    ]

    expect(codes(nodes, connections)).toEqual(
      expect.arrayContaining([
        'bpmn.node.requiresExplicitGateway',
        'bpmn.node.conditionRequiresGateway',
      ])
    )
  })

  test('validates receive-task message identity separately from runtime event name', () => {
    const receive = node('receive', 'bpmn:ReceiveTask', {
      messageRef: 'paymentMessage',
    })
    const valid = new BpmnValidator(
      [receive],
      [],
      [],
      [{ id: 'paymentMessage', name: 'payment.received' }]
    ).validateNodeRules()
    const missing = new BpmnValidator([receive], [], [], []).validateNodeRules()
    const blankEvent = new BpmnValidator(
      [receive],
      [],
      [],
      [{ id: 'paymentMessage', name: ' ' }]
    ).validateNodeRules()

    expect(valid.map((error) => error.code)).not.toEqual(
      expect.arrayContaining(['bpmn.receiveTask.unknownMessageRef', 'bpmn.message.missingName'])
    )
    expect(missing.map((error) => error.code)).toContain('bpmn.receiveTask.unknownMessageRef')
    expect(blankEvent.map((error) => error.code)).toContain('bpmn.message.missingName')
  })

  test('rejects BPMN message IDs that are duplicated or collide with executable elements', () => {
    const errors = new BpmnValidator(
      [node('sharedId', 'bpmn:StartEvent')],
      [],
      [],
      [
        { id: 'sharedId', name: 'first' },
        { id: 'sharedId', name: 'second' },
      ]
    ).validateNodeRules()

    expect(errors.map((error) => error.code)).toEqual(
      expect.arrayContaining(['bpmn.message.duplicateId', 'bpmn.message.idCollision'])
    )
  })

  test('accepts an exclusive split with conditioned branches and one unconditioned default', () => {
    const nodes = [
      node('start', 'bpmn:StartEvent'),
      node('split', 'bpmn:ExclusiveGateway', { default: 'fallback' }),
      node('left', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.Left', method: 'run' },
      }),
      node('right', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.Right', method: 'run' },
      }),
    ]
    const connections = [
      { id: 'to_split', sourceId: 'start', targetId: 'split' },
      { id: 'selected', sourceId: 'split', targetId: 'left', condition: 'enabled' },
      { id: 'fallback', sourceId: 'split', targetId: 'right' },
    ]

    expect(codes(nodes, connections).filter((code) => code.startsWith('bpmn.gateway.'))).toEqual([])
  })

  test('rejects an invalid default but allows repeated inclusive conditions', () => {
    const nodes = [
      node('start', 'bpmn:StartEvent'),
      node('split', 'bpmn:InclusiveGateway', { default: 'missing' }),
      node('left', 'bpmn:EndEvent'),
      node('right', 'bpmn:EndEvent'),
    ]
    const connections = [
      { id: 'to_split', sourceId: 'start', targetId: 'split' },
      { id: 'left_flow', sourceId: 'split', targetId: 'left', condition: 'enabled' },
      { id: 'right_flow', sourceId: 'split', targetId: 'right', condition: 'enabled' },
    ]

    const validationCodes = codes(nodes, connections)

    expect(validationCodes).toContain('bpmn.gateway.invalidDefault')
    expect(validationCodes).not.toContain('bpmn.gateway.duplicateCondition')
  })

  test('rejects mixed gateways, parallel conditions, and non-immediate flows', () => {
    const nodes = [
      node('one', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.One', method: 'run' },
      }),
      node('two', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.Two', method: 'run' },
      }),
      node('parallel', 'bpmn:ParallelGateway'),
      node('left', 'bpmn:EndEvent'),
      node('right', 'bpmn:EndEvent'),
    ]
    const connections = [
      { id: 'one_in', sourceId: 'one', targetId: 'parallel' },
      { id: 'two_in', sourceId: 'two', targetId: 'parallel' },
      {
        id: 'left_flow',
        sourceId: 'parallel',
        targetId: 'left',
        condition: 'enabled',
      },
      { id: 'right_flow', sourceId: 'parallel', targetId: 'right' },
    ]

    expect(codes(nodes, connections)).toEqual(
      expect.arrayContaining([
        'bpmn.gateway.invalidShape',
        'bpmn.gateway.parallelConditionUnsupported',
      ])
    )
  })

  test('treats multiple process boundaries as errors', () => {
    const flow = {
      id: 'test',
      code: 'test',
      name: 'test',
      type: 'BPMN',
      variables: [],
      connections: [],
      nodes: [
        node('start_one', 'bpmn:StartEvent'),
        node('start_two', 'bpmn:StartEvent'),
        node('end_one', 'bpmn:EndEvent'),
        node('end_two', 'bpmn:EndEvent'),
      ],
    } satisfies UnifiedProcessDefinition

    const result = validateProcessTopology(flow)

    expect(result.issues).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ type: 'multi-start', level: ValidationLevel.ERROR }),
        expect.objectContaining({ type: 'multi-end', level: ValidationLevel.ERROR }),
      ])
    )
  })

  test('validates each embedded subprocess as an independent graph container', () => {
    const nodes = [
      node('start', 'bpmn:StartEvent'),
      node('sub', 'bpmn:SubProcess'),
      node('end', 'bpmn:EndEvent'),
      { ...node('nestedStart', 'bpmn:StartEvent'), parentId: 'sub' },
      { ...node('nestedEnd', 'bpmn:EndEvent'), parentId: 'sub' },
    ]
    const connections = [
      { id: 'enter', sourceId: 'start', targetId: 'sub' },
      { id: 'leave', sourceId: 'sub', targetId: 'end' },
      { id: 'nested', sourceId: 'nestedStart', targetId: 'nestedEnd' },
    ]
    const flow = {
      id: 'nested',
      code: 'nested',
      name: 'nested',
      type: 'BPMN',
      nodes,
      connections,
    } satisfies UnifiedProcessDefinition

    expect(validateProcessTopology(flow).issues).not.toEqual(
      expect.arrayContaining([
        expect.objectContaining({ type: 'multi-start' }),
        expect.objectContaining({ type: 'multi-end' }),
        expect.objectContaining({ type: 'unreachable' }),
      ])
    )
    expect(new BpmnValidator(nodes, connections).validateNodeRules()).toEqual([])
  })

  test('rejects subprocess boundary violations and suspending nested activities', () => {
    const nodes = [
      node('sub', 'bpmn:SubProcess'),
      { ...node('nestedStart', 'bpmn:StartEvent'), parentId: 'sub' },
      {
        ...node('receive', 'bpmn:ReceiveTask', { messageRef: 'message' }),
        parentId: 'sub',
      },
      node('end', 'bpmn:EndEvent'),
    ]
    const connections = [{ id: 'cross', sourceId: 'nestedStart', targetId: 'end' }]

    expect(codes(nodes, connections)).toEqual(
      expect.arrayContaining([
        'bpmn.subProcess.triggerEntryChild',
        'bpmn.subProcess.crossContainerTransition',
      ])
    )
  })

  test('allows multiple called-process outputs but keeps action returns singular', () => {
    const outputMappings = [
      {
        name: 'first',
        dataType: 'java.lang.String',
        direction: 'output' as const,
        target: 'first',
      },
      {
        name: 'second',
        dataType: 'java.lang.String',
        direction: 'output' as const,
        target: 'second',
      },
    ]
    const call = node('call', 'bpmn:CallActivity', {
      calledElement: 'child',
      classpath: 'flows/child.bpmn',
      mappings: outputMappings,
    })
    const action = node('action', 'bpmn:ServiceTask', {
      action: {
        actionType: 'java',
        className: 'example.Action',
        method: 'run',
        mappings: outputMappings,
      },
    })
    const variables = [
      { name: 'first', type: 'java.lang.String', inOutType: 'return' as const },
      { name: 'second', type: 'java.lang.String', inOutType: 'return' as const },
    ]

    const callCodes = new BpmnValidator([call], [], variables)
      .validateNodeRules()
      .map((error) => error.code)
    const actionCodes = new BpmnValidator([action], [], variables)
      .validateNodeRules()
      .map((error) => error.code)

    expect(callCodes).not.toContain('bpmn.mapping.multipleOutputs')
    expect(actionCodes).toContain('bpmn.mapping.multipleOutputs')
  })

  test('rejects multiple called-process outputs mapped to the same parent variable', () => {
    const call = node('call', 'bpmn:CallActivity', {
      calledElement: 'child',
      classpath: 'flows/child.bpmn',
      mappings: [
        {
          name: 'first',
          dataType: 'java.lang.String',
          direction: 'output',
          target: 'result',
        },
        {
          name: 'second',
          dataType: 'java.lang.String',
          direction: 'output',
          target: 'result',
        },
      ],
    })

    const result = new BpmnValidator(
      [call],
      [],
      [{ name: 'result', type: 'java.lang.String', inOutType: 'return' }]
    )
      .validateNodeRules()
      .map((error) => error.code)

    expect(result).toContain('bpmn.mapping.duplicateOutputTarget')
  })

  test('rejects mapping defaults that generated invocations would ignore', () => {
    const action = node('action', 'bpmn:ServiceTask', {
      action: {
        actionType: 'java',
        className: 'example.Action',
        method: 'run',
        mappings: [
          {
            name: 'input',
            dataType: 'java.lang.String',
            direction: 'input',
            source: 'input',
            defaultValue: '',
          },
          {
            name: 'output',
            dataType: 'java.lang.String',
            direction: 'output',
            target: 'output',
            defaultValue: 'ignored',
          },
        ],
      },
    })

    const result = new BpmnValidator(
      [action],
      [],
      [
        { name: 'input', type: 'java.lang.String', inOutType: 'param' },
        { name: 'output', type: 'java.lang.String', inOutType: 'return' },
      ]
    )
      .validateNodeRules()
      .filter((error) => error.code === 'mapping.inapplicableDefault')

    expect(result.map((error) => error.params?.reason)).toEqual(['source', 'output'])
  })

  test('validates loop references and lexical variable isolation before XML generation', () => {
    const loop = node('loop', 'bpmn:ServiceTask', {
      action: { actionType: 'java', className: 'example.Action', method: 'run' },
      loopCharacteristics: {
        type: 'multiInstance',
        isSequential: true,
        collection: 'missing',
        item: 'items',
        index: 'index',
      },
    })
    const result = new BpmnValidator(
      [loop],
      [],
      [{ name: 'items', type: 'java.util.List<java.lang.String>', inOutType: 'param' }]
    )
      .validateNodeRules()
      .map((error) => error.code)

    expect(result).toEqual(
      expect.arrayContaining(['bpmn.loop.unknownCollection', 'bpmn.loop.variableShadowing'])
    )
  })

  test('resolves and protects enclosing multi-instance variables', () => {
    const outer = node('outer', 'bpmn:SubProcess', {
      loopCharacteristics: {
        type: 'multiInstance',
        isSequential: true,
        collection: 'items',
        item: 'outerItem',
        index: 'outerIndex',
      },
    })
    const inner = {
      ...node('inner', 'bpmn:ServiceTask', {
        action: { actionType: 'java', className: 'example.Action', method: 'run' },
        loopCharacteristics: {
          type: 'multiInstance',
          isSequential: true,
          collection: 'outerItem',
          item: 'innerItem',
          index: 'outerIndex',
        },
      }),
      parentId: 'outer',
    }

    const result = new BpmnValidator(
      [outer, inner],
      [],
      [
        {
          name: 'items',
          type: 'java.util.List<java.util.List<java.lang.String>>',
          inOutType: 'param',
        },
      ]
    )
      .validateNodeRules()
      .filter((error) => error.elementId === 'inner')

    expect(result.map((error) => error.code)).not.toContain('bpmn.loop.unknownCollection')
    expect(result.map((error) => error.code)).toContain('bpmn.loop.variableShadowing')
  })

  test('validates parallel multi-instance output variables and element role', () => {
    const loop = node('loop', 'bpmn:ServiceTask', {
      action: { actionType: 'java', className: 'example.Action', method: 'run' },
      loopCharacteristics: {
        type: 'multiInstance',
        isSequential: false,
        collection: 'items',
        item: 'item',
        target: 'missingResults',
        source: 'iterationResult',
      },
    })

    const result = new BpmnValidator(
      [loop],
      [],
      [
        { name: 'items', type: 'java.util.List<java.lang.String>', inOutType: 'param' },
        { name: 'iterationResult', type: 'java.lang.String', inOutType: 'return' },
      ]
    )
      .validateNodeRules()
      .map((error) => error.code)

    expect(result).toEqual(
      expect.arrayContaining(['bpmn.loop.unknownOutputReference', 'bpmn.loop.outputSourceNotInner'])
    )
  })

  test('accepts parallel multi-instance without output aggregation', () => {
    const loop = node('loop', 'bpmn:ServiceTask', {
      action: { actionType: 'java', className: 'example.Action', method: 'run' },
      loopCharacteristics: {
        type: 'multiInstance',
        isSequential: false,
        collection: 'items',
        item: 'item',
      },
    })

    const result = new BpmnValidator(
      [loop],
      [],
      [{ name: 'items', type: 'java.util.List<java.lang.String>', inOutType: 'param' }]
    ).validateNodeRules()

    expect(result.map((error) => error.code)).not.toContain('bpmn.loop.invalid')
  })

  test('does not report an element-role error for an unknown output variable', () => {
    const loop = node('loop', 'bpmn:ServiceTask', {
      action: { actionType: 'java', className: 'example.Action', method: 'run' },
      loopCharacteristics: {
        type: 'multiInstance',
        isSequential: false,
        collection: 'items',
        item: 'item',
        target: 'results',
        source: 'missingResult',
      },
    })

    const result = new BpmnValidator(
      [loop],
      [],
      [
        { name: 'items', type: 'java.util.List<java.lang.String>', inOutType: 'param' },
        { name: 'results', type: 'java.util.List<java.lang.String>', inOutType: 'return' },
      ]
    )
      .validateNodeRules()
      .map((error) => error.code)

    expect(result).toContain('bpmn.loop.unknownOutputReference')
    expect(result).not.toContain('bpmn.loop.outputSourceNotInner')
  })

  test('rejects direct state mutation in gateway and loop conditions', () => {
    const gateway = node('gateway', 'bpmn:ExclusiveGateway')
    const loop = node('loop', 'bpmn:ServiceTask', {
      action: { actionType: 'java', className: 'example.Action', method: 'run' },
      loopCharacteristics: {
        type: 'standard',
        testBefore: true,
        loopCondition: 'remaining-- > 0',
      },
    })
    const connections = [
      {
        id: 'mutating',
        sourceId: 'gateway',
        targetId: 'loop',
        condition: 'approved = true',
      },
    ]

    const result = new BpmnValidator([gateway, loop], connections)
      .validateNodeRules()
      .filter((error) => error.code === 'condition.directMutation')

    expect(result).toHaveLength(2)
    expect(result.map((error) => error.params?.operator)).toEqual(
      expect.arrayContaining(['=', '--'])
    )
  })

  test('validates InvocationPolicy values for explicit scripts', () => {
    const task = node('task', 'bpmn:ServiceTask', {
      action: {
        actionType: 'script',
        language: 'java',
        source: 'counter++;',
        invocationPolicy: { maxAttempts: 101 },
      },
    })

    expect(codes([task], [])).toEqual(expect.arrayContaining(['bpmn.invocationPolicy.invalid']))
  })

  test('accepts concurrent branches that converge directly at the single end event', () => {
    const flow = {
      id: 'direct-end',
      code: 'direct.end',
      name: 'Direct End',
      type: 'BPMN',
      variables: [],
      nodes: [
        node('start', 'bpmn:StartEvent'),
        node('fork', 'bpmn:ParallelGateway'),
        node('end', 'bpmn:EndEvent'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'fork' },
        { id: 'left', sourceId: 'fork', targetId: 'end' },
        { id: 'right', sourceId: 'fork', targetId: 'end' },
      ],
    } satisfies UnifiedProcessDefinition

    expect(validateProcessTopology(flow).issues).not.toEqual(
      expect.arrayContaining([expect.objectContaining({ type: 'deadlock' })])
    )
  })
})
