import { SearchOutlined } from '@ant-design/icons'
import { Input, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'

import {
  buildShortcutItems,
  getCategoryColor,
  getCategoryLabel,
  SHORTCUT_CATEGORIES,
  type ShortcutCategory,
  type ShortcutItem,
} from './keyboardShortcutsData'

import { LocalizedModal as Modal } from '@/shared/components/LocalizedModal'
import { useEscapeToClose } from '@/shared/hooks/useEscapeToClose'

import './DesignerSurfaces.css'
import './KeyboardShortcutsModal.css'

interface KeyboardShortcutsModalProps {
  open: boolean
  onClose: () => void
}

function KeyboardShortcutsModal({ open, onClose }: KeyboardShortcutsModalProps) {
  const { t } = useTranslation()
  const [searchText, setSearchText] = useState('')
  useEscapeToClose(open, onClose)

  const shortcuts = useMemo(() => buildShortcutItems(), [])

  const filteredShortcuts = useMemo(() => {
    if (!searchText) return shortcuts

    const lowerSearch = searchText.toLowerCase()
    return shortcuts.filter((item) => {
      const categoryLabel = getCategoryLabel(t, item.category).toLowerCase()
      const description = t(item.descriptionKey).toLowerCase()
      return (
        item.shortcut.toLowerCase().includes(lowerSearch) ||
        description.includes(lowerSearch) ||
        categoryLabel.includes(lowerSearch)
      )
    })
  }, [searchText, shortcuts, t])

  const columns: ColumnsType<ShortcutItem> = useMemo(
    () => [
      {
        title: t('designer.shortcutsModal.col.category'),
        dataIndex: 'category',
        key: 'category',
        width: 120,
        filters: SHORTCUT_CATEGORIES.map((cat) => ({
          text: getCategoryLabel(t, cat),
          value: cat,
        })),
        onFilter: (value, record) => record.category === value,
        render: (category: ShortcutCategory) => (
          <Tag color={getCategoryColor(category)}>{getCategoryLabel(t, category)}</Tag>
        ),
      },
      {
        title: t('designer.shortcutsModal.col.shortcut'),
        dataIndex: 'shortcut',
        key: 'shortcut',
        width: 220,
        render: (shortcut: string) => (
          <Space size={4}>
            {shortcut.split(' / ').map((key, index) => (
              <kbd key={index} className="kbd-shortcut">
                {key}
              </kbd>
            ))}
          </Space>
        ),
      },
      {
        title: t('designer.shortcutsModal.col.description'),
        dataIndex: 'descriptionKey',
        key: 'description',
        ellipsis: true,
        render: (descriptionKey: string) => t(descriptionKey),
      },
    ],
    [t]
  )

  return (
    <Modal
      title={t('designer.shortcutsModal.title')}
      open={open}
      onCancel={onClose}
      footer={null}
      width={800}
      className="designer-surface-modal"
      destroyOnHidden
    >
      <Space vertical size="middle" style={{ width: '100%' }}>
        <Input
          placeholder={t('designer.shortcutsModal.searchPlaceholder')}
          aria-label={t('designer.shortcutsModal.searchPlaceholder')}
          prefix={<SearchOutlined aria-hidden="true" />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          allowClear
        />

        <Table
          dataSource={filteredShortcuts}
          columns={columns}
          rowKey="key"
          size="small"
          pagination={false}
          scroll={{ y: 500 }}
          bordered
        />

        <div className="shortcuts-tip">
          <p className="shortcuts-tip-title">{t('designer.shortcutsModal.tipTitle')}</p>
          <ul style={{ marginLeft: 20, marginBottom: 0 }}>
            <li>{t('designer.shortcutsModal.tipMac')}</li>
            <li>{t('designer.shortcutsModal.tipConflict')}</li>
            <li>{t('designer.shortcutsModal.tipHelp')}</li>
          </ul>
        </div>
      </Space>
    </Modal>
  )
}

export default KeyboardShortcutsModal
