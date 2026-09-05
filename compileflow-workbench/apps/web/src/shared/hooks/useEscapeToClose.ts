import { useEffect, useRef } from 'react'

/**
 * Closes a top-level surface consistently in browsers where a portalled dialog
 * does not always receive the initial Escape key during its enter transition.
 */
export function useEscapeToClose(open: boolean, onClose: () => void) {
  const onCloseRef = useRef(onClose)

  useEffect(() => {
    onCloseRef.current = onClose
  }, [onClose])

  useEffect(() => {
    if (!open) return

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      event.stopPropagation()
      onCloseRef.current()
    }

    window.addEventListener('keydown', handleKeyDown, { capture: true })
    return () => window.removeEventListener('keydown', handleKeyDown, { capture: true })
  }, [open])
}
