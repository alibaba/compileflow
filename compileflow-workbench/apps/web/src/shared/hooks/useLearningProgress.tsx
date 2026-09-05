import { CheckCircleFilled, TrophyOutlined } from '@ant-design/icons'
import { Button, Progress } from 'antd'
import React, { useCallback, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import styles from '../components/LearningProgressCard.module.css'

import {
  type LearningProgress,
  readLearningProgress,
  writeLearningProgress,
} from './learningProgressStorage'

import { SemanticTag } from '@/shared/components/page'

function createInitialProgress(): LearningProgress {
  return {
    completedExamples: [],
    totalExamples: 0,
    lastAccessTime: Date.now(),
  }
}

function readStoredProgress(): LearningProgress {
  return readLearningProgress() ?? createInitialProgress()
}

export const useLearningProgress = () => {
  const [progress, setProgress] = useState<LearningProgress>(readStoredProgress)

  const updateProgress = useCallback(
    (createNext: (current: LearningProgress) => LearningProgress) => {
      setProgress((current) => {
        const next = createNext(current)
        writeLearningProgress(next)
        return next
      })
    },
    []
  )

  const markAsCompleted = useCallback(
    (exampleId: string) => {
      updateProgress((current) => {
        const completedExamples = current.completedExamples.includes(exampleId)
          ? current.completedExamples
          : [...current.completedExamples, exampleId]

        return {
          ...current,
          completedExamples,
          lastAccessTime: Date.now(),
        }
      })
    },
    [updateProgress]
  )

  const isCompleted = useCallback(
    (exampleId: string) => {
      return progress.completedExamples.includes(exampleId)
    },
    [progress.completedExamples]
  )

  const getCompletionRate = useCallback(() => {
    if (progress.totalExamples === 0) return 0
    return Math.round((progress.completedExamples.length / progress.totalExamples) * 100)
  }, [progress.completedExamples.length, progress.totalExamples])

  const setTotalExamples = useCallback(
    (total: number) => {
      updateProgress((current) => ({
        ...current,
        totalExamples: total,
      }))
    },
    [updateProgress]
  )

  const resetProgress = useCallback(() => {
    updateProgress((current) => ({
      completedExamples: [],
      totalExamples: current.totalExamples,
      lastAccessTime: Date.now(),
    }))
  }, [updateProgress])

  return useMemo(
    () => ({
      progress,
      markAsCompleted,
      isCompleted,
      getCompletionRate,
      setTotalExamples,
      resetProgress,
    }),
    [getCompletionRate, isCompleted, markAsCompleted, progress, resetProgress, setTotalExamples]
  )
}

interface LearningProgressCardProps {
  exampleId: string
  onMarkComplete?: () => void
}

export const LearningProgressCard: React.FC<LearningProgressCardProps> = ({
  exampleId,
  onMarkComplete,
}) => {
  const { t } = useTranslation()
  const { isCompleted, markAsCompleted, getCompletionRate, progress } = useLearningProgress()

  const completed = isCompleted(exampleId)
  const completionRate = getCompletionRate()

  const handleMarkComplete = () => {
    markAsCompleted(exampleId)
    onMarkComplete?.()
  }

  return (
    <section
      className={`${styles.panel} ${completed ? styles.panelCompleted : styles.panelActive}`}
    >
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <TrophyOutlined className={styles.trophy} />
          <h2 className={styles.title}>{t('learning.progress')}</h2>
        </div>
        {completed && (
          <SemanticTag kind="status-success">
            <CheckCircleFilled /> {t('learning.completed')}
          </SemanticTag>
        )}
      </div>

      <div>
        <div className={styles.progressRow}>
          <span className={styles.progressLabel}>{t('learning.totalProgress')}</span>
          <span className={styles.progressValue}>
            {progress.completedExamples.length} / {progress.totalExamples}
          </span>
        </div>
        <Progress
          percent={completionRate}
          strokeColor={{ '0%': 'var(--primary-500)', '100%': 'var(--success-main)' }}
          size="small"
        />
      </div>

      {!completed && (
        <Button type="primary" block onClick={handleMarkComplete} className={styles.completeBtn}>
          {t('learning.markComplete')}
        </Button>
      )}

      {completed && <span className={styles.congrats}>{t('learning.congrats')}</span>}
    </section>
  )
}
