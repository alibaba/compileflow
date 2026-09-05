import type { TagProps } from 'antd'
import { Tag } from 'antd'

import styles from './SemanticTag.module.css'

export type SemanticTagKind =
  | 'level-beginner'
  | 'level-intermediate'
  | 'level-advanced'
  | 'level-expert'
  | 'process-bpmn'
  | 'process-tbbpm'
  | 'status-draft'
  | 'status-published'
  | 'status-archived'
  | 'status-success'
  | 'status-error'
  | 'status-warning'
  | 'status-info'
  | 'default'

const kindClassMap: Record<SemanticTagKind, string> = {
  'level-beginner': styles.levelBeginner,
  'level-intermediate': styles.levelIntermediate,
  'level-advanced': styles.levelAdvanced,
  'level-expert': styles.levelExpert,
  'process-bpmn': styles.processBpmn,
  'process-tbbpm': styles.processTbbpm,
  'status-draft': styles.statusDraft,
  'status-published': styles.statusPublished,
  'status-archived': styles.statusArchived,
  'status-success': styles.statusSuccess,
  'status-error': styles.statusError,
  'status-warning': styles.statusWarning,
  'status-info': styles.statusInfo,
  default: styles.default,
}

export interface SemanticTagProps extends Omit<TagProps, 'color'> {
  kind?: SemanticTagKind
}

export function SemanticTag({ kind = 'default', className, children, ...rest }: SemanticTagProps) {
  return (
    <Tag {...rest} className={[kindClassMap[kind], className].filter(Boolean).join(' ')}>
      {children}
    </Tag>
  )
}

/** Maps numeric difficulty level (1–4) to semantic tag kind. */
export function levelToTagKind(level: number): SemanticTagKind {
  const map: SemanticTagKind[] = [
    'level-beginner',
    'level-intermediate',
    'level-advanced',
    'level-expert',
  ]
  return map[Math.min(Math.max(level, 1), 4) - 1] ?? 'default'
}

/** Maps a process model type to its semantic tag kind. */
export function modelTypeToTagKind(modelType?: string): SemanticTagKind {
  if (modelType === 'TBBPM') return 'process-tbbpm'
  if (modelType === 'BPMN') return 'process-bpmn'
  return 'default'
}

/** Maps a lifecycle status to its semantic tag kind. */
export function statusToTagKind(status: string): SemanticTagKind {
  const normalized = status.toLowerCase()
  if (normalized === 'draft') return 'status-draft'
  if (normalized === 'published' || normalized === 'success' || normalized === 'completed') {
    return 'status-success'
  }
  if (normalized === 'archived') return 'status-archived'
  if (normalized === 'error' || normalized === 'failed') return 'status-error'
  if (normalized === 'warning' || normalized === 'in_progress' || normalized === 'aborted') {
    return 'status-warning'
  }
  if (normalized === 'info') return 'status-info'
  return 'default'
}
