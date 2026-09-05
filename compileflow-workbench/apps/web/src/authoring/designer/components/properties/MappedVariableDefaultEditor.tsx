import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { VariableMapping } from '../../types/action'

import { DefaultValueEditor } from './DefaultValueEditor'

interface MappedVariableDefaultEditorProps {
  variable: VariableMapping
  onChange: (defaultValue: string | undefined) => void
}

export function MappedVariableDefaultEditor({
  variable,
  onChange,
}: MappedVariableDefaultEditorProps) {
  const labels = usePropertyLabels()
  if (variable.direction === 'output') return <span>-</span>

  const hasSource = Boolean(variable.source?.trim())

  return (
    <DefaultValueEditor
      value={variable.defaultValue}
      onChange={onChange}
      enabledLabel={labels.defaultValueEnabled}
      inputLabel={labels.colDefaultValue}
      placeholder={labels.phDefaultValue}
      disabled={hasSource}
    />
  )
}
