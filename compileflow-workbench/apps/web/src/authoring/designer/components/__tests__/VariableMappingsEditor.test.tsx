import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { vi } from 'vitest'

import type { ActionDefinition, VariableMapping } from '../../types/action'
import { ActionEditor, VariableMappingsEditor } from '../properties/ActionEditor'

vi.mock('@/shared/components/LazyMonacoEditor', () => ({
  MonacoEditor: ({
    value,
    onChange,
    options,
  }: {
    value: string
    onChange: (value: string) => void
    options?: { ariaLabel?: string }
  }) => (
    <textarea
      aria-label={options?.ariaLabel}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}))

vi.mock('@/app/hooks', () => ({
  useAppSelector: () => ({ variables: [] }),
}))

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}))

function ProcessCallMappingsHarness({
  onChange,
}: {
  onChange: (value: VariableMapping[]) => void
}) {
  const [value, setValue] = useState<VariableMapping[]>([])
  return (
    <VariableMappingsEditor
      value={value}
      processCall
      onChange={(nextValue) => {
        setValue(nextValue)
        onChange(nextValue)
      }}
    />
  )
}

function ActionEditorHarness({ onChange }: { onChange: (value: ActionDefinition) => void }) {
  const [value, setValue] = useState<ActionDefinition>({
    actionType: 'script',
    language: 'qlexpress',
    source: '"Hello"',
    mappings: [],
  })
  return (
    <ActionEditor
      value={value}
      onChange={(nextValue) => {
        setValue(nextValue)
        onChange(nextValue)
      }}
    />
  )
}

describe('VariableMappingsEditor', () => {
  it('uses the process-call contract without a Java type field', () => {
    const onChange = vi.fn()
    render(<ProcessCallMappingsHarness onChange={onChange} />)

    expect(screen.getByText('designer.props.section.varTransfer')).toBeInTheDocument()
    expect(screen.getByText('designer.props.table.emptySubVars')).toBeInTheDocument()
    expect(screen.queryByText('designer.props.col.javaType')).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'plus designer.props.common.add' }))

    expect(onChange).toHaveBeenLastCalledWith([{ direction: 'input', target: '' }])
    expect(screen.queryByText('designer.props.col.javaType')).not.toBeInTheDocument()
  })

  it('exposes editable mapping fields by accessible labels', () => {
    const onChange = vi.fn()
    render(<ProcessCallMappingsHarness onChange={onChange} />)

    fireEvent.click(screen.getByRole('button', { name: 'plus designer.props.common.add' }))
    fireEvent.change(screen.getByLabelText('designer.props.col.localVarName'), {
      target: { value: 'request' },
    })
    fireEvent.change(screen.getByLabelText('designer.props.col.mappingReference'), {
      target: { value: 'payload' },
    })

    expect(onChange).toHaveBeenLastCalledWith([
      { direction: 'input', target: 'request', source: 'payload' },
    ])
  })
})

describe('ActionEditor', () => {
  it('exposes script action fields by accessible labels', () => {
    const onChange = vi.fn()
    render(<ActionEditorHarness onChange={onChange} />)

    fireEvent.change(screen.getByLabelText('designer.props.common.scriptLanguage'), {
      target: { value: 'java' },
    })
    fireEvent.change(screen.getByLabelText('designer.props.common.scriptSource'), {
      target: { value: 'return true;' },
    })

    expect(onChange).toHaveBeenLastCalledWith(expect.objectContaining({ source: 'return true;' }))
  })
})
