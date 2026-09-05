import {
  ArrowRightOutlined,
  FileTextOutlined,
  NodeIndexOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { Alert, Button, Divider, Input, Space, Spin, Tag, Typography } from 'antd'
import type { CSSProperties, ReactNode } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { createLearnExampleDetailPath, createLearnExamplesPath, ROUTES } from '@/shared/constants'
import type { ProcessModelType } from '@/shared/contracts'
import type { Example } from '@/shared/contracts'
import { filterExamplesByText } from '@/shared/examples/exampleSearch'
import { localizeExample } from '@/shared/examples/localizeExample'
import { openDesignerFromWorkspaceProcess } from '@/shared/services/designerNavigation'

const { Text } = Typography

interface QuickLink {
  labelKey: string
  path: string
  descKey: string
}

interface SearchRowProps {
  children: ReactNode
  onClick: () => void
}

interface GlobalSearchProps {
  /** Example loader injected by shell layer to avoid shell to learn dependency violation. */
  loadExamples: () => Promise<Example[]>
  /** Local workspace process loader injected by shell layer. */
  loadProcesses: () => Promise<SearchableProcess[]>
  open?: boolean
  onOpenChange?: (open: boolean) => void
  hideTrigger?: boolean
  disableShortcut?: boolean
}

export interface SearchableProcess {
  code: string
  id: string
  name: string
  type: ProcessModelType
}

interface SearchCatalog {
  examples: Example[]
  failedSources: number
  loading: boolean
  processes: SearchableProcess[]
}

const QUICK_LINKS: QuickLink[] = [
  {
    labelKey: 'nav.learn.examples',
    path: ROUTES.LEARN_EXAMPLES,
    descKey: 'search.quickLink.examplesDesc',
  },
  {
    labelKey: 'nav.ops.processes',
    path: ROUTES.OPERATE_PROCESSES,
    descKey: 'search.quickLink.processesDesc',
  },
  {
    labelKey: 'nav.ops.monitoring',
    path: ROUTES.OPERATE_MONITORING,
    descKey: 'search.quickLink.monitoringDesc',
  },
  { labelKey: 'nav.ops.logs', path: ROUTES.OPERATE_LOGS, descKey: 'search.quickLink.logsDesc' },
]

const rowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  padding: 'var(--spacing-2) var(--spacing-3)',
  borderRadius: 'var(--radius-md)',
  cursor: 'pointer',
  transition: 'background var(--transition-fast)',
  width: '100%',
  border: 0,
  background: 'transparent',
  color: 'inherit',
  textAlign: 'left',
}

function isMacPlatform(): boolean {
  if (typeof navigator === 'undefined') return false

  const navigatorWithUserAgentData = navigator as Navigator & {
    userAgentData?: { platform?: string }
  }
  const platform = navigatorWithUserAgentData.userAgentData?.platform
  if (platform) return platform.toLowerCase().includes('mac')

  return /Mac|iPhone|iPad/i.test(navigator.userAgent)
}

function filterProcesses(processes: SearchableProcess[], searchValue: string): SearchableProcess[] {
  const query = searchValue.trim().toLowerCase()
  if (!query) return []
  return processes
    .filter(
      (process) =>
        process.name.toLowerCase().includes(query) || process.code.toLowerCase().includes(query)
    )
    .slice(0, 6)
}

function useSearchCatalog(
  loadExamples: () => Promise<Example[]>,
  loadProcesses: () => Promise<SearchableProcess[]>,
  enabled: boolean
) {
  const [catalog, setCatalog] = useState<SearchCatalog>({
    examples: [],
    failedSources: 0,
    loading: false,
    processes: [],
  })
  const generation = useRef(0)

  const reload = useCallback(async () => {
    const request = ++generation.current
    setCatalog((current) => ({ ...current, failedSources: 0, loading: true }))
    const [examples, processes] = await Promise.allSettled([loadExamples(), loadProcesses()])
    if (request !== generation.current) return
    setCatalog((current) => ({
      examples: examples.status === 'fulfilled' ? examples.value : current.examples,
      failedSources:
        Number(examples.status === 'rejected') + Number(processes.status === 'rejected'),
      loading: false,
      processes: processes.status === 'fulfilled' ? processes.value : current.processes,
    }))
  }, [loadExamples, loadProcesses])

  useEffect(() => {
    if (!enabled) return
    void reload()
    return () => {
      generation.current += 1
    }
  }, [enabled, reload])

  return { ...catalog, reload }
}

