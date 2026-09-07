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
  onApply: (xml: string, signal: AbortSignal) => Promise<void>
}) {
  const { t } = useTranslation()
  const [draft, setDraft] = useState(sourceXml)
  const [isDirty, setIsDirty] = useState(false)
  const [isApplying, setIsApplying] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const buffer = useRef({ draft: sourceXml, dirty: false, version: 0 })
  const applying = useRef<AbortController | null>(null)
  const identity = useRef(documentId)

  const cancel = useCallback(() => {
    applying.current?.abort()
    applying.current = null
    buffer.current.version += 1
  }, [])

  useLayoutEffect(() => {
    if (identity.current !== documentId) {
      cancel()
      identity.current = documentId
      buffer.current.dirty = false
      setIsDirty(false)
      setIsApplying(false)
      setError(null)
    }
    if (!buffer.current.dirty) {
      buffer.current.draft = sourceXml
      setDraft(sourceXml)
    }
  }, [sourceXml, documentId, cancel])

  useLayoutEffect(() => cancel, [cancel])

  const handleChange = useCallback(
    (value: string | undefined) => {
      cancel()
      const next = value ?? ''
      buffer.current.draft = next
      buffer.current.dirty = next !== sourceXml
      setDraft(next)
      setIsDirty(buffer.current.dirty)
      setIsApplying(false)
      setError(null)
    },
    [cancel, sourceXml]
  )

  const handleApply = useCallback(async () => {
    if (applying.current) return false
    if (!buffer.current.dirty) return true
    const controller = new AbortController()
    applying.current = controller
    setIsApplying(true)
    try {
      await onApply(buffer.current.draft, controller.signal)
      if (controller.signal.aborted) return false
      buffer.current.dirty = false
      setIsDirty(false)
      setError(null)
      return true
    } catch (err) {
      if (!controller.signal.aborted) {
        setError(toError(err, t('designer.xmlEditor.applyFailed')).message)
      }
      return false
    } finally {
      if (applying.current === controller) {
        applying.current = null
        setIsApplying(false)
      }
    }
  }, [onApply, t])

  const save = useCallback(
    async (persist: () => Promise<boolean>) => {
      const version = buffer.current.version
      if (!(await handleApply()) || version !== buffer.current.version) return false
      const saved = await persist()
      return saved && version === buffer.current.version && !buffer.current.dirty
    },
    [handleApply]
  )

  return {
    draft,
    isDirty,
    isApplying,
    error,
    handleChange,
    handleApply,
    save,
    handleReset: () => handleChange(sourceXml),
    clearError: () => setError(null),
  }
}
