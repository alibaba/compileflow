import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useXmlDraft } from '../../hooks/useXmlDraft'
import { XmlCodeEditorPanel } from '../XmlCodeEditorPanel'

vi.mock('@/shared/components/LazyMonacoEditor', () => ({
  MonacoEditor: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
    <textarea aria-label="XML" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

describe('XML input concurrency', () => {
  it.each(['resolve', 'reject'] as const)(
    'ignores an obsolete apply %s after newer typing',
    async (outcome) => {
      let finish!: () => void
      let fail!: (error: Error) => void
      const applying = new Promise<void>((resolve, reject) => {
        finish = resolve
        fail = reject
      })
      function Page() {
        const editor = useXmlDraft({
          sourceXml: 'original',
          documentId: 'document',
          onApply: () => applying,
        })
        return <XmlCodeEditorPanel editor={editor} />
      }
      render(<Page />)
      fireEvent.change(screen.getByLabelText('XML'), { target: { value: 'first' } })
      fireEvent.click(screen.getByLabelText('designer.xmlEditor.apply'))
      fireEvent.change(screen.getByLabelText('XML'), { target: { value: 'newer' } })
      await act(async () => {
        if (outcome === 'resolve') finish()
        else fail(new Error('obsolete parse'))
      })
      expect(screen.getByLabelText('XML')).toHaveValue('newer')
      expect(screen.getByLabelText('designer.xmlEditor.apply')).toBeEnabled()
      expect(screen.queryByText('obsolete parse')).not.toBeInTheDocument()
    }
  )
})
