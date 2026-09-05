import { ExclamationCircleOutlined } from '@ant-design/icons'
import { Divider, Form, Input, InputNumber, Select } from 'antd'
import type { CSSProperties, ReactNode } from 'react'
import { useCallback, useMemo } from 'react'
import { useTranslation } from 'react-i18next'

import { usePropertyChange } from '../../hooks/usePropertyChange'
import { usePropertyLabels } from '../../hooks/usePropertyLabels'
import { replaceContainerChildren, selectTbbpmNodes } from '../../store/editorSlice'
import { MAX_LOOP_ITERATIONS } from '../../types/loopLimits'
import { isNodeAncestor } from '../../types/nodeHierarchy'
import { TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES, type TbbpmNode } from '../../types/tbbpm'

import { PropertiesTabLayout } from './PropertiesTabLayout'

import { useAppDispatch, useAppSelector } from '@/app/hooks'

type LoopUpdates = Partial<TbbpmNode['properties']>

interface LoopPropertiesTabProps {
  node: TbbpmNode
  onUpdate: (_nodeId: string, _updates: LoopUpdates) => void
}

const CODE_FONT_STYLE: CSSProperties = {
  fontFamily: 'Monaco, Consolas, monospace',
  fontSize: 12,
}

const EXAMPLE_BOX_STYLE: CSSProperties = {
  padding: 12,
  background: 'var(--color-fill-secondary)',
  border: '1px solid var(--color-border-light)',
  borderRadius: 4,
  ...CODE_FONT_STYLE,
}

const CONTROL_BOX_STYLE: CSSProperties = {
  padding: 12,
  background: 'var(--color-fill-secondary)',
  border: '1px solid var(--color-border-light)',
  borderRadius: 4,
  fontSize: 12,
}

const WARNING_BOX_STYLE: CSSProperties = {
  padding: 12,
  background: 'var(--color-warning-bg)',
  border: '1px solid var(--color-warning-border)',
  borderRadius: 4,
  fontSize: 12,
  color: 'var(--warning-dark)',
  marginTop: 12,
}

function ExampleBox({ examples, title }: { examples: ReactNode[]; title: string }) {
  return (
    <div style={EXAMPLE_BOX_STYLE}>
      <div style={{ color: 'var(--color-text-tertiary)', marginBottom: 8 }}>{title}</div>
      {examples.map((example, index) => (
        <div key={index}>{example}</div>
      ))}
    </div>
  )
}

function InlineCode({ children }: { children: ReactNode }) {
  return (
    <span
      style={{
        fontFamily: 'Monaco, Consolas, monospace',
        background: 'var(--color-fill-secondary)',
        padding: '2px 6px',
        borderRadius: 3,
      }}
    >
      {children}
    </span>
  )
}

function WhileSection({
  node,
  onInputChange,
  onUpdate,
}: {
  node: TbbpmNode
  onInputChange: ReturnType<typeof usePropertyChange>['handleInputChange']
  onUpdate: LoopPropertiesTabProps['onUpdate']
}) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()

  return (
    <>
      <Divider style={{ margin: '24px 0 16px 0' }}>
        {t('designer.props.section.whileConfig')}
      </Divider>
      <Form.Item
        label={t('designer.props.node.loop.whileExpr')}
        required
        help={t('designer.props.node.loop.whileExprHelp')}
      >
        <Input.TextArea
          value={node.properties.condition || ''}
          onChange={onInputChange('condition')}
          placeholder={labels.phLoopWhile}
          aria-label={t('designer.props.node.loop.whileExpr')}
          rows={3}
          style={CODE_FONT_STYLE}
        />
      </Form.Item>
      <Form.Item label={t('designer.props.node.loop.maxIterations')} required>
        <InputNumber
          min={1}
          max={MAX_LOOP_ITERATIONS}
          precision={0}
          value={node.properties.maxIterations}
          onChange={(value) => onUpdate(node.id, { maxIterations: value ?? undefined })}
          aria-label={t('designer.props.node.loop.maxIterations')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.index')}
        help={t('designer.props.node.loop.indexHelp')}
      >
        <Input
          value={node.properties.index || ''}
          onChange={onInputChange('index')}
          placeholder={labels.phLoopIteration}
          aria-label={t('designer.props.node.loop.index')}
        />
      </Form.Item>
      <ExampleBox
        title={t('designer.props.node.loop.whileExampleTitle')}
        examples={['counter < 10', 'hasNext == true', '!finished && maxAttempts < 3']}
      />
    </>
  )
}

