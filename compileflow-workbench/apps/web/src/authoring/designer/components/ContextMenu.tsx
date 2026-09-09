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
import { useEffect, useLayoutEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
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
  const menuRef = useRef<HTMLDivElement>(null)

  useLayoutEffect(() => {
    const menu = menuRef.current
    if (!visible || !position || !menu) return
    const bounds = menu.getBoundingClientRect()
    menu.style.left = `${Math.max(8, Math.min(position.x, window.innerWidth - bounds.width - 8))}px`
    menu.style.top = `${Math.max(8, Math.min(position.y, window.innerHeight - bounds.height - 8))}px`
  }, [visible, position, menuType])

  useEffect(() => {
    const handleClick = () => {
      if (visible) {
        onClose()
      }
    }
    const handleKeyDown = (event: KeyboardEvent) => {
      if (visible && event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
    }

    document.addEventListener('click', handleClick)
    document.addEventListener('keydown', handleKeyDown)
    window.addEventListener('resize', handleClick)
    return () => {
      document.removeEventListener('click', handleClick)
      document.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('resize', handleClick)
    }
  }, [visible, onClose])

  const nodeMenuItems: MenuProps['items'] = [
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
  ]

  const edgeMenuItems: MenuProps['items'] = [
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
  ]

  const canvasMenuItems: MenuProps['items'] = [
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
  ]

  const menuItems = { node: nodeMenuItems, edge: edgeMenuItems, canvas: canvasMenuItems }

  if (!visible || !position || !menuType) return null

  return createPortal(
    <div
      ref={menuRef}
      style={{
        position: 'fixed',
        left: position.x,
        top: position.y,
        maxWidth: 'calc(100vw - 16px)',
        maxHeight: 'calc(100dvh - 16px)',
        overflow: 'auto',
        zIndex: 10000,
        boxShadow:
          '0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05)',
      }}
    >
      <Menu items={menuItems[menuType]} style={{ border: '1px solid var(--color-border-light)' }} />
    </div>,
    document.body
  )
}

export default ContextMenu
