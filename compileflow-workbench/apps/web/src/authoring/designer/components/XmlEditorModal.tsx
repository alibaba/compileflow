import { App, Button } from 'antd'
import { useCallback } from 'react'
import { useTranslation } from 'react-i18next'

import { MonacoEditor } from '@/shared/components/LazyMonacoEditor'
import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

interface XmlEditorModalProps {
  open: boolean
  title: string
  value: string
  baselineValue: string
  onChange: (value: string) => void
  onApply: () => void
  onClose: () => void
}

function XmlEditorModal({
  open,
  title,
  value,
  baselineValue,
  onChange,
  onApply,
  onClose,
}: XmlEditorModalProps) {
  const { modal } = App.useApp()
  const { t } = useTranslation()
  const isDirty = value !== baselineValue

  const handleCancel = useCallback(() => {
    if (!isDirty) {
      onClose()
      return
    }
    modal.confirm({
      title: t('designer.xmlEditor.discardTitle'),
      content: t('designer.xmlEditor.discardMessage'),
      okText: t('designer.xmlEditor.discardConfirm'),
      cancelText: t('common.cancel'),
      onOk: onClose,
    })
  }, [isDirty, modal, onClose, t])
  useEscapeToClose(open, handleCancel)

  return (
    <Modal
      title={title}
      open={open}
      onCancel={handleCancel}
      width="80vw"
      styles={{ body: { padding: 0 } }}
      footer={[
        <Button key="close" onClick={handleCancel}>
          {t('common.cancel')}
        </Button>,
        <Button key="apply" type="primary" onClick={onApply} disabled={!isDirty}>
          {t('designer.xmlEditor.apply')}
        </Button>,
      ]}
    >
      <MonacoEditor
        value={value}
        onChange={(nextValue) => onChange(nextValue ?? '')}
        language="xml"
        height="60vh"
        options={{ minimap: { enabled: false }, scrollBeyondLastLine: false }}
      />
    </Modal>
  )
}

export default XmlEditorModal
