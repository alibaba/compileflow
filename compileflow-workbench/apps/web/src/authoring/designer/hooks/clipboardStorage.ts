import { type ClipboardData, DESIGNER_CLIPBOARD_KEY, parseClipboardData } from './clipboardData'

let inMemoryClipboard: string | null = null

export function readDesignerClipboard(): ClipboardData | null {
  let raw: string | null
  try {
    raw = sessionStorage.getItem(DESIGNER_CLIPBOARD_KEY)
  } catch {
    raw = inMemoryClipboard
  }

  const data = parseClipboardData(raw)
  if (data) {
    inMemoryClipboard = raw
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
  } catch {
    // The in-memory copy remains available for the current page session.
  }
}

export function clearDesignerClipboard(): void {
  inMemoryClipboard = null

  try {
    sessionStorage.removeItem(DESIGNER_CLIPBOARD_KEY)
  } catch {
    // The in-memory copy is already cleared.
  }
}
