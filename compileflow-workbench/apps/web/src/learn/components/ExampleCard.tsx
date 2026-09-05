import { ClockCircleOutlined } from '@ant-design/icons'
import { Col, Typography } from 'antd'
import { memo } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'

import {
  getExampleDuration,
  getExamplePresentation,
  getLevelLabel,
} from '../presentation/exampleMetadata'

import DifficultyRating from './DifficultyRating'
import styles from './ExampleCard.module.css'

import { modelTypeToTagKind, levelToTagKind, SemanticTag } from '@/shared/components/page'
import { createLearnExampleDetailPath } from '@/shared/constants'
import type { Example } from '@/shared/contracts'

const { Paragraph } = Typography

interface ExampleCardProps {
  example: Example
  index: number
}

const flowAccentClass: Record<string, string> = {
  BPMN: styles.accentBuild,
  TBBPM: styles.accentOperate,
}

const ExampleCard = memo<ExampleCardProps>(({ example }) => {
  const { t } = useTranslation()
  const presentation = getExamplePresentation(example, t)
  const detailPath = createLearnExampleDetailPath(example.id)

  return (
    <Col xs={24} sm={12} lg={8} className={styles.cardCol}>
      <article className={`${styles.card} ${flowAccentClass[example.modelType] ?? ''}`}>
        <Link
          to={detailPath}
          className={styles.cardLink}
          aria-label={`${t('examples.viewDetail')}: ${presentation.name}`}
        />
        <div className={styles.accentBar} />
        <div className={styles.body}>
          <div className={styles.tagRow}>
            <SemanticTag kind={modelTypeToTagKind(example.modelType)}>
              {example.modelType}
            </SemanticTag>
            <SemanticTag kind={levelToTagKind(example.level)}>
              {getLevelLabel(example.level, t)}
            </SemanticTag>
          </div>
          <h2 className={styles.title}>{presentation.name}</h2>
          <Paragraph className={styles.description}>{presentation.description}</Paragraph>
          <div className={styles.meta}>
            <div className={styles.metaLeft}>
              <ClockCircleOutlined />
              <span>{getExampleDuration(example.duration, t)}</span>
              <DifficultyRating
                className={styles.rate}
                value={example.difficulty}
                label={t('detail.difficultyValue', { value: example.difficulty })}
              />
            </div>
            <span className={styles.footerLink}>{t('examples.viewDetail')}</span>
          </div>
        </div>
      </article>
    </Col>
  )
})

ExampleCard.displayName = 'ExampleCard'

export default ExampleCard
