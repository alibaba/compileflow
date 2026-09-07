import { describe, expect, it, vi } from 'vitest'

import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import type { TbbpmNode, TbbpmNodeType } from '../../types/tbbpm'
import { createSimulationEngine, ExecutionState } from '../ProcessSimulationEngine'

function node(
  id: string,
  type: TbbpmNodeType,
  properties: TbbpmNode['properties'] = {}
): TbbpmNode {
  return {
    id,
    type,
    name: id,
    position: { x: 0, y: 0 },
    properties,
  }
}

function linearFlow(
  nodes = [node('start', 'start'), node('end', 'end')]
): UnifiedProcessDefinition {
  return {
    id: 'review-simulation',
    code: 'review.simulation',
    name: 'Review Simulation',
    type: 'TBBPM',
    nodes,
    connections: nodes.slice(1).map((target, index) => ({
      id: `edge-${index}`,
      sourceId: nodes[index].id,
      targetId: target.id,
    })),
  }
}

describe('ProcessSimulationEngine', () => {
  it.each(['stepNext', 'continue'] as const)(
    'does not complete while a breakpoint listener awaits %s',
    async (method) => {
      const engine = createSimulationEngine(linearFlow())
      engine.addBreakpoint('start')
      let resolve!: () => void
      const pending = new Promise<void>((res) => {
        resolve = res
      })
      const internals = engine as unknown as { executeNodeLogic: () => Promise<void> }
      vi.spyOn(internals, 'executeNodeLogic').mockImplementationOnce(() => pending)
      let resumed: Promise<void> | undefined
      engine.on((event) => {
        if (event.type === 'breakpoint-hit') resumed = engine[method]()
      })

      const result = await engine.start()
      const stateWhileAwaiting = engine.getState()
      const contextWhileAwaiting = engine.getContext()
      resolve()
      await resumed

      expect(result.state).toBe(ExecutionState.RUNNING)
      expect(stateWhileAwaiting).toBe(ExecutionState.RUNNING)
      expect(contextWhileAwaiting.endTime).toBeUndefined()
      expect(contextWhileAwaiting.path).toEqual(['start'])
      expect(engine.getState()).toBe(
        method === 'stepNext' ? ExecutionState.PAUSED : ExecutionState.COMPLETED
      )
    }
  )

  it.each(['stepNext', 'continue'] as const)(
    'isolates reset from an old awaited %s',
    async (method) => {
      const engine = createSimulationEngine(linearFlow())
      engine.addBreakpoint('start')
      await engine.start({ run: 'old' })
      let resolve!: () => void
      const pending = new Promise<void>((res) => {
        resolve = res
      })
      const internals = engine as unknown as { executeNodeLogic: () => Promise<void> }
      vi.spyOn(internals, 'executeNodeLogic').mockImplementationOnce(() => pending)
      const oldResume = engine[method]()
      const rejected = expect(oldResume).rejects.toThrow('SIM_ERROR_RUN_SUPERSEDED')
      engine.reset()
      const current = await engine.start({ run: 'new' })
      resolve()
      await rejected
      expect(engine.getState()).toBe(ExecutionState.PAUSED)
      expect(engine.getContext()).toEqual(current.context)
      expect(engine.getEvents()).toEqual(current.events)
    }
  )

  it.each([
    { language: 'python' },
    { mappings: [{ direction: 'input' as const, target: 'amount', source: 'other' }] },
    { mappings: [{ direction: 'output' as const, target: 'mapped' }] },
    { execution: 'effect' as const },
    { invocationPolicy: { maxAttempts: 2 } },
  ])(
    'rejects script semantics absent from the local assignment preview: %j',
    async (unsupported) => {
      const flow = linearFlow([
        node('start', 'start'),
        node('script', 'scriptTask', {
          action: {
            actionType: 'script',
            language: 'java',
            source: 'result = amount',
            ...unsupported,
          },
        }),
        node('end', 'end'),
      ])
      const result = await createSimulationEngine(flow).start({ amount: 1, other: 2 })
      expect(result.state).toBe(ExecutionState.ERROR)
      expect(result.error).toBe('SIM_ERROR_EXPRESSION_EVALUATION_FAILED:script')
      expect(result.finalVariables.result).toBeUndefined()
    }
  )

  it.each(['java', 'qlexpress'])(
    'accepts one complete %s assignment with an optional terminator',
    async (language) => {
      const flow = linearFlow([
        node('start', 'start'),
        node('script', 'scriptTask', {
          action: { actionType: 'script', language, source: 'result = amount * 2;' },
        }),
        node('end', 'end'),
      ])
      const result = await createSimulationEngine(flow).start({ amount: 3 })
      expect(result.state).toBe(ExecutionState.COMPLETED)
      expect(result.finalVariables.result).toBe(6)
    }
  )

  it.each(['resolve', 'reject'] as const)(
    'isolates reset and restart from an old awaited node: %s',
    async (outcome) => {
      const engine = createSimulationEngine(linearFlow())
      let resolve!: () => void
      let reject!: (error: Error) => void
      const pending = new Promise<void>((res, rej) => {
        resolve = res
        reject = rej
      })
      const internals = engine as unknown as { executeNodeLogic: () => Promise<void> }
      vi.spyOn(internals, 'executeNodeLogic').mockImplementationOnce(() => pending)
      const oldRun = engine.start({ generation: 'old' })
      await vi.waitFor(() => expect(internals.executeNodeLogic).toHaveBeenCalledOnce())
      engine.reset()
      engine.addBreakpoint('end')
      const newRun = await engine.start({ generation: 'new' })
      const newEvents = engine.getEvents()
      if (outcome === 'resolve') resolve()
      else reject(new Error('old node failed'))
      const oldResult = await oldRun
      expect(engine.getState()).toBe(ExecutionState.PAUSED)
      expect(engine.getContext()).toEqual(newRun.context)
      expect(engine.getEvents()).toEqual(newEvents)
      expect(oldResult.state).toBe(ExecutionState.ERROR)
      expect(oldResult.error).toBe('SIM_ERROR_RUN_SUPERSEDED')
      expect(oldResult.finalVariables.generation).toBe('old')
    }
  )

  it('stops immediately when an event listener resets traversal', async () => {
    const engine = createSimulationEngine(linearFlow())
    engine.on((event) => {
      if (event.type === 'node-enter') engine.reset()
    })
    const result = await engine.start()
    expect(engine.getState()).toBe(ExecutionState.READY)
    expect(engine.getContext().path).toEqual([])
    expect(engine.getEvents()).toEqual([])
    expect(result.error).toBe('SIM_ERROR_RUN_SUPERSEDED')
  })

  it('reports cycles as errors rather than completed simulations', async () => {
    const flow = linearFlow([node('start', 'start'), node('task', 'autoTask')])
    flow.connections.push({ id: 'cycle', sourceId: 'task', targetId: 'start' })
    const result = await createSimulationEngine(flow).start()
    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_CYCLE:start')
  })

  it('reports the traversal bound without pretending to reach the end', async () => {
    const flow = linearFlow([
      node('start', 'start'),
      ...Array.from({ length: 999 }, (_, index) => node(`task-${index}`, 'autoTask')),
      node('end', 'end'),
    ])
    const result = await createSimulationEngine(flow).start()
    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_STEP_LIMIT:end')
    expect(result.context.path).not.toContain('end')
  })

  it('detaches input, context, result and event snapshots from live state', async () => {
    const engine = createSimulationEngine(linearFlow())
    engine.addBreakpoint('end')
    const input = { nested: { count: 1 } }
    const result = await engine.start(input)
    const context = engine.getContext()
    context.path.push('forged')
    context.variables.set('forged', true)
    ;(context.variables.get('nested') as { count: number }).count = 2
    input.nested.count = 3
    result.context.path.push('also-forged')
    result.context.variables.clear()
    ;(result.finalVariables.nested as { count: number }).count = 4
    engine.getEvents()[0].nodeId = 'forged'
    result.events[0].nodeId = 'also-forged'
    engine.getBreakpoints()[0].enabled = false
    expect(engine.getContext().variables.get('nested')).toEqual({ count: 1 })
    expect(engine.getContext().variables.has('forged')).toBe(false)
    expect(engine.getContext().path).toEqual(['start', 'end'])
    expect(engine.getEvents()[0].nodeId).toBe('start')
    expect(engine.getBreakpoints()[0].enabled).toBe(true)
  })

  it.each([
    'if (false) result = 1',
    'object.result = 1',
    'int result = 1',
    '',
    'result = 1; other = 2',
  ])('rejects script preview outside one complete assignment: %s', async (source) => {
    const flow = linearFlow([
      node('start', 'start'),
      node('script', 'scriptTask', {
        action: { actionType: 'script', language: 'java', source },
      }),
      node('end', 'end'),
    ])
    const result = await createSimulationEngine(flow).start()
    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_EXPRESSION_EVALUATION_FAILED:script')
    expect(result.context.path).not.toContain('end')
  })

  it('does not ignore invalid breakpoint conditions', async () => {
    const engine = createSimulationEngine(linearFlow())
    engine.addBreakpoint('end', '1 && true')
    const result = await engine.start()
    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_EXPRESSION_EVALUATION_FAILED:end')
  })

  it('uses the safe evaluator for script assignments and exclusive routing', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'safe-simulation',
      code: 'safe.simulation',
      name: 'Safe Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('script', 'scriptTask', {
          action: { actionType: 'script', language: 'java', source: 'discount = amount * 0.1' },
        }),
        node('exclusive', 'exclusive'),
        node('approved', 'end'),
        node('rejected', 'end'),
      ],
      connections: [
        { id: 'c1', sourceId: 'start', targetId: 'script' },
        { id: 'c2', sourceId: 'script', targetId: 'exclusive' },
        {
          id: 'c3',
          sourceId: 'exclusive',
          targetId: 'approved',
          condition: 'discount == 25',
        },
        { id: 'c4', sourceId: 'exclusive', targetId: 'rejected', condition: 'true' },
      ],
    }
    const engine = createSimulationEngine(flow)

    const result = await engine.start({ amount: 250 })

    expect(result.state).toBe(ExecutionState.COMPLETED)
    expect(result.finalVariables.discount).toBe(25)
    expect(result.context.path).toContain('approved')
    expect(result.context.path).not.toContain('rejected')
  })

  it('matches declaration order and evaluates an unguarded flow only as fallback', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'declaration-order-simulation',
      code: 'declaration-order.simulation',
      name: 'Declaration Order Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('exclusive', 'exclusive'),
        node('low', 'end'),
        node('fallback', 'end'),
        node('high', 'end'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'exclusive' },
        {
          id: 'low-flow',
          sourceId: 'exclusive',
          targetId: 'low',
          condition: 'amount > 0',
        },
        {
          id: 'fallback-flow',
          sourceId: 'exclusive',
          targetId: 'fallback',
        },
        {
          id: 'high-flow',
          sourceId: 'exclusive',
          targetId: 'high',
          condition: 'amount > 0',
        },
      ],
    }
    const engine = createSimulationEngine(flow)

    const result = await engine.start({ amount: 1 })

    expect(result.state).toBe(ExecutionState.COMPLETED)
    expect(result.context.path).toContain('low')
    expect(result.context.path).not.toContain('high')
    expect(result.context.path).not.toContain('fallback')
  })

  it('fails closed when a exclusive uses JavaScript-only or ambiguous Java equality', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'invalid-condition-simulation',
      code: 'invalid.condition.simulation',
      name: 'Invalid Condition Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('exclusive', 'exclusive'),
        node('matched', 'end'),
        node('fallback', 'end'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'exclusive' },
        {
          id: 'invalid',
          sourceId: 'exclusive',
          targetId: 'matched',
          condition: 'status == "APPROVED"',
        },
        { id: 'fallback-flow', sourceId: 'exclusive', targetId: 'fallback' },
      ],
    }

    const result = await createSimulationEngine(flow).start({ status: 'APPROVED' })

    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_EXPRESSION_EVALUATION_FAILED:invalid')
    expect(result.context.path).not.toContain('matched')
    expect(result.context.path).not.toContain('fallback')
  })

  it('resumes the paused node without skipping task logic or gateway routing', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'breakpoint-simulation',
      code: 'breakpoint.simulation',
      name: 'Breakpoint Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('script', 'scriptTask', {
          action: { actionType: 'script', language: 'java', source: 'discount = amount * 0.1' },
        }),
        node('exclusive', 'exclusive'),
        node('approved', 'end'),
        node('rejected', 'end'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'script' },
        { id: 'route', sourceId: 'script', targetId: 'exclusive' },
        {
          id: 'approve',
          sourceId: 'exclusive',
          targetId: 'approved',
          condition: 'discount == 25',
        },
        { id: 'reject', sourceId: 'exclusive', targetId: 'rejected' },
      ],
    }
    const engine = createSimulationEngine(flow)
    engine.addBreakpoint('script')

    const paused = await engine.start({ amount: 250 })

    expect(paused.state).toBe(ExecutionState.PAUSED)
    expect(paused.finalVariables.discount).toBeUndefined()
    expect(paused.context.path).toEqual(['start', 'script'])
    expect(engine.getEvents().filter((event) => event.type === 'breakpoint-hit')).toHaveLength(1)

    await engine.stepNext()

    expect(engine.getState()).toBe(ExecutionState.PAUSED)
    expect(engine.getContext().variables.get('discount')).toBe(25)
    expect(engine.getContext().currentNodeId).toBe('exclusive')
    expect(engine.getContext().path).not.toContain('approved')

    await engine.continue()

    expect(engine.getState()).toBe(ExecutionState.COMPLETED)
    expect(engine.getContext().path).toContain('approved')
    expect(engine.getContext().path).not.toContain('rejected')
  })

  it('starts each completed run from an isolated context', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'restart-simulation',
      code: 'restart.simulation',
      name: 'Restart Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('script', 'scriptTask', {
          action: { actionType: 'script', language: 'java', source: 'result = amount' },
        }),
        node('end', 'end'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'script' },
        { id: 'leave', sourceId: 'script', targetId: 'end' },
      ],
    }
    const engine = createSimulationEngine(flow)

    const first = await engine.start({ amount: 1, stale: 'first-run' })
    const second = await engine.start({ amount: 2 })

    expect(first.state).toBe(ExecutionState.COMPLETED)
    expect(second.state).toBe(ExecutionState.COMPLETED)
    expect(second.finalVariables).toEqual({ amount: 2, result: 2 })
    expect(second.context.path).toEqual(['start', 'script', 'end'])
  })

  it('fails closed instead of pretending to execute concurrent branch semantics', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'parallel-simulation',
      code: 'parallel.simulation',
      name: 'Parallel Simulation',
      type: 'TBBPM',
      nodes: [
        node('start', 'start'),
        node('split', 'parallel'),
        node('left', 'end'),
        node('right', 'end'),
      ],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'split' },
        { id: 'left-flow', sourceId: 'split', targetId: 'left' },
        { id: 'right-flow', sourceId: 'split', targetId: 'right' },
      ],
    }

    const result = await createSimulationEngine(flow).start()

    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_CONCURRENT_GATEWAY_UNSUPPORTED')
    expect(result.context.path).not.toContain('left')
    expect(result.context.path).not.toContain('right')
  })

  it('fails when an exclusive gateway has no matching branch or default', async () => {
    const flow: UnifiedProcessDefinition = {
      id: 'no-match-simulation',
      code: 'no.match.simulation',
      name: 'No Match Simulation',
      type: 'TBBPM',
      nodes: [node('start', 'start'), node('exclusive', 'exclusive'), node('end', 'end')],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'exclusive' },
        {
          id: 'never',
          sourceId: 'exclusive',
          targetId: 'end',
          condition: 'amount > 100',
        },
      ],
    }

    const result = await createSimulationEngine(flow).start({ amount: 1 })

    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe('SIM_ERROR_NO_BRANCH_MATCHED:exclusive')
    expect(result.context.path).not.toContain('end')
  })

  it.each([
    ['waitTask', 'SIM_ERROR_TRIGGER_ENTRY_UNSUPPORTED:unsupported'],
    ['timerTask', 'SIM_ERROR_TIMER_UNSUPPORTED:unsupported'],
    ['while', 'SIM_ERROR_LOOP_UNSUPPORTED:unsupported'],
    ['foreach', 'SIM_ERROR_LOOP_UNSUPPORTED:unsupported'],
    ['subBpm', 'SIM_ERROR_EMBEDDED_PROCESS_UNSUPPORTED:unsupported'],
    ['bpmCall', 'SIM_ERROR_CALLED_PROCESS_UNSUPPORTED:unsupported'],
  ] as const)('fails closed for unsupported %s semantics', async (type, expectedError) => {
    const flow: UnifiedProcessDefinition = {
      id: `${type}-simulation`,
      code: `${type}.simulation`,
      name: `${type} Simulation`,
      type: 'TBBPM',
      nodes: [node('start', 'start'), node('unsupported', type), node('end', 'end')],
      connections: [
        { id: 'enter', sourceId: 'start', targetId: 'unsupported' },
        { id: 'leave', sourceId: 'unsupported', targetId: 'end' },
      ],
    }

    const result = await createSimulationEngine(flow).start()

    expect(result.state).toBe(ExecutionState.ERROR)
    expect(result.error).toBe(expectedError)
    expect(result.context.path).not.toContain('end')
  })
})
