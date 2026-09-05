import {
  BugOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  ScissorOutlined,
  SelectOutlined,
  SnippetsOutlined,
} from '@ant-design/icons'
import type { MenuProps } from 'antd'
import { Menu } from 'antd'
import { useEffect, useMemo } from 'react'
import { useTranslation } from 'react-i18next'

interface ContextMenuPosition {
  x: number
  y: number
}

interface ContextMenuProps {
  visible: boolean
  position: ContextMenuPosition | null
  menuType: 'node' | 'edge' | 'canvas' | null
  onClose: () => void
  onEdit?: () => void
  onCopy?: () => void
  onDelete?: () => void
  onBreakpoint?: () => void
  onPaste?: () => void
  onSelectAll?: () => void
}

function ContextMenu({
  visible,
  position,
  menuType,
  onClose,
  onEdit,
  onCopy,
  onDelete,
  onBreakpoint,
  onPaste,
  onSelectAll,
}: ContextMenuProps) {
  const { t } = useTranslation()

  useEffect(() => {
    const handleClick = () => {
      if (visible) {
        onClose()
      }
    }

    document.addEventListener('click', handleClick)
    return () => document.removeEventListener('click', handleClick)
  }, [visible, onClose])

  if (!visible || !position) {
    return null
  }

  const nodeMenuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'edit',
        label: t('designer.contextMenu.editProps'),
        icon: <EditOutlined />,
        onClick: () => {
          onEdit?.()
          onClose()
        },
      },
      {
        key: 'copy',
        label: t('designer.contextMenu.copy'),
        icon: <CopyOutlined />,
        onClick: () => {
          onCopy?.()
          onClose()
        },
      },
      {
        key: 'delete',
        label: t('designer.contextMenu.delete'),
        icon: <DeleteOutlined />,
        danger: true,
        onClick: () => {
          onDelete?.()
          onClose()
        },
      },
      {
        type: 'divider',
      },
      {
        key: 'breakpoint',
        label: t('designer.contextMenu.breakpoint'),
        icon: <BugOutlined />,
        onClick: () => {
          onBreakpoint?.()
          onClose()
        },
      },
    ],
    [onBreakpoint, onClose, onCopy, onDelete, onEdit, t]
  )

  const edgeMenuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'edit',
        label: t('designer.contextMenu.editCondition'),
        icon: <EditOutlined />,
        onClick: () => {
          onEdit?.()
          onClose()
        },
      },
      {
        key: 'delete',
        label: t('designer.contextMenu.deleteEdge'),
        icon: <ScissorOutlined />,
        danger: true,
        onClick: () => {
          onDelete?.()
          onClose()
        },
      },
    ],
    [onClose, onDelete, onEdit, t]
  )

  const canvasMenuItems: MenuProps['items'] = useMemo(
    () => [
      {
        key: 'paste',
        label: t('designer.contextMenu.paste'),
        icon: <SnippetsOutlined />,
        onClick: () => {
          onPaste?.()
          onClose()
        },
      },
      {
        key: 'selectAll',
        label: t('designer.contextMenu.selectAll'),
        icon: <SelectOutlined />,
        onClick: () => {
          onSelectAll?.()
          onClose()
        },
      },
    ],
    [onClose, onPaste, onSelectAll, t]
  )

  let items: MenuProps['items'] = []
  if (menuType === 'node') {
    items = nodeMenuItems
  } else if (menuType === 'edge') {
    items = edgeMenuItems
  } else if (menuType === 'canvas') {
    items = canvasMenuItems
  }

  return (
    <div
      style={{
        position: 'fixed',
        left: position.x,
        top: position.y,
        zIndex: 10000,
        boxShadow:
          '0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05)',
      }}
    >
      <Menu items={items} style={{ border: '1px solid var(--color-border-light)' }} />
    </div>
  )
}

export default ContextMenu
