import type { TFunction } from 'i18next'

export type ShortcutCategory = 'file' | 'edit' | 'canvas' | 'node' | 'nav' | 'other'

export interface ShortcutItem {
  key: string
  category: ShortcutCategory
  shortcut: string
  descriptionKey: string
}

/** Categories that appear in the shortcuts modal (only implemented actions). */
export const SHORTCUT_CATEGORIES: ShortcutCategory[] = [
  'file',
  'edit',
  'canvas',
  'node',
  'nav',
  'other',
]

export function buildShortcutItems(t: TFunction): ShortcutItem[] {
  return [
    {
      key: 'save',
      category: 'file',
      shortcut: 'Ctrl+S',
      descriptionKey: 'designer.shortcuts.item.save',
    },
    {
      key: 'undo',
      category: 'edit',
      shortcut: 'Ctrl+Z',
      descriptionKey: 'designer.shortcuts.item.undo',
    },
    {
      key: 'redo',
      category: 'edit',
      shortcut: 'Ctrl+Y / Ctrl+Shift+Z',
      descriptionKey: 'designer.shortcuts.item.redo',
    },
    {
      key: 'copy',
      category: 'edit',
      shortcut: 'Ctrl+C',
      descriptionKey: 'designer.shortcuts.item.copy',
    },
    {
      key: 'paste',
      category: 'edit',
      shortcut: 'Ctrl+V',
      descriptionKey: 'designer.shortcuts.item.paste',
    },
    {
      key: 'delete',
      category: 'edit',
      shortcut: 'Delete',
      descriptionKey: 'designer.shortcuts.item.delete',
    },
    {
      key: 'zoom-in',
      category: 'canvas',
      shortcut: 'Ctrl+Plus / Ctrl+=',
      descriptionKey: 'designer.shortcuts.item.zoomIn',
    },
    {
      key: 'zoom-out',
      category: 'canvas',
      shortcut: 'Ctrl+Minus',
      descriptionKey: 'designer.shortcuts.item.zoomOut',
    },
    {
      key: 'zoom-fit',
      category: 'canvas',
      shortcut: 'Ctrl+0',
      descriptionKey: 'designer.shortcuts.item.zoomFit',
    },
    {
      key: 'node-properties',
      category: 'node',
      shortcut: t('designer.shortcuts.gesture.doubleClickNode'),
      descriptionKey: 'designer.shortcuts.item.nodeProperties',
    },
    {
      key: 'node-connect',
      category: 'node',
      shortcut: t('designer.shortcuts.gesture.dragPort'),
      descriptionKey: 'designer.shortcuts.item.nodeConnect',
    },
    {
      key: 'node-move',
      category: 'node',
      shortcut: t('designer.shortcuts.gesture.dragNode'),
      descriptionKey: 'designer.shortcuts.item.nodeMove',
    },
    {
      key: 'search',
      category: 'nav',
      shortcut: 'Ctrl+F',
      descriptionKey: 'designer.shortcuts.item.search',
    },
    {
      key: 'help',
      category: 'other',
      shortcut: 'Ctrl+/',
      descriptionKey: 'designer.shortcuts.item.help',
    },
  ]
}

export function getCategoryColor(category: ShortcutCategory): string {
  const colorMap: Record<ShortcutCategory, string> = {
    file: 'blue',
    edit: 'green',
    canvas: 'orange',
    node: 'purple',
    nav: 'cyan',
    other: 'default',
  }
  return colorMap[category]
}

export function getCategoryLabel(t: TFunction, category: ShortcutCategory): string {
  return t(`designer.shortcuts.cat.${category}`)
}
