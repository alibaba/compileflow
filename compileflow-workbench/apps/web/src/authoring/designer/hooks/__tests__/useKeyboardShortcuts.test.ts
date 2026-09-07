import { fireEvent, renderHook } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useKeyboardShortcuts } from '../useKeyboardShortcuts'

describe('designer shortcut editing boundaries', () => {
  it.each(['true', '', 'plaintext-only'])(
    'leaves nested contenteditable=%s editing to the browser',
    (contentEditable) => {
      const handler = vi.fn()
      renderHook(() =>
        useKeyboardShortcuts([{ id: 'delete', key: 'Delete', description: 'Delete node', handler }])
      )
      const editor = document.createElement('div')
      editor.setAttribute('contenteditable', contentEditable)
      const text = document.createElement('span')
      editor.append(text)
      document.body.append(editor)
      try {
        fireEvent.keyDown(text, { key: 'Delete' })
        expect(handler).not.toHaveBeenCalled()
      } finally {
        editor.remove()
      }
    }
  )

  it('does not dispatch designer actions during IME composition', () => {
    const handler = vi.fn()
    renderHook(() =>
      useKeyboardShortcuts([{ id: 'delete', key: 'Delete', description: 'Delete node', handler }])
    )

    fireEvent.keyDown(window, { key: 'Delete', isComposing: true })

    expect(handler).not.toHaveBeenCalled()
    fireEvent.keyDown(window, { key: 'Delete' })
    expect(handler).toHaveBeenCalledOnce()
  })
})
