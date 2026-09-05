import { Alert, Button, Space } from 'antd'
import { useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { MonacoEditor } from '@/shared/components/LazyMonacoEditor'
import { toError } from '@/shared/errors'

import './XmlCodeEditorPanel.css'

export interface XmlCodeEditorPanelProps {
  sourceXml: string
  onApply: (xml: string) => Promise<void>
  /** When true, remind the user that canvas edits may not be reflected in the XML buffer yet. */
  showCanvasStaleHint?: boolean
}

export function XmlCodeEditorPanel({
  sourceXml,
  onApply,
  showCanvasStaleHint = false,
}: XmlCodeEditorPanelProps) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(sourceXml)
  const [isDirty, setIsDirty] = useState(false)
  const [isApplying, setIsApplying] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (!isDirty) {
      setDraft(sourceXml)
    }
  }, [sourceXml, isDirty])

  const handleChange = useCallback(
    (value: string | undefined) => {
      const next = value ?? ''
      setDraft(next)
      setIsDirty(next !== sourceXml)
      setError(null)
    },
    [sourceXml]
  )

  const handleApply = useCallback(async () => {
    setIsApplying(true)
    try {
      await onApply(draft)
      setIsDirty(false)
      setError(null)
    } catch (err) {
      setError(toError(err, t('designer.xmlEditor.applyFailed')).message)
    } finally {
      setIsApplying(false)
    }
  }, [draft, onApply, t])

  const handleReset = useCallback(() => {
    setDraft(sourceXml)
    setIsDirty(false)
    setError(null)
  }, [sourceXml])

  return (
    <div className="xml-code-editor-panel">
      {showCanvasStaleHint && !isDirty && (
        <Alert
          type="warning"
          showIcon
          className="xml-code-editor-hint"
          title={t('designer.split.canvasStaleHint')}
        />
      )}
      {isDirty && (
        <Alert
          type="info"
          showIcon
          className="xml-code-editor-hint"
          title={t('designer.xmlEditor.dirtyHint')}
        />
      )}
      {error && (
        <Alert
          type="error"
          showIcon
          closable
          className="xml-code-editor-error"
          title={t('designer.xmlEditor.parseError')}
          description={error}
          onClose={() => setError(null)}
        />
      )}
      <div
        className="xml-code-editor-body"
        role="region"
        aria-label={t('designer.xmlEditor.editorRegion')}
      >
        <MonacoEditor
          language="xml"
          value={draft}
          height="100%"
          options={{
            ariaLabel: t('designer.xmlEditor.editorRegion'),
            minimap: { enabled: false },
            scrollBeyondLastLine: false,
          }}
          onChange={handleChange}
        />
      </div>
      <div className="xml-code-editor-toolbar">
        <Space>
          <Button
            size="small"
            onClick={handleReset}
            disabled={!isDirty || isApplying}
            aria-label={t('designer.xmlEditor.reset')}
          >
            {t('designer.xmlEditor.reset')}
          </Button>
          <Button
            type="primary"
            size="small"
            onClick={() => void handleApply()}
            loading={isApplying}
            disabled={!isDirty}
            aria-label={t('designer.xmlEditor.apply')}
          >
            {t('designer.xmlEditor.apply')}
          </Button>
        </Space>
      </div>
    </div>
  )
}
