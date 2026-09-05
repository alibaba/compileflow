import styles from './ScriptSourceEditor.module.css'

import { MonacoEditor } from '@/shared/components/LazyMonacoEditor'

interface ScriptSourceEditorProps {
  language: string
  value: string
  ariaLabel: string
  onChange: (value: string) => void
}

function editorLanguage(language: string): string {
  return language.trim().toLowerCase() === 'java' ? 'java' : 'plaintext'
}

export function ScriptSourceEditor({
  language,
  value,
  ariaLabel,
  onChange,
}: ScriptSourceEditorProps) {
  return (
    <div className={styles.editor}>
      <MonacoEditor
        height="240px"
        language={editorLanguage(language)}
        value={value}
        onChange={(nextValue) => onChange(nextValue ?? '')}
        options={{
          ariaLabel,
          automaticLayout: true,
          fontSize: 12,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: 'on',
        }}
      />
    </div>
  )
}
