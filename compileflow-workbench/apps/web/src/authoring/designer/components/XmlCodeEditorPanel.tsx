import { Alert, Button, Space } from 'antd'
import { useTranslation } from 'react-i18next'

import type { useXmlDraft } from '../hooks/useXmlDraft'

import { MonacoEditor } from '@/shared/components/LazyMonacoEditor'

import './XmlCodeEditorPanel.css'

export interface XmlCodeEditorPanelProps {
  editor: ReturnType<typeof useXmlDraft>
  /** When true, remind the user that canvas edits may not be reflected in the XML buffer yet. */
  showCanvasStaleHint?: boolean
}

export function XmlCodeEditorPanel({
  editor,
  showCanvasStaleHint = false,
}: XmlCodeEditorPanelProps) {
  const { t } = useTranslation()
  const { draft, isDirty, isApplying, error, handleChange, handleApply, handleReset } = editor

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
          onClose={editor.clearError}
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
