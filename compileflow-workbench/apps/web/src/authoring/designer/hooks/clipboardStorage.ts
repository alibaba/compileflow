import { type ClipboardData, DESIGNER_CLIPBOARD_KEY, parseClipboardData } from './clipboardData'

let inMemoryClipboard: string | null = null
let hasUnpersistedChange = false

export function readDesignerClipboard(): ClipboardData | null {
  let raw: string | null
  try {
    raw = hasUnpersistedChange ? inMemoryClipboard : sessionStorage.getItem(DESIGNER_CLIPBOARD_KEY)
  } catch {
    raw = inMemoryClipboard
  }

  inMemoryClipboard = raw
  const data = parseClipboardData(raw)
  if (data) {
    return data
  }

  if (raw !== null) {
    clearDesignerClipboard()
  }
  return null
}

export function writeDesignerClipboard(data: ClipboardData): void {
  const serialized = JSON.stringify(data)
  inMemoryClipboard = serialized

  try {
    sessionStorage.setItem(DESIGNER_CLIPBOARD_KEY, serialized)
    hasUnpersistedChange = false
  } catch {
    // Failed writes must not let an older persisted value replace this copy.
    hasUnpersistedChange = true
  }
}

export function clearDesignerClipboard(): void {
  inMemoryClipboard = null

  try {
    sessionStorage.removeItem(DESIGNER_CLIPBOARD_KEY)
    hasUnpersistedChange = false
  } catch {
    hasUnpersistedChange = true
  }
}
