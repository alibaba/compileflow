import {
  DislikeFilled,
  DislikeOutlined,
  HeartFilled,
  HeartOutlined,
  LikeFilled,
  LikeOutlined,
  ShareAltOutlined,
} from '@ant-design/icons'
import { App, Button, Space, Tooltip } from 'antd'
import React, { useState } from 'react'
import { useTranslation } from 'react-i18next'

import styles from './FeedbackActions.module.css'

interface FeedbackActionsProps {
  exampleId: string
  exampleTitle: string
}

type FeedbackKind = 'like' | 'dislike' | 'favorite'

function feedbackStorageKey(kind: FeedbackKind, exampleId: string): string {
  return `compileflow:feedback:${kind}:${encodeURIComponent(exampleId)}`
}

function readFeedbackState(kind: FeedbackKind, exampleId: string): boolean {
  try {
    return localStorage.getItem(feedbackStorageKey(kind, exampleId)) === 'true'
  } catch {
    return false
  }
}

function writeFeedbackState(kind: FeedbackKind, exampleId: string, value: boolean): void {
  try {
    localStorage.setItem(feedbackStorageKey(kind, exampleId), String(value))
  } catch {
    // Feedback remains available in component state when browser storage is unavailable.
  }
}

function removeFeedbackState(kind: FeedbackKind, exampleId: string): void {
  try {
    localStorage.removeItem(feedbackStorageKey(kind, exampleId))
  } catch {
    // The in-memory state remains authoritative for this session.
  }
}

function feedbackStatusText({
  liked,
  disliked,
  favorited,
  t,
}: {
  liked: boolean
  disliked: boolean
  favorited: boolean
  t: ReturnType<typeof useTranslation>['t']
}): string {
  if (liked) return t('feedback.likeSuccess')
  if (disliked) return t('feedback.dislikeSuccess')
  if (favorited) return t('feedback.bookmarkAdded')
  return ''
}

const FeedbackActions: React.FC<FeedbackActionsProps> = ({ exampleId, exampleTitle }) => {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [liked, setLiked] = useState(() => readFeedbackState('like', exampleId))
  const [disliked, setDisliked] = useState(() => readFeedbackState('dislike', exampleId))
  const [favorited, setFavorited] = useState(() => readFeedbackState('favorite', exampleId))

  const handleLike = () => {
    const newLiked = !liked
    setLiked(newLiked)
    if (newLiked && disliked) {
      setDisliked(false)
      removeFeedbackState('dislike', exampleId)
    }
    writeFeedbackState('like', exampleId, newLiked)
    message.success(newLiked ? t('feedback.likeSuccess') : t('feedback.likeCancelled'))
  }

  const handleDislike = () => {
    const newDisliked = !disliked
    setDisliked(newDisliked)
    if (newDisliked && liked) {
      setLiked(false)
      removeFeedbackState('like', exampleId)
    }
    writeFeedbackState('dislike', exampleId, newDisliked)
    message.info(newDisliked ? t('feedback.dislikeSuccess') : t('feedback.dislikeCancelled'))
  }

  const handleFavorite = () => {
    const newFavorited = !favorited
    setFavorited(newFavorited)
    writeFeedbackState('favorite', exampleId, newFavorited)
    message.success(newFavorited ? t('feedback.bookmarkAdded') : t('feedback.bookmarkRemoved'))
  }

  const handleShare = async () => {
    const url = window.location.href
    if (navigator.share) {
      try {
        await navigator.share({ title: exampleTitle, text: `CompileFlow: ${exampleTitle}`, url })
        message.success(t('feedback.shareSuccess'))
      } catch (error) {
        if (!(error instanceof Error) || error.name !== 'AbortError') {
          await handleCopyLink()
        }
      }
    } else {
      await handleCopyLink()
    }
  }

  const handleCopyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      message.success(t('feedback.linkCopied'))
    } catch {
      message.error(t('feedback.copyFailed'))
    }
  }

  const statusText = feedbackStatusText({ liked, disliked, favorited, t })

  return (
    <section className={styles.panel} aria-label={t('feedback.helpfulQuestion')}>
      <Space vertical size="middle" style={{ width: '100%' }}>
        <div>
          <span className={styles.sectionLabel}>{t('feedback.helpfulQuestion')}</span>
          <Space size="middle">
            <Tooltip title={t('feedback.helpful')}>
              <Button
                type={liked ? 'primary' : 'default'}
                icon={liked ? <LikeFilled /> : <LikeOutlined />}
                onClick={handleLike}
                aria-label={liked ? t('feedback.likeCancelled') : t('feedback.helpful')}
                className={styles.actionBtn}
              >
                {t('feedback.helpful')}
              </Button>
            </Tooltip>
            <Tooltip title={t('feedback.notHelpful')}>
              <Button
                danger={disliked}
                icon={disliked ? <DislikeFilled /> : <DislikeOutlined />}
                onClick={handleDislike}
                aria-label={disliked ? t('feedback.dislikeCancelled') : t('feedback.notHelpful')}
                className={styles.actionBtn}
              >
                {t('feedback.notHelpful')}
              </Button>
            </Tooltip>
          </Space>
        </div>

        <div className={styles.divider} />

        <div>
          <span className={styles.sectionLabel}>{t('feedback.quickActions')}</span>
          <Space size="small" wrap>
            <Tooltip title={favorited ? t('feedback.bookmarkRemoved') : t('feedback.bookmark')}>
              <Button
                icon={favorited ? <HeartFilled className={styles.favorited} /> : <HeartOutlined />}
                onClick={handleFavorite}
                aria-label={favorited ? t('feedback.bookmarkRemoved') : t('feedback.bookmark')}
                className={favorited ? `${styles.iconBtn} ${styles.favorited}` : styles.iconBtn}
              >
                {t('feedback.bookmark')}
              </Button>
            </Tooltip>

            <Tooltip title={t('feedback.share')}>
              <Button
                icon={<ShareAltOutlined />}
                onClick={handleShare}
                aria-label={t('feedback.share')}
                className={styles.iconBtn}
              >
                {t('feedback.share')}
              </Button>
            </Tooltip>
          </Space>
        </div>

        {statusText && (
          <>
            <div className={styles.divider} />
            <span className={styles.statusNote}>{statusText}</span>
          </>
        )}
      </Space>
    </section>
  )
}

export default FeedbackActions
