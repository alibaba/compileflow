import {
  CheckCircleOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  MoreOutlined,
  PlusOutlined,
  RocketOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import type { MenuProps, TableColumnsType } from 'antd'
import { App, Button, Dropdown, Input, Select, Space, Table, Tooltip, Upload } from 'antd'
import type { TFunction } from 'i18next'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'

import {
  createProcess,
  deleteProcess,
  duplicateProcess,
  getProcesses,
  importProcessXml,
  publishProcess,
} from '@/shared/api/processes'
import { LoadErrorAlert } from '@/shared/components/LoadErrorAlert'
import type { ActiveFilter } from '@/shared/components/page'
import { DataPageShell, FilterBar, modelTypeToTagKind, SemanticTag } from '@/shared/components/page'
import { isOperateMockMode } from '@/shared/config/buildConfig'
import { createOperateDeployWizardPath } from '@/shared/constants'
import type { ProcessSummary, ProcessModelType } from '@/shared/contracts'
import { toError } from '@/shared/errors'
import { useDebounce } from '@/shared/hooks/useDebounce'
import { type FilterParsers, useFilterState } from '@/shared/hooks/useFilterState'
import { usePageTitle } from '@/shared/hooks/usePageTitle'
import { usePaginationItemRender } from '@/shared/hooks/usePaginationItemRender'
import { formatDateTime } from '@/shared/i18n/dateTime'
import { createUniqueId } from '@/shared/identifiers'
import { createLogger } from '@/shared/logging/logger'
import { DEFAULT_BPMN_WITH_EVENTS_XML } from '@/shared/processes/bpmnTemplates'
import { withProcessXmlIdentity } from '@/shared/processes/processXmlIdentity'
import { DEFAULT_TBBPM_WITH_NODES_XML } from '@/shared/processes/tbbpmTemplates'
import {
  getOperateProcessDesignerCapability,
  openDesignerFromOperateProcessCode,
  openNewDesigner,
} from '@/shared/services/designerNavigation'

const { Option } = Select
const { Search } = Input
const logger = createLogger('ProcessManagement')
const PAGE_SIZE = 10

type Navigate = ReturnType<typeof useNavigate>

interface ProcessFilterState {
  keyword: string
  type: ProcessModelType | ''
  page: number
}

const PROCESS_FILTER_DEFAULTS: ProcessFilterState = {
  keyword: '',
  type: '',
  page: 1,
}

const PROCESS_FILTER_PARSERS: FilterParsers<ProcessFilterState> = {
  type: (raw) => (raw === 'BPMN' || raw === 'TBBPM' ? raw : ''),
  page: (raw) => {
    const page = Number(raw)
    return Number.isInteger(page) && page > 0 ? page : 1
  },
}

interface ProcessManagementState {
  activeFilters: ActiveFilter[]
  clearFilters: () => void
  processType: ProcessModelType | ''
  processes: ProcessSummary[]
  handleCreateProcess: (type: ProcessModelType) => Promise<void>
  handleDelete: (code: string, revision: number) => Promise<void>
  handleDuplicate: (code: string, name: string) => Promise<void>
  handleImportXml: (file: File) => Promise<boolean>
  handlePublish: (code: string, revision: number) => Promise<void>
  keywordInput: string
  loadError: boolean
  loading: boolean
  page: number
  removeFilter: (key: string) => void
  reload: () => void
  setProcessType: (value: ProcessModelType | '') => void
  setKeywordInput: (value: string) => void
  setPage: (page: number) => void
  total: number
}

function createActiveFilters(keyword: string, processType: ProcessModelType | ''): ActiveFilter[] {
  const filters: ActiveFilter[] = []
  if (keyword) filters.push({ key: 'keyword', label: keyword })
  if (processType) filters.push({ key: 'type', label: processType })
  return filters
}

function useProcessFilters() {
  const [filters, , clearAll, updateFilters] = useFilterState(
    PROCESS_FILTER_DEFAULTS,
    PROCESS_FILTER_PARSERS
  )
  const processType = filters.type
  const keywordInput = filters.keyword
  const keyword = useDebounce(keywordInput, 300)
  const page = filters.page

  const setKeywordInput = useCallback(
    (value: string) => updateFilters({ keyword: value, page: 1 }),
    [updateFilters]
  )
  const setProcessType = useCallback(
    (value: ProcessModelType | '') => updateFilters({ type: value, page: 1 }),
    [updateFilters]
  )
  const setPage = useCallback((value: number) => updateFilters({ page: value }), [updateFilters])

  const removeFilter = useCallback(
    (key: string) => {
      if (key === 'keyword') updateFilters({ keyword: '', page: 1 })
      if (key === 'type') updateFilters({ type: '', page: 1 })
    },
    [updateFilters]
  )
  const clearFilters = useCallback(() => clearAll(), [clearAll])
  const activeFilters = useMemo(
    () => createActiveFilters(keyword, processType),
    [keyword, processType]
  )

  return {
    activeFilters,
    clearFilters,
    processType,
    keyword,
    keywordInput,
    page,
    removeFilter,
    setKeywordInput,
    setPage,
    setProcessType,
  }
}

function useProcessListData(filters: ReturnType<typeof useProcessFilters>) {
  const [processes, setProcesses] = useState<ProcessSummary[]>([])
  const [loading, setLoading] = useState(false)
  const [loadError, setLoadError] = useState(false)
  const [total, setTotal] = useState(0)
  const loadGeneration = useRef(0)
  const activeReload = useRef<(() => Promise<void>) | null>(null)

  const loadProcesses = useCallback(async () => {
    const generation = ++loadGeneration.current
    setLoading(true)
    setLoadError(false)
    try {
      const response = await getProcesses({
        page: filters.page,
        pageSize: PAGE_SIZE,
        keyword: filters.keyword || undefined,
        type: filters.processType || undefined,
      })
      if (generation === loadGeneration.current) {
        setProcesses(response.data)
        setTotal(response.total)
      }
    } catch (error) {
      if (generation !== loadGeneration.current) return
      logger.error('Failed to load processes', toError(error))
      setLoadError(true)
    } finally {
      if (generation === loadGeneration.current) setLoading(false)
    }
  }, [filters.processType, filters.keyword, filters.page])

  useEffect(() => {
    activeReload.current = loadProcesses
    setProcesses([])
    setTotal(0)
    void loadProcesses()
    return () => {
      activeReload.current = null
      loadGeneration.current += 1
    }
  }, [loadProcesses])

  return { processes, loading, loadError, total, loadProcesses, activeReload }
}

export function useProcessManagementData(t: TFunction, navigate: Navigate): ProcessManagementState {
  const { message } = App.useApp()
  const filters = useProcessFilters()
  const { processes, loading, loadError, total, loadProcesses, activeReload } =
    useProcessListData(filters)
  const publicationIntents = useRef(new Map<string, string>())

  const handleDelete = useCallback(
    async (code: string, revision: number) => {
      try {
        await deleteProcess(code, revision)
        if (!activeReload.current) return
        message.success(t('process.deleteSuccess'))
        void activeReload.current()
      } catch (error) {
        if (!activeReload.current) return
        logger.error('Failed to delete process', toError(error), { code })
        message.error(t('process.deleteError'))
      }
    },
    [message, t]
  )

  const handleDuplicate = useCallback(
    async (code: string, name: string) => {
      try {
        await duplicateProcess(
          code,
          `${code}_copy_${Date.now()}`,
          t('process.duplicateName', { name })
        )
        if (!activeReload.current) return
        message.success(t('process.duplicateSuccess'))
        void activeReload.current()
      } catch (error) {
        if (!activeReload.current) return
        logger.error('Failed to duplicate process', toError(error), { code })
        message.error(t('process.duplicateError'))
      }
    },
    [message, t]
  )

  const handleCreateProcess = useCallback(
    async (type: ProcessModelType) => {
      if (isOperateMockMode()) {
        openNewDesigner(navigate, { modelType: type.toLowerCase() })
        return
      }

      const code = `process-${Date.now()}`
      try {
        const name = t('process.newProcessDefaultName')
        await createProcess({
          code,
          name,
          type,
          xml: withProcessXmlIdentity(
            type === 'TBBPM' ? DEFAULT_TBBPM_WITH_NODES_XML : DEFAULT_BPMN_WITH_EVENTS_XML,
            type,
            code,
            name
          ),
          description: '',
        })
        if (!activeReload.current) return
        openDesignerFromOperateProcessCode(navigate, {
          processCode: code,
          modelType: type.toLowerCase(),
        })
      } catch (error) {
        if (!activeReload.current) return
        logger.error('Failed to create process', toError(error), { code, type })
        message.error(t('process.createError'))
      }
    },
    [navigate, t]
  )

  const handlePublish = useCallback(
    async (code: string, revision: number) => {
      const intent = `${code}\u0000${revision}`
      const idempotencyKey = publicationIntents.current.get(intent) ?? createUniqueId()
      publicationIntents.current.set(intent, idempotencyKey)
      try {
        await publishProcess(code, revision, idempotencyKey)
        publicationIntents.current.delete(intent)
        if (!activeReload.current) return
        message.success(t('process.publishSuccess'))
        void activeReload.current()
      } catch (error) {
        if (!activeReload.current) return
        logger.error('Failed to publish process', toError(error), { code })
        message.error(t('process.publishError'))
      }
    },
    [message, t]
  )

  const handleImportXml = useCallback(
    async (file: File) => {
      try {
        await importProcessXml(file)
        if (!activeReload.current) return false
        message.success(t('process.importSuccess'))
        void activeReload.current()
      } catch (error) {
        if (!activeReload.current) return false
        logger.error('Failed to import process XML', toError(error), { fileName: file.name })
        message.error(t('process.importError'))
      }
      return false
    },
    [message, t]
  )

  return {
    activeFilters: filters.activeFilters,
    clearFilters: filters.clearFilters,
    processType: filters.processType,
    processes,
    handleCreateProcess,
    handleDelete,
    handleDuplicate,
    handleImportXml,
    handlePublish,
    keywordInput: filters.keywordInput,
    loadError,
    loading,
    page: filters.page,
    removeFilter: filters.removeFilter,
    reload: () => void loadProcesses(),
    setProcessType: filters.setProcessType,
    setKeywordInput: filters.setKeywordInput,
    setPage: filters.setPage,
    total,
  }
}

function ProcessPageActions({
  onCreate,
  onImport,
  t,
}: {
  onCreate: (type: ProcessModelType) => Promise<void>
  onImport: (file: File) => Promise<boolean>
  t: TFunction
}) {
  const createMenuItems: MenuProps['items'] = [
    {
      key: 'TBBPM',
      label: t('workspace.newTbbpmAction'),
      onClick: () => {
        void onCreate('TBBPM')
      },
    },
    {
      key: 'BPMN',
      label: t('workspace.newBpmnAction'),
      onClick: () => {
        void onCreate('BPMN')
      },
    },
  ]

  return (
    <Space>
      <Upload
        accept=".xml,.bpmn"
        showUploadList={false}
        beforeUpload={(file) => {
          void onImport(file)
          return false
        }}
      >
        <Button icon={<UploadOutlined />}>{t('process.import')}</Button>
      </Upload>
      <Dropdown menu={{ items: createMenuItems }} trigger={['click']}>
        <Button type="primary" icon={<PlusOutlined />}>
          {t('process.create')}
        </Button>
      </Dropdown>
    </Space>
  )
}

function ProcessFilters({ state, t }: { state: ProcessManagementState; t: TFunction }) {
  return (
    <FilterBar
      activeFilters={state.activeFilters}
      onRemoveFilter={state.removeFilter}
      onClearAll={state.clearFilters}
      activeLabel={t('filters.active')}
      clearLabel={t('filters.clear')}
    >
      <Search
        placeholder={t('process.searchPlaceholder')}
        aria-label={t('process.searchPlaceholder')}
        value={state.keywordInput}
        enterButton={<SearchOutlined aria-label={t('common.search')} />}
        onSearch={state.setKeywordInput}
        onChange={(event) => state.setKeywordInput(event.target.value)}
        style={{ width: 200 }}
        prefix={<SearchOutlined aria-hidden="true" />}
        allowClear
      />
      <Select<ProcessModelType>
        placeholder={t('process.allTypes')}
        aria-label={t('process.allTypes')}
        style={{ width: 120 }}
        allowClear
        value={state.processType || undefined}
        onChange={(value) => state.setProcessType(value ?? '')}
      >
        <Option value="BPMN">BPMN</Option>
        <Option value="TBBPM">TBBPM</Option>
      </Select>
    </FilterBar>
  )
}

function ProcessRowActions({
  process,
  navigate,
  onDelete,
  onDuplicate,
  onPublish,
  t,
}: {
  process: ProcessSummary
  navigate: Navigate
  onDelete: (code: string, revision: number) => Promise<void>
  onDuplicate: (code: string, name: string) => Promise<void>
  onPublish: (code: string, revision: number) => Promise<void>
  t: TFunction
}) {
  const { modal } = App.useApp()
  const designerCapability = getOperateProcessDesignerCapability(process.code, process.type)
  const moreItems: MenuProps['items'] = [
    {
      key: 'publish',
      icon: <CheckCircleOutlined />,
      label: t('process.publish'),
      onClick: () => {
        void onPublish(process.code, process.revision)
      },
    },
    {
      key: 'duplicate',
      icon: <CopyOutlined />,
      label: t('common.duplicate'),
      onClick: () => {
        void onDuplicate(process.code, process.name)
      },
    },
    {
      key: 'delete',
      icon: <DeleteOutlined />,
      label: t('common.delete'),
      danger: true,
      onClick: () => {
        modal.confirm({
          title: t('process.deleteConfirm'),
          okText: t('common.yes'),
          cancelText: t('common.no'),
          okButtonProps: { danger: true },
          onOk: () => onDelete(process.code, process.revision),
        })
      },
    },
  ]

  return (
    <Space size="small">
      <Tooltip title={designerCapability.reason ?? t('process.editInDesigner')}>
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          disabled={!designerCapability.enabled}
          onClick={() =>
            openDesignerFromOperateProcessCode(navigate, {
              processCode: process.code,
              modelType: process.type,
            })
          }
        >
          {t('common.edit')}
        </Button>
      </Tooltip>
      <Button
        type="link"
        size="small"
        icon={<RocketOutlined />}
        onClick={() => navigate(createOperateDeployWizardPath({ processCode: process.code }))}
      >
        {t('deployment.deploy')}
      </Button>
      <Dropdown menu={{ items: moreItems }} trigger={['click']}>
        <Button
          type="link"
          size="small"
          icon={<MoreOutlined />}
          aria-label={`${t('common.more')}: ${process.name}`}
        />
      </Dropdown>
    </Space>
  )
}

function createProcessColumns({
  navigate,
  onDelete,
  onDuplicate,
  onPublish,
  t,
}: {
  navigate: Navigate
  onDelete: (code: string, revision: number) => Promise<void>
  onDuplicate: (code: string, name: string) => Promise<void>
  onPublish: (code: string, revision: number) => Promise<void>
  t: TFunction
}): TableColumnsType<ProcessSummary> {
  return [
    { title: t('process.code'), dataIndex: 'code', key: 'code', width: 150 },
    {
      title: t('process.name'),
      dataIndex: 'name',
      key: 'name',
      width: 200,
      responsive: ['sm'],
    },
    {
      title: t('process.type'),
      dataIndex: 'type',
      key: 'type',
      width: 100,
      responsive: ['md'],
      render: (type: string) => <SemanticTag kind={modelTypeToTagKind(type)}>{type}</SemanticTag>,
    },
    {
      title: t('process.createdBy'),
      dataIndex: 'createdBy',
      key: 'createdBy',
      width: 120,
      responsive: ['lg'],
    },
    {
      title: t('process.updatedAt'),
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 180,
      responsive: ['lg'],
      render: formatDateTime,
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_: unknown, process: ProcessSummary) => (
        <ProcessRowActions
          process={process}
          navigate={navigate}
          onDelete={onDelete}
          onDuplicate={onDuplicate}
          onPublish={onPublish}
          t={t}
        />
      ),
    },
  ]
}

