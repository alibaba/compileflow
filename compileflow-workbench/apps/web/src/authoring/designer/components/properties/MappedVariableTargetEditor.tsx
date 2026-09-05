import { Input, Select } from 'antd'

import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import type { VariableMapping } from '../../types/action'

interface MappedVariableTargetEditorProps {
  variable: VariableMapping
  processVariableNames: string[]
  onChange: (value: string | undefined) => void
}

export function MappedVariableTargetEditor({
  variable,
  processVariableNames,
  onChange,
}: MappedVariableTargetEditorProps) {
  const labels = usePropertyLabels()

  if (variable.direction === 'output') {
    return (
      <Select
        allowClear={false}
        size="small"
        status={!variable.target ? 'error' : undefined}
        value={variable.target || undefined}
        onChange={onChange}
        options={processVariableNames.map((name) => ({ label: name, value: name }))}
        placeholder={labels.phMappingReference}
        aria-label={labels.colMappingReference}
        style={{ width: '100%' }}
      />
    )
  }

  return (
    <Input
      size="small"
      value={variable.source || ''}
      onChange={(event) => onChange(event.target.value)}
      placeholder={labels.phMappingReference}
      aria-label={labels.colMappingReference}
    />
  )
}