function ForEachSection({
  node,
  onInputChange,
  onUpdate,
}: {
  node: TbbpmNode
  onInputChange: ReturnType<typeof usePropertyChange>['handleInputChange']
  onUpdate: LoopPropertiesTabProps['onUpdate']
}) {
  const { t } = useTranslation()
  const labels = usePropertyLabels()
  const updateOutput = (field: 'target' | 'source', value: string) => {
    const output = {
      target: node.properties.output?.target || '',
      source: node.properties.output?.source || '',
      [field]: value,
    }
    onUpdate(node.id, {
      output: output.target || output.source ? output : undefined,
    })
  }

  return (
    <>
      <Divider style={{ margin: '24px 0 16px 0' }}>
        {t('designer.props.section.foreachConfig')}
      </Divider>
      <Form.Item
        label={t('designer.props.node.loop.execution')}
        help={t('designer.props.node.loop.executionHelp')}
      >
        <Select
          value={node.properties.execution ?? 'sequential'}
          onChange={(execution) => onUpdate(node.id, { execution })}
          aria-label={t('designer.props.node.loop.execution')}
          options={[
            {
              value: 'sequential',
              label: t('designer.props.node.loop.sequentialExecution'),
            },
            { value: 'parallel', label: t('designer.props.node.loop.parallelExecution') },
          ]}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.collection')}
        required
        help={t('designer.props.node.loop.collectionHelp')}
      >
        <Input
          value={node.properties.collection || ''}
          onChange={onInputChange('collection')}
          placeholder={labels.phLoopCollection}
          aria-label={t('designer.props.node.loop.collection')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.item')}
        required
        help={t('designer.props.node.loop.itemHelp')}
      >
        <Input
          value={node.properties.item || ''}
          onChange={onInputChange('item')}
          placeholder={labels.phLoopElement}
          aria-label={t('designer.props.node.loop.item')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.itemType')}
        required
        help={t('designer.props.node.loop.itemTypeHelp')}
      >
        <Input
          value={node.properties.itemType || ''}
          onChange={onInputChange('itemType')}
          placeholder={labels.phLoopElementClass}
          aria-label={t('designer.props.node.loop.itemType')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.index')}
        help={t('designer.props.node.loop.indexHelp')}
      >
        <Input
          value={node.properties.index || ''}
          onChange={onInputChange('index')}
          placeholder={labels.phLoopIndex}
          aria-label={t('designer.props.node.loop.index')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.outputTarget')}
        help={t('designer.props.node.loop.outputTargetHelp')}
      >
        <Input
          value={node.properties.output?.target || ''}
          onChange={(event) => updateOutput('target', event.target.value)}
          aria-label={t('designer.props.node.loop.outputTarget')}
        />
      </Form.Item>
      <Form.Item
        label={t('designer.props.node.loop.outputSource')}
        help={t('designer.props.node.loop.outputSourceHelp')}
      >
        <Input
          value={node.properties.output?.source || ''}
          onChange={(event) => updateOutput('source', event.target.value)}
          aria-label={t('designer.props.node.loop.outputSource')}
        />
      </Form.Item>
      <ExampleBox
        title={t('designer.props.node.loop.forEachExampleTitle')}
        examples={['orderList (List<Order>)', 'order', 'index']}
      />
    </>
  )
}

function LoopBodySection({ node, nodes }: { node: TbbpmNode; nodes: TbbpmNode[] }) {
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const nodesById = useMemo(
    () => new Map(nodes.map((candidate) => [candidate.id, candidate])),
    [nodes]
  )
  const candidates = useMemo(
    () =>
      nodes.filter(
        (candidate) =>
          candidate.id !== node.id &&
          TBBPM_STRUCTURED_SCOPE_CHILD_NODE_TYPES.has(candidate.type) &&
          !(
            node.properties.execution === 'parallel' &&
            candidate.type === 'break' &&
            candidate.parentId !== node.id
          ) &&
          (candidate.parentId === node.parentId || candidate.parentId === node.id) &&
          !isNodeAncestor(candidate.id, node, nodesById)
      ),
    [node, nodes, nodesById]
  )
  const selectedIds = useMemo(
    () =>
      nodes.filter((candidate) => candidate.parentId === node.id).map((candidate) => candidate.id),
    [node.id, nodes]
  )

  const handleChange = useCallback(
    (childIds: string[]) => {
      dispatch(replaceContainerChildren({ parentId: node.id, childIds }))
    },
    [dispatch, node.id]
  )

  return (
    <>
      <Divider style={{ margin: '24px 0 16px 0' }}>{t('designer.props.section.loopBody')}</Divider>
      <Form.Item
        label={t('designer.props.node.loop.bodyNodes')}
        required
        help={t('designer.props.node.loop.bodyNodesHelp')}
      >
        <Select
          mode="multiple"
          value={selectedIds}
          onChange={handleChange}
          options={candidates.map((candidate) => ({
            value: candidate.id,
            label: `${candidate.name || candidate.id} (${candidate.id})`,
          }))}
          placeholder={t('designer.props.node.loop.bodyNodesPlaceholder')}
          optionFilterProp="label"
          aria-label={t('designer.props.node.loop.bodyNodes')}
        />
      </Form.Item>
    </>
  )
}

function LoopControlHint({ parallel }: { parallel: boolean }) {
  const { t } = useTranslation()

  return (
    <>
      <Divider style={{ margin: '24px 0 16px 0' }}>
        {t('designer.props.node.loop.controlTitle')}
      </Divider>
      <div style={CONTROL_BOX_STYLE}>
        <div style={{ fontWeight: 600, marginBottom: 8, color: 'var(--color-text-primary)' }}>
          {t('designer.props.node.loop.controlSupported')}
        </div>
        {!parallel && (
          <div style={{ marginBottom: 4 }}>
            <InlineCode>break</InlineCode> {t('designer.props.node.loop.breakDesc')}
          </div>
        )}
        <div>
          <InlineCode>continue</InlineCode> {t('designer.props.node.loop.continueDesc')}
        </div>
      </div>
    </>
  )
}

function IterationLimitHint() {
  const { t } = useTranslation()

  return (
    <div style={WARNING_BOX_STYLE}>
      <ExclamationCircleOutlined style={{ marginRight: 6 }} />
      {t('designer.props.node.loop.limitBehaviorHint')}
    </div>
  )
}

export function LoopPropertiesTab({ node, onUpdate }: LoopPropertiesTabProps) {
  const { t } = useTranslation()
  const nodes = useAppSelector(selectTbbpmNodes)
  const isWhile = node.type === 'while'
  const { handleInputChange } = usePropertyChange(node.id, onUpdate)

  return (
    <PropertiesTabLayout
      title={t(
        isWhile ? 'designer.props.node.loop.whileTitle' : 'designer.props.node.loop.forEachTitle'
      )}
      description={t(
        isWhile ? 'designer.props.node.loop.whileDesc' : 'designer.props.node.loop.forEachDesc'
      )}
    >
      {isWhile ? (
        <WhileSection node={node} onInputChange={handleInputChange} onUpdate={onUpdate} />
      ) : (
        <ForEachSection node={node} onInputChange={handleInputChange} onUpdate={onUpdate} />
      )}
      <LoopBodySection node={node} nodes={nodes} />
      <LoopControlHint parallel={!isWhile && node.properties.execution === 'parallel'} />
      {isWhile && <IterationLimitHint />}
    </PropertiesTabLayout>
  )
}