function useGlobalSearchShortcut(
  open: () => void,
  close: () => void,
  visible: boolean,
  enabled: boolean
) {
  useEffect(() => {
    if (!enabled) return
    const handleKeyDown = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key === 'k') {
        event.preventDefault()
        open()
        return
      }

      if (event.key === 'Escape' && visible) {
        close()
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [close, enabled, open, visible])
}

function SearchTrigger({ onOpen }: { onOpen: () => void }) {
  const { t } = useTranslation()

  return (
    <button
      type="button"
      className="global-search-trigger"
      aria-label={t('search.openShortcut')}
      onClick={onOpen}
    >
      <SearchOutlined style={{ fontSize: 14 }} />
      <span className="global-search-label">{t('search.trigger')}</span>
      <kbd className="global-search-kbd">{isMacPlatform() ? '⌘K' : 'Ctrl+K'}</kbd>
    </button>
  )
}

function SearchInput({
  loading,
  onSearch,
  onValueChange,
  value,
}: {
  loading: boolean
  onSearch: () => void
  onValueChange: (value: string) => void
  value: string
}) {
  const { t } = useTranslation()

  return (
    <div style={{ padding: 'var(--spacing-4)', borderBottom: '1px solid var(--border-color)' }}>
      <Input
        prefix={<SearchOutlined style={{ color: 'var(--text-tertiary)', fontSize: 16 }} />}
        placeholder={t('search.placeholder')}
        aria-label={t('search.inputLabel')}
        size="large"
        value={value}
        onChange={(event) => onValueChange(event.target.value)}
        onPressEnter={onSearch}
        autoFocus
        variant="borderless"
        style={{ fontSize: 'var(--font-size-md)' }}
        suffix={
          <Text type="secondary" style={{ fontSize: 'var(--font-size-xs)', whiteSpace: 'nowrap' }}>
            {t(loading ? 'search.indexing' : 'search.enterHint')}
          </Text>
        }
      />
    </div>
  )
}

function SearchCatalogStatus({
  failedSources,
  loading,
  onRetry,
}: {
  failedSources: number
  loading: boolean
  onRetry: () => void
}) {
  const { t } = useTranslation()
  if (loading) {
    return (
      <div role="status" style={{ padding: 'var(--spacing-3) var(--spacing-4)' }}>
        <Space>
          <Spin size="small" />
          <Text type="secondary">{t('search.indexing')}</Text>
        </Space>
      </div>
    )
  }
  if (failedSources === 0) return null
  return (
    <Alert
      type="warning"
      showIcon
      title={t('search.sourcesUnavailable')}
      action={<Button onClick={onRetry}>{t('common.retry')}</Button>}
      style={{ margin: 'var(--spacing-3) var(--spacing-4)' }}
    />
  )
}

function SearchRow({ children, onClick }: SearchRowProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      style={rowStyle}
      onMouseEnter={(event) => {
        event.currentTarget.style.background = 'var(--hover-bg)'
      }}
      onMouseLeave={(event) => {
        event.currentTarget.style.background = 'transparent'
      }}
    >
      {children}
    </button>
  )
}

