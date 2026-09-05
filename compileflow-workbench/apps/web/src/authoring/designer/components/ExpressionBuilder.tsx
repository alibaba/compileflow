import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  FunctionOutlined,
} from '@ant-design/icons'
import type { SelectProps } from 'antd'
import { Alert, Button, Collapse, Input, Select, Space, Tag, Tooltip } from 'antd'
import type { TFunction } from 'i18next'
import type { MutableRefObject } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { ExpressionVariable } from './expressionVariables'

import './ExpressionBuilder.css'

const { TextArea } = Input

type BuilderMode = 'visual' | 'text'

interface ExpressionBuilderProps {
  value?: string
  onChange?: (value: string) => void
  variables?: ExpressionVariable[]
}

interface OperatorOption {
  label: string
  value: string
  category: string
  suffix?: string
}

interface BuiltInFunctionOption {
  name: string
  description: string
}

interface ExpressionTemplate {
  name: string
  expression: string
  description: string
}

interface ExpressionValidation {
  valid: boolean
  message: string
}

function buildOperators(t: TFunction): OperatorOption[] {
  return [
    { label: t('designer.expr.op.eq'), value: '==', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.ne'), value: '!=', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.gt'), value: '>', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.lt'), value: '<', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.gte'), value: '>=', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.lte'), value: '<=', category: t('designer.expr.cat.compare') },
    { label: t('designer.expr.op.and'), value: '&&', category: t('designer.expr.cat.logic') },
    { label: t('designer.expr.op.or'), value: '||', category: t('designer.expr.cat.logic') },
    { label: t('designer.expr.op.not'), value: '!', category: t('designer.expr.cat.logic') },
    {
      label: t('designer.expr.op.contains'),
      value: '.contains(',
      category: t('designer.expr.cat.string'),
      suffix: ')',
    },
    {
      label: t('designer.expr.op.startsWith'),
      value: '.startsWith(',
      category: t('designer.expr.cat.string'),
      suffix: ')',
    },
    {
      label: t('designer.expr.op.endsWith'),
      value: '.endsWith(',
      category: t('designer.expr.cat.string'),
      suffix: ')',
    },
    {
      label: t('designer.expr.op.isNull'),
      value: ' == null',
      category: t('designer.expr.cat.null'),
    },
    {
      label: t('designer.expr.op.isNotNull'),
      value: ' != null',
      category: t('designer.expr.cat.null'),
    },
  ]
}

function buildBuiltInFunctions(t: TFunction): BuiltInFunctionOption[] {
  return [
    { name: 'isEmpty(str)', description: t('designer.expr.fn.isEmpty') },
    { name: 'isNotEmpty(str)', description: t('designer.expr.fn.isNotEmpty') },
    { name: 'contains(str, substr)', description: t('designer.expr.fn.contains') },
    { name: 'length(str)', description: t('designer.expr.fn.length') },
    { name: 'toUpperCase(str)', description: t('designer.expr.fn.toUpperCase') },
    { name: 'toLowerCase(str)', description: t('designer.expr.fn.toLowerCase') },
    { name: 'parseInt(str)', description: t('designer.expr.fn.parseInt') },
    { name: 'parseDouble(str)', description: t('designer.expr.fn.parseDouble') },
  ]
}

function buildExpressionTemplates(t: TFunction): ExpressionTemplate[] {
  return [
    {
      name: t('designer.expr.tpl.strEq'),
      expression: 'variable == "value"',
      description: t('designer.expr.tpl.strEqDesc'),
    },
    {
      name: t('designer.expr.tpl.numCmp'),
      expression: 'variable > 100',
      description: t('designer.expr.tpl.numCmpDesc'),
    },
    {
      name: t('designer.expr.tpl.range'),
      expression: 'variable >= 0 && variable <= 100',
      description: t('designer.expr.tpl.rangeDesc'),
    },
    {
      name: t('designer.expr.tpl.notEmpty'),
      expression: 'variable != null && variable != ""',
      description: t('designer.expr.tpl.notEmptyDesc'),
    },
    {
      name: t('designer.expr.tpl.contains'),
      expression: 'variable.contains("keyword")',
      description: t('designer.expr.tpl.containsDesc'),
    },
    {
      name: t('designer.expr.tpl.multiOr'),
      expression: 'variable == "A" || variable == "B"',
      description: t('designer.expr.tpl.multiOrDesc'),
    },
  ]
}

