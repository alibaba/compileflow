import { act, fireEvent, render, renderHook, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { XmlCodeEditorPanel } from '../../components/XmlCodeEditorPanel'
import { useXmlDraft } from '../useXmlDraft'

vi.mock('@/shared/components/LazyMonacoEditor', () => ({
  MonacoEditor: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
    <textarea aria-label="XML" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}))

describe('page-owned XML draft', () => {
  it('retains unapplied input across panel unmount and canvas updates', () => {
    function Page({ visible, source }: { visible: boolean; source: string }) {
      const editor = useXmlDraft({
        sourceXml: source,
        documentId: 'document',
        onApply: () => {},
      })
      return visible ? <XmlCodeEditorPanel editor={editor} /> : null
    }
    const view = render(<Page visible source="original" />)
    fireEvent.change(screen.getByLabelText('XML'), { target: { value: 'unsaved' } })
    view.rerender(<Page visible={false} source="canvas edit" />)
    view.rerender(<Page visible source="canvas edit" />)
    expect(screen.getByLabelText('XML')).toHaveValue('unsaved')
  })

  it.each(['edit', 'reload', 'unchanged'] as const)(
    'save waits for XML and refuses leaving after %s',
    async (change) => {
      const order: string[] = []
      let finish!: (value: boolean) => void
      const persist = vi.fn(() => {
        order.push('save')
        return new Promise<boolean>((resolve) => {
          finish = resolve
        })
      })
      const { result, rerender } = renderHook(
        ({ documentId }) =>
          useXmlDraft({
            sourceXml: 'original',
            documentId,
            onApply: (xml) => {
              order.push(xml)
            },
          }),
        { initialProps: { documentId: 'document' } }
      )
      act(() => result.current.handleChange('new XML'))
      let saving!: Promise<boolean>
      await act(async () => {
        saving = result.current.save(persist)
        await Promise.resolve()
      })
      expect(order).toEqual(['new XML', 'save'])
      if (change === 'edit') act(() => result.current.handleChange('newer XML'))
      if (change === 'reload') rerender({ documentId: 'same-id-new-document' })
      let canLeave: boolean | undefined
      await act(async () => {
        finish(true)
        canLeave = await saving
      })
      expect(canLeave).toBe(change === 'unchanged')
    }
  )

  it('does not persist when parsing fails', async () => {
    const persist = vi.fn()
    const { result } = renderHook(() =>
      useXmlDraft({
        sourceXml: 'original',
        documentId: 'document',
        onApply: () => {
          throw new Error('invalid XML')
        },
      })
    )
    act(() => result.current.handleChange('invalid'))
    await act(async () => expect(await result.current.save(persist)).toBe(false))
    expect(persist).not.toHaveBeenCalled()
    expect(result.current.isDirty).toBe(true)
  })
})