function SearchResults({
  onResultClick,
  results,
}: {
  onResultClick: (exampleId: string) => void
  results: Example[]
}) {
  const { t } = useTranslation()

  if (results.length === 0) return null

  return (
    <div style={{ padding: 'var(--spacing-3) var(--spacing-4)' }}>
      <Text
        type="secondary"
        style={{
          fontSize: 'var(--font-size-xs)',
          display: 'block',
          marginBottom: 'var(--spacing-2)',
        }}
      >
        {t('search.matchedExamples')}
      </Text>
      {results.map((example) => (
        <SearchRow key={example.id} onClick={() => onResultClick(example.id)}>
          <Space>
            <FileTextOutlined style={{ color: 'var(--primary-400)', fontSize: 14 }} />
            <Text strong style={{ fontSize: 'var(--font-size-base)' }}>
              {example.name}
            </Text>
            <Tag style={{ fontSize: 11, padding: '0 4px', margin: 0 }}>{example.modelType}</Tag>
          </Space>
          <ArrowRightOutlined style={{ color: 'var(--text-tertiary)', fontSize: 12 }} />
        </SearchRow>
      ))}
      <Divider style={{ margin: '8px 0' }} />
    </div>
  )
}

function ProcessSearchResults({
  onResultClick,
  results,
}: {
  onResultClick: (process: SearchableProcess) => void
  results: SearchableProcess[]
}) {
  const { t } = useTranslation()
  if (results.length === 0) return null

  return (
    <div style={{ padding: 'var(--spacing-3) var(--spacing-4)' }}>
      <Text
        type="secondary"
        style={{
          fontSize: 'var(--font-size-xs)',
          display: 'block',
          marginBottom: 'var(--spacing-2)',
        }}
      >
        {t('search.matchedProcesses')}
      </Text>
      {results.map((process) => (
        <SearchRow key={process.id} onClick={() => onResultClick(process)}>
          <Space>
            <NodeIndexOutlined style={{ color: 'var(--primary-400)', fontSize: 14 }} />
            <Text strong style={{ fontSize: 'var(--font-size-base)' }}>
              {process.name}
            </Text>
            <Text type="secondary" style={{ fontSize: 'var(--font-size-xs)' }}>
              {process.code}
            </Text>
            <Tag style={{ fontSize: 11, padding: '0 4px', margin: 0 }}>{process.type}</Tag>
          </Space>
          <ArrowRightOutlined style={{ color: 'var(--text-tertiary)', fontSize: 12 }} />
        </SearchRow>
      ))}
      <Divider style={{ margin: '8px 0' }} />
    </div>
  )
}

function QuickLinks({
  onQuickLink,
  searchValue,
  show,
}: {
  onQuickLink: (path: string) => void
  searchValue: string
  show: boolean
}) {
  const { t } = useTranslation()

  if (!show) return null

  return (
    <div style={{ padding: 'var(--spacing-3) var(--spacing-4)' }}>
      <Text
        type="secondary"
        style={{
          fontSize: 'var(--font-size-xs)',
          display: 'block',
          marginBottom: 'var(--spacing-2)',
        }}
      >
        {searchValue.trim() ? t('search.noMatch') : t('search.quickNav')}
      </Text>
      {QUICK_LINKS.map((link) => (
        <SearchRow key={link.path} onClick={() => onQuickLink(link.path)}>
          <Space>
            <NodeIndexOutlined style={{ color: 'var(--text-tertiary)', fontSize: 14 }} />
            <Text strong style={{ fontSize: 'var(--font-size-base)' }}>
              {t(link.labelKey)}
            </Text>
            <Text type="secondary" style={{ fontSize: 'var(--font-size-sm)' }}>
              {t(link.descKey)}
            </Text>
          </Space>
          <ArrowRightOutlined style={{ color: 'var(--text-tertiary)', fontSize: 12 }} />
        </SearchRow>
      ))}
    </div>
  )
}

function SearchShortcutFooter() {
  const { t } = useTranslation()
  const shortcuts = [
    ['\u21b5', t('search.submit')],
    ['Esc', t('search.close')],
  ]

  return (
    <div
      style={{
        padding: 'var(--spacing-2) var(--spacing-4)',
        borderTop: '1px solid var(--border-color)',
        display: 'flex',
        gap: 'var(--spacing-4)',
      }}
    >
      {shortcuts.map(([key, label]) => (
        <Space key={key} size={4}>
          <Tag style={{ margin: 0, fontSize: 11 }}>{key}</Tag>
          <Text type="secondary" style={{ fontSize: 'var(--font-size-xs)' }}>
            {label}
          </Text>
        </Space>
      ))}
    </div>
  )
}

