import { useCallback, useEffect, useRef } from 'react'

import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import i18n from '@/shared/i18n'
import { logger } from '@/shared/logging/logger'

export interface ShortcutConfig {
  /** 快捷键ID（用于去重和禁用） */
  id: string
  /** 按键 */
  key: string
  /** 是否需要Ctrl/Cmd */
  ctrl?: boolean
  /** 是否需要Shift */
  shift?: boolean
  /** 是否需要Alt */
  alt?: boolean
  /** 处理函数 */
  handler: () => void
  /** 描述 */
  description: string
  /** 是否禁用 */
  disabled?: boolean
  /** 作用域（可选，用于在特定组件内生效） */
  scope?: string
}

function detectConflicts(shortcuts: ShortcutConfig[]): Map<string, ShortcutConfig[]> {
  const conflicts = new Map<string, ShortcutConfig[]>()
  const keyMap = new Map<string, ShortcutConfig[]>()

  shortcuts.forEach((shortcut) => {
    if (shortcut.disabled) return

    const keyCombo = [
      shortcut.ctrl ? 'Ctrl' : '',
      shortcut.shift ? 'Shift' : '',
      shortcut.alt ? 'Alt' : '',
      shortcut.key.toUpperCase(),
    ]
      .filter(Boolean)
      .join('+')

    const matchingShortcuts = keyMap.get(keyCombo)
    if (matchingShortcuts) {
      matchingShortcuts.push(shortcut)
    } else {
      keyMap.set(keyCombo, [shortcut])
    }
  })

  keyMap.forEach((items, key) => {
    if (items.length > 1) {
      conflicts.set(key, items)
    }
  })

  return conflicts
}

function isEditableShortcutTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return (
    target.matches('input, textarea, [contenteditable="true"]') ||
    Boolean(target.closest('.monaco-editor, .ant-input, .ant-select, .ant-input-number'))
  )
}

function isShortcutInScope(shortcut: ShortcutConfig, scope?: string): boolean {
  return !scope || !shortcut.scope || shortcut.scope === scope
}

function matchesShortcut(event: KeyboardEvent, shortcut: ShortcutConfig): boolean {
  const keyMatch = event.key.toLowerCase() === shortcut.key.toLowerCase()
  const ctrlMatch = shortcut.ctrl
    ? event.ctrlKey || event.metaKey
    : !event.ctrlKey && !event.metaKey
  const shiftMatch = shortcut.shift ? event.shiftKey : !event.shiftKey
  const altMatch = shortcut.alt ? event.altKey : !event.altKey
  return keyMatch && ctrlMatch && shiftMatch && altMatch
}

export function useKeyboardShortcuts(
  shortcuts: ShortcutConfig[],
  options?: {
    /** 是否在开发环境警告冲突 */
    warnConflicts?: boolean
    /** 作用域（可选） */
    scope?: string
  }
): Map<string, ShortcutConfig[]> {
  const shortcutsRef = useRef(shortcuts)
  const conflictsRef = useRef<Map<string, ShortcutConfig[]>>(new Map())

  // 更新shortcuts引用
  useEffect(() => {
    shortcutsRef.current = shortcuts

    // 冲突检测
    const conflicts = detectConflicts(shortcuts)
    conflictsRef.current = conflicts

    // 开发环境警告
    if (options?.warnConflicts && APP_BUILD_CONFIG.buildMode === 'development') {
      if (conflicts.size > 0) {
        logger.warn('[useKeyboardShortcuts] Shortcut conflicts detected:')
        conflicts.forEach((items, key) => {
          logger.warn(`  ${key}: ${items.map((s) => s.id).join(', ')}`)
        })
      }
    }
  }, [shortcuts, options?.warnConflicts])

  const handleKeyDown = useCallback(
    (event: KeyboardEvent) => {
      if (isEditableShortcutTarget(event.target)) return

      const currentShortcuts = shortcutsRef.current

      for (const shortcut of currentShortcuts) {
        if (shortcut.disabled) continue
        if (!isShortcutInScope(shortcut, options?.scope)) continue

        if (matchesShortcut(event, shortcut)) {
          event.preventDefault()
          shortcut.handler()
          break
        }
      }
    },
    [options?.scope]
  )

  useEffect(() => {
    window.addEventListener('keydown', handleKeyDown, { capture: true })
    return () => window.removeEventListener('keydown', handleKeyDown, { capture: true })
  }, [handleKeyDown])

  return conflictsRef.current
}

export const createDesignerShortcuts = (handlers: {
  onSave: () => void
  onUndo: () => void
  onRedo: () => void
  onCopy: () => void
  onPaste: () => void
}): ShortcutConfig[] => {
  const t = i18n.t.bind(i18n)
  return [
    {
      id: 'save',
      key: 's',
      ctrl: true,
      handler: handlers.onSave,
      description: t('designer.shortcuts.item.save'),
    },
    {
      id: 'undo',
      key: 'z',
      ctrl: true,
      handler: handlers.onUndo,
      description: t('designer.shortcuts.item.undo'),
    },
    {
      id: 'redo-z',
      key: 'z',
      ctrl: true,
      shift: true,
      handler: handlers.onRedo,
      description: t('designer.shortcuts.item.redo'),
    },
    {
      id: 'redo-y',
      key: 'y',
      ctrl: true,
      handler: handlers.onRedo,
      description: t('designer.shortcuts.item.redo'),
    },
    {
      id: 'copy',
      key: 'c',
      ctrl: true,
      handler: handlers.onCopy,
      description: t('designer.shortcuts.item.copy'),
    },
    {
      id: 'paste',
      key: 'v',
      ctrl: true,
      handler: handlers.onPaste,
      description: t('designer.shortcuts.item.paste'),
    },
  ]
}