function buildVariableOptions(
  variables: ExpressionBuilderProps['variables']
): SelectProps['options'] {
  return variables?.map((variable) => ({
    label: (
      <Space>
        <span>{variable.name}</span>
        <Tag color="blue" style={{ fontSize: 11 }}>
          {variable.type}
        </Tag>
      </Space>
    ),
    value: variable.name,
    description: variable.description,
  }))
}

function validateExpressionText(expressionText: string, t: TFunction): ExpressionValidation {
  if (!expressionText || expressionText.trim() === '') {
    return { valid: false, message: t('designer.expr.validation.empty') }
  }

  const openParens = (expressionText.match(/\(/g) || []).length
  const closeParens = (expressionText.match(/\)/g) || []).length

  if (openParens !== closeParens) {
    return { valid: false, message: t('designer.expr.validation.parens') }
  }

  if (/[^\w\s!<>=&|()."'+-/*%]/.test(expressionText)) {
    return { valid: false, message: t('designer.expr.validation.illegal') }
  }

  return { valid: true, message: t('designer.expr.validation.ok') }
}

function insertTextAtCursor(
  textareaRef: MutableRefObject<HTMLTextAreaElement | null>,
  expressionText: string,
  text: string,
  suffix: string,
  onChange: (value: string) => void
) {
  const textarea = textareaRef.current
  if (!textarea) {
    onChange(expressionText + text + suffix)
    return
  }

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const newText = expressionText.substring(0, start) + text + suffix + expressionText.substring(end)

  onChange(newText)

  setTimeout(() => {
    textarea.focus()
    textarea.setSelectionRange(start + text.length, start + text.length)
  }, 0)
}

function ModeToolbar({
  mode,
  validation,
  onModeChange,
}: {
  mode: BuilderMode
  validation: ExpressionValidation
  onModeChange: (mode: BuilderMode) => void
}) {
  const { t } = useTranslation()

  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <Space>
        <Button
          size="small"
          type={mode === 'visual' ? 'primary' : 'default'}
          onClick={() => onModeChange('visual')}
          icon={<FunctionOutlined />}
        >
          {t('designer.expr.modeVisual')}
        </Button>
        <Button
          size="small"
          type={mode === 'text' ? 'primary' : 'default'}
          onClick={() => onModeChange('text')}
          icon={<CodeOutlined />}
        >
          {t('designer.expr.modeText')}
        </Button>
      </Space>

      {validation.valid ? (
        <Tag icon={<CheckCircleOutlined />} color="success">
          {validation.message}
        </Tag>
      ) : (
        <Tag icon={<CloseCircleOutlined />} color="error">
          {validation.message}
        </Tag>
      )}
    </div>
  )
}

function VisualInsertControls({
  operators,
  variableOptions,
  onInsertOperator,
  onInsertVariable,
}: {
  operators: OperatorOption[]
  variableOptions: SelectProps['options']
  onInsertOperator: (operator: string, suffix?: string) => void
  onInsertVariable: (variableName: string) => void
}) {
  const { t } = useTranslation()

  return (
    <Space vertical size="small" style={{ width: '100%' }}>
      <div>
        <div className="expression-builder-label">{t('designer.expr.insertVariable')}</div>
        <Select
          placeholder={t('designer.expr.selectVariable')}
          style={{ width: '100%' }}
          options={variableOptions}
          onChange={onInsertVariable}
          showSearch
          filterOption={(input, option) =>
            (option?.value as string).toLowerCase().includes(input.toLowerCase())
          }
        />
      </div>

      <div>
        <div className="expression-builder-label">{t('designer.expr.insertOperator')}</div>
        <Space wrap>
          {operators.map((operator) => (
            <Tooltip key={operator.value} title={operator.category}>
              <Button
                size="small"
                onClick={() => onInsertOperator(operator.value, operator.suffix)}
              >
                {operator.label}
              </Button>
            </Tooltip>
          ))}
        </Space>
      </div>
    </Space>
  )
}

function ExpressionTextInput({
  expressionText,
  textareaRef,
  onChange,
}: {
  expressionText: string
  textareaRef: MutableRefObject<HTMLTextAreaElement | null>
  onChange: (value: string) => void
}) {
  const { t } = useTranslation()

  return (
    <div>
      <div className="expression-builder-label">{t('designer.expr.label')}</div>
      <TextArea
        ref={(node) => {
          textareaRef.current = node?.resizableTextArea?.textArea ?? null
        }}
        value={expressionText}
        onChange={(event) => onChange(event.target.value)}
        placeholder={t('designer.expr.placeholder')}
        rows={4}
        className="expression-builder-textarea"
      />
    </div>
  )
}

