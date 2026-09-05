import { App } from 'antd'
import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

import { saveProcess } from '../store/editorSlice'

import type { AppDispatch } from '@/app/store'
import { toError } from '@/shared/errors'
import { createLogger } from '@/shared/logging/logger'

export const AUTO_SAVE_DEBOUNCE_MS = 30_000
const logger = createLogger('AutoSave')

export interface UseAutoSaveOptions {
  isModified: boolean
  isSaving: boolean
  canSave: boolean
  dispatch: AppDispatch
  debounceMs?: number
}

export function useAutoSave({
  isModified,
  isSaving,
  canSave,
  dispatch,
  debounceMs = AUTO_SAVE_DEBOUNCE_MS,
}: UseAutoSaveOptions): { autoSavePending: boolean } {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const [autoSavePending, setAutoSavePending] = useState(false)
  const timerRef = useRef<number | null>(null)

  useEffect(() => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }
    setAutoSavePending(false)

    if (!isModified || isSaving || !canSave) {
      return
    }

    setAutoSavePending(true)
    timerRef.current = window.setTimeout(() => {
      timerRef.current = null
      setAutoSavePending(false)
      void dispatch(saveProcess({ createSnapshot: false }))
        .unwrap()
        .catch((error: unknown) => {
          message.error(t('designer.autoSave.failed'))
          logger.error('Automatic save failed', toError(error))
        })
    }, debounceMs)

    return () => {
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current)
        timerRef.current = null
      }
    }
  }, [canSave, debounceMs, dispatch, isModified, isSaving, message, t])

  return { autoSavePending }
}
