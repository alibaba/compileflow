import { useLayoutEffect, useRef, useState } from 'react'

import { useMediaQuery } from './useMediaQuery'

export function useCompactContainer(breakpoint = 768) {
  const containerRef = useRef<HTMLDivElement>(null)
  const [width, setWidth] = useState<number>()
  const narrowViewport = useMediaQuery(`(max-width: ${breakpoint}px)`)

  useLayoutEffect(() => {
    const container = containerRef.current
    if (!container) return
    const measure = () => {
      if (container.clientWidth > 0) setWidth(container.clientWidth)
    }
    measure()
    const observer = new ResizeObserver(measure)
    observer.observe(container)
    return () => observer.disconnect()
  }, [])

  return { containerRef, compact: width === undefined ? narrowViewport : width <= breakpoint }
}
