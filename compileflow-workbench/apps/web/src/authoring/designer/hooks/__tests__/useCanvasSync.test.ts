import type { Cell, Graph } from '@antv/x6'
import { describe, expect, it, vi } from 'vitest'

import { syncGraphSelection } from '../useCanvasSync'

function cell(id: string): Cell {
  return { id } as Cell
}

describe('syncGraphSelection', () => {
  it('promotes a UI-only selection to the canonical graph selection', () => {
    const target = cell('target')
    const syncing = { current: false }
    const resetSelection = vi.fn(() => {
      expect(syncing.current).toBe(true)
    })
    const graph = {
      getCellById: () => target,
      getSelectedCells: () => [cell('stale')],
      resetSelection,
    } as unknown as Graph

    syncGraphSelection(graph, syncing, target.id)

    expect(resetSelection).toHaveBeenCalledWith(target)
    expect(syncing.current).toBe(false)
  })

  it('preserves a multi-selection that already contains the UI-selected cell', () => {
    const target = cell('target')
    const resetSelection = vi.fn()
    const graph = {
      getCellById: () => target,
      getSelectedCells: () => [cell('peer'), target],
      resetSelection,
    } as unknown as Graph

    syncGraphSelection(graph, { current: false }, target.id)

    expect(resetSelection).not.toHaveBeenCalled()
  })

  it('does not clear the graph selection when the UI selection is empty', () => {
    const resetSelection = vi.fn()
    const graph = { resetSelection } as unknown as Graph

    syncGraphSelection(graph, { current: false }, null)

    expect(resetSelection).not.toHaveBeenCalled()
  })
})
