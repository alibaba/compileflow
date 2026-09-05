import { CodeOutlined, FunctionOutlined } from '@ant-design/icons'
import { App, Input, Space, Tabs, Tag, Tooltip } from 'antd'
import type { TextAreaRef } from 'antd/es/input/TextArea'
import type { TFunction } from 'i18next'
import type { ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  findDirectJavaMutation,
  normalizeJavaConditionExpression,
} from '../validation/javaConditionExpression'

import type { ExpressionVariable } from './expressionVariables'

import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import type { ProcessModelType } from '@/shared/contracts'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

import './ConditionExpressionEditor.css'

const { TextArea } = Input

interface ConditionExpressionEditorProps {
  visible: boolean
  condition?: string
  type: ProcessModelType
  variables?: ExpressionVariable[]
  onOk: (condition: string) => void
  onCancel: () => void
}

interface OperatorOption {
  symbol: string
  desc: string
}

interface ExpressionTemplateCategory {
  category: string
  items: Array<{ label: string; value: string }>
}

interface EditorTabProps {
  expression: string
  operators: OperatorOption[]
  textAreaRef: React.RefObject<TextAreaRef | null>
  type: ProcessModelType
  variables: ConditionExpressionEditorProps['variables']
  onChange: (value: string) => void
  onInsertOperator: (operator: string) => void
  onInsertVariable: (variableName: string) => void
}

function buildOperators(t: TFunction): OperatorOption[] {
  return [
    { symbol: '==', desc: t('designer.expr.op.eq') },
    { symbol: '!=', desc: t('designer.expr.op.ne') },
    { symbol: '>', desc: t('designer.expr.op.gt') },
    { symbol: '>=', desc: t('designer.expr.op.gte') },
    { symbol: '<', desc: t('designer.expr.op.lt') },
    { symbol: '<=', desc: t('designer.expr.op.lte') },
    { symbol: '&&', desc: t('designer.expr.op.and') },
    { symbol: '||', desc: t('designer.expr.op.or') },
    { symbol: '!', desc: t('designer.expr.op.not') },
  ]
}

function buildExpressionTemplates(t: TFunction): ExpressionTemplateCategory[] {
  return [
    {
      category: t('designer.condition.tpl.compare'),
      items: [
        { label: t('designer.condition.tpl.compare.amountGt'), value: 'amount > 1000' },
        {
          label: t('designer.condition.tpl.compare.qtyRange'),
          value: 'quantity >= 10 && quantity <= 100',
        },
        {
          label: t('designer.condition.tpl.compare.priceNotEmpty'),
          value: 'price != null && price > 0',
        },
      ],
    },
    {
      category: t('designer.condition.tpl.string'),
      items: [
        {
          label: t('designer.condition.tpl.string.statusPending'),
          value: '"PENDING".equals(status)',
        },
        { label: t('designer.condition.tpl.string.vipUser'), value: '"VIP".equals(userType)' },
        {
          label: t('designer.condition.tpl.string.nameContains'),
          value: 'name != null && name.contains("keyword")',
        },
      ],
    },
    {
      category: t('designer.condition.tpl.logic'),
      items: [
        { label: t('designer.condition.tpl.logic.approvedPaid'), value: 'approved && paid' },
        {
          label: t('designer.condition.tpl.logic.urgentOrHigh'),
          value: 'urgent || "HIGH".equals(priority)',
        },
        { label: t('designer.condition.tpl.logic.notCancelled'), value: '!cancelled' },
      ],
    },
    {
      category: t('designer.condition.tpl.collection'),
      items: [
        {
          label: t('designer.condition.tpl.collection.itemsNotEmpty'),
          value: 'items != null && !items.isEmpty()',
        },
        {
          label: t('designer.condition.tpl.collection.listGt3'),
          value: 'list != null && list.size() > 3',
        },
        {
          label: t('designer.condition.tpl.collection.tagsContains'),
          value: 'tags != null && tags.contains("urgent")',
        },
      ],
    },
  ]
}

function insertTextAtCursor(
  textAreaRef: React.RefObject<TextAreaRef | null>,
  expression: string,
  insertText: string,
  onChange: (value: string) => void
) {
  const textarea = textAreaRef.current?.resizableTextArea?.textArea
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const nextExpression = expression.substring(0, start) + insertText + expression.substring(end)
  onChange(nextExpression)

  setTimeout(() => {
    textarea.focus()
    const nextPosition = start + insertText.length
    textarea.setSelectionRange(nextPosition, nextPosition)
  }, 0)
}

