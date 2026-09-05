import type { Graph, Scroller } from '@antv/x6'

const DEFAULT_PADDING = 48
const MIN_SCALE = 0.2
const MAX_SCALE = 1

function readViewport(graph: Graph) {
  const scroller = graph.getPlugin('scroller') as Scroller | undefined
  const { clientHeight: height, clientWidth: width } = scroller?.container ?? graph.container
  return { height, width }
}

/**
 * Keep X6's internal viewport in sync with the flex-sized DOM container before
 * calculating a fit. Scroller can be created while Ant Layout is still
 * resolving its side panels, so its first size is not always the final size.
 */
function resizeGraphToContainer(graph: Graph): boolean {
  const { height, width } = readViewport(graph)
  if (width <= 0 || height <= 0) return false
  const scroller = graph.getPlugin('scroller') as Scroller | undefined
  if (scroller) {
    scroller.resize(width, height)
  } else {
    graph.resize(width, height)
  }
  return true
}

/** Fit all process cells into the *actual* canvas viewport and keep 100% as the maximum zoom. */
export function fitGraphContent(graph: Graph, padding = DEFAULT_PADDING): void {
  if (graph.getCells().length === 0 || !resizeGraphToContainer(graph)) return

  const contentArea = graph.getContentArea({ useCellGeometry: true })
  const { height, width } = readViewport(graph)

  graph.zoomToRect(contentArea, {
    contentArea,
    maxScale: MAX_SCALE,
    minScale: MIN_SCALE,
    padding,
    preserveAspectRatio: true,
    viewportArea: { x: 0, y: 0, width, height },
  })
  graph.centerPoint(contentArea.getCenter().x, contentArea.getCenter().y)
}

/** Restore 100% zoom while keeping the process, rather than the virtual page, centered. */
export function resetGraphView(graph: Graph): void {
  if (graph.getCells().length === 0 || !resizeGraphToContainer(graph)) {
    graph.zoomTo(1)
    return
  }

  const center = graph.getContentArea({ useCellGeometry: true }).getCenter()
  graph.zoomTo(1, { center })
  graph.centerPoint(center.x, center.y)
}

/** Wait until flex layout and X6 cell rendering have both settled. */
export function scheduleInitialGraphFit(graph: Graph): () => void {
  let secondFrame = 0
  const firstFrame = window.requestAnimationFrame(() => {
    secondFrame = window.requestAnimationFrame(() => fitGraphContent(graph))
  })

  return () => {
    window.cancelAnimationFrame(firstFrame)
    if (secondFrame) window.cancelAnimationFrame(secondFrame)
  }
}
