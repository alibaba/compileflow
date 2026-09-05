import { SearchOutlined } from '@ant-design/icons'
import { lazy, Suspense, useCallback, useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { SearchableProcess } from './GlobalSearch'

import type { Example } from '@/shared/contracts'

const GlobalSearch = lazy(() =>
  import('./GlobalSearch').then((module) => ({ default: module.GlobalSearch }))
)

function SearchLoadingFallback() {
  const { t } = useTranslation()
  return (
    <div className="global-search-loading-backdrop">
      <div className="global-search-loading" role="status" aria-live="polite">
        <SearchOutlined spin />
        <span>{t('search.loading')}</span>
      </div>
    </div>
  )
}

interface DeferredGlobalSearchProps {
  compact?: boolean
  loadExamples: () => Promise<Example[]>
  loadProcesses: () => Promise<SearchableProcess[]>
  onOpen?: () => void
}

function isMacPlatform(): boolean {
  const userAgentData = (navigator as Navigator & { userAgentData?: { platform?: string } })
    .userAgentData
  return (userAgentData?.platform ?? navigator.userAgent).toLowerCase().includes('mac')
}

export function DeferredGlobalSearch({
  compact = false,
  loadExamples,
  loadProcesses,
  onOpen,
}: DeferredGlobalSearchProps) {
  const { t } = useTranslation()
  const [activated, setActivated] = useState(false)
  const [open, setOpen] = useState(false)

  const show = useCallback(() => {
    onOpen?.()
    setActivated(true)
    setOpen(true)
  }, [onOpen])

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        show()
      } else if (event.key === 'Escape' && open) {
        setOpen(false)
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, show])

  return (
    <>
      <button
        type="button"
        className={`global-search-trigger${compact ? ' global-search-trigger-mobile' : ''}`}
        aria-label={t(compact ? 'search.dialogTitle' : 'search.openShortcut')}
        onClick={show}
      >
        <SearchOutlined style={{ fontSize: 14 }} />
        <span className="global-search-label">{t('search.trigger')}</span>
        {!compact && <kbd className="global-search-kbd">{isMacPlatform() ? '⌘K' : 'Ctrl+K'}</kbd>}
      </button>
      {activated && (
        <Suspense fallback={open ? <SearchLoadingFallback /> : null}>
          <GlobalSearch
            loadExamples={loadExamples}
            loadProcesses={loadProcesses}
            open={open}
            onOpenChange={setOpen}
            hideTrigger
            disableShortcut
          />
        </Suspense>
      )}
    </>
  )
}
