import type { Dnd, Graph } from '@antv/x6'
import { act, fireEvent, render, screen } from '@testing-library/react'
import { vi } from 'vitest'

import { DragPaletteItem } from '../DragPaletteItem'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (_key: string, params: { label: string }) => params.label }),
}))

function renderItem(onKeyAddNode = vi.fn(), onTouchDrop = vi.fn()) {
  const createNode = vi.fn(() => ({ id: 'preview' }))
  const dndStart = vi.fn()
  render(
    <div className="node-palette">
      <DragPaletteItem
        accessibleLabel="自动任务"
        label="自动任务"
        color="#1890ff"
        graph={{ createNode } as unknown as Graph}
        dnd={{ start: dndStart } as unknown as Dnd}
        getConfig={() => ({ shape: 'tbbpm-auto-task', width: 200, height: 100 })}
        getNodeData={() => ({ label: '自动任务' })}
        onKeyAddNode={onKeyAddNode}
        onTouchDrop={onTouchDrop}
      />
    </div>
  )
  return {
    button: screen.getByRole('button', { name: '自动任务' }),
    onKeyAddNode,
    onTouchDrop,
    createNode,
    dndStart,
  }
}

function dispatchTouchPointer(
  target: Element,
  type: 'pointerdown' | 'pointermove' | 'pointerup' | 'pointercancel',
  init: { pointerId: number; clientX: number; clientY: number; button?: number }
) {
  const event = new MouseEvent(type, { bubbles: true, cancelable: true, ...init })
  Object.defineProperties(event, {
    pointerId: { value: init.pointerId },
    pointerType: { value: 'touch' },
  })
  void act(() => target.dispatchEvent(event))
}

describe('DragPaletteItem touch gestures', () => {
  it('drops a touch drag at its client coordinates without also treating it as a click', () => {
    const { button, onKeyAddNode, onTouchDrop } = renderItem()

    dispatchTouchPointer(button, 'pointerdown', {
      pointerId: 1,
      button: 0,
      clientX: 20,
      clientY: 30,
    })
    dispatchTouchPointer(button, 'pointermove', {
      pointerId: 1,
      clientX: 120,
      clientY: 40,
    })
    dispatchTouchPointer(button, 'pointerup', {
      pointerId: 1,
      clientX: 240,
      clientY: 50,
    })
    fireEvent.click(button, { detail: 1 })

    expect(onTouchDrop).toHaveBeenCalledOnce()
    expect(onTouchDrop).toHaveBeenCalledWith(240, 50)
    expect(onKeyAddNode).not.toHaveBeenCalled()
  })

  it('keeps a stationary touch as the existing single-click add action', () => {
    const { button, onKeyAddNode, onTouchDrop } = renderItem()

    dispatchTouchPointer(button, 'pointerdown', {
      pointerId: 2,
      button: 0,
      clientX: 20,
      clientY: 30,
    })
    dispatchTouchPointer(button, 'pointerup', {
      pointerId: 2,
      clientX: 23,
      clientY: 33,
    })
    fireEvent.click(button, { detail: 1 })

    expect(onTouchDrop).not.toHaveBeenCalled()
    expect(onKeyAddNode).toHaveBeenCalledOnce()
  })

  it('leaves a vertical gesture to palette scrolling without adding or dropping a node', () => {
    const { button, onKeyAddNode, onTouchDrop } = renderItem()

    dispatchTouchPointer(button, 'pointerdown', {
      pointerId: 4,
      button: 0,
      clientX: 20,
      clientY: 30,
    })
    dispatchTouchPointer(button, 'pointermove', {
      pointerId: 4,
      clientX: 22,
      clientY: 90,
    })
    dispatchTouchPointer(button, 'pointerup', {
      pointerId: 4,
      clientX: 22,
      clientY: 130,
    })
    fireEvent.click(button, { detail: 1 })

    expect(onTouchDrop).not.toHaveBeenCalled()
    expect(onKeyAddNode).not.toHaveBeenCalled()
  })

  it('does not drop when the drag finishes over the palette overlay', () => {
    const { button, onTouchDrop } = renderItem()
    vi.spyOn(button.closest('.node-palette')!, 'getBoundingClientRect').mockReturnValue({
      bottom: 300,
      height: 300,
      left: 0,
      right: 300,
      top: 0,
      width: 300,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    })

    dispatchTouchPointer(button, 'pointerdown', {
      pointerId: 3,
      button: 0,
      clientX: 20,
      clientY: 30,
    })
    dispatchTouchPointer(button, 'pointermove', {
      pointerId: 3,
      clientX: 120,
      clientY: 130,
    })
    dispatchTouchPointer(button, 'pointerup', {
      pointerId: 3,
      clientX: 240,
      clientY: 260,
    })

    expect(onTouchDrop).not.toHaveBeenCalled()
  })
})

describe('DragPaletteItem mouse gestures', () => {
  it('adds exactly once for a stationary click without starting Dnd', () => {
    const { button, onKeyAddNode, createNode, dndStart } = renderItem()

    fireEvent.mouseDown(button, { button: 0, clientX: 20, clientY: 30 })
    fireEvent.mouseUp(document, { button: 0, clientX: 20, clientY: 30 })
    fireEvent.click(button, { detail: 1 })

    expect(onKeyAddNode).toHaveBeenCalledOnce()
    expect(createNode).not.toHaveBeenCalled()
    expect(dndStart).not.toHaveBeenCalled()
  })

  it('starts Dnd only after pointer movement and suppresses the following click', () => {
    const { button, onKeyAddNode, createNode, dndStart } = renderItem()

    fireEvent.mouseDown(button, { button: 0, clientX: 20, clientY: 30 })
    fireEvent.mouseMove(document, { clientX: 21, clientY: 31 })
    expect(dndStart).not.toHaveBeenCalled()
    fireEvent.mouseMove(document, { clientX: 40, clientY: 50 })
    fireEvent.mouseUp(document, { button: 0, clientX: 100, clientY: 120 })
    fireEvent.click(button, { detail: 1 })

    expect(createNode).toHaveBeenCalledOnce()
    expect(dndStart).toHaveBeenCalledOnce()
    expect(onKeyAddNode).not.toHaveBeenCalled()
  })

  it('keeps keyboard activation available after a mouse drag leaves no button click', () => {
    const { button, onKeyAddNode } = renderItem()

    fireEvent.mouseDown(button, { button: 0, clientX: 20, clientY: 30 })
    fireEvent.mouseMove(document, { clientX: 80, clientY: 60 })
    fireEvent.mouseUp(document, { button: 0, clientX: 180, clientY: 100 })
    fireEvent.click(button, { detail: 0 })

    expect(onKeyAddNode).toHaveBeenCalledOnce()
  })
})

describe('DragPaletteItem cancelled pointers', () => {
  it('keeps keyboard activation available after a cancelled touch drag', () => {
    const { button, onKeyAddNode, onTouchDrop } = renderItem()

    dispatchTouchPointer(button, 'pointerdown', {
      pointerId: 5,
      button: 0,
      clientX: 20,
      clientY: 30,
    })
    dispatchTouchPointer(button, 'pointermove', {
      pointerId: 5,
      clientX: 100,
      clientY: 40,
    })
    dispatchTouchPointer(button, 'pointercancel', {
      pointerId: 5,
      clientX: 100,
      clientY: 40,
    })
    fireEvent.click(button, { detail: 0 })

    expect(onTouchDrop).not.toHaveBeenCalled()
    expect(onKeyAddNode).toHaveBeenCalledOnce()
  })
})
