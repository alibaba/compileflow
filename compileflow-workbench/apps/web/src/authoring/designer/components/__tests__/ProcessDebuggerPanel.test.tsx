import { act, fireEvent, render, screen } from '@testing-library/react'
import { useLayoutEffect } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { ProcessSimulationEngine } from '../../simulation/ProcessSimulationEngine'
import type { UnifiedProcessDefinition } from '../../types/flowDefinition'
import ProcessDebuggerPanel from '../ProcessDebuggerPanel'

const messages = vi.hoisted(() => ({ success: vi.fn(), error: vi.fn(), info: vi.fn() }))
vi.mock('antd', async (original) => {
  const actual = await original<typeof import('antd')>()
  return { ...actual, App: { ...actual.App, useApp: () => ({ message: messages }) } }
})
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('../EngineDebugSection', () => ({ EngineDebugSection: () => null }))

function fakeEngine() {
  let state = 'ready'
  let variables = new Map<string, unknown>()
  const listeners = new Set<(event: unknown) => void>()
  return {
    start: vi.fn(),
    stepNext: vi.fn(),
    continue: vi.fn(),
    reset: vi.fn(() => {
      state = 'ready'
      variables = new Map()
    }),
    getState: () => state,
    getContext: () => ({ variables }),
    getBreakpoints: () => [],
    on: (listener: (event: unknown) => void) => listeners.add(listener),
    off: (listener: (event: unknown) => void) => listeners.delete(listener),
    enter: (nodeId: string) => {
      for (const listener of listeners) listener({ type: 'node-enter', nodeId, timestamp: 0 })
    },
    pause: () => {
      state = 'paused'
      for (const listener of listeners) listener({ type: 'breakpoint-hit', timestamp: 0 })
    },
    complete: () => {
      state = 'completed'
      variables.set('obsoleteResult', 'old-value')
    },
  }
}

const flow: UnifiedProcessDefinition = {
  id: 'old',
  code: 'old',
  name: 'Old',
  type: 'TBBPM',
  nodes: [],
  connections: [],
}

describe('simulation debugger caller request ownership', () => {
  beforeEach(() => vi.clearAllMocks())

  it('detaches old engine events before the replacement layout is observable', () => {
    const oldEngine = fakeEngine()
    const newEngine = fakeEngine()
    const highlight = vi.fn()
    function Host({ replacement }: { replacement: boolean }) {
      useLayoutEffect(() => {
        if (replacement) oldEngine.enter('obsolete-node')
      }, [replacement])
      return (
        <ProcessDebuggerPanel
          flowDefinition={replacement ? { ...flow, name: 'Changed same ID' } : flow}
          simulationEngine={
            (replacement ? newEngine : oldEngine) as unknown as ProcessSimulationEngine
          }
          onHighlightNode={highlight}
        />
      )
    }
    const view = render(<Host replacement={false} />)
    view.rerender(<Host replacement />)
    expect(highlight).not.toHaveBeenCalledWith('obsolete-node')
    expect(screen.queryByText('obsolete-node')).not.toBeInTheDocument()
    act(() => newEngine.enter('current-node'))
    expect(highlight).toHaveBeenCalledWith('current-node')
    expect(screen.getByText('current-node')).toBeInTheDocument()
  })

  it('still displays and notifies a current successful start', async () => {
    const engine = fakeEngine()
    engine.start.mockResolvedValue({
      state: 'completed',
      finalVariables: { currentResult: 'value' },
    })
    render(
      <ProcessDebuggerPanel
        flowDefinition={flow}
        simulationEngine={engine as unknown as ProcessSimulationEngine}
      />
    )
    await act(async () =>
      fireEvent.click(screen.getByText('designer.debug.sim.start').closest('button')!)
    )
    expect(screen.getByText('currentResult')).toBeInTheDocument()
    expect(screen.getByText('designer.debug.state.completed')).toBeInTheDocument()
    expect(messages.success).toHaveBeenCalledOnce()
  })

  it.each(
    (['start', 'stepNext', 'continue'] as const).flatMap((operation) =>
      (['reset', 'document', 'engine', 'unmount'] as const).flatMap((invalidate) =>
        (['resolve', 'reject'] as const).map((outcome) => ({ operation, invalidate, outcome }))
      )
    )
  )(
    'ignores obsolete $operation $outcome after $invalidate',
    async ({ operation, invalidate, outcome }) => {
      const engine = fakeEngine()
      let finish!: (value: unknown) => void
      let fail!: (error: Error) => void
      const pending = new Promise((resolve, reject) => {
        finish = resolve
        fail = reject
      })
      engine[operation].mockReturnValue(pending)
      const props = {
        flowDefinition: flow,
        simulationEngine: engine as unknown as ProcessSimulationEngine,
      }
      const view = render(<ProcessDebuggerPanel {...props} />)
      if (operation !== 'start') act(() => engine.pause())
      const label = operation === 'stepNext' ? 'step' : operation
      fireEvent.click(screen.getByText(`designer.debug.sim.${label}`).closest('button')!)
      expect(engine[operation]).toHaveBeenCalledOnce()
      if (invalidate === 'reset')
        fireEvent.click(screen.getByText('designer.debug.sim.reset').closest('button')!)
      if (invalidate === 'document')
        view.rerender(
          <ProcessDebuggerPanel {...props} flowDefinition={{ ...flow, id: 'new', code: 'new' }} />
        )
      if (invalidate === 'engine')
        view.rerender(
          <ProcessDebuggerPanel
            {...props}
            simulationEngine={fakeEngine() as unknown as ProcessSimulationEngine}
          />
        )
      if (invalidate === 'unmount') view.unmount()
      await act(async () => {
        engine.complete()
        if (outcome === 'resolve')
          finish({ state: 'completed', finalVariables: { obsoleteResult: 'old-value' } })
        else fail(new Error('obsolete failure'))
        await pending.catch(() => {})
      })
      expect(messages.error).not.toHaveBeenCalled()
      expect(messages.success).not.toHaveBeenCalled()
      expect(screen.queryByText('obsoleteResult')).not.toBeInTheDocument()
      if (invalidate !== 'unmount')
        expect(screen.getByText('designer.debug.state.ready')).toBeInTheDocument()
    }
  )
})
