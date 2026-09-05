import {
  ArrowRightOutlined,
  DownloadOutlined,
  HistoryOutlined,
  PlusOutlined,
  RocketOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { Alert, App, Button, Empty, Space } from 'antd'
import type { ChangeEvent } from 'react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { NavigateFunction } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'

import { BUILT_IN_TEMPLATES, ensureBuiltInTemplates } from '../designer/api/builtInTemplates'
import {
  exportAllData,
  processStorage,
  importData,
  type WorkbenchDataExport,
} from '../designer/api/processStorage'
import type { ProcessTemplate, StoredProcess } from '../designer/api/processStorageTypes'

import styles from './Workspace.module.css'

import { HeroBanner } from '@/shared/components/HeroBanner'
import {
  HubMetrics,
  HubPanel,
  HubRow,
  HubRowList,
  HubSection,
  HubSurface,
  ListPanel,
  MetricGrid,
  SemanticList,
  SemanticListItem,
  SemanticListMeta,
  type HubRowAccent,
} from '@/shared/components/page'
import { ROUTES } from '@/shared/constants'
import type { ProcessModelType } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { formatDate } from '@/shared/i18n/dateTime'
import { createLogger } from '@/shared/logging/logger'
import {
  openDesignerFromTemplate,
  openDesignerFromWorkspaceProcess,
  openNewDesigner,
} from '@/shared/services/designerNavigation'

const logger = createLogger('Workspace')

interface RecentProject {
  id: string
  name: string
  type: ProcessModelType
  lastModified: number
}

interface WorkspaceStats {
  myProcesses: number
  templates: number
  thisMonth: number
}

interface QuickStartItem {
  key: string
  accent: HubRowAccent
  title: string
  desc: string
  onClick: () => void
}

function toRecentProject(
  process: Pick<StoredProcess, 'id' | 'name' | 'type' | 'updatedAt'>
): RecentProject {
  return {
    id: process.id,
    name: process.name,
    type: process.type,
    lastModified: process.updatedAt,
  }
}

function countProcessesCreatedThisMonth(processes: Array<{ createdAt: number }>): number {
  const now = new Date()
  return processes.filter((process) => {
    const createdAt = new Date(process.createdAt)
    return createdAt.getMonth() === now.getMonth() && createdAt.getFullYear() === now.getFullYear()
  }).length
}

function downloadWorkbenchData(data: WorkbenchDataExport): void {
  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `compileflow-workbench-${Date.now()}.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

function useWorkspaceData() {
  const { t } = useTranslation()
  const [recentProjects, setRecentProjects] = useState<RecentProject[]>([])
  const [templates, setTemplates] = useState<ProcessTemplate[]>([])
  const [error, setError] = useState(false)
  const [stats, setStats] = useState<WorkspaceStats>({
    myProcesses: 0,
    templates: BUILT_IN_TEMPLATES.length,
    thisMonth: 0,
  })
  const requestGeneration = useRef(0)

  const loadRecentProcesses = useCallback(async () => {
    const generation = ++requestGeneration.current
    try {
      setError(false)
      const [recentProcesses, allProcesses, loadedTemplates] = await Promise.all([
        processStorage.getRecentProcesses(10),
        processStorage.listProcesses(),
        ensureBuiltInTemplates(),
      ])

      if (generation === requestGeneration.current) {
        setRecentProjects(recentProcesses.map(toRecentProject))
        setTemplates(loadedTemplates)
        setStats({
          myProcesses: allProcesses.length,
          templates: loadedTemplates.length,
          thisMonth: countProcessesCreatedThisMonth(allProcesses),
        })
      }
    } catch (error) {
      if (generation !== requestGeneration.current) return
      setError(true)
      logger.error('Failed to load workspace data', toError(error))
    }
  }, [])

  useEffect(() => {
    void loadRecentProcesses()
    return () => {
      requestGeneration.current += 1
    }
  }, [loadRecentProcesses])

  return {
    error,
    errorMessage: t('error.loadFailed'),
    recentProjects,
    reload: loadRecentProcesses,
    stats,
    templates,
  }
}

function useWorkspaceActions(navigate: NavigateFunction, reload: () => Promise<void>) {
  const { message } = App.useApp()
  const { t } = useTranslation()
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const handleNewBpmn = useCallback(() => {
    openNewDesigner(navigate, { modelType: 'bpmn' })
  }, [navigate])

  const handleNewTbbpm = useCallback(() => {
    openNewDesigner(navigate, { modelType: 'tbbpm' })
  }, [navigate])

  const handleBrowseExamples = useCallback(() => {
    void navigate(ROUTES.LEARN_EXAMPLES)
  }, [navigate])

  const handleOpenRecent = useCallback(
    (project: RecentProject) => {
      openDesignerFromWorkspaceProcess(navigate, {
        processId: project.id,
        modelType: project.type,
      })
    },
    [navigate]
  )

  const handleUseTemplate = useCallback(
    (template: ProcessTemplate) => {
      openDesignerFromTemplate(navigate, {
        modelType: template.type,
        templateId: template.id,
      })
    },
    [navigate]
  )

  const handleExportAll = useCallback(async () => {
    try {
      downloadWorkbenchData(await exportAllData())
      void message.success(t('workspace.exportSuccess'))
    } catch (error) {
      logger.error('Workspace export failed', toError(error))
      void message.error(t('workspace.exportFailed'))
    }
  }, [t])

  const handleImportData = useCallback(() => {
    fileInputRef.current?.click()
  }, [])

  const handleFileChange = useCallback(
    async (event: ChangeEvent<HTMLInputElement>) => {
      const file = event.target.files?.[0]
      if (!file) return

      try {
        const data: unknown = JSON.parse(await file.text())
        const result = await importData(data)
        void message.success(t('workspace.importSuccess', { count: result.success }))
        await reload()
      } catch (error) {
        logger.error('Workspace import failed', toError(error))
        void message.error(t('workspace.importFailed'))
      }

      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
    },
    [reload, t]
  )

  return {
    fileInputRef,
    handleBrowseExamples,
    handleExportAll,
    handleFileChange,
    handleImportData,
    handleNewBpmn,
    handleNewTbbpm,
    handleOpenRecent,
    handleUseTemplate,
  }
}

function useWorkspaceViewModel(
  stats: WorkspaceStats,
  actions: ReturnType<typeof useWorkspaceActions>
) {
  const { t } = useTranslation()

  const metrics = useMemo(
    () => [
      {
        key: 'myProcesses',
        label: t('workspace.myProcesses'),
        value: stats.myProcesses,
        accent: 'primary' as const,
      },
      {
        key: 'templates',
        label: t('workspace.availableTemplates'),
        value: stats.templates,
        accent: 'success' as const,
      },
      {
        key: 'thisMonth',
        label: t('workspace.thisMonth'),
        value: stats.thisMonth,
        accent: 'primary' as const,
      },
    ],
    [stats.myProcesses, stats.templates, stats.thisMonth, t]
  )

  const quickStartItems: QuickStartItem[] = useMemo(
    () => [
      {
        key: 'tbbpm',
        accent: 'operate',
        title: t('workspace.newTbbpmAction'),
        desc: t('workspace.newTbbpmDesc'),
        onClick: actions.handleNewTbbpm,
      },
      {
        key: 'bpmn',
        accent: 'build',
        title: t('workspace.newBpmnAction'),
        desc: t('workspace.newBpmnDesc'),
        onClick: actions.handleNewBpmn,
      },
      {
        key: 'examples',
        accent: 'learn',
        title: t('workspace.browseExamplesAction'),
        desc: t('workspace.browseExamplesDesc'),
        onClick: actions.handleBrowseExamples,
      },
    ],
    [actions.handleBrowseExamples, actions.handleNewBpmn, actions.handleNewTbbpm, t]
  )

  return {
    metrics,
    quickStartItems,
  }
}

function WorkspaceHero({
  onNewBpmn,
  onNewTbbpm,
}: {
  onNewBpmn: () => void
  onNewTbbpm: () => void
}) {
  const { t } = useTranslation()

  return (
    <HeroBanner
      variant="build"
      layout="simple"
      eyebrow={t('nav.build')}
      title={t('workspace.title')}
      subtitle={t('workspace.subtitle')}
      primaryAction={{
        labelKey: 'workspace.newTbbpm',
        label: t('workspace.newTbbpm'),
        onClick: onNewTbbpm,
        icon: <PlusOutlined />,
        type: 'primary',
      }}
      secondaryAction={{
        labelKey: 'workspace.newBpmn',
        label: t('workspace.newBpmn'),
        onClick: onNewBpmn,
        icon: <PlusOutlined />,
        type: 'secondary',
      }}
    />
  )
}

function QuickStartSection({ items }: { items: QuickStartItem[] }) {
  const { t } = useTranslation()

  return (
    <HubSection title={t('workspace.quickStart')}>
      <HubRowList>
        {items.map((item, index) => (
          <HubRow
            key={item.key}
            index={String(index + 1).padStart(2, '0')}
            accent={item.accent}
            title={item.title}
            description={item.desc}
            action={
              <>
                {t('common.getStarted')} <ArrowRightOutlined />
              </>
            }
            onClick={item.onClick}
            ariaLabel={item.title}
          />
        ))}
      </HubRowList>
    </HubSection>
  )
}

function WorkspaceSectionHeader({
  onExportAll,
  onImportData,
}: {
  onExportAll: () => void
  onImportData: () => void
}) {
  const { t } = useTranslation()

  return (
    <div className={styles.sectionHeader}>
      <h2 className={styles.sectionTitle}>{t('workspace.myWork')}</h2>
      <Space size="small" className={styles.sectionActions}>
        <Button
          type="text"
          size="small"
          icon={<DownloadOutlined />}
          onClick={onExportAll}
          className={styles.subtleAction}
        >
          {t('workspace.exportAll')}
        </Button>
        <Button
          type="text"
          size="small"
          icon={<UploadOutlined />}
          onClick={onImportData}
          className={styles.subtleAction}
        >
          {t('workspace.importData')}
        </Button>
      </Space>
    </div>
  )
}

function RecentProjectsPanel({
  projects,
  onOpenRecent,
}: {
  projects: RecentProject[]
  onOpenRecent: (project: RecentProject) => void
}) {
  const { t } = useTranslation()

  return (
    <ListPanel title={t('workspace.recentProjects')} icon={<HistoryOutlined />} tall interactive>
      {projects.length === 0 ? (
        <Empty
          className={styles.workspaceEmpty}
          description={t('workspace.noRecentProjects')}
          image={Empty.PRESENTED_IMAGE_SIMPLE}
        />
      ) : (
        <SemanticList>
          {projects.map((project) => (
            <SemanticListItem
              key={project.id}
              actions={
                <Button type="link" size="small" onClick={() => onOpenRecent(project)}>
                  {t('workspace.open')}
                </Button>
              }
            >
              <SemanticListMeta
                title={project.name}
                description={
                  <Space>
                    <span>{project.type}</span>
                    <span className={styles.dateText}>{formatDate(project.lastModified)}</span>
                  </Space>
                }
              />
            </SemanticListItem>
          ))}
        </SemanticList>
      )}
    </ListPanel>
  )
}

function TemplatesPanel({
  onUseTemplate,
  templates,
}: {
  onUseTemplate: (template: ProcessTemplate) => void
  templates: ProcessTemplate[]
}) {
  const { t } = useTranslation()

  const presentTemplate = (template: ProcessTemplate) => {
    const nameKey = `workspace.template.${template.id}.name`
    const descriptionKey = `workspace.template.${template.id}.description`
    const translatedName = t(nameKey)
    const translatedDescription = t(descriptionKey)
    return {
      description:
        translatedDescription === descriptionKey ? template.description : translatedDescription,
      name: translatedName === nameKey ? template.name : translatedName,
    }
  }

  return (
    <ListPanel title={t('workspace.quickTemplates')} icon={<RocketOutlined />} tall interactive>
      <SemanticList>
        {templates.map((template) => {
          const presentation = presentTemplate(template)
          return (
            <SemanticListItem
              key={template.id}
              onActivate={() => onUseTemplate(template)}
              activateLabel={`${t('workspace.use')} ${presentation.name} ${template.type}`}
              actions={
                <span className={styles.templateAction} aria-hidden="true">
                  {t('workspace.use')}
                  <ArrowRightOutlined />
                </span>
              }
            >
              <SemanticListMeta
                title={presentation.name}
                description={
                  <Space>
                    <span>{template.type}</span>
                    <span className={styles.dateText}>{presentation.description}</span>
                  </Space>
                }
              />
            </SemanticListItem>
          )
        })}
      </SemanticList>
    </ListPanel>
  )
}

function WorkspaceLists({
  onOpenRecent,
  onUseTemplate,
  recentProjects,
  templates,
}: {
  onOpenRecent: (project: RecentProject) => void
  onUseTemplate: (template: ProcessTemplate) => void
  recentProjects: RecentProject[]
  templates: ProcessTemplate[]
}) {
  return (
    <div className={styles.listSection}>
      <RecentProjectsPanel projects={recentProjects} onOpenRecent={onOpenRecent} />
      <TemplatesPanel templates={templates} onUseTemplate={onUseTemplate} />
    </div>
  )
}

function Workspace() {
  usePageTitle('pageTitle.build.workspace')
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { error, errorMessage, recentProjects, reload, stats, templates } = useWorkspaceData()
  const actions = useWorkspaceActions(navigate, reload)
  const { metrics, quickStartItems } = useWorkspaceViewModel(stats, actions)

  return (
    <HubSurface className={`${styles.workspaceContainer} stagger-1`}>
      <input
        ref={actions.fileInputRef}
        type="file"
        accept=".json"
        aria-label={t('workspace.importFileLabel')}
        style={{ display: 'none' }}
        onChange={actions.handleFileChange}
      />

      <WorkspaceHero onNewBpmn={actions.handleNewBpmn} onNewTbbpm={actions.handleNewTbbpm} />
      {error && (
        <Alert
          type="error"
          showIcon
          title={errorMessage}
          action={<Button onClick={() => void reload()}>{t('common.retry')}</Button>}
        />
      )}
      <HubMetrics>
        <MetricGrid metrics={metrics} columns={3} />
      </HubMetrics>
      <QuickStartSection items={quickStartItems} />
      <HubPanel>
        <WorkspaceSectionHeader
          onExportAll={actions.handleExportAll}
          onImportData={actions.handleImportData}
        />
        <WorkspaceLists
          recentProjects={recentProjects}
          templates={templates}
          onOpenRecent={actions.handleOpenRecent}
          onUseTemplate={actions.handleUseTemplate}
        />
      </HubPanel>
    </HubSurface>
  )
}

export default Workspace