function ProcessManagement() {
  usePageTitle('pageTitle.operate.processes')
  const navigate = useNavigate()
  const { t } = useTranslation()
  const paginationItemRender = usePaginationItemRender()
  const state = useProcessManagementData(t, navigate)
  const columns = useMemo(
    () =>
      createProcessColumns({
        navigate,
        onDelete: state.handleDelete,
        onDuplicate: state.handleDuplicate,
        onPublish: state.handlePublish,
        t,
      }),
    [navigate, state.handleDelete, state.handleDuplicate, state.handlePublish, t]
  )

  return (
    <DataPageShell
      title={t('process.management')}
      accent="operate"
      eyebrow={t('nav.operate')}
      subtitle={t('process.managementDesc')}
      actions={
        <ProcessPageActions
          onCreate={state.handleCreateProcess}
          onImport={state.handleImportXml}
          t={t}
        />
      }
      filters={<ProcessFilters state={state} t={t} />}
    >
      {state.loadError && <LoadErrorAlert onRetry={state.reload} />}
      <Table
        dataSource={state.processes}
        columns={columns}
        rowKey="code"
        loading={state.loading}
        pagination={{
          current: state.page,
          pageSize: PAGE_SIZE,
          total: state.total,
          itemRender: paginationItemRender,
          onChange: state.setPage,
          showSizeChanger: false,
          showTotal: (totalCount) => t('common.totalItems', { total: totalCount }),
        }}
        scroll={{ x: 'max-content' }}
      />
    </DataPageShell>
  )
}

export default ProcessManagement
