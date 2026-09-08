import { LeftOutlined, RightOutlined } from '@ant-design/icons'
import { Button, Tooltip, Typography } from 'antd'
import React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import styles from './ExampleNavigation.module.css'

import { createLearnExampleDetailPath } from '@/shared/constants'
import type { Example } from '@/shared/contracts'

const { Text } = Typography

interface ExampleNavigationProps {
  currentExample: Example
  allExamples: Example[]
  examplesPath: string
}

const ExampleNavigation: React.FC<ExampleNavigationProps> = ({
  currentExample,
  allExamples,
  examplesPath,
}) => {
  const navigate = useNavigate()
  const { t } = useTranslation()

  const currentIndex = allExamples.findIndex((ex) => ex.id === currentExample.id)
  const prevExample = currentIndex > 0 ? allExamples[currentIndex - 1] : null
  const nextExample = currentIndex < allExamples.length - 1 ? allExamples[currentIndex + 1] : null

  React.useEffect(() => {
    const handleKeyPress = (e: KeyboardEvent) => {
      const target = e.target instanceof Element ? e.target : null
      if (
        e.altKey ||
        e.ctrlKey ||
        e.metaKey ||
        e.shiftKey ||
        target?.closest(
          'a, button, input, select, textarea, [contenteditable="true"], [role="combobox"], [role="tab"], [role="textbox"]'
        )
      ) {
        return
      }

      if (e.key === 'ArrowLeft' && prevExample) {
        void navigate(createLearnExampleDetailPath(prevExample.id), { state: { examplesPath } })
      }
      if (e.key === 'ArrowRight' && nextExample) {
        void navigate(createLearnExampleDetailPath(nextExample.id), { state: { examplesPath } })
      }
    }

    window.addEventListener('keydown', handleKeyPress)
    return () => window.removeEventListener('keydown', handleKeyPress)
  }, [examplesPath, prevExample, nextExample, navigate])

  return (
    <nav className={styles.navigationBar} aria-label={t('exampleNav.navigationLabel')}>
      <div className={styles.navInner}>
        {/* Previous */}
        <div className={styles.navHalf}>
          {prevExample ? (
            <Tooltip title={`${t('exampleNav.prev')}：${prevExample.name}`} placement="top">
              <Button
                type="text"
                icon={<LeftOutlined />}
                onClick={() =>
                  navigate(createLearnExampleDetailPath(prevExample.id), {
                    state: { examplesPath },
                  })
                }
                className={styles.navButton}
              >
                <div className={styles.prevTextContainer}>
                  <Text type="secondary" className={styles.navLabel}>
                    {t('exampleNav.prev')}
                  </Text>
                  <Text ellipsis className={styles.navName}>
                    {prevExample.name}
                  </Text>
                </div>
              </Button>
            </Tooltip>
          ) : (
            <div />
          )}
        </div>

        {/* Progress indicator */}
        <div className={styles.progressCenter}>
          <Text type="secondary" className={styles.progressLabel}>
            {t('exampleNav.progress', { current: currentIndex + 1, total: allExamples.length })}
          </Text>
          <div className={styles.progressDots}>
            {allExamples.map((_, idx) => (
              <div
                key={idx}
                className={`${styles.progressDot} ${idx === currentIndex ? styles.progressDotActive : styles.progressDotInactive}`}
              />
            ))}
          </div>
        </div>

        {/* Next */}
        <div className={styles.navHalfEnd}>
          {nextExample ? (
            <Tooltip title={`${t('exampleNav.next')}：${nextExample.name}`} placement="top">
              <Button
                type="primary"
                icon={<RightOutlined />}
                onClick={() =>
                  navigate(createLearnExampleDetailPath(nextExample.id), {
                    state: { examplesPath },
                  })
                }
                className={styles.navButtonNext}
              >
                <div className={styles.nextTextContainer}>
                  <Text className={styles.navLabelOnPrimary}>{t('exampleNav.next')}</Text>
                  <Text ellipsis className={styles.navNameOnPrimary}>
                    {nextExample.name}
                  </Text>
                </div>
              </Button>
            </Tooltip>
          ) : (
            <div />
          )}
        </div>
      </div>
    </nav>
  )
}

export default ExampleNavigation
