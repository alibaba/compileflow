import type { TFunction } from 'i18next'

import type { TopologyValidationResult, ValidationIssue } from './ProcessTopologyValidator'
import { translatePropertyValidationCode } from './propertyValidationI18n'

function issueKey(type: ValidationIssue['type'], suffix: 'message' | 'suggestion'): string {
  const camel = type.replace(/-([a-z])/g, (_, c: string) => c.toUpperCase())
  return `designer.validation.issue.${camel}.${suffix}`
}

function translatePropertyIssue(
  issue: ValidationIssue & { type: 'property' },
  t: TFunction
): { message: string } {
  return {
    message: translatePropertyValidationCode(issue.code, t, issue.params),
  }
}

function translateMultiStartIssue(
  issue: ValidationIssue,
  t: TFunction
): { message: string; suggestion: string } {
  if (issue.nodeIds.length === 0) {
    return {
      message: t('designer.validation.issue.multiStart.missing'),
      suggestion: t('designer.validation.issue.multiStart.missingSuggestion'),
    }
  }
  return {
    message: t('designer.validation.issue.multiStart.multiple', { count: issue.nodeIds.length }),
    suggestion: t('designer.validation.issue.multiStart.multipleSuggestion'),
  }
}

function translateCycleIssue(
  issue: ValidationIssue,
  t: TFunction
): { message: string; suggestion: string } {
  return {
    message: t('designer.validation.issue.cycle.message', issue.params),
    suggestion: t('designer.validation.issue.cycle.suggestion'),
  }
}

function translateDeadlockIssue(
  issue: ValidationIssue,
  t: TFunction
): { message: string; suggestion: string } {
  if (issue.params?.variant === 'missingJoin') {
    return {
      message: t('designer.validation.issue.deadlock.missingJoin', {
        name: issue.params.name,
      }),
      suggestion: t('designer.validation.issue.deadlock.missingJoinSuggestion'),
    }
  }
  return {
    message: t('designer.validation.issue.deadlock.ambiguous', issue.params),
    suggestion: t('designer.validation.issue.deadlock.ambiguousSuggestion'),
  }
}

function translateDefaultIssue(
  issue: ValidationIssue,
  t: TFunction
): { message: string; suggestion?: string } {
  const message = t(issueKey(issue.type, 'message'), issue.params ?? {})
  const suggestionKey = issueKey(issue.type, 'suggestion')
  const suggestion = t(suggestionKey)
  return {
    message,
    suggestion: suggestion !== suggestionKey ? suggestion : undefined,
  }
}

export function translateValidationIssue(
  issue: ValidationIssue,
  t: TFunction
): { message: string; suggestion?: string } {
  if (issue.type === 'property') {
    return translatePropertyIssue(issue, t)
  }
  if (issue.type === 'multi-start') {
    return translateMultiStartIssue(issue, t)
  }
  if (issue.type === 'cycle' && issue.params) {
    return translateCycleIssue(issue, t)
  }
  if (issue.type === 'deadlock' && issue.params) {
    return translateDeadlockIssue(issue, t)
  }
  return translateDefaultIssue(issue, t)
}

export function translateValidationSummary(result: TopologyValidationResult, t: TFunction): string {
  if (result.valid && result.issues.length === 0) {
    return t('designer.validation.summary.passed')
  }

  const parts: string[] = []
  if (result.errorCount > 0) {
    parts.push(t('designer.validation.summary.errors', { count: result.errorCount }))
  }
  if (result.warningCount > 0) {
    parts.push(t('designer.validation.summary.warnings', { count: result.warningCount }))
  }
  if (result.infoCount > 0) {
    parts.push(t('designer.validation.summary.infos', { count: result.infoCount }))
  }

  return t('designer.validation.summary.failed', { details: parts.join(', ') })
}