function ExpressionReferencePanels({
  builtInFunctions,
  expressionTemplates,
  onApplyTemplate,
}: {
  builtInFunctions: BuiltInFunctionOption[]
  expressionTemplates: ExpressionTemplate[]
  onApplyTemplate: (template: string) => void
}) {
  const { t } = useTranslation()

  return (
    <Collapse
      size="small"
      ghost
      items={[
        {
          key: 'functions',
          label: t('designer.expr.panelFunctions'),
          children: (
            <Space vertical size={4} style={{ width: '100%' }}>
              {builtInFunctions.map((func) => (
                <div key={func.name} className="expression-builder-func-item">
                  <code className="expression-builder-code">{func.name}</code>
                  <span className="expression-builder-desc">{func.description}</span>
                </div>
              ))}
            </Space>
          ),
        },
        {
          key: 'templates',
          label: t('designer.expr.panelTemplates'),
          children: (
            <Space vertical size={4} style={{ width: '100%' }}>
              {expressionTemplates.map((template) => (
                <div key={template.expression} className="expression-builder-template-item">
                  <div>
                    <div className="expression-builder-template-name">{template.name}</div>
                    <code className="expression-builder-template-code">{template.expression}</code>
                    <div className="expression-builder-template-desc">{template.description}</div>
                  </div>
                  <Button
                    size="small"
                    type="link"
                    onClick={() => onApplyTemplate(template.expression)}
                  >
                    {t('designer.expr.apply')}
                  </Button>
                </div>
              ))}
            </Space>
          ),
        },
      ]}
    />
  )
}

function SyntaxHint() {
  const { t } = useTranslation()

  return (
    <Alert
      title={t('designer.expr.syntaxHint')}
      description={
        <ul style={{ margin: 0, paddingLeft: 20, fontSize: 12 }}>
          <li>{t('designer.expr.syntaxTip1')}</li>
          <li>{t('designer.expr.syntaxTip2')}</li>
          <li>{t('designer.expr.syntaxTip3')}</li>
          <li>{t('designer.expr.syntaxTip4')}</li>
        </ul>
      }
      type="info"
      showIcon
      style={{ fontSize: 12 }}
    />
  )
}

export function ExpressionBuilder({
  value = '',
  onChange,
  variables = [],
}: ExpressionBuilderProps) {
  const { t } = useTranslation()
  const [expressionText, setExpressionText] = useState(value)
  const [mode, setMode] = useState<BuilderMode>('text')
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  const operators = useMemo(() => buildOperators(t), [t])
  const builtInFunctions = useMemo(() => buildBuiltInFunctions(t), [t])
  const expressionTemplates = useMemo(() => buildExpressionTemplates(t), [t])
  const variableOptions = useMemo(() => buildVariableOptions(variables), [variables])
  const validation = useMemo(() => validateExpressionText(expressionText, t), [expressionText, t])

  useEffect(() => {
    setExpressionText(value ?? '')
  }, [value])

  const handleExpressionChange = useCallback(
    (newValue: string) => {
      setExpressionText(newValue)
      onChange?.(newValue)
    },
    [onChange]
  )

  const handleInsertVariable = useCallback(
    (variableName: string) => {
      insertTextAtCursor(textareaRef, expressionText, variableName, '', handleExpressionChange)
    },
    [expressionText, handleExpressionChange]
  )

  const handleInsertOperator = useCallback(
    (operator: string, suffix: string = '') => {
      insertTextAtCursor(
        textareaRef,
        expressionText,
        ` ${operator}${suffix ? '' : ' '}`,
        suffix,
        handleExpressionChange
      )
    },
    [expressionText, handleExpressionChange]
  )

  return (
    <div className="expression-builder" style={{ width: '100%' }}>
      <Space vertical size="middle" style={{ width: '100%' }}>
        <ModeToolbar mode={mode} validation={validation} onModeChange={setMode} />

        {mode === 'visual' && (
          <VisualInsertControls
            operators={operators}
            variableOptions={variableOptions}
            onInsertOperator={handleInsertOperator}
            onInsertVariable={handleInsertVariable}
          />
        )}

        <ExpressionTextInput
          expressionText={expressionText}
          textareaRef={textareaRef}
          onChange={handleExpressionChange}
        />

        <ExpressionReferencePanels
          builtInFunctions={builtInFunctions}
          expressionTemplates={expressionTemplates}
          onApplyTemplate={handleExpressionChange}
        />

        <SyntaxHint />
      </Space>
    </div>
  )
}
