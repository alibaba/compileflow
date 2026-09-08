import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { toError } from '@/shared/errors'

export function useXmlDraft({
  sourceXml,
  documentId,
  onApply,
}: {
  sourceXml: string
  documentId: string | null
  onApply: (xml: string) => void
}) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(sourceXml)
  const [isDirty, setIsDirty] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const buffer = useRef({ draft: sourceXml, dirty: false, version: 0 })
  const identity = useRef(documentId)

  useLayoutEffect(() => {
    if (identity.current !== documentId) {
      buffer.current.version += 1
      identity.current = documentId
      buffer.current.dirty = false
      setIsDirty(false)
      setError(null)
    }
    if (!buffer.current.dirty) {
      buffer.current.draft = sourceXml
      setDraft(sourceXml)
    }
  }, [sourceXml, documentId])

  const handleChange = useCallback(
    (value: string | undefined) => {
      buffer.current.version += 1
      const next = value ?? ''
      buffer.current.draft = next
      buffer.current.dirty = next !== sourceXml
      setDraft(next)
      setIsDirty(buffer.current.dirty)
      setError(null)
    },
    [sourceXml]
  )

  const handleApply = useCallback(() => {
    if (!buffer.current.dirty) return true
    try {
      onApply(buffer.current.draft)
      buffer.current.dirty = false
      setIsDirty(false)
      setError(null)
      return true
    } catch (err) {
      setError(toError(err, t('designer.xmlEditor.applyFailed')).message)
      return false
    }
  }, [onApply, t])

  const save = useCallback(
    async (persist: () => Promise<boolean>) => {
      const version = buffer.current.version
      if (!handleApply() || version !== buffer.current.version) return false
      const saved = await persist()
      return saved && version === buffer.current.version && !buffer.current.dirty
    },
    [handleApply]
  )

  return {
    draft,
    isDirty,
    error,
    handleChange,
    handleApply,
    save,
    handleReset: () => handleChange(sourceXml),
    clearError: () => setError(null),
  }
}
