import {
  BugOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ExclamationCircleOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import type { CollapseProps } from 'antd'
import { Alert, Badge, Button, Collapse, Space, Tag } from 'antd'
import type { Key } from 'react'
import React, { useCallback, useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { UnifiedProcessDefinition } from '../types/flowDefinition'

import { validateDesignerProcess } from '@/authoring/designer/validation/designerProcessValidation'
import type {
  TopologyValidationResult,
  ValidationIssue,
  ValidationLevel,
} from '@/authoring/designer/validation/ProcessTopologyValidator'
import {
  translateValidationIssue,
  translateValidationSummary,
} from '@/authoring/designer/validation/topologyValidationI18n'
import './ValidationResultPanel.css'

interface ValidationResultPanelProps {
  flowDefinition: UnifiedProcessDefinition | null
  onHighlightNodes?: (nodeIds: string[]) => void
  onHighlightConnections?: (connectionIds: string[]) => void
  autoValidate?: boolean
}

type TypeTagConfig = Record<string, { label: string; color: string }>

interface IssueGroups {
  errors: ValidationIssue[]
  warnings: ValidationIssue[]
  infos: ValidationIssue[]
}

function buildTypeTagConfig(t: ReturnType<typeof useTranslation>['t']): TypeTagConfig {
  return {
    cycle: { label: t('designer.validation.type.cycle'), color: 'red' },
    isolated: { label: t('designer.validation.type.isolated'), color: 'orange' },
    deadlock: { label: t('designer.validation.type.deadlock'), color: 'purple' },
    unreachable: { label: t('designer.validation.type.unreachable'), color: 'volcano' },
    'multi-start': { label: t('designer.validation.type.multiStart'), color: 'gold' },
    'multi-end': { label: t('designer.validation.type.multiEnd'), color: 'red' },
    'no-end': { label: t('designer.validation.type.noEnd'), color: 'red' },
    'orphan-edge': { label: t('designer.validation.type.orphanEdge'), color: 'magenta' },
    property: { label: t('designer.validation.type.property'), color: 'geekblue' },
  }
}

function getDefaultActiveKeys(result: TopologyValidationResult): string[] {
  const keys: string[] = []
  if (result.errorCount > 0) keys.push('errors')
  if (result.warningCount > 0) keys.push('warnings')
  return keys
}

function groupIssues(issues: ValidationIssue[]): IssueGroups {
  return {
    errors: issues.filter((issue) => issue.level === 'error'),
    warnings: issues.filter((issue) => issue.level === 'warning'),
    infos: issues.filter((issue) => issue.level === 'info'),
  }
}

function normalizeActiveKeys(keys: Key | Key[]): string[] {
  return (Array.isArray(keys) ? keys : [keys]).map(String)
}

function buildIssueKey(issue: ValidationIssue, index: number): string {
  return [
    issue.level,
    issue.type,
    issue.nodeIds.join(','),
    issue.connectionIds?.join(',') ?? '',
    index,
  ].join('|')
}

function LevelIcon({ level }: { level: ValidationLevel }) {
  switch (level) {
    case 'error':
      return <CloseCircleOutlined style={{ color: 'var(--color-error)' }} />
    case 'warning':
      return <ExclamationCircleOutlined style={{ color: 'var(--warning-main)' }} />
    case 'info':
      return <InfoCircleOutlined style={{ color: 'var(--color-primary)' }} />
  }
}

function TypeTag({ type, typeTagConfig }: { type: string; typeTagConfig: TypeTagConfig }) {
  const config = typeTagConfig[type] || { label: type, color: 'default' }
  return <Tag color={config.color}>{config.label}</Tag>
}

function IssueReferenceTags({
  className,
  label,
  values,
}: {
  className: string
  label: string
  values?: string[]
}) {
  if (!values?.length) return null

  return (
    <div className={className}>
      <strong>{label}</strong>
      <Space size="small" wrap>
        {values.map((value) => (
          <Tag key={value}>{value}</Tag>
        ))}
      </Space>
    </div>
  )
}

function ValidationIssueItem({
  issue,
  typeTagConfig,
  onActivate,
}: {
  issue: ValidationIssue
  typeTagConfig: TypeTagConfig
  onActivate: (issue: ValidationIssue) => void
}) {
  const { t } = useTranslation()
  const { message, suggestion } = translateValidationIssue(issue, t)
  const handleActivate = () => onActivate(issue)

  return (
    <div
      className="validation-issue-item"
      role="button"
      tabIndex={0}
      onClick={handleActivate}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          handleActivate()
        }
      }}
    >
      <div className="issue-header">
        <LevelIcon level={issue.level} />
        <span className="issue-message">{message}</span>
        <TypeTag type={issue.type} typeTagConfig={typeTagConfig} />
      </div>

      {suggestion && (
        <div className="issue-suggestion">
          <strong>{t('designer.validation.suggestion')}</strong>
          {suggestion}
        </div>
      )}

      <IssueReferenceTags
        className="issue-nodes"
        label={t('designer.validation.involvedNodes')}
        values={issue.nodeIds}
      />
      <IssueReferenceTags
        className="issue-connections"
        label={t('designer.validation.involvedEdges')}
        values={issue.connectionIds}
      />
    </div>
  )
}

