import { Button } from 'antd'
import type { ReactNode } from 'react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useBlocker } from 'react-router-dom'

import { LocalizedModal } from '@/shared/components/LocalizedModal'

export function useUnsavedChangesGuard(
  isModified: boolean,
  save: () => Promise<boolean>
): ReactNode {
  const { t } = useTranslation()
  const [savingBeforeLeave, setSavingBeforeLeave] = useState(false)
  const [dialogReady, setDialogReady] = useState(false)
  const isModifiedRef = useRef(isModified)
  isModifiedRef.current = isModified
  const shouldBlock = useCallback(() => isModifiedRef.current, [])
  const blocker = useBlocker(shouldBlock)
  const blockerRef = useRef(blocker)
  blockerRef.current = blocker
  const saveRef = useRef(save)
  saveRef.current = save

  useEffect(() => {
    if (!isModified) return

    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }

    window.addEventListener('beforeunload', handleBeforeUnload)
    return () => window.removeEventListener('beforeunload', handleBeforeUnload)
  }, [isModified])

  const proceed = () => {
    const currentBlocker = blockerRef.current
    if (currentBlocker.state === 'blocked') currentBlocker.proceed()
  }
  const reset = () => {
    const currentBlocker = blockerRef.current
    if (currentBlocker.state === 'blocked') currentBlocker.reset()
  }
  const saveAndProceed = async () => {
    setSavingBeforeLeave(true)
    try {
      if (await saveRef.current()) proceed()
    } finally {
      setSavingBeforeLeave(false)
    }
  }

  return (
    <LocalizedModal
      open={blocker.state === 'blocked'}
      title={t('designer.actions.unsavedLeaveTitle')}
      closable={false}
      mask={{ closable: false }}
      onCancel={reset}
      afterOpenChange={setDialogReady}
      footer={
        <>
          <Button danger disabled={!dialogReady || savingBeforeLeave} onClick={proceed}>
            {t('designer.actions.leaveWithoutSaving')}
          </Button>
          <Button disabled={!dialogReady || savingBeforeLeave} onClick={reset}>
            {t('common.cancel')}
          </Button>
          <Button
            type="primary"
            disabled={!dialogReady}
            loading={savingBeforeLeave}
            onClick={() => void saveAndProceed()}
          >
            {t('designer.actions.saveAndLeave')}
          </Button>
        </>
      }
    >
      {t('designer.actions.unsavedLeaveContent')}
    </LocalizedModal>
  )
}
