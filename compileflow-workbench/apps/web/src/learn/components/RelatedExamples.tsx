import { ClockCircleOutlined, RocketOutlined } from '@ant-design/icons'
import { Button, Empty, Typography } from 'antd'
import React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import DifficultyRating from './DifficultyRating'
import styles from './RelatedExamples.module.css'

import {
  getExampleDuration,
  getExamplePresentation,
  getLevelLabel,
} from '@/learn/presentation/exampleMetadata'
import { levelToTagKind, SemanticTag } from '@/shared/components/page'
import { createLearnExampleDetailPath, createLearnExamplesPath } from '@/shared/constants'
import type { Example } from '@/shared/contracts'

const { Text, Paragraph } = Typography

interface RelatedExamplesProps {
  currentExample: Example
  allExamples: Example[]
  maxRecommendations?: number
}

const calculateSimilarity = (example1: Example, example2: Example): number => {
  const tags1 = new Set(example1.tags)
  const tags2 = new Set(example2.tags)
  const intersection = new Set([...tags1].filter((tag) => tags2.has(tag)))
  const union = new Set([...tags1, ...tags2])
  const tagSimilarity = union.size > 0 ? intersection.size / union.size : 0
  const difficultyDiff = Math.abs(example1.difficulty - example2.difficulty)
  const difficultySimilarity = Math.max(0, 1 - difficultyDiff / 5)
  const categorySimilarity = example1.category === example2.category ? 1 : 0
  return tagSimilarity * 0.6 + difficultySimilarity * 0.2 + categorySimilarity * 0.2
}

const RelatedExamples: React.FC<RelatedExamplesProps> = ({
  currentExample,
  allExamples,
  maxRecommendations = 3,
}) => {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const recommendations = allExamples
    .filter((ex) => ex.id !== currentExample.id)
    .map((ex) => ({ example: ex, score: calculateSimilarity(currentExample, ex) }))
    .sort((a, b) => b.score - a.score)
    .slice(0, maxRecommendations)

  return (
    <section className={styles.panel}>
      <h2 className={styles.title}>
        <RocketOutlined /> {t('examples.related')}
      </h2>

      {recommendations.length === 0 ? (
        <Empty description={t('examples.noRelated')} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <div className={styles.list}>
          {recommendations.map(({ example, score }) => {
            const presentation = getExamplePresentation(example, t)
            return (
              <button
                key={example.id}
                type="button"
                className={styles.item}
                onClick={() => navigate(createLearnExampleDetailPath(example.id))}
              >
                <div className={styles.itemHeader}>
                  <Text strong className={styles.itemName}>
                    {presentation.name}
                  </Text>
                  {score > 0.7 && (
                    <SemanticTag kind="status-warning">
                      {t('examples.highlyRecommended')}
                    </SemanticTag>
                  )}
                </div>
                <Paragraph ellipsis={{ rows: 2 }} type="secondary" className={styles.itemDesc}>
                  {presentation.description}
                </Paragraph>
                <div className={styles.itemMeta}>
                  <SemanticTag kind={levelToTagKind(example.level)}>
                    {getLevelLabel(example.level, t)}
                  </SemanticTag>
                  <span className={styles.duration}>
                    <ClockCircleOutlined /> {getExampleDuration(example.duration, t)}
                  </span>
                  <DifficultyRating
                    className={styles.stars}
                    value={example.difficulty}
                    label={t('detail.difficultyValue', { value: example.difficulty })}
                  />
                </div>
              </button>
            )
          })}
        </div>
      )}

      <Button
        type="link"
        block
        className={styles.viewAll}
        onClick={() => navigate(createLearnExamplesPath())}
      >
        {t('examples.viewAll')}
      </Button>
    </section>
  )
}

export default RelatedExamples