type ValidationResult =
  | { valid: true }
  | { valid: false; type: 'warning' | 'error'; message: ReactNode }

function validateConditionExpression(expression: string, t: TFunction): ValidationResult {
  if (!expression.trim()) {
    return { valid: false, type: 'warning', message: t('designer.expr.validation.empty') }
  }
  const mutation = findDirectJavaMutation(expression)
  if (mutation) {
    return {
      valid: false,
      type: 'error',
      message: t('designer.condition.validation.directMutation', { operator: mutation }),
    }
  }

  const openBraces = (expression.match(/\{/g) || []).length
  const closeBraces = (expression.match(/\}/g) || []).length
  if (openBraces !== closeBraces) {
    return { valid: false, type: 'error', message: t('designer.expr.validation.parens') }
  }

  return { valid: true }
}

function VariableTags({
  variables,
  onInsertVariable,
}: {
  variables: ConditionExpressionEditorProps['variables']
  onInsertVariable: (variableName: string) => void
}) {
  const { t } = useTranslation()
  if (!variables?.length) return null

  return (
    <div className="expression-variables-group">
      <div className="expression-section-label">{t('designer.condition.variables')}</div>
      <Space wrap>
        {variables.map((variable) => (
          <Tooltip
            key={variable.name}
            title={`${variable.type}${variable.description ? ` - ${variable.description}` : ''}`}
          >
            <Tag style={{ cursor: 'pointer' }} onClick={() => onInsertVariable(variable.name)}>
              {variable.name}
            </Tag>
          </Tooltip>
        ))}
      </Space>
    </div>
  )
}

function OperatorTags({
  operators,
  onInsertOperator,
}: {
  operators: OperatorOption[]
  onInsertOperator: (operator: string) => void
}) {
  const { t } = useTranslation()

  return (
    <div>
      <div className="expression-section-label">{t('designer.condition.operators')}</div>
      <Space wrap>
        {operators.map((operator) => (
          <Tooltip key={operator.symbol} title={operator.desc}>
            <Tag
              style={{ cursor: 'pointer', fontFamily: 'monospace' }}
              onClick={() => onInsertOperator(operator.symbol)}
            >
              {operator.symbol}
            </Tag>
          </Tooltip>
        ))}
      </Space>
    </div>
  )
}

function EditorTab({
  expression,
  operators,
  textAreaRef,
  type,
  variables,
  onChange,
  onInsertOperator,
  onInsertVariable,
}: EditorTabProps) {
  const { t } = useTranslation()

  return (
    <div className="expression-editor-content">
      <div className="expression-input-area">
        <div className="expression-section-label">
          <CodeOutlined /> {t('designer.condition.content')}
        </div>
        <TextArea
          aria-label={t('designer.condition.content')}
          ref={textAreaRef}
          value={expression}
          onChange={(event) => onChange(event.target.value)}
          placeholder={type === 'BPMN' ? 'amount > 1000' : '"APPROVED".equals(status)'}
          rows={6}
          className="expression-textarea"
        />
      </div>

      <div className="expression-quick-insert">
        <VariableTags variables={variables} onInsertVariable={onInsertVariable} />
        <OperatorTags operators={operators} onInsertOperator={onInsertOperator} />
      </div>
    </div>
  )
}

function TemplatesTab({
  categories,
  onSelectTemplate,
}: {
  categories: ExpressionTemplateCategory[]
  onSelectTemplate: (template: string) => void
}) {
  return (
    <div className="expression-templates">
      {categories.map((category) => (
        <div key={category.category} className="expression-template-category">
          <div className="expression-template-category-title">
            <FunctionOutlined /> {category.category}
          </div>
          <Space vertical style={{ width: '100%' }} size={8}>
            {category.items.map((item) => (
              <button
                type="button"
                key={item.value}
                className="template-item"
                onClick={() => onSelectTemplate(item.value)}
              >
                <span className="template-label">{item.label}</span>
                <span className="template-value">{item.value}</span>
              </button>
            ))}
          </Space>
        </div>
      ))}
    </div>
  )
}

function CodeLine({ children }: { children: ReactNode }) {
  return <li>{children}</li>
}

