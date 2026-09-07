import { describe, expect, it } from 'vitest'

import type { TbbpmNode } from '../../types/tbbpm'
import { TbbpmValidator } from '../TbbpmValidator'

describe('TBBPM validation on cyclic parents', () => {
  it.each(['loop variables', 'nearest loop'] as const)(
    'terminates while resolving %s',
    (lookup) => {
      const cyclic: TbbpmNode = {
        id: 'cycle',
        type: lookup === 'loop variables' ? 'while' : 'subBpm',
        position: { x: 0, y: 0 },
        properties:
          lookup === 'loop variables'
            ? { condition: 'true', maxIterations: 1, index: 'index' }
            : {},
      }
      // Bound synchronous traversal so a regression fails instead of hanging the test worker.
      let parentReads = 0
      Object.defineProperty(cyclic, 'parentId', {
        get() {
          if (++parentReads > 100) throw new Error('Unbounded cyclic-parent traversal')
          return cyclic.id
        },
      })
      const nodes: TbbpmNode[] = [cyclic]
      if (lookup === 'nearest loop') {
        nodes.push(
          {
            id: 'parallel',
            type: 'foreach',
            position: { x: 0, y: 0 },
            properties: {
              execution: 'parallel',
              collection: 'items',
              item: 'item',
              itemType: 'java.lang.String',
            },
          },
          {
            id: 'break',
            type: 'break',
            parentId: cyclic.id,
            position: { x: 0, y: 0 },
            properties: {},
          }
        )
      }

      const errors = new TbbpmValidator(nodes, [], [{ name: 'items' }]).validateNodeRules()
      expect(errors.map((error) => error.code)).toContain('container.parentCycle')
    }
  )
})
