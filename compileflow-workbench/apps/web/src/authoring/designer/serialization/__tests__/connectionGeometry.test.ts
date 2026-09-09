import { describe, expect, it } from 'vitest'

import { CONNECTION_PORTS } from '../../types/graphTypes'
import { readConnectionGeometry, writeConnectionGeometry } from '../connectionGeometry'

const element = (content: string) =>
  new DOMParser().parseFromString(`<transition>${content}</transition>`, 'text/xml').documentElement

describe('connection geometry', () => {
  it.each(
    CONNECTION_PORTS.flatMap((sourcePort) =>
      CONNECTION_PORTS.map((targetPort) => ({ sourcePort, targetPort }))
    )
  )('round-trips $sourcePort to $targetPort without changing bend coordinates', (ports) => {
    const geometry = {
      ...ports,
      waypoints: [
        { x: -12.5, y: 0 },
        { x: 40, y: 75 },
      ],
    }
    expect(
      readConnectionGeometry(
        element(writeConnectionGeometry({ id: 'edge', sourceId: 'a', targetId: 'b', ...geometry }))
      )
    ).toEqual(geometry)
  })

  it('does not add geometry to automatic connections', () => {
    expect(writeConnectionGeometry({ id: 'edge', sourceId: 'a', targetId: 'b' })).toBe('')
    expect(readConnectionGeometry(element(''))).toEqual({})
  })

  it.each([
    '<?workbench-edge {"sourcePort":"invalid"}?>',
    '<?workbench-edge {"waypoints":[{"x":"1","y":2}]}?>',
    '<?workbench-edge invalid?>',
    '<?workbench-edge {}?><?workbench-edge {}?>',
  ])('rejects invalid geometry instead of silently discarding it: %s', (content) => {
    expect(() => readConnectionGeometry(element(content))).toThrow()
  })
})
