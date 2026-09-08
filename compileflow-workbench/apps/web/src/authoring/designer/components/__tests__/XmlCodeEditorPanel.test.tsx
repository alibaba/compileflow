import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { useXmlDraft } from '../../hooks/useXmlDraft'
import { XmlCodeEditorPanel } from '../XmlCodeEditorPanel'

vi.mock('@/shared/components/LazyMonacoEditor', () => ({
  MonacoEditor: ({ value, onChange }: { value: string; onChange: (value: string) => void }) => (
    <textarea aria-label="XML" value={value} onChange={(event) => onChange(event.target.value)} />
  ),
}))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

describe('XML input validation', () => {
  it('keeps malformed XML editable and shows the parser error', () => {
    function Page() {
      const editor = useXmlDraft({
        sourceXml: 'original',
        documentId: 'document',
        onApply: () => {
          throw new Error('invalid XML')
        },
      })
      return <XmlCodeEditorPanel editor={editor} />
    }
    render(<Page />)
    fireEvent.change(screen.getByLabelText('XML'), { target: { value: 'malformed' } })
    fireEvent.click(screen.getByLabelText('designer.xmlEditor.apply'))

    expect(screen.getByLabelText('XML')).toHaveValue('malformed')
    expect(screen.getByLabelText('designer.xmlEditor.apply')).toBeEnabled()
    expect(screen.getByText('invalid XML')).toBeInTheDocument()
  })
})
