import { HistoryOutlined, RollbackOutlined } from '@ant-design/icons'
import { App, Button, Empty, Spin, Tag } from 'antd'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { ProcessSnapshot } from '../api/processStorageTypes'
import { importXml, selectCurrentProcess } from '../store/editorSlice'

import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { SemanticList, SemanticListItem, SemanticListMeta } from '@/shared/components/page'
import { toError } from '@/shared/errors'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'
import { formatDateTime } from '@/shared/i18n/dateTime'

interface LocalSnapshotsModalProps {
  open: boolean
  onClose: () => void
}

function LocalSnapshotsModal({ open, onClose }: LocalSnapshotsModalProps) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const dispatch = useAppDispatch()
  const currentProcess = useAppSelector(selectCurrentProcess)
  const [loading, setLoading] = useState(false)
  const [snapshots, setSnapshots] = useState<ProcessSnapshot[]>([])
  const [restoringId, setRestoringId] = useState<string | null>(null)
  const loadGeneration = useRef(0)
  useEscapeToClose(open, onClose)

  const loadSnapshots = useCallback(async () => {
    if (!currentProcess?.id) return
    const generation = ++loadGeneration.current
    setLoading(true)
    try {
      const { processStorage } = await import('../api/processStorage')
      const items = await processStorage.getProcessSnapshots(currentProcess.id)
      if (generation === loadGeneration.current) setSnapshots(items)
    } catch {
      if (generation !== loadGeneration.current) return
      message.error(t('designer.localSnapshots.loadFailed'))
      setSnapshots([])
    } finally {
      if (generation === loadGeneration.current) setLoading(false)
    }
  }, [currentProcess?.id, t])

  useEffect(() => {
    if (open) {
      void loadSnapshots()
    }
    return () => {
      loadGeneration.current += 1
    }
  }, [open, loadSnapshots])

  const handleRestore = async (snapshot: ProcessSnapshot) => {
    if (!currentProcess) return
    setRestoringId(snapshot.id)
    try {
      await dispatch(
        importXml({
          xml: snapshot.definition,
          type: currentProcess.type,
        })
      ).unwrap()
      message.success(t('designer.localSnapshots.restored'))
      onClose()
    } catch (err) {
      message.error(
        t('designer.localSnapshots.restoreFailed', {
          message: toError(err, t('designer.actions.xmlParseFailed')).message,
        })
      )
    } finally {
      setRestoringId(null)
    }
  }

  return (
    <Modal
      title={
        <span>
          <HistoryOutlined style={{ marginRight: 8 }} />
          {t('designer.localSnapshots.title')}
        </span>
      }
      open={open}
      onCancel={onClose}
      footer={null}
      width={560}
      destroyOnHidden
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : snapshots.length === 0 ? (
        <Empty description={t('designer.localSnapshots.empty')} />
      ) : (
        <SemanticList>
          {snapshots.map((item) => (
            <SemanticListItem
              key={item.id}
              actions={
                <Button
                  type="link"
                  size="small"
                  icon={<RollbackOutlined />}
                  loading={restoringId === item.id}
                  onClick={() => void handleRestore(item)}
                  aria-label={t('designer.localSnapshots.restore')}
                >
                  {t('designer.localSnapshots.restore')}
                </Button>
              }
            >
              <SemanticListMeta
                title={<Tag>{formatDateTime(item.createdAt)}</Tag>}
                description={t('designer.localSnapshots.snapshotSize', {
                  chars: item.definition.length,
                })}
              />
            </SemanticListItem>
          ))}
        </SemanticList>
      )}
    </Modal>
  )
}

export default LocalSnapshotsModal