export function GlobalSearch({
  disableShortcut = false,
  hideTrigger = false,
  loadExamples,
  loadProcesses,
  onOpenChange,
  open,
}: GlobalSearchProps) {
  const { i18n, t } = useTranslation()
  const navigate = useNavigate()
  const [internalOpen, setInternalOpen] = useState(false)
  const visible = open ?? internalOpen
  const [searchValue, setSearchValue] = useState('')
  const catalog = useSearchCatalog(loadExamples, loadProcesses, visible)
  const language = i18n.resolvedLanguage || i18n.language
  const localizedExamples = useMemo(
    () => catalog.examples.map((example) => localizeExample(example, t, language)),
    [catalog.examples, language, t]
  )
  const searchResults = useMemo(
    () => filterExamplesByText(localizedExamples, catalog.examples, searchValue, 6),
    [catalog.examples, localizedExamples, searchValue]
  )
  const processResults = useMemo(
    () => filterProcesses(catalog.processes, searchValue),
    [catalog.processes, searchValue]
  )

  const closeSearch = useCallback(() => {
    setInternalOpen(false)
    onOpenChange?.(false)
    setSearchValue('')
  }, [onOpenChange])

  const openSearch = useCallback(() => {
    setInternalOpen(true)
    onOpenChange?.(true)
  }, [onOpenChange])

  const handleSearch = useCallback(() => {
    const query = searchValue.trim()
    if (!query || catalog.loading) return

    if (searchResults.length > 0) {
      closeSearch()
      void navigate(createLearnExampleDetailPath(searchResults[0].id))
      return
    }
    if (processResults.length > 0) {
      closeSearch()
      openDesignerFromWorkspaceProcess(navigate, {
        processId: processResults[0].id,
        modelType: processResults[0].type,
      })
      return
    }

    closeSearch()
    void navigate(createLearnExamplesPath({ search: query }))
  }, [catalog.loading, closeSearch, navigate, processResults, searchResults, searchValue])

  const handleQuickLink = useCallback(
    (path: string) => {
      closeSearch()
      void navigate(path)
    },
    [closeSearch, navigate]
  )

  const handleResultClick = useCallback(
    (exampleId: string) => {
      closeSearch()
      void navigate(createLearnExampleDetailPath(exampleId))
    },
    [closeSearch, navigate]
  )

  const handleProcessResultClick = useCallback(
    (process: SearchableProcess) => {
      closeSearch()
      openDesignerFromWorkspaceProcess(navigate, {
        processId: process.id,
        modelType: process.type,
      })
    },
    [closeSearch, navigate]
  )

  useGlobalSearchShortcut(openSearch, closeSearch, visible, !disableShortcut)

  return (
    <>
      {!hideTrigger && <SearchTrigger onOpen={openSearch} />}
      <Modal
        title={t('search.dialogTitle')}
        open={visible}
        onCancel={closeSearch}
        footer={null}
        width={600}
        styles={{ body: { padding: 0 } }}
        closable
      >
        <SearchInput
          loading={catalog.loading}
          value={searchValue}
          onValueChange={setSearchValue}
          onSearch={handleSearch}
        />
        <SearchCatalogStatus
          failedSources={catalog.failedSources}
          loading={catalog.loading}
          onRetry={() => void catalog.reload()}
        />
        <SearchResults results={searchResults} onResultClick={handleResultClick} />
        <ProcessSearchResults results={processResults} onResultClick={handleProcessResultClick} />
        <QuickLinks
          searchValue={searchValue}
          show={
            !searchValue.trim() ||
            (!catalog.loading && searchResults.length === 0 && processResults.length === 0)
          }
          onQuickLink={handleQuickLink}
        />
        <SearchShortcutFooter />
      </Modal>
    </>
  )
}
