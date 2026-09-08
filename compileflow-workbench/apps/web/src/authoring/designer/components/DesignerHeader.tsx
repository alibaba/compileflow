import {
  ArrowLeftOutlined,
  BorderOutlined,
  BugOutlined,
  CheckOutlined,
  CheckSquareOutlined,
  CodeOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  EditOutlined,
  ExportOutlined,
  FileOutlined,
  HistoryOutlined,
  KeyOutlined,
  MoreOutlined,
  QuestionCircleOutlined,
  RedoOutlined,
  SaveOutlined,
  SearchOutlined,
  SettingOutlined,
  SnippetsOutlined,
  UndoOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { App, Button, Dropdown, Input, Space, Tag, Tooltip } from 'antd'
import type { TFunction } from 'i18next'
import { memo, useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'

import type { UnifiedProcessDefinition } from '../types'

import { useMediaQuery } from '@/shared/hooks/useMediaQuery'

interface DesignerHeaderProps {
  currentProcess: UnifiedProcessDefinition
  isModified: boolean
  isSaving: boolean
  canUndo: boolean
  canRedo: boolean
  onBack: () => void
  onSave: () => void
  onUndo: () => void
  onRedo: () => void
  onCopy?: () => void
  onPaste?: () => void
  onUpdateName: (name: string) => void
  onExportXml?: () => void
  onImportXml?: () => void
  onExportImage?: () => void
  onDuplicate?: () => void
  onDelete?: () => void
  onValidate?: () => void
  onDebug?: () => void
  onToggleGrid?: () => void
  showGridlines: boolean
  onSearch?: () => void
  onShowShortcuts?: () => void
  onShowVariables?: () => void
  onShowHelp?: () => void
  onShowXmlEditor?: () => void
  onShowLocalSnapshots?: () => void
  /** Shown when the flow is opened from the operate console (business key). */
  operateProcessCode?: string | null
}

type HeaderActionDefinition = (() => void) | undefined

interface ProcessNameEditorProps {
  currentProcess: UnifiedProcessDefinition
  isModified: boolean
  onUpdateName: (name: string) => void
}

interface ToolButtonProps {
  ariaLabel: string
  disabled?: boolean
  icon: React.ReactNode
  onClick?: HeaderActionDefinition
  pressed?: boolean
  title: string
}

interface HeaderLeftProps {
  currentProcess: UnifiedProcessDefinition
  isModified: boolean
  onBack: () => void
  onUpdateName: (name: string) => void
  operateProcessCode?: string | null
}

interface SaveButtonProps {
  isModified: boolean
  isSaving: boolean
  onSave: () => void
}

interface MoreMenuOptions {
  confirmDelete: () => void
  onDuplicate?: () => void
  onExportImage?: () => void
  onExportXml?: () => void
  onImportXml?: () => void
  onShowXmlEditor?: () => void
  operateProcessCode?: string | null
  t: TFunction
}

interface CompactMenuOptions extends MoreMenuOptions {
  canRedo: boolean
  canUndo: boolean
  currentProcess: UnifiedProcessDefinition
  onCopy?: () => void
  onDebug?: () => void
  onPaste?: () => void
  onRedo: () => void
  onSearch?: () => void
  onShowHelp?: () => void
  onShowShortcuts?: () => void
  onShowVariables?: () => void
  onShowLocalSnapshots?: () => void
  onToggleGrid?: () => void
  onUndo: () => void
  onValidate?: () => void
}

const HeaderToolButton = ({
  ariaLabel,
  disabled,
  icon,
  onClick,
  pressed,
  title,
}: ToolButtonProps) => (
  <Tooltip title={title}>
    <Button
      type="text"
      size="small"
      icon={icon}
      onClick={onClick}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-pressed={pressed}
    />
  </Tooltip>
)

function buildMoreActions({
  confirmDelete,
  onDuplicate,
  onExportImage,
  onExportXml,
  onImportXml,
  onShowXmlEditor,
  operateProcessCode,
  t,
}: MoreMenuOptions): NonNullable<MenuProps['items']> {
  const flowChildren: NonNullable<MenuProps['items']> = []

  if (!operateProcessCode) {
    flowChildren.push({
      key: 'duplicate',
      label: t('designer.header.menu.duplicate'),
      icon: <CopyOutlined />,
      onClick: onDuplicate,
    })
  }

  flowChildren.push({
    key: 'delete',
    label: t('designer.header.menu.deleteProcess'),
    icon: <DeleteOutlined />,
    danger: true,
    onClick: confirmDelete,
  })

  return [
    {
      key: 'export',
      type: 'group' as const,
      label: t('designer.header.menu.exportGroup'),
      children: [
        {
          key: 'export-xml',
          label: t('designer.header.menu.exportXml'),
          icon: <DownloadOutlined />,
          onClick: onExportXml,
        },
        {
          key: 'export-image',
          label: t('designer.header.menu.exportImage'),
          icon: <ExportOutlined />,
          onClick: onExportImage,
        },
        {
          key: 'import-xml',
          label: t('designer.header.menu.importXml'),
          icon: <UploadOutlined />,
          onClick: onImportXml,
        },
        {
          key: 'xml-editor',
          label: t('designer.header.menu.xmlEditor'),
          icon: <CodeOutlined />,
          onClick: onShowXmlEditor,
        },
      ],
    },
    { type: 'divider' as const },
    {
      key: 'flow',
      type: 'group' as const,
      label: t('designer.header.menu.flowGroup'),
      children: flowChildren,
    },
  ]
}

function buildCompactActions(options: CompactMenuOptions): NonNullable<MenuProps['items']> {
  const toolItems: NonNullable<MenuProps['items']> = [
    {
      key: 'toggle-grid',
      label: options.t('designer.header.toggleGrid'),
      icon: <BorderOutlined />,
      onClick: options.onToggleGrid,
    },
    {
      key: 'search',
      label: options.t('designer.header.searchNodes'),
      icon: <SearchOutlined />,
      onClick: options.onSearch,
    },
    {
      key: 'validate',
      label: options.t('designer.header.validate'),
      icon: <CheckSquareOutlined />,
      onClick: options.onValidate,
    },
    {
      key: 'debug',
      label: options.t('designer.header.debug'),
      icon: <BugOutlined />,
      onClick: options.onDebug,
    },
  ]

  if (options.currentProcess.type === 'TBBPM') {
    toolItems.push({
      key: 'variables',
      label: options.t('designer.header.variables'),
      icon: <SettingOutlined />,
      onClick: options.onShowVariables,
    })
  }

  toolItems.push(
    {
      key: 'shortcuts',
      label: options.t('designer.header.shortcuts'),
      icon: <KeyOutlined />,
      onClick: options.onShowShortcuts,
    },
    {
      key: 'help',
      label: options.t('designer.header.help'),
      icon: <QuestionCircleOutlined />,
      onClick: options.onShowHelp,
    }
  )

  if (!options.operateProcessCode && options.onShowLocalSnapshots) {
    toolItems.push({
      key: 'version-history',
      label: options.t('designer.localSnapshots.title'),
      icon: <HistoryOutlined />,
      onClick: options.onShowLocalSnapshots,
    })
  }

  return [
    {
      key: 'editing',
      label: options.t('designer.header.menu.editingGroup'),
      children: [
        {
          key: 'undo',
          label: options.t('designer.header.undo'),
          icon: <UndoOutlined />,
          disabled: !options.canUndo,
          onClick: options.onUndo,
        },
        {
          key: 'redo',
          label: options.t('designer.header.redo'),
          icon: <RedoOutlined />,
          disabled: !options.canRedo,
          onClick: options.onRedo,
        },
        {
          key: 'copy',
          label: options.t('designer.header.copy'),
          icon: <CopyOutlined />,
          disabled: !options.onCopy,
          onClick: options.onCopy,
        },
        {
          key: 'paste',
          label: options.t('designer.header.paste'),
          icon: <SnippetsOutlined />,
          disabled: !options.onPaste,
          onClick: options.onPaste,
        },
      ],
    },
    {
      key: 'tools',
      label: options.t('designer.header.menu.toolsGroup'),
      children: toolItems,
    },
    ...buildMoreActions(options),
  ]
}

function ProcessNameEditor({ currentProcess, isModified, onUpdateName }: ProcessNameEditorProps) {
  const { t } = useTranslation()
  const [isEditingName, setIsEditingName] = useState(false)
  const [tempName, setTempName] = useState('')

  const startEditingName = useCallback(() => {
    setTempName(currentProcess.name)
    setIsEditingName(true)
  }, [currentProcess.name])

  const handleNameSave = useCallback(() => {
    if (tempName.trim()) {
      onUpdateName(tempName.trim())
    }
    setIsEditingName(false)
  }, [onUpdateName, tempName])

  return (
    <>
      <h1 className="designer-sr-only">{currentProcess.name}</h1>
      <Tooltip title={isEditingName ? undefined : t('designer.header.editName')} placement="bottom">
        <div className="flow-name-editor">
          {isEditingName ? (
            <Input.Search
              value={tempName}
              onChange={(event) => setTempName(event.target.value)}
              onSearch={handleNameSave}
              onBlur={handleNameSave}
              onKeyDown={(event) => event.key === 'Escape' && setIsEditingName(false)}
              enterButton={<CheckOutlined aria-label={t('common.save')} />}
              aria-label={t('designer.header.editName')}
              size="small"
              style={{ width: 240 }}
              autoFocus
            />
          ) : (
            <button
              type="button"
              className="flow-name-display"
              onClick={startEditingName}
              aria-label={t('designer.header.editName')}
            >
              <span className="flow-name-text">{currentProcess.name}</span>
              <EditOutlined className="edit-icon" />
              {isModified && (
                <span className="modified-indicator" title={t('designer.header.unsavedChanges')}>
                  ●
                </span>
              )}
            </button>
          )}
        </div>
      </Tooltip>
    </>
  )
}

function ProcessIdentityTags({
  currentProcess,
  operateProcessCode,
}: Pick<HeaderLeftProps, 'currentProcess' | 'operateProcessCode'>) {
  const { t } = useTranslation()
  return (
    <Space size={4} className="header-type-tags">
      <Tag
        color={currentProcess.type === 'BPMN' ? 'blue' : 'purple'}
        icon={<FileOutlined />}
        className="header-type-tag"
      >
        {currentProcess.type}
      </Tag>
      {operateProcessCode && (
        <Tag className="header-type-tag header-process-code-tag" title={operateProcessCode}>
          {operateProcessCode}
        </Tag>
      )}
      <Tag className="header-type-tag">
        {t(operateProcessCode ? 'designer.status.draft' : 'designer.status.local')}
      </Tag>
    </Space>
  )
}

function HeaderLeft({
  currentProcess,
  isModified,
  onBack,
  onUpdateName,
  operateProcessCode,
}: HeaderLeftProps) {
  const { t } = useTranslation()
  const backTitle = operateProcessCode
    ? t('designer.header.backToOperate')
    : t('designer.header.backToWorkspace')

  return (
    <div className="header-left">
      <Tooltip title={backTitle}>
        <Button
          type="text"
          size="small"
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
          className="back-button"
          aria-label={backTitle}
        >
          {operateProcessCode ? t('designer.header.operate') : t('designer.header.workspace')}
        </Button>
      </Tooltip>

      <div className="tool-group-divider" />
      <ProcessNameEditor
        currentProcess={currentProcess}
        isModified={isModified}
        onUpdateName={onUpdateName}
      />
      <ProcessIdentityTags
        currentProcess={currentProcess}
        operateProcessCode={operateProcessCode}
      />
    </div>
  )
}

function HistoryControls({
  canRedo,
  canUndo,
  onRedo,
  onUndo,
}: Pick<DesignerHeaderProps, 'canRedo' | 'canUndo' | 'onRedo' | 'onUndo'>) {
  const { t } = useTranslation()
  return (
    <div className="toolbar-strip">
      <HeaderToolButton
        title={t('designer.header.undo')}
        ariaLabel={t('designer.header.undo')}
        icon={<UndoOutlined />}
        disabled={!canUndo}
        onClick={onUndo}
      />
      <HeaderToolButton
        title={t('designer.header.redo')}
        ariaLabel={t('designer.header.redo')}
        icon={<RedoOutlined />}
        disabled={!canRedo}
        onClick={onRedo}
      />
    </div>
  )
}

function ClipboardControls({ onCopy, onPaste }: Pick<DesignerHeaderProps, 'onCopy' | 'onPaste'>) {
  const { t } = useTranslation()
  return (
    <div className="toolbar-strip">
      <HeaderToolButton
        title={t('designer.header.copy')}
        ariaLabel={t('designer.header.copy')}
        icon={<CopyOutlined />}
        disabled={!onCopy}
        onClick={onCopy}
      />
      <HeaderToolButton
        title={t('designer.header.paste')}
        ariaLabel={t('designer.header.paste')}
        icon={<SnippetsOutlined />}
        disabled={!onPaste}
        onClick={onPaste}
      />
    </div>
  )
}

function ViewToolControls({
  onDebug,
  onSearch,
  onToggleGrid,
  onValidate,
  showGridlines,
}: Pick<
  DesignerHeaderProps,
  'onDebug' | 'onSearch' | 'onToggleGrid' | 'onValidate' | 'showGridlines'
>) {
  const { t } = useTranslation()
  return (
    <div className="toolbar-strip">
      <HeaderToolButton
        title={t('designer.header.toggleGrid')}
        ariaLabel={t('designer.header.toggleGrid')}
        icon={<BorderOutlined />}
        onClick={onToggleGrid}
        pressed={showGridlines}
      />
      <HeaderToolButton
        title={t('designer.header.searchNodes')}
        ariaLabel={t('designer.header.searchNodes')}
        icon={<SearchOutlined />}
        onClick={onSearch}
      />
      <HeaderToolButton
        title={t('designer.header.validate')}
        ariaLabel={t('designer.header.validate')}
        icon={<CheckSquareOutlined />}
        onClick={onValidate}
      />
      <HeaderToolButton
        title={t('designer.header.debug')}
        ariaLabel={t('designer.header.debug')}
        icon={<BugOutlined />}
        onClick={onDebug}
      />
    </div>
  )
}

function InfoControls({
  onShowHelp,
  onShowShortcuts,
  onShowVariables,
  onShowLocalSnapshots,
  operateProcessCode,
}: Pick<
  DesignerHeaderProps,
  | 'onShowHelp'
  | 'onShowShortcuts'
  | 'onShowVariables'
  | 'onShowLocalSnapshots'
  | 'operateProcessCode'
>) {
  const { t } = useTranslation()
  return (
    <div className="toolbar-strip">
      <HeaderToolButton
        title={t('designer.header.variables')}
        ariaLabel={t('designer.header.variables')}
        icon={<SettingOutlined />}
        onClick={onShowVariables}
      />
      <HeaderToolButton
        title={t('designer.header.shortcuts')}
        ariaLabel={t('designer.header.shortcuts')}
        icon={<KeyOutlined />}
        onClick={onShowShortcuts}
      />
      <HeaderToolButton
        title={t('designer.header.help')}
        ariaLabel={t('designer.header.help')}
        icon={<QuestionCircleOutlined />}
        onClick={onShowHelp}
      />
      {!operateProcessCode && onShowLocalSnapshots && (
        <HeaderToolButton
          title={t('designer.localSnapshots.title')}
          ariaLabel={t('designer.localSnapshots.title')}
          icon={<HistoryOutlined />}
          onClick={onShowLocalSnapshots}
        />
      )}
    </div>
  )
}

function SaveButton({ isModified, isSaving, onSave }: SaveButtonProps) {
  const { t } = useTranslation()
  const label = isSaving
    ? t('designer.header.saving')
    : isModified
      ? t('designer.header.saveDirty')
      : t('designer.header.saved')

  return (
    <Button
      type="primary"
      size="small"
      icon={<SaveOutlined />}
      loading={isSaving}
      onClick={onSave}
      className={`header-save-btn${isModified && !isSaving ? ' save-btn-modified' : ''}`}
      aria-label={label}
    >
      {label}
    </Button>
  )
}

function HeaderDivider() {
  return <div className="tool-group-divider" />
}

function HeaderRight({
  confirmDelete,
  props,
}: {
  confirmDelete: () => void
  props: DesignerHeaderProps
}) {
  const { t } = useTranslation()
  const compact = useMediaQuery('(max-width: 768px)')
  const moreActions = buildMoreActions({
    confirmDelete,
    onDuplicate: props.onDuplicate,
    onExportImage: props.onExportImage,
    onExportXml: props.onExportXml,
    onImportXml: props.onImportXml,
    onShowXmlEditor: props.onShowXmlEditor,
    operateProcessCode: props.operateProcessCode,
    t,
  })
  const compactActions = buildCompactActions({
    canRedo: props.canRedo,
    canUndo: props.canUndo,
    confirmDelete,
    currentProcess: props.currentProcess,
    onCopy: props.onCopy,
    onDebug: props.onDebug,
    onDuplicate: props.onDuplicate,
    onExportImage: props.onExportImage,
    onExportXml: props.onExportXml,
    onImportXml: props.onImportXml,
    onPaste: props.onPaste,
    onRedo: props.onRedo,
    onSearch: props.onSearch,
    onShowHelp: props.onShowHelp,
    onShowShortcuts: props.onShowShortcuts,
    onShowVariables: props.onShowVariables,
    onShowLocalSnapshots: props.onShowLocalSnapshots,
    onShowXmlEditor: props.onShowXmlEditor,
    onToggleGrid: props.onToggleGrid,
    onUndo: props.onUndo,
    onValidate: props.onValidate,
    operateProcessCode: props.operateProcessCode,
    t,
  })

  return (
    <div className="header-right">
      {!compact && (
        <>
          <HistoryControls
            canUndo={props.canUndo}
            canRedo={props.canRedo}
            onUndo={props.onUndo}
            onRedo={props.onRedo}
          />
          <HeaderDivider />
          <ClipboardControls onCopy={props.onCopy} onPaste={props.onPaste} />
          <HeaderDivider />
          <ViewToolControls
            onToggleGrid={props.onToggleGrid}
            onSearch={props.onSearch}
            onValidate={props.onValidate}
            onDebug={props.onDebug}
            showGridlines={props.showGridlines}
          />
          <HeaderDivider />
          <InfoControls
            onShowVariables={props.onShowVariables}
            onShowShortcuts={props.onShowShortcuts}
            onShowHelp={props.onShowHelp}
            onShowLocalSnapshots={props.onShowLocalSnapshots}
            operateProcessCode={props.operateProcessCode}
          />
          <HeaderDivider />
        </>
      )}
      <SaveButton isModified={props.isModified} isSaving={props.isSaving} onSave={props.onSave} />
      <Dropdown
        menu={{ items: compact ? compactActions : moreActions }}
        trigger={['click']}
        placement="bottomRight"
      >
        <Button
          type="text"
          size="small"
          icon={<MoreOutlined />}
          className="header-more-btn"
          aria-label={t('designer.header.menu.moreActions')}
        />
      </Dropdown>
    </div>
  )
}

const DesignerHeader = memo(function DesignerHeader(props: DesignerHeaderProps) {
  const { modal } = App.useApp()
  const { t } = useTranslation()
  const confirmDelete = useCallback(() => {
    modal.confirm({
      title: t('designer.header.deleteConfirmTitle'),
      content: t('designer.header.deleteConfirmContent', { name: props.currentProcess.name }),
      okText: t('common.delete'),
      okType: 'danger',
      cancelText: t('common.cancel'),
      onOk: props.onDelete,
    })
  }, [modal, props.currentProcess.name, props.onDelete, t])

  return (
    <div className="designer-header">
      <HeaderLeft
        currentProcess={props.currentProcess}
        isModified={props.isModified}
        onBack={props.onBack}
        onUpdateName={props.onUpdateName}
        operateProcessCode={props.operateProcessCode}
      />
      <HeaderRight confirmDelete={confirmDelete} props={props} />
    </div>
  )
})

export default DesignerHeader