function renderCodeTranslation(text: string): ReactNode {
  const openTag = '<code>'
  const closeTag = '</code>'
  const openIndex = text.indexOf(openTag)
  const closeIndex = text.indexOf(closeTag, openIndex + openTag.length)

  if (openIndex < 0 || closeIndex < 0) {
    return text
  }

  return [
    text.slice(0, openIndex),
    <code key="code">{text.slice(openIndex + openTag.length, closeIndex)}</code>,
    text.slice(closeIndex + closeTag.length),
  ]
}

function HelpTab() {
  const { t } = useTranslation()

  return (
    <div className="expression-help" style={{ padding: '16px 0' }}>
      <div style={{ marginBottom: 16 }}>
        <h4>{t('designer.condition.help.syntaxTitle')}</h4>
        <ul>
          <CodeLine>{renderCodeTranslation(t('designer.condition.help.syntax1'))}</CodeLine>
          <CodeLine>{renderCodeTranslation(t('designer.condition.help.syntax2'))}</CodeLine>
          <li>{t('designer.condition.help.syntax3')}</li>
        </ul>
      </div>

      <div style={{ marginBottom: 16 }}>
        <h4>{t('designer.condition.help.examplesTitle')}</h4>
        <pre className="help-code">
          {`// Numeric compare
amount > 1000

// String compare
"APPROVED".equals(status)

// Complex logic
approved && (amount <= 10000 || vip)

// Null-safe method call
name != null && name.startsWith("A")

// Collection ops
items != null && !items.isEmpty()`}
        </pre>
      </div>

      <div>
        <h4>{t('designer.condition.help.notesTitle')}</h4>
        <ul>
          <li>{t('designer.condition.help.note1')}</li>
          <li>{t('designer.condition.help.note2')}</li>
          <li>{renderCodeTranslation(t('designer.condition.help.note3'))}</li>
          <li>{t('designer.condition.help.note4')}</li>
        </ul>
      </div>
    </div>
  )
}

export default function ConditionExpressionEditor({
  visible,
  condition = '',
  type,
  variables = [],
  onOk,
  onCancel,
}: ConditionExpressionEditorProps) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [currentExpression, setCurrentExpression] = useState(() =>
    normalizeJavaConditionExpression(condition)
  )
  const textAreaRef = useRef<TextAreaRef>(null)
  const operators = useMemo(() => buildOperators(t), [t])
  const expressionTemplates = useMemo(() => buildExpressionTemplates(t), [t])
  useEscapeToClose(visible, onCancel)

  useEffect(() => {
    setCurrentExpression(normalizeJavaConditionExpression(condition))
  }, [condition])

  const handleInsertVariable = useCallback(
    (variableName: string) => {
      insertTextAtCursor(textAreaRef, currentExpression, variableName, setCurrentExpression)
    },
    [currentExpression]
  )

  const handleInsertOperator = useCallback(
    (operator: string) => {
      insertTextAtCursor(textAreaRef, currentExpression, ` ${operator} `, setCurrentExpression)
    },
    [currentExpression]
  )

  const handleOk = useCallback(() => {
    const result = validateConditionExpression(currentExpression, t)
    if (result.valid) {
      onOk(normalizeJavaConditionExpression(currentExpression))
    } else if (result.type === 'warning') {
      message.warning(result.message)
    } else {
      message.error(result.message)
    }
  }, [currentExpression, message, onOk, t])

  const tabItems = useMemo(
    () => [
      {
        key: 'editor',
        label: t('designer.condition.tab.editor'),
        children: (
          <EditorTab
            expression={currentExpression}
            operators={operators}
            textAreaRef={textAreaRef}
            type={type}
            variables={variables}
            onChange={setCurrentExpression}
            onInsertOperator={handleInsertOperator}
            onInsertVariable={handleInsertVariable}
          />
        ),
      },
      {
        key: 'templates',
        label: t('designer.condition.tab.templates'),
        children: (
          <TemplatesTab categories={expressionTemplates} onSelectTemplate={setCurrentExpression} />
        ),
      },
      {
        key: 'help',
        label: t('designer.condition.tab.help'),
        children: <HelpTab />,
      },
    ],
    [
      currentExpression,
      expressionTemplates,
      handleInsertOperator,
      handleInsertVariable,
      operators,
      t,
      type,
      variables,
    ]
  )

  return (
    <Modal
      title={t('designer.condition.title')}
      open={visible}
      onOk={handleOk}
      onCancel={onCancel}
      width={700}
      okText={t('common.confirm')}
      cancelText={t('common.cancel')}
      className="condition-expression-editor-modal"
    >
      <Tabs items={tabItems} />
    </Modal>
  )
}
