import { Flex, Input, Switch } from 'antd'

interface DefaultValueEditorProps {
  value: string | undefined
  onChange: (value: string | undefined) => void
  enabledLabel: string
  inputLabel?: string
  placeholder: string
  disabled?: boolean
  size?: 'small' | 'middle' | 'large'
}

export function DefaultValueEditor({
  value,
  onChange,
  enabledLabel,
  inputLabel,
  placeholder,
  disabled = false,
  size = 'small',
}: DefaultValueEditorProps) {
  const enabled = value !== undefined

  return (
    <Flex gap={8} align="center">
      <Switch
        size="small"
        checked={enabled}
        disabled={disabled}
        onChange={(checked) => onChange(checked ? '' : undefined)}
        aria-label={enabledLabel}
      />
      <Input
        size={size}
        style={{ flex: 1 }}
        disabled={disabled || !enabled}
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        aria-label={inputLabel ?? placeholder}
      />
    </Flex>
  )
}
