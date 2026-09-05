import type { EditorProps } from '@monaco-editor/react'
import { Spin } from 'antd'
import React, { lazy, Suspense } from 'react'
import { useTranslation } from 'react-i18next'

const MonacoEditorLazy = lazy(async () => {
  const [{ configureMonacoLocal }, editorModule] = await Promise.all([
    import('@/shared/monaco/configureMonacoLocal'),
    import('@monaco-editor/react'),
  ])
  configureMonacoLocal()
  return { default: editorModule.default || editorModule.Editor }
})

function MonacoEditorLoading() {
  const { t } = useTranslation()
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        height: '100%',
      }}
    >
      <Spin size="large" description={t('designer.loading.monaco')} />
    </div>
  )
}

export const MonacoEditor: React.FC<EditorProps> = (props) => (
  <Suspense fallback={<MonacoEditorLoading />}>
    <MonacoEditorLazy {...props} />
  </Suspense>
)
