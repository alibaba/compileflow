import { Export, Graph, Scroller, Selection, Shape, Snapline } from '@antv/x6'
import { MutableRefObject, useEffect, useRef } from 'react'

import { APP_BUILD_CONFIG } from '@/shared/config/buildConfig'
import { createLogger } from '@/shared/logging/logger'

const logger = createLogger('useX6Graph')

export interface UseX6GraphOptions {
  showGridlines?: boolean
  onReady?: (graph: Graph) => void
  /** Whether the graph accepts multiple edges between the same cells. */
  allowMultiEdge?: boolean
  /** Optional domain-specific connection validation. */
  validateConnection?: (args: {
    sourceView?: { cell: { shape: string } } | null
    targetView?: { cell: { shape: string } } | null
  }) => boolean
}

function readCssColor(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function resolveGraphSurface() {
  return {
    paper: readCssColor('--graph-paper', '#f4f6f9'),
    grid: readCssColor('--graph-grid', '#d7dde6'),
  }
}

function measureGraphLayout(container: HTMLDivElement, stableHost?: HTMLElement) {
  const host = stableHost ?? container.parentElement ?? container
  return {
    host,
    width: Math.max(1, host.clientWidth || container.clientWidth || 1),
    height: Math.max(1, host.clientHeight || container.clientHeight || 1),
  }
}

function applyGraphSurface(graph: Graph, showGridlines: boolean): void {
  const { paper, grid } = resolveGraphSurface()
  graph.drawBackground({ color: paper })
  graph.drawGrid({
    size: graph.getGridSize() || 20,
    type: 'dot',
    args: { color: grid, thickness: 1.5 },
  } as never)
  if (showGridlines) {
    graph.showGrid()
  } else {
    graph.hideGrid()
  }
}

function exposeDebugGraph(graph?: Graph): void {
  if (APP_BUILD_CONFIG.buildMode !== 'development' || !APP_BUILD_CONFIG.enableDebug) return
  const debugWindow = window as Window & { __x6Graph?: Graph }
  if (graph) {
    debugWindow.__x6Graph = graph
  } else {
    delete debugWindow.__x6Graph
  }
}

function observeGraphTheme(graph: Graph, showGridlines: () => boolean): MutationObserver {
  const observer = new MutationObserver(() => {
    applyGraphSurface(graph, showGridlines())
  })
  observer.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
  return observer
}

export function useX6Graph(
  containerRef: MutableRefObject<HTMLDivElement | null>,
  options: UseX6GraphOptions = {}
): MutableRefObject<Graph | null> {
  const { showGridlines = true, onReady, allowMultiEdge = true, validateConnection } = options
  const graphRef = useRef<Graph | null>(null)
  const onReadyRef = useRef(onReady)
  const showGridlinesRef = useRef(showGridlines)

  useEffect(() => {
    onReadyRef.current = onReady
    showGridlinesRef.current = showGridlines
  }, [onReady, showGridlines])

  useEffect(() => {
    if (!containerRef.current) {
      logger.warn('Container ref is null, cannot initialize graph')
      return
    }

    logger.info('Initializing X6 Graph...')
    const surface = resolveGraphSurface()
    // The Scroller plugin owns the X6 container dimensions after initialization.
    // Measure the layout host so flex layout cannot initialize the graph at 1px high.
    const initialLayout = measureGraphLayout(containerRef.current)

    const graphInstance = new Graph({
      container: containerRef.current,
      width: initialLayout.width,
      height: initialLayout.height,
      // Scroller reparents the graph container into a virtual content element, so X6's generic
      // auto-resize observer would measure that virtual surface and create a width feedback loop.
      // The stable layout-host ResizeObserver below owns viewport synchronization instead.
      autoResize: false,
      grid: {
        size: 20,
        visible: showGridlines,
        type: 'dot',
        args: { color: surface.grid, thickness: 1.5 },
      },
      background: { color: surface.paper },
      mousewheel: {
        enabled: true,
        modifiers: ['ctrl', 'meta'],
        minScale: 0.1,
        maxScale: 4,
        factor: 1.1,
      },
      panning: false,
      connecting: {
        router: { name: 'orth', args: { padding: 20 } },
        connector: { name: 'rounded', args: { radius: 10 } },
        snap: { radius: 20 },
        allowBlank: false,
        allowLoop: false,
        allowNode: true,
        allowEdge: false,
        allowMulti: allowMultiEdge,
        ...(validateConnection ? { validateConnection } : {}),
        createEdge() {
          return new Shape.Edge({
            shape: 'edge',
            // Seed data so canvas sync can safely read the condition before Redux fills it.
            data: {},
            attrs: {
              line: {
                stroke: '#8f8f8f',
                strokeWidth: 2,
                targetMarker: { name: 'block', width: 12, height: 8 },
              },
              // Wider transparent interaction area so edge:click fires reliably.
              wrap: {
                stroke: 'transparent',
                strokeWidth: 16,
              },
            },
            zIndex: -1,
          })
        },
      },
      highlighting: {
        nodeAvailable: { name: 'className', args: { className: 'available-node' } },
        magnetAvailable: { name: 'className', args: { className: 'available-magnet' } },
        magnetAdsorbed: { name: 'className', args: { className: 'adsorbed-magnet' } },
      },
    })

    const scroller = new Scroller({
      enabled: true,
      pannable: true,
      pageVisible: false,
      pageBreak: false,
    })
    graphInstance.use(scroller)
    graphInstance.use(
      new Selection({
        enabled: true,
        multiple: true,
        rubberband: true,
        movable: true,
        showNodeSelectionBox: true,
        modifiers: 'shift',
        strict: false,
        selectNodeOnMoved: true,
        selectCellOnMoved: true,
      })
    )
    graphInstance.use(new Snapline({ enabled: true, sharp: true }))
    graphInstance.use(new Export())

    graphRef.current = graphInstance

    exposeDebugGraph(graphInstance)

    logger.info('Graph initialized successfully')
    onReadyRef.current?.(graphInstance)

    const handleResize = () => {
      if (containerRef.current) {
        // Scroller reparents the graph container into its virtual content element. Keep measuring
        // the original layout host or every resize feeds the virtual canvas size back into itself.
        const { width, height } = measureGraphLayout(containerRef.current, initialLayout.host)
        scroller.resize(width, height)
      }
    }
    window.addEventListener('resize', handleResize)

    const resizeObserver =
      typeof ResizeObserver !== 'undefined' ? new ResizeObserver(handleResize) : null
    if (resizeObserver) {
      resizeObserver.observe(initialLayout.host)
    }

    const themeObserver = observeGraphTheme(graphInstance, () => showGridlinesRef.current)

    return () => {
      window.removeEventListener('resize', handleResize)
      resizeObserver?.disconnect()
      themeObserver.disconnect()
      logger.info('Disposing graph...')
      // X6 React-shape nodes own nested React roots. Disposing them synchronously during the
      // parent route's React commit triggers React 19's nested-root unmount race warning.
      window.setTimeout(() => graphInstance.dispose(), 0)
      graphRef.current = null
      exposeDebugGraph()
    }
  }, [])

  useEffect(() => {
    const graph = graphRef.current
    if (!graph) return
    applyGraphSurface(graph, showGridlines)
  }, [showGridlines])

  return graphRef
}