function buildIssuesItem({
  count,
  icon,
  issues,
  label,
  panelKey,
  typeTagConfig,
  onActivateIssue,
}: {
  count: number
  icon: React.ReactNode
  issues: ValidationIssue[]
  label: string
  panelKey: string
  typeTagConfig: TypeTagConfig
  onActivateIssue: (issue: ValidationIssue) => void
}): NonNullable<CollapseProps['items']>[number] | null {
  if (issues.length === 0) return null

  return {
    key: panelKey,
    label: (
      <div className="panel-header">
        <Badge count={count} offset={[10, 0]}>
          {icon}
        </Badge>
        <span className="panel-header-label">{label}</span>
      </div>
    ),
    children: issues.map((issue, index) => (
      <ValidationIssueItem
        key={buildIssueKey(issue, index)}
        issue={issue}
        typeTagConfig={typeTagConfig}
        onActivate={onActivateIssue}
      />
    )),
  }
}

function EmptyValidationResult() {
  const { t } = useTranslation()

  return (
    <Alert
      title={t('designer.validation.notRun')}
      description={t('designer.validation.notRunDesc')}
      type="info"
      icon={<InfoCircleOutlined />}
    />
  )
}

function PassedValidationResult() {
  const { t } = useTranslation()

  return (
    <Alert
      title={t('designer.validation.passed')}
      description={t('designer.validation.passedDesc')}
      type="success"
      icon={<CheckCircleOutlined />}
    />
  )
}

function ValidationResultContent({
  activeKeys,
  result,
  typeTagConfig,
  onActivateIssue,
  onActiveKeysChange,
}: {
  activeKeys: string[]
  result: TopologyValidationResult | null
  typeTagConfig: TypeTagConfig
  onActivateIssue: (issue: ValidationIssue) => void
  onActiveKeysChange: (keys: string[]) => void
}) {
  const { t } = useTranslation()

  if (!result) return <EmptyValidationResult />
  if (result.valid && result.issues.length === 0) return <PassedValidationResult />

  const { errors, warnings, infos } = groupIssues(result.issues)

  return (
    <div className="validation-result-content">
      <Alert
        title={translateValidationSummary(result, t)}
        type={result.valid ? 'warning' : 'error'}
        icon={result.valid ? <ExclamationCircleOutlined /> : <CloseCircleOutlined />}
        className="validation-summary-alert"
      />

      <Collapse
        activeKey={activeKeys}
        onChange={(keys) => onActiveKeysChange(normalizeActiveKeys(keys))}
        items={[
          buildIssuesItem({
            count: errors.length,
            icon: <CloseCircleOutlined style={{ color: 'var(--color-error)', fontSize: 16 }} />,
            issues: errors,
            label: t('designer.validation.errors'),
            panelKey: 'errors',
            typeTagConfig,
            onActivateIssue,
          }),
          buildIssuesItem({
            count: warnings.length,
            icon: (
              <ExclamationCircleOutlined style={{ color: 'var(--warning-main)', fontSize: 16 }} />
            ),
            issues: warnings,
            label: t('designer.validation.warnings'),
            panelKey: 'warnings',
            typeTagConfig,
            onActivateIssue,
          }),
          buildIssuesItem({
            count: infos.length,
            icon: <InfoCircleOutlined style={{ color: 'var(--color-primary)', fontSize: 16 }} />,
            issues: infos,
            label: t('designer.validation.infos'),
            panelKey: 'infos',
            typeTagConfig,
            onActivateIssue,
          }),
        ].filter((item): item is NonNullable<typeof item> => item !== null)}
      />
    </div>
  )
}

function ValidationPanelHeader({
  disabled,
  onRunValidation,
}: {
  disabled: boolean
  onRunValidation: () => void
}) {
  const { t } = useTranslation()

  return (
    <div className="panel-header-bar">
      <div className="panel-title">
        <BugOutlined />
        <span>{t('designer.validation.title')}</span>
      </div>
      <Button
        type="text"
        size="small"
        icon={<ReloadOutlined />}
        onClick={onRunValidation}
        disabled={disabled}
      >
        {t('designer.validation.revalidate')}
      </Button>
    </div>
  )
}

const ValidationResultPanel = React.memo(function ValidationResultPanel({
  flowDefinition,
  onHighlightNodes,
  onHighlightConnections,
  autoValidate = true,
}: ValidationResultPanelProps) {
  const { t } = useTranslation()
  const [validationResult, setValidationResult] = useState<TopologyValidationResult | null>(null)
  const [activeKeys, setActiveKeys] = useState<string[]>([])
  const typeTagConfig = useMemo(() => buildTypeTagConfig(t), [t])

  const runValidation = useCallback(() => {
    if (!flowDefinition) {
      setValidationResult(null)
      return
    }

    const result = validateDesignerProcess(flowDefinition)
    setValidationResult(result)
    setActiveKeys(getDefaultActiveKeys(result))
  }, [flowDefinition])

  useEffect(() => {
    if (autoValidate && flowDefinition) {
      runValidation()
    }
  }, [autoValidate, flowDefinition, runValidation])

  const handleIssueClick = useCallback(
    (issue: ValidationIssue) => {
      if (issue.nodeIds.length > 0) {
        onHighlightNodes?.(issue.nodeIds)
      }
      if (issue.connectionIds?.length) {
        onHighlightConnections?.(issue.connectionIds)
      }
    },
    [onHighlightConnections, onHighlightNodes]
  )

  return (
    <div className="validation-result-panel">
      <ValidationPanelHeader disabled={!flowDefinition} onRunValidation={runValidation} />

      <div className="panel-content">
        <ValidationResultContent
          activeKeys={activeKeys}
          result={validationResult}
          typeTagConfig={typeTagConfig}
          onActivateIssue={handleIssueClick}
          onActiveKeysChange={setActiveKeys}
        />
      </div>
    </div>
  )
})

ValidationResultPanel.displayName = 'ValidationResultPanel'

export default ValidationResultPanel
